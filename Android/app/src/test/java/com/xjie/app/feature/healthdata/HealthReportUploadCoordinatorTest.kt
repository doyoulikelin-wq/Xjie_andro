package com.xjie.app.feature.healthdata

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.auth.TestAuthManagerFactory
import com.xjie.app.core.network.api.HealthReportUploadApi
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import java.util.ArrayDeque
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Tag

class HealthReportUploadCoordinatorTest {
    @Test
    fun localPersistenceFailureMakesZeroNetworkCalls() = runTest {
        val local = FakeLocalOriginalStore().apply {
            persistFailure = HealthReportLocalOriginalStoreException(
                HealthReportLocalOriginalStoreError.WriteFailed,
            )
        }
        val remote = FakeRemote()
        val coordinator = coordinator(authenticatedManager(), remote, local)

        val error = runCatching {
            coordinator.upload(
                inputs = listOf(asset("private-original", "report.pdf")),
                source = HealthReportUploadSource.Document,
                subjectUserId = SUBJECT_ID,
            )
        }.exceptionOrNull()

        assertTrue(error is HealthReportLocalOriginalStoreException)
        assertEquals(emptyList<String>(), remote.networkCalls)
        assertEquals(listOf("local.persist"), local.events)
    }

    @Test
    fun orderedAssetSetBindsProofAcknowledgesThenValidatesRuntime() = runTest {
        val events = mutableListOf<String>()
        val local = FakeLocalOriginalStore(events)
        val remote = FakeRemote(events)
        val coordinator = coordinator(authenticatedManager(), remote, local)
        val first = asset(byteArrayOf(0, 1, 2, 0xff.toByte()), "page-1.png")
        val second = asset("second-page", "page-2.jpg")

        val outcome = coordinator.upload(
            inputs = listOf(first, second),
            source = HealthReportUploadSource.PhotoLibrary,
            subjectUserId = SUBJECT_ID,
            title = "年度体检",
        )

        assertTrue(outcome is HealthReportUploadOutcome.Completed)
        assertEquals(
            listOf(
                "local.persist",
                "remote.start",
                "remote.asset.1",
                "remote.asset.2",
                "remote.seal",
                "local.bind",
                "local.proof",
                "remote.ack",
                "remote.runtime",
            ),
            events,
        )
        assertEquals("photo_library", remote.sessionRequests.single().mediaKind)
        assertEquals(2, remote.sessionRequests.single().expectedPageCount)
        assertArrayEquals(first.data, remote.uploads[0].input.data)
        assertArrayEquals(second.data, remote.uploads[1].input.data)
        assertEquals(listOf(1, 2), remote.uploads.map { it.assetIndex })
        assertEquals(
            listOf("request-fixed-asset-1", "request-fixed-asset-2"),
            remote.uploads.map { it.clientAssetId },
        )
        assertEquals("request-fixed", remote.acknowledgements.single().clientRequestId)
        assertEquals(2, remote.acknowledgements.single().assetCount)
    }

    @Test
    fun singleFlightRejectsSecondEntryAndAccountAtoBtoAStopsBeforeAssetUpload() = runTest {
        val auth = authenticatedManager(account = "account-a")
        val local = FakeLocalOriginalStore()
        val remote = FakeRemote().apply {
            startGate = CompletableDeferred()
        }
        val coordinator = coordinator(auth, remote, local)
        val first = async {
            runCatching {
                coordinator.upload(
                    listOf(asset("first", "first.png")),
                    HealthReportUploadSource.Camera,
                    SUBJECT_ID,
                )
            }.exceptionOrNull()
        }
        remote.startEntered.await()

        val busy = runCatching {
            coordinator.upload(
                listOf(asset("second", "second.png")),
                HealthReportUploadSource.Camera,
                SUBJECT_ID,
            )
        }.exceptionOrNull()
        assertCoordinatorError(HealthReportUploadCoordinatorError.Busy, busy)
        assertEquals(1, local.persistCalls)

        auth.establishSession(jwt("account-b"), "refresh-b", SUBJECT_ID.toString())
        auth.establishSession(jwt("account-a"), "refresh-a2", SUBJECT_ID.toString())
        remote.startGate?.complete(Unit)

        assertCoordinatorError(HealthReportUploadCoordinatorError.OwnerChanged, first.await())
        assertEquals(0, remote.uploads.size)
        assertEquals(listOf("start"), remote.networkCalls)
    }

