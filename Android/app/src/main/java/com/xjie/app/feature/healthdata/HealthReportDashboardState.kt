package com.xjie.app.feature.healthdata

import com.xjie.app.core.auth.AuthManager

enum class HealthReportDashboardContentState {
    Loading,
    Available,
    Error,
    Empty,
}

enum class HealthReportDashboardPhase {
    Recognizing,
    AwaitingReview,
    Committing,
    Recoverable,
    Failed,
    ScorePending,
    Completed,
    Unknown,
}

internal object HealthReportHistoryDestination {
    private const val PREFIX = "report-workflow-"

    fun encode(workflowId: Long): String {
        require(workflowId > 0L) { "workflow identity must be positive" }
        return "$PREFIX$workflowId"
    }

    fun decode(destinationId: String): Long? = destinationId
        .takeIf { it.startsWith(PREFIX) }
        ?.removePrefix(PREFIX)
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
}

sealed interface HealthReportDashboardUploadState {
    data object Idle : HealthReportDashboardUploadState
    data object Submitting : HealthReportDashboardUploadState

    data class RecoveryRequired(
        val recovery: HealthReportUploadRecovery,
    ) : HealthReportDashboardUploadState

    data class Completed(
        val runtime: HealthReportRuntime,
        val duplicate: Boolean,
        val acknowledgementDeferred: Boolean,
    ) : HealthReportDashboardUploadState

    data class Failed(
        val message: String,
    ) : HealthReportDashboardUploadState
}

data class HealthReportDashboardPresentation(
    val title: String,
    val summary: String,
    val reportActionTitle: String,
    val phaseTag: String,
)

/**
 * Account-generation-owned report dashboard state shared by every report entry point.
 *
 * Internal workflow identity is retained for authenticated actions, but Compose must render only
 * [presentation] and [HealthReportReleasePresentation] output.
 */
data class HealthReportDashboardState(
    val owner: AuthManager.AccountScopeSnapshot? = null,
    val loading: Boolean = false,
    val items: List<HealthReportHistoryItem> = emptyList(),
    val runtime: HealthReportRuntime? = null,
    val readError: String? = null,
    val detailWarning: String? = null,
    val upload: HealthReportDashboardUploadState = HealthReportDashboardUploadState.Idle,
) {
    val latestItem: HealthReportHistoryItem? get() = items.firstOrNull()
    val recentItems: List<HealthReportHistoryItem> get() = items.take(3)

    /** A history runtime is usable only for the exact history row it describes. */
    val latestRuntime: HealthReportRuntime?
        get() = runtime?.takeIf { candidate ->
            candidate.workflowId == latestItem?.workflowId
        }

    /** Newly uploaded work remains independent until authoritative history contains that workflow. */
    val pendingUploadRuntime: HealthReportRuntime?
        get() = (upload as? HealthReportDashboardUploadState.Completed)
            ?.runtime
            ?.takeIf { candidate -> candidate.workflowId != latestItem?.workflowId }

    /** Runtime rendered inside the history card; it can never decorate a different history row. */
    val displayedRuntime: HealthReportRuntime?
        get() = latestRuntime ?: pendingUploadRuntime.takeIf { latestItem == null }

    val contentState: HealthReportDashboardContentState
        get() = HealthReportDashboardPolicy.contentState(
            loading = loading,
            hasReport = latestItem != null || pendingUploadRuntime != null,
            hasError = readError != null,
        )

    val phase: HealthReportDashboardPhase
        get() = HealthReportDashboardPolicy.phase(
            workflowStatus = displayedRuntime?.workflowStatus ?: latestItem?.status,
            failureCode = displayedRuntime?.failureCode,
        )

    val presentation: HealthReportDashboardPresentation
        get() = HealthReportDashboardPolicy.presentation(phase)

    fun belongsTo(authManager: AuthManager): Boolean = owner?.let(authManager::isCurrent) ?: true

    companion object {
        fun initial(): HealthReportDashboardState = HealthReportDashboardState()
    }
}

