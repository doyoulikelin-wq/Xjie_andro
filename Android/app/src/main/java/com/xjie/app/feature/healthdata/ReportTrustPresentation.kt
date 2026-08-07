package com.xjie.app.feature.healthdata

import com.xjie.app.core.model.HealthDocument
import com.xjie.app.core.model.HealthReportInterpretation
import com.xjie.app.core.model.HealthReportScoreSnapshot

/**
 * User-facing projection of the server-owned report workflow.
 *
 * OCR completion alone is never admission. Keeping this policy pure makes every upload entry
 * point use the same wording and prevents chat from treating an unconfirmed report as evidence.
 */
internal object ReportTrustPresentation {
    enum class Stage {
        Uploading,
        Recognizing,
        AwaitingConfirmation,
        Committing,
        CompletedScorePending,
        Completed,
        Failed,
        LegacyUnverified,
    }

    fun stage(document: HealthDocument): Stage = when (document.report_workflow_status) {
        "draft", "uploading" -> Stage.Uploading
        "recognizing" -> Stage.Recognizing
        "awaiting_confirmation" -> Stage.AwaitingConfirmation
        "committing" -> Stage.Committing
        "completed_score_pending" -> Stage.CompletedScorePending
        "completed" -> Stage.Completed
        "failed" -> Stage.Failed
        else -> when (document.extraction_status) {
            "pending" -> Stage.Recognizing
            "failed" -> Stage.Failed
            else -> Stage.LegacyUnverified
        }
    }

    fun title(document: HealthDocument): String = when (stage(document)) {
        Stage.Uploading -> "正在上传报告"
        Stage.Recognizing -> "报告已上传，正在识别"
        Stage.AwaitingConfirmation -> "识别完成，等待检查"
        Stage.Committing -> "正在确认入库"
        Stage.CompletedScorePending -> "已确认入库，评分待更新"
        Stage.Completed -> "已确认入库"
        Stage.Failed -> "报告处理失败"
        Stage.LegacyUnverified -> "历史结果尚未验证"
    }

    fun nextStep(document: HealthDocument): String {
        if (document.report_duplicate) {
            return "检测到相同报告，已复用原有处理记录；不会重复入库。请继续原流程的复核或查看结果。"
        }
        return when (stage(document)) {
            Stage.Uploading, Stage.Recognizing ->
                "识别完成后需要检查并确认；确认前不会进入趋势、画像、AI 或评分。"
            Stage.AwaitingConfirmation ->
                "请检查异常、低置信度和冲突字段，再确认整份报告入库。"
            Stage.Committing -> "服务器正在保存确认结果，请勿重复提交。"
            Stage.CompletedScorePending ->
                "结构化数据已入库，可用于可信问答；相关评分仍在更新。"
            Stage.Completed -> "结构化数据已入库，可查看本次解读和实际评分变化。"
            Stage.Failed -> "请查看失败原因后重试；失败结果不会进入健康数据。"
            Stage.LegacyUnverified ->
                "这份旧识别结果没有报告级确认记录，不会进入趋势、画像、AI 或评分。"
        }
    }

    fun isAdmitted(document: HealthDocument): Boolean = stage(document) in setOf(
        Stage.CompletedScorePending,
        Stage.Completed,
    )

    fun requiresReview(document: HealthDocument): Boolean =
        stage(document) == Stage.AwaitingConfirmation

    /** Completed workflows have one primary action; pre-admission states never expose it. */
    fun interpretationPrimaryAction(workflowStatus: String): String? =
        if (workflowStatus in setOf("completed", "completed_score_pending")) {
            "查看本次解读"
        } else {
            null
        }

    /** Pending is authoritative even when one or more actual snapshots already exist. */
    fun scoreHeadline(interpretation: HealthReportInterpretation): String {
        if (interpretation.score_pending) {
            val completedCount = interpretation.score_snapshots.count {
                it.calculation_status == "completed"
            }
            return if (completedCount > 0) {
                "评分仍待更新；已有 $completedCount 项可核验快照，其余尚未收口。"
            } else {
                "评分待更新；报告已入库，但当前没有完整评分结果。"
            }
        }
        return when (interpretation.score_state) {
            "completed" -> "评分流程已完成；以下仅展示服务端实际快照。"
            "partial_failed" -> "评分部分完成；失败项不会显示推测结果。"
            "failed" -> "评分更新未完成；报告入库结果不受影响。"
            else -> "当前没有可核验的评分快照。"
        }
    }

    /** Multiple source observations may support one candidate, but never inflate impact count. */
    fun profileImpactCount(interpretation: HealthReportInterpretation): Int =
        interpretation.profile_impacts.map { it.profile_candidate_id }.distinct().size

    fun scoreConfidenceLabel(snapshot: HealthReportScoreSnapshot): String? = when {
        snapshot.before_confidence != null && snapshot.after_confidence != null ->
            "${snapshot.before_confidence.asPercent()} → ${snapshot.after_confidence.asPercent()}"
        snapshot.after_confidence != null -> "本次 ${snapshot.after_confidence.asPercent()}"
        snapshot.before_confidence != null -> "前值 ${snapshot.before_confidence.asPercent()}（本次未提供）"
        else -> null
    }

    fun scoreDirectionLabel(direction: String): String = when (direction) {
        "higher_is_better" -> "服务端定义：数值越高越好"
        "lower_is_better" -> "服务端定义：数值越低越好"
        else -> "评分方向暂无法确认"
    }

    private fun Double.asPercent(): String = "${(this * 100).toInt()}%"
}
