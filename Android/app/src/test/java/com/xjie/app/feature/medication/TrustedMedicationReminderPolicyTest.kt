package com.xjie.app.feature.medication

import com.xjie.app.core.model.MedicationInventoryEstimate
import com.xjie.app.core.model.MedicationPrefillCandidate
import com.xjie.app.core.model.MedicationTodaySummary
import com.xjie.app.core.model.MedicationTodayTask
import com.xjie.app.core.model.TrustedMedicationPlan
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedMedicationReminderPolicyTest {
    @Test
    fun defaultReminderIsOffAndLockScreenMedicationNameIsPrivate() {
        val settings = TrustedMedicationReminderPolicy.defaults(plan())

        assertFalse(settings.enabled)
        assertFalse(settings.showMedicationNameOnLockScreen)
        assertTrue(settings.soundEnabled)
        assertEquals(15, settings.snoozeMinutes)
        assertTrue(TrustedMedicationReminderPolicy.isCurrentForPlan(settings, plan()))
    }

    @Test
    fun reminderValidationRejectsInvalidTimeRangeAndCourseOverrun() {
        val base = TrustedMedicationReminderPolicy.defaults(plan())
        assertTrue(TrustedMedicationReminderPolicy.validate(base.copy(enabled = true)).isValid)

        assertFalse(
            TrustedMedicationReminderPolicy.validate(
                base.copy(enabled = true, scheduleTimes = listOf("25:00")),
            ).isValid,
        )
        assertFalse(
            TrustedMedicationReminderPolicy.validate(
                base.copy(enabled = true, reminderEndDate = "2026-08-01"),
            ).isValid,
        )
        assertFalse(
            TrustedMedicationReminderPolicy.validate(
                base.copy(enabled = true, snoozeMinutes = 2),
            ).isValid,
        )
    }

    @Test
    fun alternateDayReminderUsesDoseDateAndAdvanceAcrossMidnight() {
        val zone = ZoneId.of("Asia/Shanghai")
        val settings = TrustedMedicationReminderPolicy.defaults(plan()).copy(
            enabled = true,
            cadence = TrustedMedicationReminderSettings.CADENCE_ALTERNATE_DAYS,
            anchorDate = "2026-07-15",
            scheduleTimes = listOf("00:30"),
            advanceMinutes = 60,
        )

        val first = TrustedMedicationReminderPolicy.nextTrigger(
            settings,
            ZonedDateTime.of(2026, 7, 14, 22, 0, 0, 0, zone),
            LocalTime.of(0, 30),
        )
        val second = TrustedMedicationReminderPolicy.nextTrigger(
            settings,
            ZonedDateTime.of(2026, 7, 14, 23, 31, 0, 0, zone),
            LocalTime.of(0, 30),
        )

        assertEquals(LocalDate.of(2026, 7, 14), first?.toLocalDate())
        assertEquals(LocalTime.of(23, 30), first?.toLocalTime())
        assertEquals(LocalDate.of(2026, 7, 16), second?.toLocalDate())
        assertEquals(LocalTime.of(23, 30), second?.toLocalTime())
    }

    @Test
    fun trustedPlanAndSnoozeAlarmIdentitiesAreStableAndSeparate() {
        val time = LocalTime.of(20, 0)
        val planFirst = MedicationReminderPolicy.trustedPlanRequestCode(11, time)
        val planRetry = MedicationReminderPolicy.trustedPlanRequestCode(11, time)
        val snooze = MedicationReminderPolicy.trustedSnoozeRequestCode(
            "dose:v1:11:2026-07-15:20:00",
        )
        val legacy = MedicationReminderPolicy.medicationRequestCode(11, time)

        assertEquals(planFirst, planRetry)
        assertNotEquals(planFirst, snooze)
        assertNotEquals(planFirst, legacy)
        assertNotEquals(0, planFirst)
    }

    @Test
    fun confirmedRateCountsOnlyUserConfirmedTakenAndSkippedTasks() {
        val tasks = listOf(
            task("taken", "user_confirmed"),
            task("skipped", "user_confirmed", hour = 9),
            task("snoozed", "schedule_derived", hour = 10),
            task("possibly_missed", "schedule_derived", hour = 11),
        )
        val summary = summary(tasks)

        val rate = TrustedMedicationReminderPolicy.confirmedRate(listOf(summary))

        assertEquals(2, rate.confirmedCount)
        assertEquals(4, rate.plannedCount)
        assertEquals(50, rate.percentage)
        assertEquals("已确认率 50%（2/4）", rate.label)
    }

    @Test
    fun coursePresentationNeverGuessesRefillOrCourseConfirmedRate() {
        val course = TrustedMedicationReminderPolicy.coursePresentation(
            plan(),
            today = LocalDate.of(2026, 7, 15),
        )

        assertEquals(6, course.elapsedDays)
        assertTrue(course.endingSoon)
        assertTrue(course.refillEligibility.contains("暂不可用"))
        assertTrue(course.refillEligibility.contains("可核验证据"))
        assertTrue(course.confirmedRate.contains("服务端尚未提供完整疗程聚合"))
    }

    @Test
    fun addSourcesAreRealCandidatesOrExplicitlyUnavailable() {
        val withoutCandidates = TrustedMedicationReminderPolicy.addCapabilities(emptyList())
        assertFalse(withoutCandidates.single {
            it.action == MedicationAddAction.PrescriptionImport
        }.available)
        assertFalse(withoutCandidates.single {
            it.action == MedicationAddAction.HistoryRestart
        }.available)
        assertTrue(withoutCandidates.single { it.action == MedicationAddAction.RawOcrText }.available)
        assertTrue(withoutCandidates.single { it.action == MedicationAddAction.Manual }.available)

        val withCandidates = TrustedMedicationReminderPolicy.addCapabilities(
            listOf(candidate("prescription_import"), candidate("history", id = 9)),
        )
        assertTrue(withCandidates.single {
            it.action == MedicationAddAction.PrescriptionImport
        }.available)
        assertTrue(withCandidates.single {
            it.action == MedicationAddAction.HistoryRestart
        }.available)
    }

    @Test
    fun reminderStopsAfterConfirmedCourseEnd() {
        val zone = ZoneId.of("Asia/Shanghai")
        val settings = TrustedMedicationReminderPolicy.defaults(plan()).copy(enabled = true)
        val trigger = TrustedMedicationReminderPolicy.nextTrigger(
            settings,
            ZonedDateTime.of(2026, 7, 21, 8, 0, 0, 0, zone),
            LocalTime.of(20, 0),
        )

        assertNull(trigger)
    }

    private fun plan() = TrustedMedicationPlan(
        plan_id = 11,
        subject_user_id = 99,
        generic_name = "阿托伐他汀",
        dose_text = "20mg",
        schedule_times = listOf("20:00"),
        meal_relation = "after_meal",
        course_start = "2026-07-10",
        course_end = "2026-07-20",
        source_type = "manual",
        source_ref = "manual",
        status = "active",
        version = 3,
        confirmed_at = "2026-07-10T10:00:00+08:00",
        trust_state = "user_confirmed",
        reminder_management = "client_managed",
        reminder_default_enabled = false,
        server_notification_scheduled = false,
        inventory = MedicationInventoryEstimate(
            is_estimate = true,
            label = "预计剩余",
            estimated_remaining = 18.0,
            estimated_consumed = 12.0,
            inventory_unit = "片",
            basis = "user_confirmed_taken_events_only",
        ),
    )

    private fun candidate(source: String, id: Long = 8) = MedicationPrefillCandidate(
        candidate_id = id,
        subject_user_id = 99,
        client_event_id = "event-$id",
        source_type = source,
        source_ref = "$source:$id",
        extracted_data = buildJsonObject {},
        review_status = "pending_review",
        version = 1,
        trust_state = "unconfirmed_prefill",
        requires_user_confirmation = true,
        plan_created = false,
        confirmation_endpoint = "/api/medications/trust/plans/confirm",
    )

    private fun task(status: String, assertion: String, hour: Int = 8) = MedicationTodayTask(
        occurrence_key = "dose:v1:11:2026-07-15:${hour.toString().padStart(2, '0')}:00",
        plan_id = 11,
        plan_version = 3,
        generic_name = "药品",
        scheduled_local_date = "2026-07-15",
        scheduled_time = "${hour.toString().padStart(2, '0')}:00",
        scheduled_at = "2026-07-15T${hour.toString().padStart(2, '0')}:00:00+08:00",
        status = status,
        status_label = status,
        status_assertion = assertion,
        occurrence_version = if (assertion == "user_confirmed") 1 else 0,
        latest_event_id = if (assertion == "user_confirmed") hour.toLong() else null,
        possibly_missed_is_not_confirmation = status == "possibly_missed",
        notification_schedule_status = "client_managed",
    )

    private fun summary(tasks: List<MedicationTodayTask>) = MedicationTodaySummary(
        subject_user_id = 99,
        local_date = "2026-07-15",
        planned_count = tasks.size,
        taken_count = tasks.count { it.status == "taken" },
        awaiting_confirmation_count = tasks.count { it.status == "awaiting_confirmation" },
        possibly_missed_count = tasks.count { it.status == "possibly_missed" },
        skipped_count = tasks.count { it.status == "skipped" },
        snoozed_count = tasks.count { it.status == "snoozed" },
        adverse_reaction_count = 0,
        next_task = tasks.firstOrNull { it.status !in setOf("taken", "skipped") },
        tasks = tasks,
        missed_assertion_policy = "elapsed_time_never_confirms_missed",
    )
}
