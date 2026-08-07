package com.xjie.app.feature.healthdata

import android.net.Uri
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.auth.TestAuthManagerFactory
import com.xjie.app.core.model.HealthReportReview
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.time.LocalDate
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.Tag

@OptIn(ExperimentalCoroutinesApi::class)
class HealthReportDashboardStateTest {
    @Test
    fun firstReadErrorNeverMasqueradesAsEmptyAndCachedReportWinsDuringRefresh() {
        assertEquals(
            HealthReportDashboardContentState.Loading,
            HealthReportDashboardPolicy.contentState(
                loading = true,
                hasReport = false,
                hasError = false,
            ),
        )
        assertEquals(
            HealthReportDashboardContentState.Error,
            HealthReportDashboardPolicy.contentState(
                loading = false,
                hasReport = false,
                hasError = true,
            ),
        )
        assertEquals(
            HealthReportDashboardContentState.Empty,
            HealthReportDashboardPolicy.contentState(
                loading = false,
                hasReport = false,
                hasError = false,
            ),
        )
        assertEquals(
            HealthReportDashboardContentState.Available,
            HealthReportDashboardPolicy.contentState(
                loading = true,
                hasReport = true,
                hasError = true,
            ),
        )
        assertEquals(
            HealthReportDashboardContentState.Empty,
            HealthReportDashboardPolicy.contentState(
                loading = false,
                hasReport = false,
                hasError = false,
            ),
        )
    }

    @Test
    fun workflowMatrixDistinguishesEveryReleaseDashboardPhase() {
        val expected = mapOf(
            "draft" to HealthReportDashboardPhase.Recognizing,
            "uploading" to HealthReportDashboardPhase.Recognizing,
            "recognizing" to HealthReportDashboardPhase.Recognizing,
            "awaiting_confirmation" to HealthReportDashboardPhase.AwaitingReview,
            "committing" to HealthReportDashboardPhase.Committing,
            "completed_score_pending" to HealthReportDashboardPhase.ScorePending,
            "completed" to HealthReportDashboardPhase.Completed,
            "server_future" to HealthReportDashboardPhase.Unknown,
        )
        expected.forEach { (status, phase) ->
            assertEquals(phase, HealthReportDashboardPolicy.phase(status, null))
        }
        assertEquals(
            HealthReportDashboardPhase.Recoverable,
            HealthReportDashboardPolicy.phase("failed", "report_ocr_stalled"),
        )
        assertEquals(
            HealthReportDashboardPhase.Failed,
            HealthReportDashboardPolicy.phase("failed", "unknown_internal_failure"),
        )
        val presentations = HealthReportDashboardPhase.entries
            .associateWith(HealthReportDashboardPolicy::presentation)
        assertEquals("原件已保存 · 解析中", presentations.getValue(HealthReportDashboardPhase.Recognizing).title)
        assertEquals("识别完成 · 待确认", presentations.getValue(HealthReportDashboardPhase.AwaitingReview).title)
        assertEquals("确认完成 · 入库中", presentations.getValue(HealthReportDashboardPhase.Committing).title)
        assertEquals("解析未完成", presentations.getValue(HealthReportDashboardPhase.Recoverable).title)
        assertEquals("已完成解析 · 评分待更新", presentations.getValue(HealthReportDashboardPhase.ScorePending).title)
        assertEquals("已完成解析", presentations.getValue(HealthReportDashboardPhase.Completed).title)
        assertEquals("报告状态待确认", presentations.getValue(HealthReportDashboardPhase.Unknown).title)
    }

    @Test
    fun uploadRuntimeNeverDecoratesDifferentHistoryWorkflow() {
        val oldReport = historyItem(workflowId = WORKFLOW_ID, status = "completed")
        val newRuntime = runtime(workflowId = WORKFLOW_ID + 1, status = "recognizing")
        val state = HealthReportDashboardState(
            items = listOf(oldReport),
            runtime = newRuntime,
            upload = HealthReportDashboardUploadState.Completed(
                runtime = newRuntime,
                duplicate = false,
                acknowledgementDeferred = true,
            ),
        )

        assertEquals(null, state.latestRuntime)
        assertEquals(WORKFLOW_ID + 1, state.pendingUploadRuntime?.workflowId)
        assertEquals(HealthReportDashboardPhase.Completed, state.phase)
        assertEquals(oldReport, state.latestItem)
    }

