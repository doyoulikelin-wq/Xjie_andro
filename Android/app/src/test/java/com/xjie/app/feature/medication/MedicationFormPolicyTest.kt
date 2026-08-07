package com.xjie.app.feature.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationFormPolicyTest {
    @Test
    fun invalidReminderTimeNeverSilentlyDisappearsOnSave() {
        val invalid = MedicationFormPolicy.validate(
            times = listOf("08:00", "25:90"),
            courseStart = "",
            courseEnd = "",
        )

        assertFalse(invalid.isValid)
        assertTrue(requireNotNull(invalid.error).contains("25:90"))

        val valid = MedicationFormPolicy.validate(
            times = listOf(" 08:00 ", "08:00", "20:30"),
            courseStart = "",
            courseEnd = "",
        )
        assertTrue(valid.isValid)
        assertEquals(listOf("08:00", "20:30"), valid.normalizedTimes)
    }

    @Test
    fun courseRangeRequiresRealInclusiveDatesInOrder() {
        assertFalse(MedicationFormPolicy.validate(emptyList(), "2026-02-30", "").isValid)
        assertFalse(MedicationFormPolicy.validate(emptyList(), "2026-07-16", "2026-07-15").isValid)
        assertTrue(MedicationFormPolicy.validate(emptyList(), "2026-07-15", "2026-07-15").isValid)
    }
}