    @Test
    fun replacementPersistenceFailureMakesZeroRecoveryNetworkCalls() = runTest {
        val local = FakeLocalOriginalStore()
        val remote = FakeRemote().apply {
            sealResults.add(rejectedSeal(missing = listOf(2)))
        }
        val coordinator = coordinator(authenticatedManager(), remote, local)
        val initial = coordinator.upload(
            listOf(asset("first", "first.jpg"), asset("second", "second.jpg")),
            HealthReportUploadSource.PhotoLibrary,
            SUBJECT_ID,
        )
        assertTrue(initial is HealthReportUploadOutcome.RecoveryRequired)
        remote.networkCalls.clear()
        local.replacementFailure = HealthReportLocalOriginalStoreException(
            HealthReportLocalOriginalStoreError.WriteFailed,
        )

        val error = runCatching {
            coordinator.recoverAsset(asset("replacement", "replacement.jpg"), 2)
        }.exceptionOrNull()

        assertTrue(error is HealthReportLocalOriginalStoreException)
        assertEquals(emptyList<String>(), remote.networkCalls)
    }

    @Test
    fun recoveryPersistsFirstAndReusesAssetSetAndStableRecoveryIdentity() = runTest {
        val events = mutableListOf<String>()
        val local = FakeLocalOriginalStore(events)
        val remote = FakeRemote(events).apply {
            sealResults.add(rejectedSeal(missing = listOf(2)))
            sealResults.add(rejectedSeal(problems = listOf(2)))
            sealResults.add(successSeal())
        }
        val coordinator = coordinator(authenticatedManager(), remote, local)
        val initial = coordinator.upload(
            listOf(asset("first", "first.jpg"), asset("second", "second.jpg")),
            HealthReportUploadSource.PhotoLibrary,
            SUBJECT_ID,
        )
        assertTrue(initial is HealthReportUploadOutcome.RecoveryRequired)
        events.clear()

        val wrongPage = runCatching {
            coordinator.recoverAsset(asset("wrong-page", "page-1.jpg"), 1)
        }.exceptionOrNull()
        assertCoordinatorError(HealthReportUploadCoordinatorError.NoMatchingRecovery, wrongPage)
        assertEquals(emptyList<AssetCall>(), remote.recoveries)
        assertEquals(emptyList<String>(), events)

        val firstRetry = coordinator.recoverAsset(asset("replacement-one", "page-2.jpg"), 2)
        assertTrue(firstRetry is HealthReportUploadOutcome.RecoveryRequired)
        val completed = coordinator.recoverAsset(asset("replacement-two", "page-2.jpg"), 2)

        assertTrue(completed is HealthReportUploadOutcome.Completed)
        assertEquals(listOf(ASSET_SET_ID, ASSET_SET_ID), remote.recoveries.map { it.assetSetId })
        assertEquals(
            listOf("request-fixed-recovery-2", "request-fixed-recovery-2"),
            remote.recoveries.map { it.clientAssetId },
        )
        assertEquals(
            listOf(
                "local.replace.2",
                "remote.recover.2",
                "remote.seal",
                "local.replace.2",
                "remote.recover.2",
                "remote.seal",
                "local.bind",
                "local.proof",
                "remote.ack",
                "remote.runtime",
            ),
            events,
        )
        assertEquals(null, coordinator.pendingRecovery())
    }

    @Test
    fun localProofFailureMakesZeroAcknowledgementOrRuntimeCalls() = runTest {
        val local = FakeLocalOriginalStore().apply {
            proofFailure = HealthReportLocalOriginalStoreException(
                HealthReportLocalOriginalStoreError.IntegrityMismatch(1),
            )
        }
        val remote = FakeRemote()
        val coordinator = coordinator(authenticatedManager(), remote, local)

        val error = runCatching {
            coordinator.upload(
                listOf(asset("private", "report.pdf")),
                HealthReportUploadSource.Document,
                SUBJECT_ID,
            )
        }.exceptionOrNull()

        assertTrue(error is HealthReportLocalOriginalStoreException)
        assertEquals(0, remote.acknowledgements.size)
        assertEquals(0, remote.runtimeFetchCount)
    }

