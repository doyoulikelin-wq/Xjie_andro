package com.xjie.app.feature.meals

import com.xjie.app.core.model.DietaryBusinessDay
import com.xjie.app.core.model.DietaryMealType
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DietaryBusinessDayTest {
    @Test
    fun asiaShanghaiFourAmBoundaryOwnsTheLogicalDietDate() {
        val before = OffsetDateTime.parse("2026-07-15T03:59:59+08:00").toInstant()
        val boundary = OffsetDateTime.parse("2026-07-15T04:00:00+08:00").toInstant()

        assertEquals("2026-07-14", DietaryBusinessDay.dateKey(before))
        assertEquals("2026-07-15", DietaryBusinessDay.dateKey(boundary))
        assertEquals("Asia/Shanghai", DietaryBusinessDay.TIME_ZONE)
    }

    @Test
    fun futureSelectionIsClampedAndMealTypeUsesShanghaiClock() {
        val now = Instant.parse("2026-07-15T04:00:00Z") // 12:00 in Shanghai

        assertEquals(
            LocalDate.parse("2026-07-15"),
            DietaryBusinessDay.clampSelection(LocalDate.parse("2026-07-20"), now),
        )
        assertEquals(DietaryMealType.Lunch, DietaryBusinessDay.inferredMealType(now))
    }
}
