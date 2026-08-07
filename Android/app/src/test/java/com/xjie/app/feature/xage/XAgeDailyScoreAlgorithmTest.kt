package com.xjie.app.feature.xage

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XAgeDailyScoreAlgorithmTest {
    @Test
    fun dailyScoreConfidenceUsesRelevantCompletenessAndKeepsSparseEstimatesVisible() {
        val now = Instant.parse("2026-08-07T00:00:00Z")
        val hrv = XAgeDailyScoreSample(
            metricId = "hrv",
            indicatorName = "心率变异性",
            value = 46.0,
            unit = "ms",
            measuredAt = now,
        )
        val sparse = XAgeDailyScoreAlgorithm.compute(
            XAgeAlgorithmContext(samples = listOf(hrv), referenceDate = now),
        )
        val unrelatedVolume = XAgeDailyScoreAlgorithm.compute(
            XAgeAlgorithmContext(
                trendPointCount = 10_000,
                documentCount = 600,
                watchedIndicatorCount = 80,
                samples = listOf(hrv),
                referenceDate = now,
            ),
        )

        assertEquals(sparse.pressure.confidence, unrelatedVolume.pressure.confidence)
        assertTrue(sparse.pressure.confidence in 1..59)

        val daily = XAgeDailyScorePresentationPolicy.presentation(sparse)
        assertTrue(daily.pressure.hasDisplayableScore)
        assertEquals(sparse.pressure.value.toString(), daily.pressure.dailyDisplayValue)
        assertEquals("xage.daily.estimate.v1", daily.pressure.dailyEstimateVersion)
        assertEquals(null, daily.pressure.serverSnapshotVersion)
    }
}
