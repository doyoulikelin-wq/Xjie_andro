package com.xjie.app.feature.chat

import com.xjie.app.core.model.ChatInteractionRoute
import com.xjie.app.core.model.ChatResponse
import com.xjie.app.core.model.Citation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ChatCitationReference(
    val number: Int,
    val citation: Citation,
)

val ChatMessageItem.relevantCitationReferences: List<ChatCitationReference>
    get() {
        val markers = CITATION_MARKER.findAll("$content\n${analysis.orEmpty()}")
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.toIntOrNull() }
            .toSet()
        return citations.mapIndexedNotNull { index, citation ->
            val number = index + 1
            citation.takeIf { number in markers }?.let {
                ChatCitationReference(number = number, citation = it)
            }
        }
    }

val ChatMessageItem.hasDistinctAnalysis: Boolean
    get() = !analysis.isNullOrBlank() &&
        normalizedComparisonText(content) != normalizedComparisonText(analysis.orEmpty())

object ChatPresentationPolicy {
    fun selectContent(
        response: ChatResponse,
        streamedRoute: ChatInteractionRoute? = null,
    ): String {
        val summary = cleanContent(response.summary.orEmpty())
        val detailed = cleanContent(
            response.answer_markdown?.takeIf(String::isNotBlank)
                ?: response.analysis.orEmpty()
        )
        val route = response.interaction_route ?: streamedRoute
        return when {
            route?.depth.equals("deep", ignoreCase = true) && detailed.isNotBlank() -> detailed
            summary.isNotBlank() && !looksIncomplete(summary) -> summary
            detailed.isNotBlank() -> detailed
            summary.isNotBlank() -> summary
            else -> "回答为空，请重试。"
        }
    }

    fun distinctAnalysis(response: ChatResponse, content: String): String? {
        val analysis = cleanAnalysis(response.analysis.orEmpty())
        return analysis.takeIf {
            it.isNotBlank() && normalizedComparisonText(it) != normalizedComparisonText(content)
        }
    }

    fun cleanContent(text: String): String {
        var value = text.trim()
        if (value.startsWith("```") && value.endsWith("```")) {
            value = value
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }
        if (value.startsWith("{") && value.contains("\"summary\"")) {
            extractSummary(value)?.let { return it.trim() }
        }
        return value
    }

    fun cleanAnalysis(text: String): String = text
        .trim()
        .replace(ANALYSIS_HEADING, "")
        .trim()

    fun looksIncomplete(text: String): Boolean {
        var value = text.trim()
        while (value.isNotEmpty()) {
            val previous = value
            value = value.replace(TRAILING_CITATIONS, "").trim()
            value = value.replace(TRAILING_TERMINAL_PUNCTUATION, "").trim()
            if (value == previous) break
        }
        if (value.isEmpty()) return true
        return DANGLING_ENDINGS.any(value::endsWith) ||
            BOLD_MARKER.findAll(value).count() % 2 != 0
    }

    fun studyDesignDisplayText(raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (value.any { it.code in 0x3400..0x9fff || it.code in 0xf900..0xfaff }) return value
        return when (value.lowercase().replace(NON_ALPHANUMERIC, "_").trim('_')) {
            "systematic_review_meta_analysis", "systematic_review_and_meta_analysis", "meta_analysis" -> "系统综述与荟萃分析"
            "systematic_review" -> "系统综述"
            "systematic_review_of_observational_studies" -> "观察性研究系统综述"
            "mechanistic_observational_study" -> "机制性观察研究"
            "clinical_practice_guideline", "practice_guideline", "guideline" -> "临床实践指南"
            "rct", "randomized_controlled_trial", "randomised_controlled_trial",
            "randomized_clinical_trial", "randomised_clinical_trial", "randomized_trial" -> "随机对照试验"
            "prospective_cohort", "prospective_cohort_study" -> "前瞻性队列研究"
            "retrospective_cohort", "retrospective_cohort_study" -> "回顾性队列研究"
            "cohort", "cohort_study" -> "队列研究"
            "observational_cohort", "observational_cohort_study" -> "观察性队列研究"
            "case_control", "case_control_study" -> "病例对照研究"
            "cross_sectional", "cross_sectional_study" -> "横断面研究"
            "observational", "observational_study" -> "观察性研究"
            "clinical_trial", "controlled_clinical_trial" -> "临床试验"
            "case_series" -> "病例系列"
            "case_report" -> "病例报告"
            "mechanism", "mechanistic_study" -> "机制研究"
            else -> "其他研究设计"
        }
    }

    private fun extractSummary(raw: String): String? = runCatching {
        val objectValue = Json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        (objectValue["summary"] as? JsonPrimitive)?.contentOrNull
    }.getOrNull()
}

private val CITATION_MARKER = Regex("\\[(\\d{1,2})]")
private val TRAILING_CITATIONS = Regex("(?:\\s*\\[\\d{1,2}])+\\s*$")
private val TRAILING_TERMINAL_PUNCTUATION = Regex("[。.!！?？；;]+\\s*$")
private val DANGLING_ENDINGS = listOf(
    "但", "但是", "不过", "然而", "因为", "由于", "导致", "形成", "归因于", "包括", "例如",
    "以及", "和", "与", "或", "，", ",", "：", ":",
)
private val ANALYSIS_HEADING = Regex(
    "^(?:#{1,6}\\s*)?(?:详细分析|分析|analysis)\\s*[:：]?\\s*",
    setOf(RegexOption.IGNORE_CASE),
)
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
private val BOLD_MARKER = Regex("\\*\\*")

private fun normalizedComparisonText(value: String): String = value
    .lowercase()
    .filterNot { it.isWhitespace() || it in "#*_`>-" }