    @Test
    fun acknowledgementFailureDefersRetirementButStillReturnsBoundRuntime() = runTest {
        val local = FakeLocalOriginalStore()
        val remote = FakeRemote().apply {
            acknowledgementFailure = IOException("offline")
        }
        val coordinator = coordinator(authenticatedManager(), remote, local)

        val outcome = coordinator.upload(
            listOf(asset("private", "report.pdf")),
            HealthReportUploadSource.Document,
            SUBJECT_ID,
        ) as HealthReportUploadOutcome.Completed

        assertEquals(HealthReportLocalOriginalAcknowledgementStatus.Deferred, outcome.acknowledgement)
        assertEquals(1, remote.acknowledgements.size)
        assertEquals(1, remote.runtimeFetchCount)
    }

    @Test
    fun exactDuplicateBindsLocallyButNeverAcknowledgesServerRetirement() = runTest {
        val local = FakeLocalOriginalStore()
        val remote = FakeRemote().apply {
            sealResults.add(successSeal(duplicate = true))
        }
        val coordinator = coordinator(authenticatedManager(), remote, local)

        val outcome = coordinator.upload(
            listOf(asset("duplicate", "report.pdf")),
            HealthReportUploadSource.Document,
            SUBJECT_ID,
        ) as HealthReportUploadOutcome.Completed

        assertTrue(outcome.duplicate)
        assertEquals(
            HealthReportLocalOriginalAcknowledgementStatus.SkippedExactDuplicate,
            outcome.acknowledgement,
        )
        assertEquals(0, remote.acknowledgements.size)
        assertEquals(1, local.bindCalls)
        assertEquals(1, local.proofCalls)
        assertEquals(1, remote.runtimeFetchCount)
    }

    @Test
    fun mismatchedServerOwnerFailsClosedBeforeAnyAssetUpload() = runTest {
        val remote = FakeRemote().apply { sessionSubjectOverride = SUBJECT_ID + 1 }
        val coordinator = coordinator(authenticatedManager(), remote, FakeLocalOriginalStore())

        val error = runCatching {
            coordinator.upload(
                listOf(asset("private", "report.pdf")),
                HealthReportUploadSource.Document,
                SUBJECT_ID,
            )
        }.exceptionOrNull()

        assertCoordinatorError(HealthReportUploadCoordinatorError.InvalidServerResponse, error)
        assertEquals(0, remote.uploads.size)
        assertEquals(listOf("start"), remote.networkCalls)
    }

    @Test
    fun apiUsesAccountTaggedVersionedAssetSetRoutes() {
        val methods = HealthReportUploadApi::class.java.declaredMethods.associateBy { it.name }
        assertEquals(
            "api/health-data/report-upload-sessions",
            requireNotNull(
                methods.getValue("startUploadSession").getAnnotation(POST::class.java),
            ).value,
        )
        assertEquals(
            "api/health-data/report-upload-sessions/{assetSetId}/assets/{assetIndex}",
            requireNotNull(methods.getValue("uploadAsset").getAnnotation(PUT::class.java)).value,
        )
        assertNotNull(methods.getValue("uploadAsset").getAnnotation(Multipart::class.java))
        assertEquals(
            "api/health-data/report-upload-sessions/{assetSetId}/assets/{assetIndex}/replacement",
            requireNotNull(methods.getValue("recoverAsset").getAnnotation(PUT::class.java)).value,
        )
        assertEquals(
            "api/health-data/report-upload-sessions/{assetSetId}/seal",
            requireNotNull(
                methods.getValue("sealUploadSession").getAnnotation(POST::class.java),
            ).value,
        )
        assertEquals(
            "api/health-data/report-workflows/{workflowId}/local-original-ack",
            requireNotNull(
                methods.getValue("acknowledgeLocalOriginal").getAnnotation(POST::class.java),
            ).value,
        )
        assertEquals(
            "api/health-data/report-upload-sessions/{assetSetId}",
            requireNotNull(
                methods.getValue("abandonUploadSession").getAnnotation(DELETE::class.java),
            ).value,
        )
        assertEquals(
            "api/health-data/report-workflows/{workflowId}/runtime",
            requireNotNull(methods.getValue("fetchRuntime").getAnnotation(GET::class.java)).value,
        )
        listOf(
            "startUploadSession",
            "uploadAsset",
            "recoverAsset",
            "sealUploadSession",
            "acknowledgeLocalOriginal",
            "abandonUploadSession",
            "fetchRuntime",
        ).forEach { methodName ->
            val method = methods.getValue(methodName)
            assertNotNull("${method.name} must carry the immutable auth owner", method.parameters[0].getAnnotation(Tag::class.java))
        }
    }

