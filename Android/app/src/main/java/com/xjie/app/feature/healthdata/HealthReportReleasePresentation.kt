package com.xjie.app.feature.healthdata

import com.xjie.app.core.model.HealthReportCandidate
import com.xjie.app.core.model.HealthReportConfirmationEvent
import com.xjie.app.core.model.HealthReportObservation
import com.xjie.app.core.model.HealthReportProfileImpact
import com.xjie.app.core.model.HealthReportScoreSnapshot
import java.util.Locale
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The only projection boundary allowed to turn report server payloads into Release UI text.
 *
 * DTO identifiers and arbitrary dictionaries remain available to trusted client logic, but no
 * Compose branch may interpolate them. Unknown text fails closed to stable user wording.
 */
internal object HealthReportReleasePresentation {
    data class Observation(
        val title: String,
        val status: String,
        val value: String,
        val reference: String,
        val provenance: String,
    )

    data class Candidate(
        val title: String,
        val originalValue: String,
        val confirmedValue: String,
        val status: String,
    )

    data class Confirmation(
        val title: String,
        val change: String,
    )

    data class ProfileCandidate(
        val title: String,
        val summary: String,
        val status: String,
        val evidenceSummary: String,
    )

    data class Score(
        val kindTitle: String,
        val status: String,
        val value: String,
        val semanticSummary: String?,
        val confidenceSummary: String?,
        val directionSummary: String?,
        val evidenceSummary: String?,
        val missingInputsSummary: String?,
        val failureSummary: String?,
    )

    fun notice(value: String): String = safeServerText(value)
        ?: "本解读仅供健康管理参考，不构成诊断或治疗建议。"

    fun unavailableReason(value: String?, fallback: String): String =
        safeServerText(value) ?: fallback

    fun followUpItems(values: List<String>): List<String> = values.mapNotNull(::safeServerText)

    fun reportTitle(value: String?): String = safeServerText(value) ?: "健康报告"

    fun userMessage(value: String?, fallback: String): String = safeServerText(value) ?: fallback

    fun observation(value: HealthReportObservation): Observation = Observation(
        title = safeServerText(value.canonical_name) ?: "已确认报告指标",
        status = if (value.abnormal_state == "abnormal") "异常" else "已确认",
        value = valueAndUnit(value.value_numeric?.reportNumber() ?: safeServerText(value.value_text), value.unit),
        reference = safeServerText(value.reference_text) ?: when {
            value.reference_low != null && value.reference_high != null ->
                "${value.reference_low.reportNumber()}–${value.reference_high.reportNumber()}"
            value.reference_low != null -> "≥ ${value.reference_low.reportNumber()}"
            value.reference_high != null -> "≤ ${value.reference_high.reportNumber()}"
            else -> "未记录"
        },
        provenance = "已通过本次报告的确认记录核对。",
    )

    fun candidate(value: HealthReportCandidate): Candidate = Candidate(
        title = safeServerText(value.canonical_name) ?: "报告指标",
        originalValue = valueAndUnit(safeServerText(value.raw_value), value.raw_unit),
        confirmedValue = valueAndUnit(
            value.normalized_value?.reportNumber()
                ?: safeServerText(value.normalized_text)
                ?: safeServerText(value.raw_value),
            value.normalized_unit ?: value.raw_unit,
        ),
        status = reviewStatus(value.review_status),
    )

    fun confirmation(value: HealthReportConfirmationEvent): Confirmation = Confirmation(
        title = when (value.event_type) {
            "confirm" -> "确认"
            "correct" -> "修正"
            "reject" -> "未采用"
            "manual_add" -> "手动补录"
            else -> "状态已更新"
        },
        change = eventChange(value.before_data, value.after_data),
    )

