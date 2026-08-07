package com.xjie.app.feature.xage

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XAgeDailyScoreEvidenceContractTest {
    @Test
    fun dailyScoreRejectsRawReportFieldsAndNormalizesAdmittedUnits() {
        assertFalse(XAgeDailyScoreEvidenceContract.admitsServerSource("raw_report"))
        assertFalse(XAgeDailyScoreEvidenceContract.admitsServerSource("ocr_candidate"))
        assertTrue(XAgeDailyScoreEvidenceContract.admitsServerSource("document"))
        assertEquals(
            2.0,
            XAgeDailyScoreEvidenceContract.canonicalValue(0.2, "mg/dL", "hsCRP")!!,
            0.0001,
        )
        assertEquals(
            8.0,
            XAgeDailyScoreEvidenceContract.canonicalValue(480.0, "min", "睡眠")!!,
            0.0001,
        )
        assertNull(XAgeDailyScoreEvidenceContract.canonicalValue(46.0, "unknown", "HRV/PRV"))
        assertNull(XAgeDailyScoreEvidenceContract.canonicalValue(900.0, "ms", "HRV/PRV"))
    }

    @Test
    fun confidenceRejectsExpiredSignalsAndServerRoundTripDoesNotInflate() {
        val now = Instant.parse("2026-08-07T00:00:00Z")
        val recent = trend(measuredAt = "2026-08-07T00:00:00Z")
        val expired = trend(measuredAt = "2016-08-07T00:00:00Z")
        val single = XAgeDailyScoreAlgorithm.compute(
            XAgeAlgorithmContext(serverTrends = listOf(recent), referenceDate = now),
        )
        val roundTripDuplicate = XAgeDailyScoreAlgorithm.compute(
            XAgeAlgorithmContext(serverTrends = listOf(recent, recent.copy()), referenceDate = now),
        )
        val staleOnly = XAgeDailyScoreAlgorithm.compute(
            XAgeAlgorithmContext(serverTrends = listOf(expired), referenceDate = now),
        )

        assertEquals(single.pressure.confidence, roundTripDuplicate.pressure.confidence)
        assertEquals(single.pressure.value, roundTripDuplicate.pressure.value)
        assertEquals(0, staleOnly.pressure.confidence)
        assertEquals(50, staleOnly.pressure.value)
    }

    private fun trend(measuredAt: String) = XAgeAlgorithmTrend(
        name = "HRV",
        value = 46.0,
        unit = "ms",
        refLow = null,
        refHigh = null,
        abnormal = false,
        measuredAt = measuredAt,
        source = "device",
        confidence = 0.9,
    )
}
