package com.xjie.app.feature.healthdata

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.auth.TestAuthManagerFactory
import com.xjie.app.core.model.HealthReportConfirmBody
import com.xjie.app.core.model.HealthReportManualCandidateBody
import com.xjie.app.core.model.HealthReportReview
import com.xjie.app.core.model.IndicatorInfo
import com.xjie.app.core.model.WatchedIndicatorItem
import com.xjie.app.core.network.AuthInterceptor
import com.xjie.app.core.network.api.HealthDataApi
import com.xjie.app.core.network.api.WatchBody
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class HealthMutationAccountIsolationTest {
    @Test
    fun staleOwnerTagSendsZeroReportAndIndicatorMutationsAfterAccountAtoBtoA() = runTest {
        val auth = authenticatedManager()
        val staleOwner = requireNotNull(auth.captureAccountScope())
        switchAccountAtoBtoA(auth)
        val server = MockWebServer()
        repeat(4) { server.enqueue(MockResponse().setResponseCode(500)) }
        server.start()
        try {
            val api = healthDataApi(server, auth)
            val calls: List<suspend () -> Unit> = listOf(
                {
                    api.confirmReport(
                        staleOwner,
                        WORKFLOW_ID,
                        HealthReportConfirmBody(
                            subject_user_id = SUBJECT_ID,
                            client_event_id = "confirm-event",
                            workflow_version = 3,
                        ),
                    )
                    Unit
                },
                {
                    api.addManualReportCandidate(
                        staleOwner,
                        WORKFLOW_ID,
                        HealthReportManualCandidateBody(
                            subject_user_id = SUBJECT_ID,
                            workflow_version = 3,
                            client_event_id = "manual-event",
                            canonical_name = "ALT",
                            raw_name = "ALT",
                            value_numeric = 42.0,
                        ),
                    )
                    Unit
                },
                { api.watch(staleOwner, WatchBody("ALT")) },
                { api.unwatch(staleOwner, "AST") },
            )

            val failures = calls.map { call -> runCatching { call() }.exceptionOrNull() }

            assertTrue(failures.all { it is IOException })
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun reportMutationsCaptureOwnerBeforeDispatchAndSendZeroRequestsAfterAccountAtoBtoA() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val auth = authenticatedManager()
                val owner = requireNotNull(auth.captureAccountScope())
                val repo = mockk<HealthDataRepository>(relaxed = true)
                coEvery { repo.reportReview(owner, WORKFLOW_ID, SUBJECT_ID) } returns review()
                val viewModel = documentViewModel(repo, auth)
                viewModel.fetch(HealthReportHistoryDestination.encode(WORKFLOW_ID.toLong()))
                advanceUntilIdle()
                var manualCallback = false

                viewModel.confirmReport()
                viewModel.addManualCandidate(
                    ManualReportCandidateDraft(name = "ALT", value = "42"),
                ) { manualCallback = true }
                switchAccountAtoBtoA(auth)
                advanceUntilIdle()

                coVerify(exactly = 0) { repo.confirmReport(any(), any(), any()) }
                coVerify(exactly = 0) { repo.addManualReportCandidate(any(), any(), any()) }
                assertFalse(manualCallback)
                assertFalse(viewModel.state.value.confirming)
                assertFalse(viewModel.state.value.addingManualCandidate)
                assertTrue(viewModel.state.value.error?.contains("变化") == true)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun lateReportMutationResponsesCannotOverwriteAfterAccountAtoBtoA() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = authenticatedManager()
            val owner = requireNotNull(auth.captureAccountScope())
            val repo = mockk<HealthDataRepository>(relaxed = true)
            val confirmResponse = CompletableDeferred<HealthReportReview>()
            val manualResponse = CompletableDeferred<HealthReportReview>()
            coEvery { repo.reportReview(owner, WORKFLOW_ID, SUBJECT_ID) } returns review()
            coEvery { repo.confirmReport(owner, WORKFLOW_ID, any()) } coAnswers {
                confirmResponse.await()
            }
            coEvery { repo.addManualReportCandidate(owner, WORKFLOW_ID, any()) } coAnswers {
                manualResponse.await()
            }
            val viewModel = documentViewModel(repo, auth)
            viewModel.fetch(HealthReportHistoryDestination.encode(WORKFLOW_ID.toLong()))
            advanceUntilIdle()
            var manualCallback = false

            viewModel.confirmReport()
            viewModel.addManualCandidate(
                ManualReportCandidateDraft(name = "ALT", value = "42"),
            ) { manualCallback = true }
            runCurrent()
            switchAccountAtoBtoA(auth)
            confirmResponse.complete(review(version = 4, status = "completed", canConfirm = false))
            manualResponse.complete(review(version = 4))
            advanceUntilIdle()

            assertEquals(3, viewModel.state.value.review?.version)
            assertEquals("awaiting_confirmation", viewModel.state.value.review?.status)
            assertFalse(manualCallback)
            assertFalse(viewModel.state.value.confirming)
            assertFalse(viewModel.state.value.addingManualCandidate)
            assertTrue(viewModel.state.value.error?.contains("变化") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun indicatorBatchCapturesOneOwnerAndSendsZeroMutationsAfterAccountAtoBtoA() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = authenticatedManager()
            val repo = indicatorRepository()
            val viewModel = IndicatorTrendViewModel(repo, auth)
            viewModel.fetchIndicators()
            advanceUntilIdle()

            viewModel.applySelection(setOf("ALT"))
            switchAccountAtoBtoA(auth)
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.watch(any(), any()) }
            coVerify(exactly = 0) { repo.unwatch(any(), any()) }
            assertEquals(listOf("AST"), viewModel.state.value.watchedNames)
            assertEquals(
                "账号或健康数据所属用户已变化，请重新打开后再试。",
                IndicatorTrendErrorPresentation.message(requireNotNull(viewModel.state.value.error)),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun indicatorLateMutationCannotRefreshOrOverwriteAfterAccountAtoBtoA() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = authenticatedManager()
            val owner = requireNotNull(auth.captureAccountScope())
            val repo = indicatorRepository()
            val watchResponse = CompletableDeferred<Unit>()
            coEvery { repo.watch(owner, "ALT") } coAnswers { watchResponse.await() }
            val viewModel = IndicatorTrendViewModel(repo, auth)
            viewModel.fetchIndicators()
            advanceUntilIdle()

            viewModel.applySelection(setOf("ALT"))
            runCurrent()
            switchAccountAtoBtoA(auth)
            watchResponse.complete(Unit)
            advanceUntilIdle()

            coVerify(exactly = 1) { repo.watch(owner, "ALT") }
            coVerify(exactly = 0) { repo.unwatch(any(), any()) }
            assertEquals(listOf("AST"), viewModel.state.value.watchedNames)
            assertTrue(viewModel.state.value.error?.contains("变化") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun indicatorRepository(): HealthDataRepository =
        mockk<HealthDataRepository>(relaxed = true).also { repo ->
            coEvery { repo.listIndicators() } returns listOf(
                IndicatorInfo(name = "ALT", category = "liver", count = 2),
                IndicatorInfo(name = "AST", category = "liver", count = 2),
            )
            coEvery { repo.watchedIndicators() } returns listOf(
                WatchedIndicatorItem(
                    indicator_name = "AST",
                    category = "liver",
                    display_order = 0,
                ),
            )
            coEvery { repo.trends(listOf("AST")) } returns emptyList()
        }

    private fun documentViewModel(
        repo: HealthDataRepository,
        auth: AuthManager,
    ): DocumentDetailViewModel = DocumentDetailViewModel(
        repo = repo,
        authManager = auth,
        localOriginalStore = mockk<HealthReportLocalOriginalStoreContract>(relaxed = true),
    )

    private fun healthDataApi(server: MockWebServer, auth: AuthManager): HealthDataApi =
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(auth)).build())
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(HealthDataApi::class.java)

    private fun authenticatedManager(): AuthManager = TestAuthManagerFactory.create().also {
        it.establishSession(jwt("account-a", "first"), "refresh-a", SUBJECT_ID.toString())
    }

    private fun switchAccountAtoBtoA(auth: AuthManager) {
        auth.establishSession(jwt("account-b", "middle"), "refresh-b", SUBJECT_ID.toString())
        auth.establishSession(jwt("account-a", "second"), "refresh-a2", SUBJECT_ID.toString())
    }

    private fun jwt(subject: String, nonce: String): String {
        fun segment(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        return "${segment("""{"alg":"none"}""")}." +
            "${segment("""{"sub":"$subject","nonce":"$nonce"}""")}.signature"
    }

    private fun review(
        version: Int = 3,
        status: String = "awaiting_confirmation",
        canConfirm: Boolean = true,
    ): HealthReportReview = HealthReportReview(
        workflow_id = WORKFLOW_ID,
        subject_user_id = SUBJECT_ID,
        status = status,
        version = version,
        report_type = "exam",
        pending_review_count = 0,
        auto_accepted_count = 0,
        admitted_observation_count = 0,
        requires_report_confirmation = status == "awaiting_confirmation",
        can_confirm = canConfirm,
    )

    private companion object {
        const val SUBJECT_ID = 7L
        const val WORKFLOW_ID = 501
    }
}
