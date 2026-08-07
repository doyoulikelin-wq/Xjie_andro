package com.xjie.app.feature.xage

import com.xjie.app.feature.healthconnect.HealthConnectMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XAgeScoreStatusPresentationTest {
    @Test
    fun confidenceExplanationNeverClaimsPresentSignalsAreMissing() {
        val metric = metric(
            confidence = 52,
            signalCount = 6,
            signalTarget = 6,
            missing = emptyList(),
        )

        val explanation = XAgeScoreStatusPresentation.confidenceExplanation(
            XAgeDataKind.Pressure,
            metric,
        )

        assertTrue(explanation.contains("已覆盖 6/6"))
        assertTrue(explanation.contains("关键数据类型已覆盖"))
        assertFalse(explanation.contains("仍需补充"))
    }

    @Test
    fun summaryPresentationHasDistinctUnavailableFirstUseNeedsDataAndReadyStates() {
        val unavailable = XAgeScoreStatusPresentation.summaryPresentation(
            XAgeTrustedScorePresentationPolicy.unavailable,
        )
        val firstUse = XAgeScoreStatusPresentation.summaryPresentation(
            scores(metric(confidence = 20), metric(confidence = 30), metric(confidence = 40)),
        )
        val needsData = XAgeScoreStatusPresentation.summaryPresentation(
            scores(
                metric(confidence = 45, missing = listOf("近期心率"), isReady = true),
                metric(confidence = 80, isReady = true),
                metric(confidence = 85, isReady = true),
            ),
        )
        val ready = XAgeScoreStatusPresentation.summaryPresentation(
            scores(
                metric(confidence = 80, isReady = true),
                metric(confidence = 85, isReady = true),
                metric(confidence = 90, isReady = true),
            ),
        )

        assertEquals(XAgeScoreStatusPresentation.SummaryMode.Unavailable, unavailable.mode)
        assertEquals(XAgeScoreStatusPresentation.UNAVAILABLE_PROMPT_TITLE, unavailable.title)
        assertEquals(XAgeScoreStatusPresentation.SummaryMode.FirstUse, firstUse.mode)
        assertEquals(XAgeScoreStatusPresentation.NO_SUPPORT_DATA_PROMPT_TITLE, firstUse.title)
        assertEquals(XAgeScoreStatusPresentation.SummaryMode.NeedsData, needsData.mode)
        assertEquals("补齐评分数据", needsData.title)
        assertTrue(needsData.message.contains("压力置信度较低："))
        assertTrue(needsData.message.contains("近期心率"))
        assertEquals(XAgeScoreStatusPresentation.SummaryMode.Ready, ready.mode)
        assertEquals("今日状态", ready.title)
    }

    @Test
    fun scoreRingExposesIndependentDetailExplanationAndCompletenessActions() {
        val tags = XAgeScoreAction.entries.map { action ->
            XAgeScoreActionContract.testTag(XAgeDataKind.Pressure, action)
        }
        val source = metric(confidence = 25, missing = listOf("近期心率"))
        val info = XAgeScoreInfoPresentationPolicy.presentation(XAgeDataKind.Pressure, source)

        assertEquals(3, tags.distinct().size)
        assertEquals(
            listOf(
                "xage.data.score.pressure",
                "xage.data.score.pressure.info",
                "xage.data.score.pressure.confidenceWarning",
            ),
            tags,
        )
        assertEquals(48, XAgeScoreActionContract.MINIMUM_TOUCH_TARGET_DP)
        assertEquals("压力评分说明", info.title)
        assertEquals(source.simpleExplanation, info.conclusion)
        assertFalse(info.conclusion.contains("当前数据完整度"))
        assertTrue(
            XAgeScoreStatusPresentation.confidenceExplanation(XAgeDataKind.Pressure, source)
                .contains("当前数据完整度"),
        )
    }
}

class XAgeScoreRingGeometryTest {
    @Test
    fun confidenceOuterRingWrapsScoreArcAndLowConfidenceRequiresExplanation() {
        val size = 100.0

        assertTrue(
            XAgeScoreRingGeometry.confidenceInnerRadius(size) >
                XAgeScoreRingGeometry.scoreOuterRadius(size),
        )
        assertEquals(133.2f, XAgeScoreRingGeometry.SCORE_ARC_START_DEGREES)
        assertEquals(273.6f, XAgeScoreRingGeometry.SCORE_ARC_SWEEP_DEGREES)
        assertTrue(XAgeScoreStatusPresentation.requiresConfidenceAttention(metric(confidence = 59)))
        assertFalse(XAgeScoreStatusPresentation.requiresConfidenceAttention(metric(confidence = 60)))
    }
}

class XAgeHealthTrendRequestContractTest {
    @Test
    fun trendRequestsCoverSupportedHealthConnectAndRequiredLabIndicators() {
        val names = XAgeHealthTrendRequestContract.names(listOf("用户关注", "hsCRP"))

        assertTrue(HealthConnectMetric.entries.all { it.indicatorName in names })
        assertTrue(XAgeHealthTrendRequestContract.algorithmRequiredNames.all(names::contains))
        assertEquals(10, XAgeHealthTrendRequestContract.algorithmRequiredNames.size)
        assertEquals(names.size, names.map { it.lowercase() }.distinct().size)
        assertTrue("用户关注" in names)
    }
}

private fun metric(
    confidence: Int,
    signalCount: Int = 0,
    signalTarget: Int = 0,
    missing: List<String> = emptyList(),
    isReady: Boolean = false,
) = XAgeMetricScore(
    value = 50,
    confidence = confidence,
    isReady = isReady,
    badgeLabel = "低置信参考",
    stateLabel = "低置信参考分",
    summary = "参考分",
    simpleExplanation = "说明",
    explanation = "说明",
    nextAction = "下一步",
    fields = emptyList(),
    drivers = emptyList(),
    isProxy = false,
    dailyEstimateVersion = XAgeDailyScorePresentationPolicy.ALGORITHM_VERSION,
    confidenceSignalCount = signalCount,
    confidenceSignalTarget = signalTarget,
    confidenceMissingSignals = missing,
)

private fun scores(
    pressure: XAgeMetricScore,
    recovery: XAgeMetricScore,
    inflammation: XAgeMetricScore,
) = XAgeCompositeScores(
    pressure = pressure,
    recovery = recovery,
    inflammation = inflammation,
    xAge = XAgeTrustedScorePresentationPolicy.unavailable.xAge,
)