    @Test
    fun networkRepositoryPreservesExactReplacementBytesAndFormIdentity() = runTest {
        val api = mockk<HealthReportUploadApi>()
        val file = slot<MultipartBody.Part>()
        val subject = slot<RequestBody>()
        val clientAsset = slot<RequestBody>()
        val owner = AuthManager.AccountScopeSnapshot("scope", SUBJECT_ID.toString(), 3)
        val bytes = byteArrayOf(0, 1, 0x7f, 0x80.toByte(), 0xfe.toByte(), 0xff.toByte())
        val input = asset(bytes, "原件.heic")
        coEvery {
            api.recoverAsset(
                owner = owner,
                assetSetId = ASSET_SET_ID,
                assetIndex = 2,
                file = capture(file),
                subjectUserId = capture(subject),
                clientAssetId = capture(clientAsset),
            )
        } returns HealthReportRecoveredAsset(
            assetId = 102,
            assetIndex = 2,
            clientAssetId = "request-fixed-recovery-2",
            filename = input.fileName,
            mimeType = "image/heic",
            byteSize = bytes.size.toLong(),
            sha256 = healthReportSha256(bytes),
            assetSetId = ASSET_SET_ID,
            sessionStatus = "open",
            receivedAssetCount = 2,
        )

        HealthReportUploadNetworkRepository(api, Json { ignoreUnknownKeys = true }).recoverAsset(
            owner,
            ASSET_SET_ID,
            2,
            SUBJECT_ID,
            input,
            "request-fixed-recovery-2",
        )

        assertArrayEquals(bytes, file.captured.body.bytes())
        assertEquals("image/heic", file.captured.body.contentType()?.toString())
        assertTrue(file.captured.headers?.get("Content-Disposition").orEmpty().contains("name=\"file\""))
        assertEquals(SUBJECT_ID.toString(), subject.captured.utf8())
        assertEquals("request-fixed-recovery-2", clientAsset.captured.utf8())
    }

    private fun coordinator(
        auth: AuthManager,
        remote: HealthReportUploadRemote,
        local: HealthReportLocalOriginalStoreContract,
    ) = HealthReportUploadCoordinator(
        remote = remote,
        authManager = auth,
        localOriginalStore = local,
        makeRequestId = { "request-fixed" },
        uploadGate = Mutex(),
    )

