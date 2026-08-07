package com.xjie.app.feature.medication

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationReminderPolicyTest {
    @Test
    fun firedDailyReminder_schedulesTheNextStrictlyFutureLocalOccurrence() {
        val zone = ZoneId.of("Asia/Shanghai")
        val firedAt = ZonedDateTime.of(2026, 7, 15, 8, 30, 0, 0, zone)

        val next = MedicationReminderPolicy.nextDailyTrigger(
            now = firedAt,
            time = LocalTime.of(8, 30),
        )

        assertEquals(LocalDate.of(2026, 7, 16), next?.toLocalDate())
        assertEquals(LocalTime.of(8, 30), next?.toLocalTime())
        assertTrue(next!!.isAfter(firedAt))
    }

    @Test
    fun dailyReminder_recalculatesWallClockTimeAcrossDaylightSavingChange() {
        val zone = ZoneId.of("Europe/Berlin")
        val beforeFirst = ZonedDateTime.of(2026, 10, 24, 8, 0, 0, 0, zone)
        val first = MedicationReminderPolicy.nextDailyTrigger(beforeFirst, LocalTime.of(9, 0))!!
        val second = MedicationReminderPolicy.nextDailyTrigger(
            first.plusMinutes(1),
            LocalTime.of(9, 0),
        )!!

        assertEquals(LocalTime.of(9, 0), first.toLocalTime())
        assertEquals(LocalTime.of(9, 0), second.toLocalTime())
        assertEquals(Duration.ofHours(25), Duration.between(first.toInstant(), second.toInstant()))
    }

    @Test
    fun medicationCourse_startsInTheFutureAndEndsInclusively() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, zone)
        val start = LocalDate.of(2026, 7, 20)
        val end = LocalDate.of(2026, 7, 20)

        val first = MedicationReminderPolicy.nextDailyTrigger(
            now = now,
            time = LocalTime.of(8, 0),
            courseStart = start,
            courseEnd = end,
        )
        val afterCourse = MedicationReminderPolicy.nextDailyTrigger(
            now = first!!.plusMinutes(1),
            time = LocalTime.of(8, 0),
            courseStart = start,
            courseEnd = end,
        )

        assertEquals(start, first.toLocalDate())
        assertNull(afterCourse)
    }

    @Test
    fun medicationRequestIdentity_isStableWhenScheduleOrderChanges() {
        val morning = LocalTime.of(8, 0)
        val evening = LocalTime.of(20, 0)

        val firstPass = listOf(morning, evening).associateWith {
            MedicationReminderPolicy.medicationRequestCode(4_294_967_300L, it)
        }
        val reorderedPass = listOf(evening, morning).associateWith {
            MedicationReminderPolicy.medicationRequestCode(4_294_967_300L, it)
        }

        assertEquals(firstPass[morning], reorderedPass[morning])
        assertEquals(firstPass[evening], reorderedPass[evening])
        assertNotEquals(firstPass[morning], firstPass[evening])
        assertFalse(firstPass.values.contains(0))
    }

    @Test
    fun trustedSnoozeIdentityIsStableAndSeparateFromDailyReminder() {
        val occurrence = "dose:v1:11:2026-07-15:20:00"
        val first = MedicationReminderPolicy.trustedSnoozeRequestCode(occurrence)
        val retry = MedicationReminderPolicy.trustedSnoozeRequestCode(occurrence)
        val daily = MedicationReminderPolicy.medicationRequestCode(11, LocalTime.of(20, 0))

        assertEquals(first, retry)
        assertNotEquals(0, first)
        assertNotEquals(daily, first)
    }

    @Test
    fun normalizedTimes_rejectsInvalidValuesAndDeduplicatesEquivalentEntries() {
        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(20, 15)),
            MedicationReminderPolicy.normalizedTimes(
                listOf("20:15", "invalid", "08:00", "08:00:30"),
            ),
        )
    }

    @Test
    fun elderlySlots_doNotWrapIntoDuplicateDaysForLargeIntervals() {
        assertEquals(
            listOf(LocalTime.of(8, 0)),
            MedicationReminderPolicy.elderlySlots(
                intervalMinutes = 24 * 60,
                maximumSlots = 32,
            ),
        )
    }

    @Test
    fun legacyMigrationSweep_coversFormerCancellationDomainExactlyOnceAndStopsAfterCommit() {
        val requestCodes = MedicationReminderPolicy.legacyMigrationRequestCodes().toList()

        assertEquals(3_200, requestCodes.size)
        assertEquals(0, requestCodes.first())
        assertEquals(3_199, requestCodes.last())
        assertEquals(requestCodes.size, requestCodes.toSet().size)
        assertTrue(MedicationReminderPolicy.shouldRunLegacyMigration(completed = false))
        assertFalse(MedicationReminderPolicy.shouldRunLegacyMigration(completed = true))
    }
}