    @Test
    fun preWorkflowRecoveryNeverRelabelsCachedHistoryReport() {
        val state = HealthReportDashboardState(
            items = listOf(historyItem(workflowId = WORKFLOW_ID, status = "completed")),
            upload = HealthReportDashboardUploadState.RecoveryRequired(
                HealthReportUploadRecovery(
                    assetSetId = 71,
                    failureCode = "missing_page",
                    actionCode = "upload_missing_pages",
                    problemAssetIndices = emptyList(),
                    missingPageIndices = listOf(2),
                ),
            ),
        )

        assertEquals(HealthReportDashboardContentState.Available, state.contentState)
        assertEquals(HealthReportDashboardPhase.Completed, state.phase)
        assertEquals("已完成解析", state.presentation.title)
    }

    @Test
    fun controllerLoadsOneYearLatestAndKeepsCachedReportAfterRefreshFailure() = runTest {
        val auth = authenticatedManager()
        val remote = FakeDashboardRemote()
        val controller = controller(auth, remote)

        controller.refresh()

        assertEquals("2025-08-07", remote.dateFrom)
        assertEquals("2026-08-07", remote.dateTo)
        assertEquals(HealthReportDashboardContentState.Available, controller.state.value.contentState)
        assertEquals(WORKFLOW_ID, controller.state.value.latestItem?.workflowId)
        assertEquals(HealthReportDashboardPhase.ScorePending, controller.state.value.phase)

        remote.historyFailure = IOException("offline with internal_url.example")
        controller.refresh()

        assertEquals(HealthReportDashboardContentState.Available, controller.state.value.contentState)
        assertEquals(WORKFLOW_ID, controller.state.value.latestItem?.workflowId)
        assertEquals("健康报告暂时无法读取，请稍后重试。", controller.state.value.readError)

        val firstFailure = controller(authenticatedManager(), FakeDashboardRemote().apply {
            historyFailure = IOException("offline")
        })
        firstFailure.refresh()
        assertEquals(HealthReportDashboardContentState.Error, firstFailure.state.value.contentState)
        assertFalse(firstFailure.state.value.contentState == HealthReportDashboardContentState.Empty)
    }

    @Test
    fun appLevelUploadStateUsesCoordinatorThenPublishesValidatedDashboard() = runTest {
        val auth = authenticatedManager()
        val uploadCoordinator = mockk<HealthReportUploadCoordinator>()
        val runtime = runtime()
        coEvery {
            uploadCoordinator.upload(
                inputs = any(),
                source = HealthReportUploadSource.Document,
                subjectUserId = SUBJECT_ID,
                reportType = "exam",
                title = null,
                hospital = null,
                reportDate = null,
            )
        } returns HealthReportUploadOutcome.Completed(
            runtime = runtime,
            duplicate = false,
            acknowledgement = HealthReportLocalOriginalAcknowledgementStatus.Accepted(true),
        )
        val controller = HealthReportDashboardController(
            remote = FakeDashboardRemote(),
            uploadCoordinator = uploadCoordinator,
            authManager = auth,
            today = { LocalDate.of(2026, 8, 7) },
            uploadStateGate = Mutex(),
        )

        controller.upload(
            listOf(HealthReportUploadAssetInput("exact".encodeToByteArray(), "report.pdf")),
            HealthReportUploadSource.Document,
        )

        val state = controller.state.value
        assertTrue(state.upload is HealthReportDashboardUploadState.Completed)
        assertEquals(HealthReportDashboardContentState.Available, state.contentState)
        assertEquals(HealthReportDashboardPhase.ScorePending, state.phase)
    }

