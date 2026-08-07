package com.xjie.app.feature.medication

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Pure reminder rules shared by initial scheduling, alarm delivery, reboot, and clock changes.
 * Daily reminders are always recalculated against the current local calendar instead of adding
 * a fixed 24-hour interval, so wall-clock time survives time-zone and daylight-saving changes.
 */
internal object MedicationReminderPolicy {
    private val minuteFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun normalizedTimes(values: List<String>): List<LocalTime> = values
        .mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }
        .map { it.withSecond(0).withNano(0) }
        .distinct()
        .sorted()

    fun canonicalTime(time: LocalTime): String = time.format(minuteFormatter)

    fun parseDate(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Returns the first valid occurrence strictly after [now], or null after the course ends. */
    fun nextDailyTrigger(
        now: ZonedDateTime,
        time: LocalTime,
        courseStart: LocalDate? = null,
        courseEnd: LocalDate? = null,
    ): ZonedDateTime? {
        var date = now.toLocalDate()
        if (courseStart != null && date.isBefore(courseStart)) date = courseStart

        var candidate = date.atTime(time).atZone(now.zone)
        if (!candidate.isAfter(now)) {
            date = date.plusDays(1)
            candidate = date.atTime(time).atZone(now.zone)
        }

        if (courseEnd != null && date.isAfter(courseEnd)) return null
        return candidate
    }

    /** Stable across process restarts and schedule-list reordering. Intent data adds collision safety. */
    fun medicationRequestCode(medicationId: Long, time: LocalTime): Int {
        return stableRequestCode(medicationAlarmIdentity(medicationId, time))
    }

    /** Stable one-shot identity; separate namespace prevents collisions with daily reminders. */
    fun trustedSnoozeRequestCode(occurrenceKey: String): Int =
        stableRequestCode("trusted-snooze:$occurrenceKey")

    fun trustedSnoozeRequestCode(snapshot: TrustedMedicationSnoozeSnapshot): Int =
        stableRequestCode(trustedSnoozeAlarmIdentity(snapshot))

    /** Stable trusted-plan identity, isolated from both legacy schedules and one-shot snoozes. */
    fun trustedPlanRequestCode(planId: Long, time: LocalTime): Int =
        stableRequestCode(trustedPlanAlarmIdentity(planId, time))

    fun trustedPlanRequestCode(
        settings: TrustedMedicationReminderSettings,
        time: LocalTime,
    ): Int = stableRequestCode(trustedPlanAlarmIdentity(settings, time))

    private fun stableRequestCode(identity: String): Int {
        var hash = 0x811C9DC5u
        identity.encodeToByteArray().forEach { byte ->
            hash = (hash xor byte.toUByte().toUInt()) * 0x01000193u
        }
        return (hash.toInt() and Int.MAX_VALUE).let { if (it == 0) 1 else it }
    }

    fun medicationAlarmIdentity(medicationId: Long, time: LocalTime): String =
        "$medicationId:${canonicalTime(time)}"

    fun trustedPlanAlarmIdentity(planId: Long, time: LocalTime): String =
        "trusted-plan:$planId:${canonicalTime(time)}"

    fun trustedPlanAlarmIdentity(
        settings: TrustedMedicationReminderSettings,
        time: LocalTime,
    ): String = listOf(
        "trusted-plan",
        settings.accountScope,
        settings.selectedSubjectId,
        settings.authGeneration,
        settings.subjectUserId,
        settings.planId,
        settings.planVersion,
        settings.timezoneId,
        canonicalTime(time),
    ).joinToString(":")

    fun trustedSnoozeAlarmIdentity(snapshot: TrustedMedicationSnoozeSnapshot): String = listOf(
        "trusted-snooze",
        snapshot.accountScope,
        snapshot.selectedSubjectId,
        snapshot.authGeneration,
        snapshot.subjectUserId,
        snapshot.planId,
        snapshot.planVersion,
        snapshot.timezoneId,
        snapshot.occurrenceKey,
    ).joinToString(":")

    fun elderlySlots(intervalMinutes: Int, maximumSlots: Int): List<LocalTime> {
        if (intervalMinutes <= 0 || maximumSlots <= 0) return emptyList()
        val slots = mutableListOf<LocalTime>()
        var minuteOfDay = 8L * 60L
        val endMinuteOfDay = 22L * 60L
        while (minuteOfDay <= endMinuteOfDay && slots.size < maximumSlots) {
            slots += LocalTime.of((minuteOfDay / 60L).toInt(), (minuteOfDay % 60L).toInt())
            minuteOfDay += intervalMinutes.toLong()
        }
        return slots
    }

    /** Exact request-code domain scanned by the legacy implementation's cancellation loop. */
    fun legacyMigrationRequestCodes(): IntRange =
        0 until LEGACY_MEDICATION_CAPACITY * LEGACY_SLOTS_PER_MEDICATION

    fun shouldRunLegacyMigration(completed: Boolean): Boolean = !completed

    private const val LEGACY_MEDICATION_CAPACITY = 100
    private const val LEGACY_SLOTS_PER_MEDICATION = 32
}
