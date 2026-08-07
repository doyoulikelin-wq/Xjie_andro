package com.xjie.app.feature.weight

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.IndicatorTrend
import com.xjie.app.core.model.TrendPoint
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightDashboardParityTest {
    @Test
    fun latestBmiAndTrendUseFullHistoryHeightAndCurrentRelativeThreeMonthWindow() {
        val trend = IndicatorTrend(
            name = "体重",
            unit = "kg",
            points = listOf(
                point("2026-03-01", 80.0, source = "manual"),
                point("2026-04-21", 62.0, source = "document"),
                point("2026-07-20", 70.0, source = "manual"),
            ),
        )
        val presentation = WeightDashboardPolicy.presentation(
            WeightNetworkSnapshot(listOf(trend), profileHeightCm = 175.0),
            LocalDate.parse("2026-07-21"),
        )

        assertEquals(70.0, presentation.latestWeightKg!!, 0.0001)
        assertEquals(LocalDate.parse("2026-07-20"), presentation.latestDate)
        assertEquals(22.857, presentation.bmi!!, 0.001)
        assertEquals(listOf(62.0, 70.0), presentation.recentSamples.map { it.weightKg })
        assertEquals(
            WeightAxisDomain(57.0, 75.0),
            WeightDashboardPolicy.weightAxisDomain(presentation.recentSamples.map { it.weightKg }),
        )
        assertEquals(
            listOf(57, 63, 69, 75),
            WeightDashboardPolicy.weightAxisTicks(presentation.recentSamples.map { it.weightKg }),
        )
        assertTrue(
            WeightDashboardPolicy.chartContentWidthDp(
                presentation.windowStart,
                presentation.windowEnd,
                300f,
            ) > 1_700f,
        )
    }

    @Test
    fun sourceAdmissionAcceptsServerConfirmedFamiliesAndRejectsUnboundOrMalformedSamples() {
        val today = LocalDate.parse("2026-07-21")
        val trend = IndicatorTrend(
            name = "体重",
            unit = "kg",
            points = listOf(
                point("2026-07-10", 60.0, source = "manual"),
                point("2026-07-11", 61.0, source = "document"),
                point(
                    "2026-07-12",
                    62.0,
                    source = "device",
                    sourceMetric = "bodyWeight",
                    sourceId = "bodyWeight-hc-stable",
                ),
                point("2026-07-13", 63.0, source = "device", sourceMetric = "bodyWeight"),
                point(
                    "2026-07-14",
                    64.0,
                    source = "device",
                    sourceMetric = "steps",
                    sourceId = "wrong-metric",
                ),
                point("2026-07-15", 65.0, source = "spreadsheet"),
                point("2026-07-16", 66.0, source = "manual", valueKind = "category"),
                point("2026-07-22", 67.0, source = "manual"),
            ),
        )

        val admitted = WeightDashboardPolicy.admittedWeightSamples(trend, today)

        assertEquals(listOf(60.0, 61.0, 62.0), admitted.map { it.weightKg })
        assertEquals(
            listOf(
                WeightEvidenceSource.Manual,
                WeightEvidenceSource.ConfirmedReport,
                WeightEvidenceSource.Device,
            ),
            admitted.map { it.source },
        )
        assertTrue(
            WeightDashboardPolicy.admittedWeightSamples(trend.copy(unit = "lb"), today).isEmpty(),
        )
    }

    @Test
    fun emptyMissingHeightAndInputBoundariesNeverFabricateBmiOrSavedValues() {
        val today = LocalDate.parse("2026-07-21")
        val weight = IndicatorTrend(
            name = "体重",
            unit = "kg",
            points = listOf(point("2026-07-20", 70.0, source = "manual")),
        )
        val missingHeight = WeightDashboardPolicy.presentation(
            WeightNetworkSnapshot(listOf(weight), profileHeightCm = null),
            today,
        )

        assertTrue(missingHeight.needsHeight)
        assertNull(missingHeight.bmi)
        assertEquals(60, WeightDashboardPolicy.validatedHeight("60"))
        assertEquals(210, WeightDashboardPolicy.validatedHeight("210"))
        assertNull(WeightDashboardPolicy.validatedHeight("59"))
        assertNull(WeightDashboardPolicy.validatedHeight("211"))
        assertEquals("175", WeightDashboardPolicy.appendHeightDigit("175", 9))
        assertEquals("17", WeightDashboardPolicy.deleteHeightDigit("175"))

        assertEquals(WeightPickerSelection(77, 6), WeightDashboardPolicy.pickerSelection(77.64))
        assertEquals(WeightPickerSelection(65, 0), WeightDashboardPolicy.pickerSelection(null))
        assertEquals(77.6, WeightDashboardPolicy.weightFromPicker(77, 6)!!, 0.0001)
        assertNull(WeightDashboardPolicy.weightFromPicker(19, 9))
        assertTrue(WeightDashboardPolicy.validWeight(250.9))
        assertFalse(WeightDashboardPolicy.validWeight(251.0))
    }

    @Test
    fun requestTokenRejectsAccountSubjectAndAtoBtoAGenerationChanges() {
        val firstA = owner("account-a", "7", generation = 4)
        val accountB = owner("account-b", "8", generation = 5)
        val secondA = owner("account-a", "7", generation = 6)
        val token = WeightRequestToken(firstA, sequence = 9)

        assertTrue(token.accepts(token, firstA))
        assertFalse(token.accepts(token, accountB))
        assertFalse(token.accepts(token, secondA))
        assertFalse(token.accepts(WeightRequestToken(firstA, sequence = 10), firstA))
    }

    private fun point(
        date: String,
        value: Double,
        source: String,
        sourceMetric: String? = null,
        sourceId: String? = null,
        valueKind: String? = null,
    ): TrendPoint = TrendPoint(
        date = date,
        value = value,
        abnormal = false,
        source = source,
        measured_at = "${date}T08:00:00Z",
        source_metric = sourceMetric,
        source_id = sourceId,
        value_kind = valueKind,
        source_local_date = date,
    )

    private fun owner(
        account: String,
        subject: String,
        generation: Long,
    ) = AuthManager.AccountScopeSnapshot(account, subject, generation)
}
