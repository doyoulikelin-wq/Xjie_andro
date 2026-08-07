package com.xjie.app.feature.healthdata

import android.content.Context
import com.xjie.app.core.auth.AuthManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

/**
 * App-level owner of the report asset-set lifecycle.
 *
 * Every entry point must hand exact bytes to this coordinator. The local store is always committed
 * before the first network call, and every later remote boundary revalidates the immutable account,
 * subject, and auth generation captured at the start of the operation.
 */
@Singleton
class HealthReportUploadCoordinator internal constructor(
    private val remote: HealthReportUploadRemote,
    private val authManager: AuthManager,
    private val localOriginalStore: HealthReportLocalOriginalStoreContract,
    private val makeRequestId: () -> String,
    private val uploadGate: Mutex,
) {
    @Inject
    constructor(
        remote: HealthReportUploadNetworkRepository,
        authManager: AuthManager,
        @ApplicationContext context: Context,
    ) : this(
        remote = remote,
        authManager = authManager,
        localOriginalStore = HealthReportLocalOriginalStore.production(context),
        makeRequestId = { UUID.randomUUID().toString() },
        uploadGate = Mutex(),
    )

    private data class PendingRecoveryContext(
        val owner: AuthManager.AccountScopeSnapshot,
        val assetSetId: Long,
        val subjectUserId: Long,
        val clientRequestId: String,
        val sealRequest: HealthReportSealRequest,
        val recovery: HealthReportUploadRecovery,
    )

    @Volatile
    private var pendingRecoveryContext: PendingRecoveryContext? = null

    suspend fun upload(
        inputs: List<HealthReportUploadAssetInput>,
        source: HealthReportUploadSource,
        subjectUserId: Long,
        reportType: String = "exam",
        title: String? = null,
        hospital: String? = null,
        reportDate: String? = null,
    ): HealthReportUploadOutcome = withUploadLease {
        validateInputs(inputs, reportType, title, hospital, reportDate)
        val owner = captureOwner(subjectUserId)
        val requestId = normalizedRequestId()

        // This is the permanent zero-network boundary. No remote method is reachable before it.
        localOriginalStore.persistUpload(
            inputs = inputs,
            clientRequestId = requestId,
            accountScope = owner.accountScope,
            subjectUserId = subjectUserId,
        )
        ensureCurrent(owner, subjectUserId)
        pendingRecoveryContext = null

        val mediaKind = mediaKind(source, inputs)
        val sessionRequest = HealthReportUploadSessionRequest(
            subjectUserId = subjectUserId,
            clientRequestId = requestId,
            mediaKind = mediaKind.wireValue,
            expectedPageCount = inputs.size.takeUnless { mediaKind == HealthReportUploadMediaKind.Pdf },
        )
        val session = remote.startUploadSession(owner, sessionRequest)
        ensureCurrent(owner, subjectUserId)
        validateSession(session, sessionRequest)

        inputs.forEachIndexed { offset, input ->
            val assetIndex = offset + 1
            val clientAssetId = initialClientAssetId(requestId, assetIndex)
            ensureCurrent(owner, subjectUserId)
            val uploaded = remote.uploadAsset(
                owner = owner,
                assetSetId = session.assetSetId,
                assetIndex = assetIndex,
                subjectUserId = subjectUserId,
                input = input,
                clientAssetId = clientAssetId,
            )
            ensureCurrent(owner, subjectUserId)
            validateUploadedAsset(uploaded, input, assetIndex, clientAssetId)
        }

        val sealRequest = HealthReportSealRequest(
            subjectUserId = subjectUserId,
            reportType = reportType,
            title = normalizedTitle(title, inputs),
            hospital = hospital?.trim()?.takeIf(String::isNotEmpty),
            reportDate = reportDate?.trim()?.takeIf(String::isNotEmpty),
        )
        val seal = remote.sealUploadSession(owner, session.assetSetId, sealRequest)
        ensureCurrent(owner, subjectUserId)
        finishSeal(
            owner = owner,
            assetSetId = session.assetSetId,
            clientRequestId = requestId,
            sealRequest = sealRequest,
            seal = seal,
        )
    }

    suspend fun recoverAsset(
        input: HealthReportUploadAssetInput,
        assetIndex: Int,
    ): HealthReportUploadOutcome = withUploadLease {
        val context = pendingRecoveryContext
            ?: fail(HealthReportUploadCoordinatorError.NoMatchingRecovery)
        if (input.data.isEmpty() || !context.recovery.accepts(assetIndex)) {
            fail(HealthReportUploadCoordinatorError.NoMatchingRecovery)
        }
        ensureCurrent(context.owner, context.subjectUserId)

        // Replacement has the same local-first invariant as a new upload.
        localOriginalStore.persistReplacement(
            input = input,
            assetIndex = assetIndex,
            clientRequestId = context.clientRequestId,
            accountScope = context.owner.accountScope,
            subjectUserId = context.subjectUserId,
        )
        ensureCurrent(context.owner, context.subjectUserId)

        val clientAssetId = recoveryClientAssetId(context.clientRequestId, assetIndex)
        val recovered = remote.recoverAsset(
            owner = context.owner,
            assetSetId = context.assetSetId,
            assetIndex = assetIndex,
            subjectUserId = context.subjectUserId,
            input = input,
            clientAssetId = clientAssetId,
        )
        ensureCurrent(context.owner, context.subjectUserId)
        validateRecoveredAsset(
            recovered = recovered,
            input = input,
            assetSetId = context.assetSetId,
            assetIndex = assetIndex,
            clientAssetId = clientAssetId,
        )

        val seal = remote.sealUploadSession(
            context.owner,
            context.assetSetId,
            context.sealRequest,
        )
        ensureCurrent(context.owner, context.subjectUserId)
        finishSeal(
            owner = context.owner,
            assetSetId = context.assetSetId,
            clientRequestId = context.clientRequestId,
            sealRequest = context.sealRequest,
            seal = seal,
        )
    }

    /** Retries only the proof/ACK phase after a previous offline or old-server failure. */
    suspend fun retryLocalOriginalAcknowledgement(
        workflowId: Long,
        subjectUserId: Long,
        expectedOwner: AuthManager.AccountScopeSnapshot? = null,
    ): HealthReportLocalOriginalAcknowledgementStatus {
        if (workflowId <= 0L) fail(HealthReportUploadCoordinatorError.InvalidInput)
        val owner = expectedOwner ?: captureOwner(subjectUserId)
        if (owner.subjectId.trim().toLongOrNull() != subjectUserId || !authManager.isCurrent(owner)) {
            fail(HealthReportUploadCoordinatorError.OwnerChanged)
        }
        val proof = localOriginalStore.bindingProof(
            workflowId = workflowId,
            accountScope = owner.accountScope,
            subjectUserId = subjectUserId,
        )
        ensureCurrent(owner, subjectUserId)
        validateProof(proof)
        return acknowledge(owner, workflowId, subjectUserId, proof)
    }

    suspend fun abandonRecovery() = withUploadLease {
        val context = pendingRecoveryContext
            ?: fail(HealthReportUploadCoordinatorError.NoMatchingRecovery)
        ensureCurrent(context.owner, context.subjectUserId)
        val result = remote.abandonUploadSession(
            owner = context.owner,
            assetSetId = context.assetSetId,
            subjectUserId = context.subjectUserId,
        )
        ensureCurrent(context.owner, context.subjectUserId)
        if (
            result.assetSetId != context.assetSetId ||
            result.subjectUserId != context.subjectUserId ||
            result.status != "abandoned" ||
            result.cleanupPending
        ) {
            fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
        }
        if (pendingRecoveryContext === context) pendingRecoveryContext = null
    }

    fun pendingRecovery(): HealthReportUploadRecovery? {
        val context = pendingRecoveryContext ?: return null
        if (!authManager.isCurrent(context.owner)) {
            if (pendingRecoveryContext === context) pendingRecoveryContext = null
            return null
        }
        return context.recovery
    }

    private suspend fun finishSeal(
        owner: AuthManager.AccountScopeSnapshot,
        assetSetId: Long,
        clientRequestId: String,
        sealRequest: HealthReportSealRequest,
        seal: HealthReportSealResult,
    ): HealthReportUploadOutcome {
        val failureCode = seal.failureCode?.trim()?.takeIf(String::isNotEmpty)
        validateSealEnvelope(seal, assetSetId, failureCode)
        if (failureCode != null) {
            val recovery = recoveryFrom(seal, failureCode)
            pendingRecoveryContext = PendingRecoveryContext(
                owner = owner,
                assetSetId = assetSetId,
                subjectUserId = sealRequest.subjectUserId,
                clientRequestId = clientRequestId,
                sealRequest = sealRequest,
                recovery = recovery,
            )
            return HealthReportUploadOutcome.RecoveryRequired(recovery)
        }

        val workflowId = seal.workflowId?.takeIf { it > 0L }
            ?: fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
        localOriginalStore.bindWorkflow(
            workflowId = workflowId,
            clientRequestId = clientRequestId,
            accountScope = owner.accountScope,
            subjectUserId = sealRequest.subjectUserId,
        )
        ensureCurrent(owner, sealRequest.subjectUserId)
        val proof = localOriginalStore.bindingProof(
            workflowId = workflowId,
            accountScope = owner.accountScope,
            subjectUserId = sealRequest.subjectUserId,
        )
        ensureCurrent(owner, sealRequest.subjectUserId)
        validateProof(proof)
        if (proof.clientRequestId != clientRequestId) {
            fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
        }

        val acknowledgement = if (seal.duplicate) {
            HealthReportLocalOriginalAcknowledgementStatus.SkippedExactDuplicate
        } else {
            acknowledge(owner, workflowId, sealRequest.subjectUserId, proof)
        }
        ensureCurrent(owner, sealRequest.subjectUserId)
        val runtime = remote.fetchRuntime(
            owner = owner,
            workflowId = workflowId,
            subjectUserId = sealRequest.subjectUserId,
        )
        ensureCurrent(owner, sealRequest.subjectUserId)
        if (
            runtime.workflowId != workflowId ||
            runtime.subjectUserId != sealRequest.subjectUserId ||
            runtime.workflowVersion <= 0
        ) {
            fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
        }
        pendingRecoveryContext = null
        return HealthReportUploadOutcome.Completed(
            runtime = runtime,
            duplicate = seal.duplicate,
            acknowledgement = acknowledgement,
        )
    }

    private suspend fun acknowledge(
        owner: AuthManager.AccountScopeSnapshot,
        workflowId: Long,
        subjectUserId: Long,
        proof: HealthReportLocalOriginalBindingProof,
    ): HealthReportLocalOriginalAcknowledgementStatus {
        val request = HealthReportLocalOriginalAcknowledgementRequest(
            subjectUserId = subjectUserId,
            clientRequestId = proof.clientRequestId,
            contractVersion = proof.contractVersion,
            assetCount = proof.assetCount,
            aggregateSha256 = proof.aggregateSha256,
        )
        return try {
            val result = remote.acknowledgeLocalOriginal(owner, workflowId, request)
            ensureCurrent(owner, subjectUserId)
            if (
                result.workflowId != workflowId ||
                result.contractVersion != proof.contractVersion ||
                !result.accepted
            ) {
                fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
            }
            HealthReportLocalOriginalAcknowledgementStatus.Accepted(
                result.serverOriginalRetirementEligible,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: HealthReportUploadCoordinatorException) {
            throw error
        } catch (_: Exception) {
            // Missing ACK can never authorize deletion; the backend retains its processing copy.
            ensureCurrent(owner, subjectUserId)
            HealthReportLocalOriginalAcknowledgementStatus.Deferred
        }
    }

    private fun validateSession(
        session: HealthReportUploadSession,
        request: HealthReportUploadSessionRequest,
    ) {
        if (
            session.assetSetId <= 0L ||
            session.subjectUserId != request.subjectUserId ||
            session.status != "open" ||
            session.mediaKind != request.mediaKind ||
            session.expectedPageCount != request.expectedPageCount ||
            session.receivedAssetCount != 0
        ) {
            fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
        }
    }

    private fun validateUploadedAsset(
        uploaded: HealthReportUploadedAsset,
        input: HealthReportUploadAssetInput,
        assetIndex: Int,
        clientAssetId: String,
    ) {
        val bytes = input.data
        if (
            uploaded.assetId <= 0L ||
            uploaded.assetIndex != assetIndex ||
            uploaded.clientAssetId != clientAssetId ||
            uploaded.byteSize != bytes.size.toLong() ||
            uploaded.sha256 != healthReportSha256(bytes)
        ) {
            fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
        }
    }

    private fun validateRecoveredAsset(
        recovered: HealthReportRecoveredAsset,
        input: HealthReportUploadAssetInput,
        assetSetId: Long,
        assetIndex: Int,
        clientAssetId: String,
    ) {
        val bytes = input.data
        if (
            recovered.assetId <= 0L ||
            recovered.assetSetId != assetSetId ||
            recovered.assetIndex != assetIndex ||
            recovered.clientAssetId != clientAssetId ||
            recovered.byteSize != bytes.size.toLong() ||
            recovered.sha256 != healthReportSha256(bytes) ||
            recovered.sessionStatus != "open" ||
            recovered.receivedAssetCount !in 1..MAX_ASSET_COUNT
        ) {
            fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
        }
    }

    private fun validateSealEnvelope(
        seal: HealthReportSealResult,
        assetSetId: Long,
        failureCode: String?,
    ) {
        if (seal.assetSetId != assetSetId) {
            fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
        }
        if (failureCode != null) {
            if (seal.status != "rejected" || seal.workflowId != null || seal.duplicate) {
                fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
            }
            return
        }
        val expectedStatus = if (seal.duplicate) "sealed" else "attached"
        if (
            seal.status != expectedStatus ||
            seal.workflowId == null ||
            seal.recoveryAction != null ||
            seal.problemAssetIndices.isNotEmpty() ||
            seal.missingPageIndices.isNotEmpty()
        ) {
            fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
        }
    }

    private fun validateProof(proof: HealthReportLocalOriginalBindingProof) {
        if (
            proof.contractVersion != LOCAL_ORIGINAL_CONTRACT_VERSION ||
            proof.clientRequestId.isBlank() ||
            proof.assetCount !in 1..MAX_ASSET_COUNT ||
            !SHA256_PATTERN.matches(proof.aggregateSha256)
        ) {
            fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
        }
    }

    private fun validateInputs(
        inputs: List<HealthReportUploadAssetInput>,
        reportType: String,
        title: String?,
        hospital: String?,
        reportDate: String?,
    ) {
        if (
            inputs.isEmpty() ||
            inputs.size > MAX_ASSET_COUNT ||
            inputs.any { it.data.isEmpty() } ||
            reportType !in REPORT_TYPES ||
            (title != null && title.trim().let { it.isEmpty() || it.length > MAX_TITLE_LENGTH }) ||
            (hospital != null && hospital.trim().length > MAX_HOSPITAL_LENGTH) ||
            (reportDate != null && !REPORT_DATE_PATTERN.matches(reportDate.trim()))
        ) {
            fail(HealthReportUploadCoordinatorError.InvalidInput)
        }
    }

    private fun captureOwner(subjectUserId: Long): AuthManager.AccountScopeSnapshot {
        val owner = authManager.captureAccountScope()
            ?: fail(HealthReportUploadCoordinatorError.InvalidOwner)
        if (
            subjectUserId <= 0L ||
            owner.subjectId.trim().toLongOrNull() != subjectUserId ||
            !authManager.isCurrent(owner)
        ) {
            fail(HealthReportUploadCoordinatorError.InvalidOwner)
        }
        return owner
    }

    private fun ensureCurrent(owner: AuthManager.AccountScopeSnapshot, subjectUserId: Long) {
        if (
            owner.subjectId.trim().toLongOrNull() != subjectUserId ||
            !authManager.isCurrent(owner)
        ) {
            fail(HealthReportUploadCoordinatorError.OwnerChanged)
        }
    }

    private fun normalizedRequestId(): String {
        val value = makeRequestId().trim().take(MAX_REQUEST_ID_LENGTH)
        return value.takeIf(String::isNotEmpty)
            ?: fail(HealthReportUploadCoordinatorError.InvalidInput)
    }

    private suspend fun <T> withUploadLease(block: suspend () -> T): T {
        val token = Any()
        if (!uploadGate.tryLock(token)) fail(HealthReportUploadCoordinatorError.Busy)
        return try {
            block()
        } finally {
            uploadGate.unlock(token)
        }
    }

    private fun recoveryFrom(
        seal: HealthReportSealResult,
        failureCode: String,
    ): HealthReportUploadRecovery = HealthReportUploadRecovery(
        assetSetId = seal.assetSetId,
        failureCode = failureCode,
        actionCode = seal.recoveryAction?.trim()?.takeIf(String::isNotEmpty)
            ?: defaultRecoveryAction(failureCode),
        problemAssetIndices = validRecoveryIndices(seal.problemAssetIndices),
        missingPageIndices = validRecoveryIndices(seal.missingPageIndices),
    )

    private fun validRecoveryIndices(values: List<Int>): List<Int> {
        val result = values.distinct().sorted()
        if (result.any { it !in 1..MAX_ASSET_COUNT }) {
            fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
        }
        return result
    }

    private fun mediaKind(
        source: HealthReportUploadSource,
        inputs: List<HealthReportUploadAssetInput>,
    ): HealthReportUploadMediaKind {
        if (inputs.size == 1) {
            when (inputs.single().fileName.substringAfterLast('.', "").lowercase()) {
                "pdf" -> return HealthReportUploadMediaKind.Pdf
                "csv" -> return HealthReportUploadMediaKind.Csv
            }
        }
        return when (source) {
            HealthReportUploadSource.Camera -> HealthReportUploadMediaKind.Camera
            HealthReportUploadSource.PhotoLibrary -> HealthReportUploadMediaKind.PhotoLibrary
            HealthReportUploadSource.Document,
            HealthReportUploadSource.ChatAttachment,
            HealthReportUploadSource.ExternalImport,
            -> HealthReportUploadMediaKind.Legacy
        }
    }

    private fun normalizedTitle(
        explicit: String?,
        inputs: List<HealthReportUploadAssetInput>,
    ): String {
        explicit?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
        val first = safeDisplayName(inputs.first().fileName, 1)
        if (inputs.size == 1) return first.take(MAX_TITLE_LENGTH)
        val stem = first.substringBeforeLast('.', first).ifBlank { "健康报告" }
        return "$stem 等 ${inputs.size} 页".take(MAX_TITLE_LENGTH)
    }

    private fun safeDisplayName(raw: String, index: Int): String {
        val name = raw.replace('\\', '/')
            .substringAfterLast('/')
            .filterNot(Char::isISOControl)
            .trim()
            .take(MAX_TITLE_LENGTH)
        return name.takeUnless { it.isBlank() || it == "." || it == ".." }
            ?: "报告原件-$index"
    }

    private fun initialClientAssetId(requestId: String, assetIndex: Int): String =
        "$requestId-asset-$assetIndex"

    private fun recoveryClientAssetId(requestId: String, assetIndex: Int): String =
        "$requestId-recovery-$assetIndex"

    private fun defaultRecoveryAction(failureCode: String): String = when (failureCode) {
        "missing_page", "invalid_page_manifest" -> "upload_missing_pages"
        "blur", "blurry_image", "blank_page", "low_resolution", "unreadable_image" ->
            "replace_problem_pages"
        else -> "retry_upload"
    }

    private fun fail(error: HealthReportUploadCoordinatorError): Nothing {
        throw HealthReportUploadCoordinatorException(error)
    }

    private companion object {
        const val MAX_ASSET_COUNT = 100
        const val MAX_REQUEST_ID_LENGTH = 60
        const val MAX_TITLE_LENGTH = 256
        const val MAX_HOSPITAL_LENGTH = 256
        const val LOCAL_ORIGINAL_CONTRACT_VERSION = 1
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        val REPORT_DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        val REPORT_TYPES = setOf("unknown", "exam", "lab", "imaging", "medical_record", "other")
    }
}