    fun profileCandidate(values: List<HealthReportProfileImpact>): ProfileCandidate {
        val first = values.firstOrNull()
        val title = first?.proposed_value
            ?.get("canonical_name")
            ?.let(::scalarValue)
            ?.let { if (it.endsWith("候选")) it else "${it}候选" }
            ?: categoryTitle(first?.category)
        val rawValue = first?.proposed_value?.let { proposed ->
            scalarValue(
                proposed["latest_value_numeric"]
                    ?: proposed["latest_value_text"]
                    ?: proposed["value_numeric"]
                    ?: proposed["value_text"],
            )
        }
        val unit = first?.proposed_value?.get("unit")?.let(::scalarValue)
        val occurrenceCount = first?.proposed_value?.get("occurrence_count")?.let(::scalarValue)
        val summary = when {
            rawValue != null -> "待复核候选值：${valueAndUnit(rawValue, unit)}"
            occurrenceCount != null -> "本次报告中共有 $occurrenceCount 条相关记录，等待复核。"
            else -> "已生成一项待复核的画像候选。"
        }
        val evidenceCount = values.map { it.source_observation_id }.distinct().size
        return ProfileCandidate(
            title = title,
            summary = summary,
            status = profileStatus(first?.review_status),
            evidenceSummary = if (evidenceCount > 0) {
                "已由 $evidenceCount 条已确认报告数据支持；候选仍只计为一项。"
            } else {
                "候选仍需复核，未接受前不代表画像事实。"
            },
        )
    }

    fun score(value: HealthReportScoreSnapshot): Score {
        val failure = when {
            value.calculation_status == "failed" || !value.failure_code.isNullOrBlank() ->
                "本项评分暂未完成，请稍后再查看。"
            else -> null
        }
        return Score(
            kindTitle = when (value.score_kind) {
                "stress" -> "压力"
                "recovery" -> "恢复"
                "inflammation" -> "炎症"
                else -> "其他健康评分"
            },
            status = when (value.calculation_status) {
                "completed" -> "已完成"
                "failed" -> "未完成"
                else -> "待更新"
            },
            value = scoreValue(value),
            semanticSummary = when (value.semantic_outcome) {
                "improved" -> "服务端结论：改善"
                "worsened" -> "服务端结论：变差"
                "unchanged" -> "服务端结论：未变化"
                null, "" -> null
                else -> "服务端结论：暂无法判断"
            },
            confidenceSummary = ReportTrustPresentation.scoreConfidenceLabel(value)?.let {
                "置信度：$it"
            },
            directionSummary = when (value.score_direction) {
                "higher_is_better" -> "服务端定义：数值越高越好"
                "lower_is_better" -> "服务端定义：数值越低越好"
                null, "" -> null
                else -> "评分方向暂无法确认"
            },
            evidenceSummary = value.evidence.takeIf { it.isNotEmpty() }?.let {
                "已依据本次报告中已确认的数据进行计算。"
            },
            missingInputsSummary = value.missing_inputs.takeIf { it.isNotEmpty() }?.let {
                "部分必要信息尚未确认，本项暂不计算。"
            },
            failureSummary = failure,
        )
    }

    fun failureMessage(code: String?): String = when (code) {
        "missing_page" -> "报告页码不完整，请补齐缺失页后再提交。"
        "invalid_page_manifest" -> "报告页序有冲突，请重新整理页序。"
        "blur", "blurry_image" -> "报告中有模糊页面，请重拍对应页。"
        "blank_page" -> "报告中有空白页面，请替换后重试。"
        "low_resolution" -> "报告图片分辨率过低，请重拍对应页。"
        "unreadable_image", "unreadable_pdf" -> "报告文件无法读取，请替换原文件。"
        "asset_too_large", "file_too_large" -> "单页文件过大，请压缩或重新导出后上传。"
        "too_many_pages" -> "报告页数超过上限，请拆分后上传。"
        "quality_component_unavailable", "pdf_component_unavailable" ->
            "报告检查服务暂时不可用，请稍后重试。"
        "report_ocr_storage_unavailable", "report_ocr_provider_unavailable", "report_ocr_stalled" ->
            "报告处理暂未完成，请重新上传同一份报告以恢复处理。"
        "report_ocr_retry_exhausted" -> "报告多次识别未完成，请重新上传清晰原件后重试。"
        "no_reviewable_candidates" -> "暂未识别出可核对的指标，请重新上传更清晰的原件。"
        else -> "报告处理未完成，请重试或联系客服。"
    }

    fun transientError(fallback: String = "报告详情暂时无法读取，请稍后重试。"): String = fallback

