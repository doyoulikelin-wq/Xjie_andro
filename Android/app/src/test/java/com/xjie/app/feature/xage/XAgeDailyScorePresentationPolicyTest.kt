package com.xjie.app.feature.xage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XAgeDailyScorePresentationPolicyTest {
    @Test
    fun productionTrustPolicyRejectsReadyLocalResearchScoresAndKeepsXAgeDisabled() {
        val daily = XAgeDailyScorePresentationPolicy.presentation(
            XAgeDailyScoreAlgorithm.compute(XAgeAlgorithmContext()),
        )
        val local = daily.copy(pressure = daily.pressure.copy(isReady = true, value = 72))
        val trusted = XAgeTrustedScorePresentationPolicy.presentation(local)

        assertTrue(local.pressure.isReady)
        assertTrue(local.pressure.hasDisplayableScore)
        assertNull(local.pressure.serverSnapshotVersion)
        assertFalse(trusted.pressure.hasDisplayableScore)
        assertEquals("--", trusted.pressure.displayValue)
        assertFalse(XAgeTrustedScorePresentationPolicy.IS_XAGE_CONSUMPTION_ENABLED)
        assertFalse(trusted.xAge.isTrustedForDisplay)
        assertEquals("--", trusted.xAge.displayAge)
    }

    @Test
    fun unavailableScoreStatusDoesNotClaimEstimateWasGenerated() {
        val unavailable = XAgeDailyScorePresentationPolicy.presentation(
            XAgeServerSyncSnapshot.placeholder,
        )

        assertTrue(XAgeScoreStatusPresentation.isUnavailable(unavailable))
        assertEquals(
            XAgeScoreStatusPresentation.UNAVAILABLE_PROMPT_MESSAGE,
            unavailable.todaySummary,
        )
        assertFalse(unavailable.todaySummary.contains("已生成"))
        assertEquals("--", unavailable.pressure.dailyDisplayValue)
    }
}
