package com.xjie.app.feature.healthdata

/** One user-selected source asset. Array order is the one-based report page order. */
class HealthReportUploadAssetInput(
    data: ByteArray,
    val fileName: String,
) {
    private val storedData = data.copyOf()
    val data: ByteArray get() = storedData.copyOf()

    override fun equals(other: Any?): Boolean =
        other is HealthReportUploadAssetInput &&
            fileName == other.fileName &&
            storedData.contentEquals(other.storedData)

    override fun hashCode(): Int = 31 * fileName.hashCode() + storedData.contentHashCode()
}

/** A fully verified local page; [data] is the exact user-selected byte sequence. */
class HealthReportLocalOriginalAsset(
    val assetIndex: Int,
    val fileName: String,
    val mimeType: String,
    val byteSize: Long,
    val sha256: String,
    data: ByteArray,
) {
    private val storedData = data.copyOf()
    val data: ByteArray get() = storedData.copyOf()

    override fun equals(other: Any?): Boolean =
        other is HealthReportLocalOriginalAsset &&
            assetIndex == other.assetIndex &&
            fileName == other.fileName &&
            mimeType == other.mimeType &&
            byteSize == other.byteSize &&
            sha256 == other.sha256 &&
            storedData.contentEquals(other.storedData)

    override fun hashCode(): Int {
        var result = assetIndex
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + byteSize.hashCode()
        result = 31 * result + sha256.hashCode()
        return 31 * result + storedData.contentHashCode()
    }
}

/** Manifest/file-attribute index that never loads report body bytes. */
data class HealthReportLocalOriginalMetadata(
    val assetIndex: Int,
    val fileName: String,
    val mimeType: String,
    val byteSize: Long,
    val sha256: String,
)

/** Exact local proof consumed by the backend local-original acknowledgement contract. */
data class HealthReportLocalOriginalBindingProof(
    val contractVersion: Int,
    val clientRequestId: String,
    val assetCount: Int,
    val aggregateSha256: String,
)

enum class HealthReportLocalOriginalBindingCheckpoint {
    JournalPersisted,
    ManifestPersisted,
    BindingPersisted,
}

sealed interface HealthReportLocalOriginalStoreError {
    data object InvalidIdentity : HealthReportLocalOriginalStoreError
    data class InvalidAsset(val assetIndex: Int) : HealthReportLocalOriginalStoreError
    data object ReportNotFound : HealthReportLocalOriginalStoreError
    data object CorruptManifest : HealthReportLocalOriginalStoreError
    data class IntegrityMismatch(val assetIndex: Int) : HealthReportLocalOriginalStoreError
    data object WriteFailed : HealthReportLocalOriginalStoreError
}

class HealthReportLocalOriginalStoreException(
    val error: HealthReportLocalOriginalStoreError,
    cause: Throwable? = null,
) : java.io.IOException(
    when (error) {
        HealthReportLocalOriginalStoreError.InvalidIdentity -> "当前报告的账号或用户信息无效，请重新登录后再试。"
        is HealthReportLocalOriginalStoreError.InvalidAsset -> "报告原件为空或页序无效，请重新选择文件。"
        HealthReportLocalOriginalStoreError.ReportNotFound -> "这份报告的本地原件不存在，可尝试从服务器重新加载。"
        HealthReportLocalOriginalStoreError.CorruptManifest -> "报告原件索引已损坏，请重新上传原文件。"
        is HealthReportLocalOriginalStoreError.IntegrityMismatch -> "报告原件完整性校验失败，请重新上传原文件。"
        HealthReportLocalOriginalStoreError.WriteFailed -> "报告原件未能安全保存到本机，本次不会上传，请检查存储空间后重试。"
    },
    cause,
)

interface HealthReportLocalOriginalStoreContract {
    suspend fun persistUpload(
        inputs: List<HealthReportUploadAssetInput>,
        clientRequestId: String,
        accountScope: String,
        subjectUserId: Long,
    )

    suspend fun persistReplacement(
        input: HealthReportUploadAssetInput,
        assetIndex: Int,
        clientRequestId: String,
        accountScope: String,
        subjectUserId: Long,
    )

    suspend fun bindWorkflow(
        workflowId: Long,
        clientRequestId: String,
        accountScope: String,
        subjectUserId: Long,
    )

    suspend fun loadAssets(
        workflowId: Long,
        accountScope: String,
        subjectUserId: Long,
    ): List<HealthReportLocalOriginalAsset>

    suspend fun listAssets(
        workflowId: Long,
        accountScope: String,
        subjectUserId: Long,
    ): List<HealthReportLocalOriginalMetadata>

    suspend fun loadAsset(
        workflowId: Long,
        assetIndex: Int,
        accountScope: String,
        subjectUserId: Long,
    ): HealthReportLocalOriginalAsset

    suspend fun bindingProof(
        workflowId: Long,
        accountScope: String,
        subjectUserId: Long,
    ): HealthReportLocalOriginalBindingProof
}
