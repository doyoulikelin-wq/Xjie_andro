package com.xjie.app.feature.healthdata

import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class HealthReportUploadSource {
    Camera,
    PhotoLibrary,
    Document,
    ChatAttachment,
    ExternalImport,
}

enum class HealthReportUploadMediaKind(val wireValue: String) {
    Camera("camera"),
    PhotoLibrary("photo_library"),
    Pdf("pdf"),
    Csv("csv"),
    Legacy("legacy"),
}

@Serializable
data class HealthReportUploadSessionRequest(
    @SerialName("subject_user_id") val subjectUserId: Long,
    @SerialName("client_request_id") val clientRequestId: String,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("expected_page_count") val expectedPageCount: Int? = null,
)

@Serializable
data class HealthReportUploadSession(
    @SerialName("asset_set_id") val assetSetId: Long,
    @SerialName("subject_user_id") val subjectUserId: Long,
    val status: String,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("expected_page_count") val expectedPageCount: Int? = null,
    @SerialName("received_asset_count") val receivedAssetCount: Int,
    @SerialName("aggregate_sha256") val aggregateSha256: String? = null,
)

@Serializable
data class HealthReportUploadedAsset(
    @SerialName("asset_id") val assetId: Long,
    @SerialName("asset_index") val assetIndex: Int,
    @SerialName("client_asset_id") val clientAssetId: String,
    val filename: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("byte_size") val byteSize: Long,
    val sha256: String,
)

@Serializable
data class HealthReportRecoveredAsset(
    @SerialName("asset_id") val assetId: Long,
    @SerialName("asset_index") val assetIndex: Int,
    @SerialName("client_asset_id") val clientAssetId: String,
    val filename: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("byte_size") val byteSize: Long,
    val sha256: String,
    @SerialName("asset_set_id") val assetSetId: Long,
    @SerialName("session_status") val sessionStatus: String,
    @SerialName("received_asset_count") val receivedAssetCount: Int,
)

@Serializable
data class HealthReportSealRequest(
    @SerialName("subject_user_id") val subjectUserId: Long,
    @SerialName("report_type") val reportType: String,
    val title: String,
    val hospital: String? = null,
    @SerialName("report_date") val reportDate: String? = null,
)

@Serializable
data class HealthReportSealResult(
    @SerialName("asset_set_id") val assetSetId: Long,
    val status: String,
    @SerialName("workflow_id") val workflowId: Long? = null,
    val duplicate: Boolean = false,
    @SerialName("failure_code") val failureCode: String? = null,
    @SerialName("recovery_action") val recoveryAction: String? = null,
    @SerialName("problem_asset_indices") val problemAssetIndices: List<Int> = emptyList(),
    @SerialName("missing_page_indices") val missingPageIndices: List<Int> = emptyList(),
)

@Serializable
data class HealthReportLocalOriginalAcknowledgementRequest(
    @SerialName("subject_user_id") val subjectUserId: Long,
    @SerialName("client_request_id") val clientRequestId: String,
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("asset_count") val assetCount: Int,
    @SerialName("aggregate_sha256") val aggregateSha256: String,
)

@Serializable
data class HealthReportLocalOriginalAcknowledgementResult(
    @SerialName("workflow_id") val workflowId: Long,
    @SerialName("contract_version") val contractVersion: Int,
    val accepted: Boolean,
    @SerialName("server_original_retirement_eligible")
    val serverOriginalRetirementEligible: Boolean,
)

@Serializable
data class HealthReportUploadSessionAbandonResult(
    @SerialName("asset_set_id") val assetSetId: Long,
    @SerialName("subject_user_id") val subjectUserId: Long,
    val status: String,
    @SerialName("cleanup_pending") val cleanupPending: Boolean,
)

@Serializable
data class HealthReportPrimaryAction(
    val code: String,
    val enabled: Boolean,
    @SerialName("pending_count") val pendingCount: Int = 0,
    @SerialName("target_workflow_id") val targetWorkflowId: Long? = null,
)

