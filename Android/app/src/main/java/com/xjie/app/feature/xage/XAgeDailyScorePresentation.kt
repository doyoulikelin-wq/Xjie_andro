package com.xjie.app.feature.xage

import com.xjie.app.feature.healthconnect.HealthConnectMetric
import java.time.Instant

/** Server-trusted score and XAge consumers remain fail-closed on Android. */
object XAgeTrustedScorePresentationPolicy {
    const val AUTHORITY = "server"
    const val IS_XAGE_CONSUMPTION_ENABLED = false

    val unavailable: XAgeCompositeScores
        get() = XAgeCompositeScores(
            pressure = pendingMetric("压力"),
            recovery = pendingMetric("恢复"),
            inflammation = pendingMetric("炎症"),
            xAge = XAgeAgeScore(
                isReady = false,
                age = "--",
                delta = "尚未启用",
                status = "X年龄尚未启用",
                summary = "等待版本化验证",
            ),
        )

    /** Local research output cannot enter the server-trusted presentation channel. */
    fun presentation(@Suppress("UNUSED_PARAMETER") localResearch: XAgeCompositeScores?): XAgeCompositeScores =
        unavailable

    private fun pendingMetric(name: String) = XAgeMetricScore(
        value = 0,
        confidence = 0,
        isReady = false,
        badgeLabel = "待更新",
        stateLabel = "${name}评分待更新",
        summary = "当前没有可展示的服务端版本化${name}评分。",
        simpleExplanation = "评分待更新。只有服务端版本化评分快照可以展示。",
        explanation = "本地算法结果仅用于每日参考分，不会获得服务端可信身份。",
        nextAction = "可继续同步健康数据或上传报告；同步完成不代表可信评分已生成。",
        fields = listOf(XAgeScoreField("可信评分", "待服务端版本化快照")),
        drivers = listOf(XAgeScoreDriver("评分来源", "服务端", "当前尚无冻结并版本化的评分接口。")),
        isProxy = false,
    )
}

/** The single versioned entry point for local pressure, recovery, and inflammation estimates. */
object XAgeDailyScorePresentationPolicy {
    const val ALGORITHM_VERSION = "xage.daily.estimate.v1"

    fun presentation(
        snapshot: XAgeServerSyncSnapshot,
        samples: List<XAgeDailyScoreSample> = emptyList(),
        referenceDate: Instant = Instant.now(),
    ): XAgeCompositeScores {
        if (!snapshot.isLoaded || snapshot.isLoggedOut) {
            return XAgeTrustedScorePresentationPolicy.unavailable
        }
        val localEstimate = XAgeDailyScoreAlgorithm.compute(
            XAgeAlgorithmContext(
                userAge = snapshot.userAge,
                profileHeightCm = snapshot.profileHeightCm,
                profileWeightKg = snapshot.profileWeightKg,
                dashboardScore = snapshot.dashboardScore,
                trendPointCount = snapshot.trendPointCount,
                documentCount = snapshot.trustedDocumentCount,
                watchedIndicatorCount = snapshot.watchedIndicatorCount,
                samples = samples,
                serverTrends = snapshot.algorithmTrends,
                referenceDate = referenceDate,
            ),
        )
        return presentation(localEstimate)
    }

    fun presentation(localEstimate: XAgeCompositeScores): XAgeCompositeScores = XAgeCompositeScores(
        pressure = dailyMetric(localEstimate.pressure, target = 6),
        recovery = dailyMetric(localEstimate.recovery, target = 5),
        inflammation = dailyMetric(localEstimate.inflammation, target = 6),
        xAge = XAgeTrustedScorePresentationPolicy.unavailable.xAge,
    )

    private fun dailyMetric(source: XAgeMetricScore, target: Int): XAgeMetricScore = source.copy(
        dailyEstimateVersion = ALGORITHM_VERSION,
        confidenceSignalTarget = source.confidenceSignalTarget.takeIf { it > 0 } ?: target,
    )
}

/** Exact required trend names; unknown/watched volume cannot replace an algorithm dependency. */
object XAgeHealthTrendRequestContract {
    val algorithmRequiredNames: List<String> = listOf(
        "hsCRP",
        "超敏C反应蛋白",
        "C反应蛋白",
        "白细胞计数",
        "白细胞",
        "NLR",
        "中性粒细胞淋巴细胞比值",
        "IL-6",
        "白介素6",
        "TNF-α",
    )