    @Test
    fun successfulUploadRemainsVisibleWhenHistoryRefreshFails() = runTest {
        val auth = authenticatedManager()
        val uploadCoordinator = mockk<HealthReportUploadCoordinator>()
        val newRuntime = runtime(workflowId = WORKFLOW_ID + 1, status = "recognizing")
        coEvery { uploadCoordinator.upload(any(), any(), any(), any(), any(), any(), any()) } returns
            HealthReportUploadOutcome.Completed(
                runtime = newRuntime,
                duplicate = false,
                acknowledgement = HealthReportLocalOriginalAcknowledgementStatus.Deferred,
            )
        val controller = HealthReportDashboardController(
            remote = FakeDashboardRemote().apply { historyFailure = IOException("offline") },
            uploadCoordinator = uploadCoordinator,
            authManager = auth,
            today = { LocalDate.of(2026, 8, 7) },
            uploadStateGate = Mutex(),
            backgroundScope = this,
        )

        controller.upload(
            listOf(HealthReportUploadAssetInput("exact".encodeToByteArray(), "report.pdf")),
            HealthReportUploadSource.Document,
        )

        val state = controller.state.value
        assertEquals(HealthReportDashboardContentState.Available, state.contentState)
        assertEquals(newRuntime, state.pendingUploadRuntime)
        assertEquals(HealthReportDashboardPhase.Recognizing, state.phase)
        assertEquals("健康报告暂时无法读取，请稍后重试。", state.readError)
    }

    @Test
    fun eventuallyConsistentEmptyHistoryDoesNotEraseActiveUploadRuntime() = runTest {
        val auth = authenticatedManager()
        val uploadCoordinator = mockk<HealthReportUploadCoordinator>()
        val newRuntime = runtime(workflowId = WORKFLOW_ID + 1, status = "recognizing")
        coEvery { uploadCoordinator.upload(any(), any(), any(), any(), any(), any(), any()) } returns
            HealthReportUploadOutcome.Completed(
                runtime = newRuntime,
                duplicate = false,
                acknowledgement = HealthReportLocalOriginalAcknowledgementStatus.Accepted(false),
            )
        val controller = HealthReportDashboardController(
            remote = FakeDashboardRemote().apply { historyItems = emptyList() },
            uploadCoordinator = uploadCoordinator,
            authManager = auth,
            today = { LocalDate.of(2026, 8, 7) },
            uploadStateGate = Mutex(),
            backgroundScope = this,
        )

        controller.upload(
            listOf(HealthReportUploadAssetInput("exact".encodeToByteArray(), "report.pdf")),
            HealthReportUploadSource.Document,
        )

        val state = controller.state.value
        assertEquals(emptyList<HealthReportHistoryItem>(), state.items)
        assertEquals(newRuntime, state.pendingUploadRuntime)
        assertEquals(HealthReportDashboardContentState.Available, state.contentState)
        assertEquals(HealthReportDashboardPhase.Recognizing, state.phase)
    }