internal object HealthReportDashboardPolicy {
    private val recoverableFailureCodes = setOf(
        "blur",
        "blurry_image",
        "blank_page",
        "low_resolution",
        "missing_page",
        "invalid_page_manifest",
        "unreadable_image",
        "unreadable_pdf",
        "report_ocr_storage_unavailable",
        "report_ocr_provider_unavailable",
        "report_ocr_stalled",
        "report_ocr_retry_exhausted",
        "no_reviewable_candidates",
    )

    fun contentState(
        loading: Boolean,
        hasReport: Boolean,
        hasError: Boolean,
    ): HealthReportDashboardContentState = when {
        hasReport -> HealthReportDashboardContentState.Available
        loading -> HealthReportDashboardContentState.Loading
        hasError -> HealthReportDashboardContentState.Error
        else -> HealthReportDashboardContentState.Empty
    }

    fun phase(
        workflowStatus: String?,
        failureCode: String?,
    ): HealthReportDashboardPhase {
        return when (workflowStatus) {
            "draft", "uploading", "recognizing" -> HealthReportDashboardPhase.Recognizing
            "awaiting_confirmation" -> HealthReportDashboardPhase.AwaitingReview
            "committing" -> HealthReportDashboardPhase.Committing
            "completed_score_pending" -> HealthReportDashboardPhase.ScorePending
            "completed" -> HealthReportDashboardPhase.Completed
            "failed" -> if (failureCode in recoverableFailureCodes) {
                HealthReportDashboardPhase.Recoverable
            } else {
                HealthReportDashboardPhase.Failed
            }
            else -> HealthReportDashboardPhase.Unknown
        }
    }

    fun presentation(phase: HealthReportDashboardPhase): HealthReportDashboardPresentation =
        when (phase) {
            HealthReportDashboardPhase.Recognizing -> HealthReportDashboardPresentation(
                title = "原件已保存 · 解析中",
                summary = "原始文件已安全保存，系统正在解析指标、单位和参考范围。",
                reportActionTitle = "查看解析进度",
                phaseTag = "recognizing",
            )
            HealthReportDashboardPhase.AwaitingReview -> HealthReportDashboardPresentation(
                title = "识别完成 · 待确认",
                summary = "字段识别已经完成，等待你核对后正式写入可信健康数据。",
                reportActionTitle = "核对报告字段",
                phaseTag = "awaitingReview",
            )
            HealthReportDashboardPhase.Committing -> HealthReportDashboardPresentation(
                title = "确认完成 · 入库中",
                summary = "报告字段已经确认，系统正在写入可信健康数据和确认记录。",
                reportActionTitle = "查看入库进度",
                phaseTag = "committing",
            )
            HealthReportDashboardPhase.Recoverable -> HealthReportDashboardPresentation(
                title = "解析未完成",
                summary = "本次处理未完成，原件仍安全保存；请按页面提示补页、替换或重试。",
                reportActionTitle = "继续处理报告",
                phaseTag = "recoverable",
            )
            HealthReportDashboardPhase.Failed -> HealthReportDashboardPresentation(
                title = "解析未完成",
                summary = "本次处理未完成，原件仍安全保存；可查看详情或重新上传清晰原件。",
                reportActionTitle = "查看问题与原件",
                phaseTag = "failed",
            )
            HealthReportDashboardPhase.ScorePending -> HealthReportDashboardPresentation(
                title = "已完成解析 · 评分待更新",
                summary = "结构化数据已经确认入库；相关评分仍在更新，不会展示推测结果。",
                reportActionTitle = "查看报告解读",
                phaseTag = "scorePending",
            )
            HealthReportDashboardPhase.Completed -> HealthReportDashboardPresentation(
                title = "已完成解析",
                summary = "报告已经完成确认，可查看本次解读、已确认指标和实际评分记录。",
                reportActionTitle = "查看报告解读",
                phaseTag = "completed",
            )
            HealthReportDashboardPhase.Unknown -> HealthReportDashboardPresentation(
                title = "报告状态待确认",
                summary = "报告原件已保存，当前状态暂时无法确认；可稍后刷新或查看原件。",
                reportActionTitle = "查看报告与原件",
                phaseTag = "unknown",
            )
        }
}