    fun names(watchedNames: List<String>): List<String> {
        val supportedHealthConnectNames = HealthConnectMetric.entries.map { it.indicatorName }
        val seen = mutableSetOf<String>()
        return (supportedHealthConnectNames + algorithmRequiredNames + watchedNames).mapNotNull { raw ->
            val trimmed = raw.trim()
            val key = trimmed.lowercase()
            trimmed.takeIf { it.isNotEmpty() && seen.add(key) }
        }
    }
}

object XAgeScoreStatusPresentation {
    const val LOW_CONFIDENCE_THRESHOLD = 60
    const val HIGH_CONFIDENCE_THRESHOLD = 80
    const val NO_SUPPORT_DATA_PROMPT_TITLE = "参考分置信度较低"
    const val NO_SUPPORT_DATA_PROMPT_MESSAGE =
        "已给出低置信参考分；外环表示数据完整度。点击小感叹号可查看缺失数据和提升方法。"
    const val UNAVAILABLE_PROMPT_TITLE = "每日评分准备中"
    const val UNAVAILABLE_PROMPT_MESSAGE = "登录并完成当前账号的数据加载后，才会生成每日参考分。"

    enum class SummaryMode {
        Unavailable,
        FirstUse,
        NeedsData,
        Ready,
    }

    data class SummaryPresentation(
        val mode: SummaryMode,
        val title: String,
        val message: String,
    )

    fun summaryPresentation(scores: XAgeCompositeScores): SummaryPresentation = when {
        isUnavailable(scores) -> SummaryPresentation(
            mode = SummaryMode.Unavailable,
            title = UNAVAILABLE_PROMPT_TITLE,
            message = UNAVAILABLE_PROMPT_MESSAGE,
        )
        isFirstUse(scores) -> SummaryPresentation(
            mode = SummaryMode.FirstUse,
            title = NO_SUPPORT_DATA_PROMPT_TITLE,
            message = NO_SUPPORT_DATA_PROMPT_MESSAGE,
        )
        needsData(scores) -> SummaryPresentation(
            mode = SummaryMode.NeedsData,
            title = "补齐评分数据",
            message = missingKinds(scores).joinToString("\n") { kind ->
                val metric = scores.score(kind)
                "${kind.label}置信度较低：${missingDataMessage(kind, metric)}"
            },
        )
        else -> SummaryPresentation(
            mode = SummaryMode.Ready,
            title = "今日状态",
            message = scores.todaySummary,
        )
    }

    fun isUnavailable(scores: XAgeCompositeScores): Boolean =
        metrics(scores).all { !it.hasDisplayableScore }

    fun isFirstUse(scores: XAgeCompositeScores): Boolean =
        !isUnavailable(scores) && metrics(scores).all { !it.isReady }

    fun missingKinds(scores: XAgeCompositeScores): List<XAgeDataKind> =
        XAgeDataKind.entries.filter { kind ->
            val metric = scores.score(kind)
            metric.hasDisplayableScore && requiresConfidenceAttention(metric)
        }

    fun needsData(scores: XAgeCompositeScores): Boolean =
        !isFirstUse(scores) && missingKinds(scores).isNotEmpty()

    fun confidenceProgress(metric: XAgeMetricScore): Float =
        metric.confidence.coerceIn(0, 100) / 100f

    fun requiresConfidenceAttention(metric: XAgeMetricScore): Boolean =
        metric.confidence < LOW_CONFIDENCE_THRESHOLD

    fun confidenceBand(metric: XAgeMetricScore): String = when {
        metric.confidence < LOW_CONFIDENCE_THRESHOLD -> "低置信度"
        metric.confidence < HIGH_CONFIDENCE_THRESHOLD -> "中置信度"
        else -> "高置信度"
    }

    fun confidenceExplanation(kind: XAgeDataKind, metric: XAgeMetricScore): String {
        val coverage = if (metric.confidenceSignalTarget > 0) {
            "已覆盖 ${metric.confidenceSignalCount}/${metric.confidenceSignalTarget} 类关键数据"
        } else {
            "尚无有效相关信号"
        }
        return "当前数据完整度 ${metric.confidence.coerceIn(0, 100)}%，$coverage。" +
            "外环只表示本次评分的数据支撑程度，不代表健康状况好坏。" +
            missingDataMessage(kind, metric)
    }