    @Test
    fun deferredLocalOriginalAcknowledgementRetriesAfterHistoryLoadWithoutBlockingContent() = runTest {
        val auth = authenticatedManager()
        val retryEntered = CompletableDeferred<Unit>()
        val retryGate = CompletableDeferred<Unit>()
        val uploadCoordinator = mockk<HealthReportUploadCoordinator>()
        coEvery {
            uploadCoordinator.retryLocalOriginalAcknowledgement(
                workflowId = WORKFLOW_ID,
                subjectUserId = SUBJECT_ID,
                expectedOwner = any(),
            )
        } coAnswers {
            retryEntered.complete(Unit)
            retryGate.await()
            HealthReportLocalOriginalAcknowledgementStatus.Accepted(true)
        }
        val controller = HealthReportDashboardController(
            remote = FakeDashboardRemote(),
            uploadCoordinator = uploadCoordinator,
            authManager = auth,
            today = { LocalDate.of(2026, 8, 7) },
            uploadStateGate = Mutex(),
            backgroundScope = this,
        )

        controller.refresh()
        assertEquals(HealthReportDashboardContentState.Available, controller.state.value.contentState)
        assertFalse(retryEntered.isCompleted)

        runCurrent()
        assertTrue(retryEntered.isCompleted)
        assertEquals(HealthReportDashboardContentState.Available, controller.state.value.contentState)
        retryGate.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            uploadCoordinator.retryLocalOriginalAcknowledgement(
                workflowId = WORKFLOW_ID,
                subjectUserId = SUBJECT_ID,
                expectedOwner = any(),
            )
        }
    }

    @Test
    fun accountOrSubjectChangeCancelsDeferredAcknowledgementBeforeNetwork() = runTest {
        val auth = authenticatedManager("account-a")
        val uploadCoordinator = mockk<HealthReportUploadCoordinator>(relaxed = true)
        val controller = HealthReportDashboardController(
            remote = FakeDashboardRemote(),
            uploadCoordinator = uploadCoordinator,
            authManager = auth,
            today = { LocalDate.of(2026, 8, 7) },
            uploadStateGate = Mutex(),
            backgroundScope = this,
        )

        controller.refresh()
        auth.establishSession(jwt("account-b"), "refresh-b", SUBJECT_ID.toString())
        auth.establishSession(jwt("account-a"), "refresh-a2", SUBJECT_ID.toString())
        advanceUntilIdle()

        coVerify(exactly = 0) {
            uploadCoordinator.retryLocalOriginalAcknowledgement(any(), any(), any())
        }
    }

    @Test
    fun historyAcknowledgementSkipsMissingLocalBindingAndExactDuplicates() = runTest {
        val auth = authenticatedManager()
        val uploadCoordinator = mockk<HealthReportUploadCoordinator>()
        coEvery { uploadCoordinator.upload(any(), any(), any(), any(), any(), any(), any()) } returns
            HealthReportUploadOutcome.Completed(
                runtime = runtime(),
                duplicate = true,
                acknowledgement = HealthReportLocalOriginalAcknowledgementStatus.SkippedExactDuplicate,
            )
        val controller = HealthReportDashboardController(
            remote = FakeDashboardRemote(),
            uploadCoordinator = uploadCoordinator,
            authManager = auth,
            today = { LocalDate.of(2026, 8, 7) },
            uploadStateGate = Mutex(),
            backgroundScope = this,
        )

        controller.upload(
            listOf(HealthReportUploadAssetInput("duplicate".encodeToByteArray(), "report.pdf")),
            HealthReportUploadSource.Document,
        )
        advanceUntilIdle()

        assertEquals(HealthReportDashboardContentState.Available, controller.state.value.contentState)
        coVerify(exactly = 0) {
            uploadCoordinator.retryLocalOriginalAcknowledgement(any(), any(), any())
        }

        val missingBindingCoordinator = mockk<HealthReportUploadCoordinator>()
        coEvery {
            missingBindingCoordinator.retryLocalOriginalAcknowledgement(
                workflowId = WORKFLOW_ID,
                subjectUserId = SUBJECT_ID,
                expectedOwner = any(),
            )
        } throws HealthReportLocalOriginalStoreException(
            HealthReportLocalOriginalStoreError.ReportNotFound,
        )
        val missingBindingController = HealthReportDashboardController(
            remote = FakeDashboardRemote(),
            uploadCoordinator = missingBindingCoordinator,
            authManager = auth,
            today = { LocalDate.of(2026, 8, 7) },
            uploadStateGate = Mutex(),
            backgroundScope = this,
        )
        missingBindingController.refresh()
        advanceUntilIdle()

        assertEquals(
            HealthReportDashboardContentState.Available,
            missingBindingController.state.value.contentState,
        )
        assertEquals(null, missingBindingController.state.value.readError)
        coVerify(exactly = 1) {
            missingBindingCoordinator.retryLocalOriginalAcknowledgement(
                workflowId = WORKFLOW_ID,
                subjectUserId = SUBJECT_ID,
                expectedOwner = any(),
            )
        }
    }

    @Test
    fun accountAtoBtoAHistoryResponseCannotBecomeVisibleAgain() = runTest {
        val auth = authenticatedManager("account-a")
        val remote = FakeDashboardRemote().apply { historyGate = CompletableDeferred() }
        val controller = controller(auth, remote)
        val request = async { controller.refresh() }
        remote.historyEntered.await()

        auth.establishSession(jwt("account-b"), "refresh-b", SUBJECT_ID.toString())
        auth.establishSession(jwt("account-a"), "refresh-a2", SUBJECT_ID.toString())
        remote.historyGate?.complete(Unit)
        request.await()

        assertEquals(HealthReportDashboardState.initial(), controller.visibleState())
        assertTrue(controller.state.value.loading)
    }

    @Test
    fun historyEndpointCarriesImmutableOwnerAndOneYearQueries() {
        val method = com.xjie.app.core.network.api.HealthReportUploadApi::class.java
            .declaredMethods.single { it.name == "fetchHistory" }
        assertEquals(
            "api/health-data/report-workflows",
            requireNotNull(method.getAnnotation(GET::class.java)).value,
        )
        assertTrue(method.parameters[0].isAnnotationPresent(Tag::class.java))
        val queryNames = method.parameterAnnotations.flatten()
            .filterIsInstance<retrofit2.http.Query>()
            .map { it.value }
            .toSet()
        assertTrue(queryNames.containsAll(setOf("subject_user_id", "date_from", "date_to")))

        val reviewMethod = com.xjie.app.core.network.api.HealthDataApi::class.java
            .declaredMethods.single { it.name == "reportReview" }
        assertEquals(
            "api/health-data/report-workflows/{workflowId}/review",
            requireNotNull(reviewMethod.getAnnotation(GET::class.java)).value,
        )
        assertTrue(reviewMethod.parameters[0].isAnnotationPresent(Tag::class.java))
    }

    @Test
    fun authoritativeHistoryOpensWithoutLegacyDocument() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val auth = authenticatedManager()
            val owner = requireNotNull(auth.captureAccountScope())
            val repo = mockk<HealthDataRepository>()
            val local = mockk<HealthReportLocalOriginalStoreContract>(relaxed = true)
            coEvery { repo.reportReview(owner, WORKFLOW_ID.toInt(), SUBJECT_ID) } returns review()
            val viewModel = DocumentDetailViewModel(repo, auth, local)

            viewModel.fetch(HealthReportHistoryDestination.encode(WORKFLOW_ID))
            advanceUntilIdle()

            assertEquals(WORKFLOW_ID, viewModel.state.value.authoritativeWorkflowId)
            assertEquals(WORKFLOW_ID.toInt(), viewModel.state.value.review?.workflow_id)
            coVerify(exactly = 0) { repo.document(any()) }
            coVerify(exactly = 1) { repo.reportReview(owner, WORKFLOW_ID.toInt(), SUBJECT_ID) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun traceFailureFallsBackToAccountIsolatedLocalOriginal() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val auth = authenticatedManager()
            val owner = requireNotNull(auth.captureAccountScope())
            val repo = mockk<HealthDataRepository>()
            val local = mockk<HealthReportLocalOriginalStoreContract>()
            val metadata = HealthReportLocalOriginalMetadata(
                assetIndex = 1,
                fileName = "report.pdf",
                mimeType = "application/pdf",
                byteSize = 5,
                sha256 = "a".repeat(64),
            )
            val asset = HealthReportLocalOriginalAsset(
                assetIndex = 1,
                fileName = metadata.fileName,
                mimeType = metadata.mimeType,
                byteSize = metadata.byteSize,
                sha256 = metadata.sha256,
                data = "local".encodeToByteArray(),
            )
            val uri = mockk<Uri>()
            coEvery { repo.reportReview(owner, WORKFLOW_ID.toInt(), SUBJECT_ID) } returns review()
            coEvery { local.listAssets(WORKFLOW_ID, owner.accountScope, SUBJECT_ID) } returns
                listOf(metadata)
            coEvery { local.loadAsset(WORKFLOW_ID, 1, owner.accountScope, SUBJECT_ID) } returns asset
            coEvery { repo.cacheLocalReportOriginal(asset) } returns uri
            val viewModel = DocumentDetailViewModel(repo, auth, local)
            viewModel.fetch(HealthReportHistoryDestination.encode(WORKFLOW_ID))
            advanceUntilIdle()

            viewModel.prepareOriginalDocument()
            advanceUntilIdle()

            assertEquals(uri, viewModel.state.value.originalFileUri)
            coVerify(exactly = 1) { repo.cacheLocalReportOriginal(asset) }
            coVerify(exactly = 0) { repo.cacheOriginalDocument(any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun lateOpenCannotRestoreOldTraceAfterAccountAtoBtoA() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val auth = authenticatedManager("account-a")
            val owner = requireNotNull(auth.captureAccountScope())
            val repo = mockk<HealthDataRepository>()
            val gate = CompletableDeferred<Unit>()
            coEvery { repo.reportReview(owner, WORKFLOW_ID.toInt(), SUBJECT_ID) } coAnswers {
                gate.await()
                review()
            }
            val viewModel = DocumentDetailViewModel(
                repo,
                auth,
                mockk<HealthReportLocalOriginalStoreContract>(relaxed = true),
            )
            viewModel.fetch(HealthReportHistoryDestination.encode(WORKFLOW_ID))
            runCurrent()

            auth.establishSession(jwt("account-b"), "refresh-b", SUBJECT_ID.toString())
            auth.establishSession(jwt("account-a"), "refresh-a2", SUBJECT_ID.toString())
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(null, viewModel.state.value.review)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun controller(
        auth: AuthManager,
        remote: HealthReportDashboardRemote,
    ): HealthReportDashboardController = HealthReportDashboardController(
        remote = remote,
        uploadCoordinator = mockk(relaxed = true),
        authManager = auth,
        today = { LocalDate.of(2026, 8, 7) },
        uploadStateGate = Mutex(),
    )

    private fun authenticatedManager(account: String = "account-a"): AuthManager =
        TestAuthManagerFactory.create().also {
            it.establishSession(jwt(account), "refresh", SUBJECT_ID.toString())
        }

    private fun jwt(account: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        fun encode(value: String): String = encoder.encodeToString(value.encodeToByteArray())
        return listOf(
            encode("{}"),
            encode("{\"sub\":\"$account\"}"),
            encode("signature"),
        ).joinToString(".")
    }

    private class FakeDashboardRemote : HealthReportDashboardRemote {
        var historyFailure: Exception? = null
        var historyGate: CompletableDeferred<Unit>? = null
        val historyEntered = CompletableDeferred<Unit>()
        var dateFrom: String? = null
        var dateTo: String? = null
        var historyItems: List<HealthReportHistoryItem> = listOf(historyItem())

        override suspend fun fetchHistory(
            owner: AuthManager.AccountScopeSnapshot,
            subjectUserId: Long,
            dateFrom: String,
            dateTo: String,
        ): HealthReportHistoryResponse {
            this.dateFrom = dateFrom
            this.dateTo = dateTo
            historyEntered.complete(Unit)
            historyGate?.await()
            historyFailure?.let { throw it }
            return HealthReportHistoryResponse(historyItems)
        }

        override suspend fun fetchRuntime(
            owner: AuthManager.AccountScopeSnapshot,
            workflowId: Long,
            subjectUserId: Long,
        ): HealthReportRuntime = runtime(workflowId = workflowId)
    }

    private companion object {
        const val SUBJECT_ID = 7L
        const val WORKFLOW_ID = 501L

        fun runtime(
            workflowId: Long = WORKFLOW_ID,
            status: String = "completed_score_pending",
        ) = HealthReportRuntime(
            workflowId = workflowId,
            workflowVersion = 3,
            subjectUserId = SUBJECT_ID,
            state = status,
            workflowStatus = status,
        )

        fun historyItem(
            workflowId: Long = WORKFLOW_ID,
            status: String = "completed_score_pending",
        ) = HealthReportHistoryItem(
            workflowId = workflowId,
            status = status,
            reportType = "exam",
            title = "年度体检报告",
            hospital = "市第一人民医院",
            reportDate = "2026-08-01",
            createdAt = "2026-08-01T08:00:00Z",
        )

        fun review() = HealthReportReview(
            workflow_id = WORKFLOW_ID.toInt(),
            legacy_document_id = null,
            subject_user_id = SUBJECT_ID,
            status = "completed_score_pending",
            version = 3,
            report_type = "exam",
            pending_review_count = 0,
            auto_accepted_count = 2,
            admitted_observation_count = 2,
            requires_report_confirmation = false,
            can_confirm = false,
        )
    }
}
