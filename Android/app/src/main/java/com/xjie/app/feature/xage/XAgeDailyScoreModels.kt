package com.xjie.app.feature.xage

import java.time.Instant

/** The only three local daily estimates that may appear in the XAGE data header. */
enum class XAgeDataKind(
    val label: String,
    val accessibilityKey: String,
) {
    Pressure("压力", "pressure"),
    Recovery("恢复", "recovery"),
    Inflammation("炎症", "inflammation"),
}

data class XAgeScoreField(
    val title: String,
    val value: String,
)

data class XAgeScoreDriver(
    val title: String,
    val value: String,
    val note: String,
)

/**
 * A computed metric and its evidence-completeness metadata.
 *
 * [serverSnapshotVersion] and [dailyEstimateVersion] deliberately represent different trust
 * channels. A local daily estimate must never mint a server snapshot version.
 */
data class XAgeMetricScore(
    val value: Int,
    val confidence: Int,
    val isReady: Boolean,
    val badgeLabel: String,
    val stateLabel: String,
    val summary: String,
    val simpleExplanation: String,
    val explanation: String,
    val nextAction: String,
    val fields: List<XAgeScoreField>,
    val drivers: List<XAgeScoreDriver>,
    val isProxy: Boolean,
    val serverSnapshotVersion: String? = null,
    val dailyEstimateVersion: String? = null,
    val confidenceSignalCount: Int = 0,
    val confidenceSignalTarget: Int = 0,
    val confidenceMissingSignals: List<String> = emptyList(),
) {
    val researchValueText: String
        get() = if (isReady) value.toString() else "--"

    val isTrustedForDisplay: Boolean
        get() = isReady && serverSnapshotVersion != null

    val isDailyEstimateForDisplay: Boolean
        get() = dailyEstimateVersion == XAgeDailyScorePresentationPolicy.ALGORITHM_VERSION

    val hasDisplayableScore: Boolean
        get() = isTrustedForDisplay || isDailyEstimateForDisplay

    val displayValue: String
        get() = if (isTrustedForDisplay) value.toString() else "--"

    val dailyDisplayValue: String
        get() = if (hasDisplayableScore) value.toString() else "--"
}

data class XAgeAgeScore(
    val isReady: Boolean,
    val age: String,
    val delta: String,
    val status: String,
    val summary: String,
    val serverSnapshotVersion: String? = null,
) {
    val isTrustedForDisplay: Boolean
        get() = isReady &&
            serverSnapshotVersion != null &&
            XAgeTrustedScorePresentationPolicy.IS_XAGE_CONSUMPTION_ENABLED

    val displayAge: String
        get() = if (isTrustedForDisplay) age else "--"

    val displayDelta: String
        get() = if (isTrustedForDisplay) delta else "尚未启用"
}

data class XAgeCompositeScores(
    val pressure: XAgeMetricScore,
    val recovery: XAgeMetricScore,
    val inflammation: XAgeMetricScore,
    val xAge: XAgeAgeScore,
) {
    fun score(kind: XAgeDataKind): XAgeMetricScore = when (kind) {
        XAgeDataKind.Pressure -> pressure
        XAgeDataKind.Recovery -> recovery
        XAgeDataKind.Inflammation -> inflammation
    }

    val todaySummary: String
        get() {
            if (XAgeScoreStatusPresentation.isUnavailable(this)) {
                return XAgeScoreStatusPresentation.UNAVAILABLE_PROMPT_MESSAGE
            }
            val lowConfidenceKinds = XAgeScoreStatusPresentation.missingKinds(this)
            if (lowConfidenceKinds.isNotEmpty()) {
                return "已生成每日参考分；${lowConfidenceKinds.joinToString("、") { it.label }}的数据完整度较低，请结合外环和感叹号说明查看。"
            }
            return "${recovery.stateLabel}，${pressure.stateLabel}；${inflammation.stateLabel}。"
        }
}

data class XAgeDailyScoreSample(
    val metricId: String,
    val indicatorName: String,
    val value: Double,
    val unit: String,
    val measuredAt: Instant,
    val displayValue: String = value.toCompactString(),
    val displayUnit: String = unit,
)

data class XAgeAlgorithmTrend(
    val name: String,
    val value: Double,
    val unit: String?,
    val refLow: Double?,
    val refHigh: Double?,
    val abnormal: Boolean,
    val measuredAt: String?,
    val source: String,
    val confidence: Double,
    val displayValue: String? = null,
) {
    val resolvedDisplayValue: String
        get() = displayValue?.trim()?.takeIf { it.isNotEmpty() }
            ?: buildString {
                append(value.toCompactString())
                unit?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    append(' ')
                    append(it)
                }
            }

    companion object {
        fun normalizedKey(raw: String): String = raw.lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("/", "")
            .replace("（", "")
            .replace("）", "")
            .replace("(", "")
            .replace(")", "")
    }
}

data class XAgeAlgorithmContext(
    val userAge: Int? = null,
    val profileHeightCm: Double? = null,
    val profileWeightKg: Double? = null,
    val dashboardScore: Int? = null,
    val trendPointCount: Int = 0,
    val documentCount: Int = 0,
    val watchedIndicatorCount: Int = 0,
    val samples: List<XAgeDailyScoreSample> = emptyList(),
    val serverTrends: List<XAgeAlgorithmTrend> = emptyList(),
    val referenceDate: Instant = Instant.now(),
)

internal fun Double.toCompactString(): String {
    if (!isFinite()) return toString()
    if (this == toLong().toDouble()) return toLong().toString()
    return "%.4f".format(java.util.Locale.US, this).trimEnd('0').trimEnd('.')
}