    private fun authenticatedManager(
        account: String = "account-a",
        subject: Long = SUBJECT_ID,
    ): AuthManager = TestAuthManagerFactory.create().also {
        it.establishSession(jwt(account), "refresh", subject.toString())
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

    private fun asset(value: String, name: String): HealthReportUploadAssetInput =
        asset(value.encodeToByteArray(), name)

    private fun asset(value: ByteArray, name: String): HealthReportUploadAssetInput =
        HealthReportUploadAssetInput(value, name)

    private fun rejectedSeal(
        missing: List<Int> = emptyList(),
        problems: List<Int> = emptyList(),
    ) = HealthReportSealResult(
        assetSetId = ASSET_SET_ID,
        status = "rejected",
        failureCode = if (missing.isNotEmpty()) "missing_page" else "blur",
        recoveryAction = if (missing.isNotEmpty()) "upload_missing_pages" else "replace_problem_pages",
        problemAssetIndices = problems,
        missingPageIndices = missing,
    )

    private fun successSeal(duplicate: Boolean = false) = HealthReportSealResult(
        assetSetId = ASSET_SET_ID,
        status = if (duplicate) "sealed" else "attached",
        workflowId = WORKFLOW_ID,
        duplicate = duplicate,
    )

    private fun assertCoordinatorError(expected: HealthReportUploadCoordinatorError, error: Throwable?) {
        val coordinatorError = error as? HealthReportUploadCoordinatorException
            ?: throw AssertionError("expected $expected, got $error")
        assertEquals(expected, coordinatorError.error)
    }

    private fun RequestBody.bytes(): ByteArray = Buffer().also(::writeTo).readByteArray()

    private fun RequestBody.utf8(): String = Buffer().also(::writeTo).readUtf8()

    private data class AssetCall(
        val assetSetId: Long,
        val assetIndex: Int,
        val input: HealthReportUploadAssetInput,
        val clientAssetId: String,
    )

    private class FakeLocalOriginalStore(
        val events: MutableList<String> = mutableListOf(),
    ) : HealthReportLocalOriginalStoreContract {
        var persistFailure: Exception? = null
        var replacementFailure: Exception? = null
        var proofFailure: Exception? = null
        var persistCalls = 0
        var bindCalls = 0
        var proofCalls = 0
        private var clientRequestId = ""
        private var assetCount = 0

        override suspend fun persistUpload(
            inputs: List<HealthReportUploadAssetInput>,
            clientRequestId: String,
            accountScope: String,
            subjectUserId: Long,
        ) {
            events += "local.persist"
            persistCalls += 1
            persistFailure?.let { throw it }
            this.clientRequestId = clientRequestId
            assetCount = inputs.size
        }

        override suspend fun persistReplacement(
            input: HealthReportUploadAssetInput,
            assetIndex: Int,
            clientRequestId: String,
            accountScope: String,
            subjectUserId: Long,
        ) {
            events += "local.replace.$assetIndex"
            replacementFailure?.let { throw it }
            assertEquals(this.clientRequestId, clientRequestId)
        }

        override suspend fun bindWorkflow(
            workflowId: Long,
            clientRequestId: String,
            accountScope: String,
            subjectUserId: Long,
        ) {
            events += "local.bind"
            bindCalls += 1
            assertEquals(this.clientRequestId, clientRequestId)
        }

        override suspend fun loadAssets(
            workflowId: Long,
            accountScope: String,
            subjectUserId: Long,
        ): List<HealthReportLocalOriginalAsset> = error("unexpected loadAssets")

        override suspend fun listAssets(
            workflowId: Long,
            accountScope: String,
            subjectUserId: Long,
        ): List<HealthReportLocalOriginalMetadata> = error("unexpected listAssets")

        override suspend fun loadAsset(
            workflowId: Long,
            assetIndex: Int,
            accountScope: String,
            subjectUserId: Long,
        ): HealthReportLocalOriginalAsset = error("unexpected loadAsset")

        override suspend fun bindingProof(
            workflowId: Long,
            accountScope: String,
            subjectUserId: Long,
        ): HealthReportLocalOriginalBindingProof {
            events += "local.proof"
            proofCalls += 1
            proofFailure?.let { throw it }
            return HealthReportLocalOriginalBindingProof(
                contractVersion = 1,
                clientRequestId = clientRequestId,
                assetCount = assetCount,
                aggregateSha256 = "a".repeat(64),
            )
        }
    }

    private class FakeRemote(
        private val events: MutableList<String> = mutableListOf(),
    ) : HealthReportUploadRemote {
        val networkCalls = mutableListOf<String>()
        val sessionRequests = mutableListOf<HealthReportUploadSessionRequest>()
        val uploads = mutableListOf<AssetCall>()
        val recoveries = mutableListOf<AssetCall>()
        val acknowledgements = mutableListOf<HealthReportLocalOriginalAcknowledgementRequest>()
        val sealResults = ArrayDeque<HealthReportSealResult>()
        var startGate: CompletableDeferred<Unit>? = null
        val startEntered = CompletableDeferred<Unit>()
        var sessionSubjectOverride: Long? = null
        var acknowledgementFailure: Exception? = null
        var runtimeFetchCount = 0

        override suspend fun startUploadSession(
            owner: AuthManager.AccountScopeSnapshot,
            request: HealthReportUploadSessionRequest,
        ): HealthReportUploadSession {
            record("start", "remote.start")
            sessionRequests += request
            startEntered.complete(Unit)
            startGate?.await()
            return HealthReportUploadSession(
                assetSetId = ASSET_SET_ID,
                subjectUserId = sessionSubjectOverride ?: request.subjectUserId,
                status = "open",
                mediaKind = request.mediaKind,
                expectedPageCount = request.expectedPageCount,
                receivedAssetCount = 0,
            )
        }

        override suspend fun uploadAsset(
            owner: AuthManager.AccountScopeSnapshot,
            assetSetId: Long,
            assetIndex: Int,
            subjectUserId: Long,
            input: HealthReportUploadAssetInput,
            clientAssetId: String,
        ): HealthReportUploadedAsset {
            record("asset.$assetIndex", "remote.asset.$assetIndex")
            uploads += AssetCall(assetSetId, assetIndex, input, clientAssetId)
            return HealthReportUploadedAsset(
                assetId = 100 + assetIndex.toLong(),
                assetIndex = assetIndex,
                clientAssetId = clientAssetId,
                filename = input.fileName,
                mimeType = healthReportUploadMimeType(input.fileName),
                byteSize = input.data.size.toLong(),
                sha256 = healthReportSha256(input.data),
            )
        }

        override suspend fun recoverAsset(
            owner: AuthManager.AccountScopeSnapshot,
            assetSetId: Long,
            assetIndex: Int,
            subjectUserId: Long,
            input: HealthReportUploadAssetInput,
            clientAssetId: String,
        ): HealthReportRecoveredAsset {
            record("recover.$assetIndex", "remote.recover.$assetIndex")
            recoveries += AssetCall(assetSetId, assetIndex, input, clientAssetId)
            return HealthReportRecoveredAsset(
                assetId = 200 + assetIndex.toLong(),
                assetIndex = assetIndex,
                clientAssetId = clientAssetId,
                filename = input.fileName,
                mimeType = healthReportUploadMimeType(input.fileName),
                byteSize = input.data.size.toLong(),
                sha256 = healthReportSha256(input.data),
                assetSetId = assetSetId,
                sessionStatus = "open",
                receivedAssetCount = assetIndex,
            )
        }

        override suspend fun sealUploadSession(
            owner: AuthManager.AccountScopeSnapshot,
            assetSetId: Long,
            request: HealthReportSealRequest,
        ): HealthReportSealResult {
            record("seal", "remote.seal")
            return if (sealResults.isEmpty()) {
                HealthReportSealResult(
                    assetSetId = ASSET_SET_ID,
                    status = "attached",
                    workflowId = WORKFLOW_ID,
                )
            } else {
                sealResults.removeFirst()
            }
        }

        override suspend fun acknowledgeLocalOriginal(
            owner: AuthManager.AccountScopeSnapshot,
            workflowId: Long,
            request: HealthReportLocalOriginalAcknowledgementRequest,
        ): HealthReportLocalOriginalAcknowledgementResult {
            record("ack", "remote.ack")
            acknowledgements += request
            acknowledgementFailure?.let { throw it }
            return HealthReportLocalOriginalAcknowledgementResult(
                workflowId = workflowId,
                contractVersion = request.contractVersion,
                accepted = true,
                serverOriginalRetirementEligible = true,
            )
        }

        override suspend fun abandonUploadSession(
            owner: AuthManager.AccountScopeSnapshot,
            assetSetId: Long,
            subjectUserId: Long,
        ): HealthReportUploadSessionAbandonResult {
            record("abandon", "remote.abandon")
            return HealthReportUploadSessionAbandonResult(
                assetSetId = assetSetId,
                subjectUserId = subjectUserId,
                status = "abandoned",
                cleanupPending = false,
            )
        }

        override suspend fun fetchRuntime(
            owner: AuthManager.AccountScopeSnapshot,
            workflowId: Long,
            subjectUserId: Long,
        ): HealthReportRuntime {
            record("runtime", "remote.runtime")
            runtimeFetchCount += 1
            return HealthReportRuntime(
                workflowId = workflowId,
                workflowVersion = 1,
                subjectUserId = subjectUserId,
                state = "recognizing",
                workflowStatus = "recognizing",
            )
        }

        private fun record(network: String, ordered: String) {
            networkCalls += network
            events += ordered
        }
    }

    private companion object {
        const val SUBJECT_ID = 7L
        const val ASSET_SET_ID = 91L
        const val WORKFLOW_ID = 501L
    }
}