    fun scalarValue(value: JsonElement?): String? = when (value) {
        null, JsonNull -> null
        is JsonPrimitive -> when {
            value.isString -> safeServerText(value.content)
            value.booleanOrNull != null -> if (value.booleanOrNull == true) "是" else "否"
            value.doubleOrNull != null -> value.doubleOrNull?.reportNumber()
            else -> null
        }
        else -> null
    }

    private fun scoreValue(value: HealthReportScoreSnapshot): String {
        if (value.calculation_status != "completed" || value.after_value == null) {
            return if (value.calculation_status == "failed") "本项未更新" else "本项仍在计算"
        }
        return value.before_value?.let {
            "${it.reportNumber()} → ${value.after_value.reportNumber()}"
        } ?: "本次结果 ${value.after_value.reportNumber()}（无可比前值）"
    }

    private fun eventChange(
        before: Map<String, JsonElement>,
        after: Map<String, JsonElement>,
    ): String {
        val beforeValue = eventValue(before)
        val afterValue = eventValue(after)
        return when {
            beforeValue == "未记录" -> afterValue
            beforeValue == afterValue -> afterValue
            else -> "$beforeValue → $afterValue"
        }
    }

    private fun eventValue(values: Map<String, JsonElement>): String = valueAndUnit(
        scalarValue(values["value_numeric"] ?: values["value_text"]),
        scalarValue(values["unit"]),
    )

    private fun valueAndUnit(value: String?, unit: String?): String = listOfNotNull(
        value?.takeIf(String::isNotBlank),
        safeServerText(unit),
    ).joinToString(" ").ifBlank { "未记录" }

    private fun categoryTitle(category: String?): String = when (category) {
        "basic" -> "基本健康信息候选"
        "safety" -> "健康安全信息候选"
        "long_term_health" -> "长期健康趋势候选"
        "goals" -> "健康目标候选"
        "medication" -> "用药信息候选"
        else -> "健康画像候选"
    }

    private fun profileStatus(status: String?): String = when (status) {
        "pending_review" -> "待复核"
        "accepted" -> "已接受"
        "rejected" -> "未采用"
        "superseded" -> "已被替代"
        "conflict" -> "存在冲突"
        else -> "状态待刷新"
    }

    private fun reviewStatus(status: String): String = when (status) {
        "pending_review" -> "待检查"
        "auto_accepted" -> "字段自动通过"
        "confirmed" -> "已确认"
        "corrected" -> "已修正"
        "rejected" -> "未采用"
        else -> "状态待刷新"
    }

    private fun safeServerText(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_SERVER_TEXT_LENGTH }
            ?: return null
        val lower = value.lowercase(Locale.ROOT)
        if (INTERNAL_FRAGMENTS.any(lower::contains) || lower in INTERNAL_TOKENS) return null
        if (value.any { it == '{' || it == '}' || it == '[' || it == ']' || it == '"' }) return null
        if (NUMBER_PATTERN.matches(value)) return value
        val hasHan = HAN_PATTERN.containsMatchIn(value)
        val hasWhitespace = value.any(Char::isWhitespace)
        if (!hasHan && !hasWhitespace && value.any { it == '_' || it == '.' }) return null
        return value
    }

    private fun Double.reportNumber(): String = if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.4f", this).trimEnd('0').trimEnd('.')
    }

    private const val MAX_SERVER_TEXT_LENGTH = 500
    private val NUMBER_PATTERN = Regex("[-+]?\\d+(?:\\.\\d+)?")
    private val HAN_PATTERN = Regex("\\p{IsHan}")
    private val INTERNAL_FRAGMENTS = setOf(
        "event_id",
        "candidate_id",
        "profile_candidate_id",
        "observation_id",
        "observation_ids",
        "source_observation_id",
        "confirmation_event_id",
        "trace_id",
        "workflow_id",
        "asset_id",
        "client_asset_id",
        "storage_id",
        "storage_key",
        "object_key",
        "failure_id",
        "failure_code",
        "snapshot_id",
        "fact_key",
        "algorithm_id",
        "algorithm_version",
        "proposed_value",
        "missing_inputs",
        "raw_json",
    )
    private val INTERNAL_TOKENS = setOf(
        "accepted",
        "completed",
        "conflict",
        "failed",
        "null",
        "pending",
        "pending_review",
        "processing",
        "recognizing",
        "rejected",
        "superseded",
    )
}