    fun accessibilitySummary(kind: XAgeDataKind, metric: XAgeMetricScore): String {
        if (!metric.hasDisplayableScore) return "${kind.label}评分待更新"
        val warning = if (requiresConfidenceAttention(metric)) "，置信度较低" else ""
        return "${kind.label}评分 ${metric.dailyDisplayValue} 分，数据完整度 " +
            "${metric.confidence.coerceIn(0, 100)}%$warning"
    }

    fun missingDataMessage(kind: XAgeDataKind, metric: XAgeMetricScore): String {
        val gap = if (metric.confidenceMissingSignals.isEmpty()) {
            "关键数据类型已覆盖；完整度不足主要因为部分数据较旧、质量较低或连续性不足。"
        } else {
            "仍需补充：${metric.confidenceMissingSignals.joinToString("、")}。"
        }
        return gap + improvementMessage(kind)
    }

    private fun improvementMessage(kind: XAgeDataKind): String = when (kind) {
        XAgeDataKind.Pressure ->
            "可使用小捷硬件获得更精准的 HRV 反馈，或同步 Health Connect 中的近期心率、睡眠和活动。"
        XAgeDataKind.Recovery ->
            "可使用小捷硬件，或同步 Health Connect 中的近期 HRV、睡眠、静息心率和生理稳定性数据。"
        XAgeDataKind.Inflammation ->
            "可同步 Health Connect，或上传并确认血常规、hsCRP 等近期体检报告。"
    }

    private fun metrics(scores: XAgeCompositeScores) =
        listOf(scores.pressure, scores.recovery, scores.inflammation)
}

enum class XAgeScoreAction {
    Detail,
    Explanation,
    Completeness,
}

/** Stable three-action accessibility contract shared by UI and regressions. */
object XAgeScoreActionContract {
    const val MINIMUM_TOUCH_TARGET_DP = 48

    fun testTag(kind: XAgeDataKind, action: XAgeScoreAction): String {
        val base = "xage.data.score.${kind.accessibilityKey}"
        return when (action) {
            XAgeScoreAction.Detail -> base
            XAgeScoreAction.Explanation -> "$base.info"
            XAgeScoreAction.Completeness -> "$base.confidenceWarning"
        }
    }
}

data class XAgeScoreInfoPresentation(
    val title: String,
    val scoreTypeAndConfidence: String,
    val conclusion: String,
    val professionalBasis: String,
    val emptyEvidenceMessage: String,
    val nextAction: String,
)

object XAgeScoreInfoPresentationPolicy {
    fun presentation(kind: XAgeDataKind, metric: XAgeMetricScore) = XAgeScoreInfoPresentation(
        title = "${kind.label}评分说明",
        scoreTypeAndConfidence =
            "${if (metric.isProxy) "代理参考分" else "每日参考分"} · " +
                "${XAgeScoreStatusPresentation.confidenceBand(metric)} ${metric.confidence.coerceIn(0, 100)}%",
        conclusion = metric.simpleExplanation,
        professionalBasis = metric.explanation,
        emptyEvidenceMessage = "当前没有有效相关信号，参考分使用中性起点；外环为 0%。",
        nextAction = metric.nextAction,
    )
}

/** Pure geometry used by Compose and JVM tests. All values are in the caller's size unit. */
object XAgeScoreRingGeometry {
    const val SCORE_ARC_START_DEGREES = 133.2f
    const val SCORE_ARC_SWEEP_DEGREES = 273.6f

    fun confidenceLineWidth(size: Double): Double = maxOf(3.0, size * 0.045)

    fun scoreRingSize(size: Double): Double = size * 0.84

    fun scoreLineWidth(size: Double): Double = maxOf(7.0, scoreRingSize(size) * 0.1)

    fun confidenceInnerRadius(size: Double): Double =
        size / 2.0 - confidenceLineWidth(size) / 2.0

    fun scoreOuterRadius(size: Double): Double =
        scoreRingSize(size) / 2.0 + scoreLineWidth(size) / 2.0
}