@Serializable
data class HealthReportRuntime(
    @SerialName("workflow_id") val workflowId: Long,
    @SerialName("workflow_version") val workflowVersion: Int,
    @SerialName("subject_user_id") val subjectUserId: Long,
    val state: String,
    @SerialName("workflow_status") val workflowStatus: String,
    @SerialName("failure_code") val failureCode: String? = null,
    @SerialName("primary_action") val primaryAction: HealthReportPrimaryAction? = null,
)

@Serializable
data class HealthReportHistoryItem(
    @SerialName("workflow_id") val workflowId: Long,
    val status: String,
    @SerialName("report_type") val reportType: String,
    val title: String,
    val hospital: String? = null,
    @SerialName("report_date") val reportDate: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class HealthReportHistoryResponse(
    val items: List<HealthReportHistoryItem> = emptyList(),
)

data class HealthReportUploadRecovery(
    val assetSetId: Long,
    val failureCode: String,
    val actionCode: String,
    val problemAssetIndices: List<Int>,
    val missingPageIndices: List<Int>,
) {
    val nextAssetIndex: Int?
        get() = missingPageIndices.firstOrNull() ?: problemAssetIndices.firstOrNull()

    fun accepts(assetIndex: Int): Boolean =
        assetIndex > 0 &&
            (assetIndex in problemAssetIndices || assetIndex in missingPageIndices)
}

sealed interface HealthReportLocalOriginalAcknowledgementStatus {
    data class Accepted(
        val serverOriginalRetirementEligible: Boolean,
    ) : HealthReportLocalOriginalAcknowledgementStatus

    data object Deferred : HealthReportLocalOriginalAcknowledgementStatus
    data object SkippedExactDuplicate : HealthReportLocalOriginalAcknowledgementStatus
}

sealed interface HealthReportUploadOutcome {
    data class Completed(
        val runtime: HealthReportRuntime,
        val duplicate: Boolean,
        val acknowledgement: HealthReportLocalOriginalAcknowledgementStatus,
    ) : HealthReportUploadOutcome

    data class RecoveryRequired(
        val recovery: HealthReportUploadRecovery,
    ) : HealthReportUploadOutcome
}

sealed interface HealthReportUploadCoordinatorError {
    data object Busy : HealthReportUploadCoordinatorError
    data object InvalidOwner : HealthReportUploadCoordinatorError
    data object InvalidInput : HealthReportUploadCoordinatorError
    data object OwnerChanged : HealthReportUploadCoordinatorError
    data object InvalidServerResponse : HealthReportUploadCoordinatorError
    data object NoMatchingRecovery : HealthReportUploadCoordinatorError
}

class HealthReportUploadCoordinatorException(
    val error: HealthReportUploadCoordinatorError,
) : IllegalStateException(
    when (error) {
        HealthReportUploadCoordinatorError.Busy -> "另一份报告正在上传，请等待完成后再试。"
        HealthReportUploadCoordinatorError.InvalidOwner -> "当前登录信息不完整，请重新登录后上传。"
        HealthReportUploadCoordinatorError.InvalidInput -> "报告原件为空或页序无效，请重新选择文件。"
        HealthReportUploadCoordinatorError.OwnerChanged -> "账号或报告所属用户已变化，请重新开始上传。"
        HealthReportUploadCoordinatorError.InvalidServerResponse -> "服务器返回的报告任务身份不一致，请稍后重试。"
        HealthReportUploadCoordinatorError.NoMatchingRecovery -> "报告恢复任务已变化，请重新上传整份报告。"
    },
)

internal fun healthReportUploadMimeType(fileName: String): String = when (
    fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "tif", "tiff" -> "image/tiff"
    "csv" -> "text/csv"
    "pdf" -> "application/pdf"
    else -> "application/octet-stream"
}

internal fun healthReportSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
