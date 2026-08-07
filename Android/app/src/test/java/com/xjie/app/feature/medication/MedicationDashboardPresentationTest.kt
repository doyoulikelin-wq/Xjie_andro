package com.xjie.app.feature.medication

import com.xjie.app.core.model.MedicationInventoryEstimate
import com.xjie.app.core.model.MedicationTodaySummary
import com.xjie.app.core.model.MedicationTodayTask
import com.xjie.app.core.model.TrustedMedicationPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationDashboardPresentationTest {
    @Test
    fun heroIsFiniteAndUsesOnlyServerNextTask() {
        assertEquals(
            MedicationDashboardHeroState.Loading,
            MedicationDashboardPresentation.hero(null, emptyList(), loading = true),
        )
        assertEquals(
            MedicationDashboardHeroState.NoMedication,
            MedicationDashboardPresentation.hero(today(next = null), emptyList(), loading = false),
        )

        val serverTask = task(status = "possibly_missed")
        val next = MedicationDashboardPresentation.hero(
            today = today(next = serverTask),
            plans = listOf(plan()),
            loading = false,
        )

        assertTrue(next is MedicationDashboardHeroState.NextDose)
        assertSame(serverTask, (next as MedicationDashboardHeroState.NextDose).task)
        assertEquals("possibly_missed", next.task.status)
        assertTrue(next.task.possibly_missed_is_not_confirmation)
    }

    @Test
    fun plansWithoutServerNextTaskResolveHandledWithoutGuessing() {
        val state = MedicationDashboardPresentation.hero(
            today = today(next = null).copy(empty_state = "今天没有更多待处理剂次"),
            plans = listOf(plan()),
            loading = false,
        )

        assertEquals(
            MedicationDashboardHeroState.AllHandled("今天没有更多待处理剂次"),
            state,
        )
    }

    private fun today(next: MedicationTodayTask?) = MedicationTodaySummary(
        subject_user_id = 7,
        local_date = "2026-08-07",
        planned_count = if (next == null) 0 else 1,
        taken_count = 0,
        awaiting_confirmation_count = 0,
        possibly_missed_count = if (next?.status == "possibly_missed") 1 else 0,
        skipped_count = 0,
        snoozed_count = 0,
        adverse_reaction_count = 0,
        next_task = next,
        tasks = listOfNotNull(next),
        empty_state = null,
        missed_assertion_policy = "elapsed_time_never_confirms_missed",
    )

    private fun task(status: String) = MedicationTodayTask(
        occurrence_key = "dose:v1:11:2026-08-07:20:00",
        plan_id = 11,
        plan_version = 3,
        generic_name = "阿托伐他汀",
        dose_text = "20mg",
        scheduled_local_date = "2026-08-07",
        scheduled_time = "20:00",
        scheduled_at = "2026-08-07T20:00:00+08:00",
        status = status,
        status_label = "可能漏服（待确认）",
        status_assertion = "schedule_derived",
        occurrence_version = 2,
        possibly_missed_is_not_confirmation = status == "possibly_missed",
        notification_schedule_status = "client_managed",
    )

    private fun plan() = TrustedMedicationPlan(
        plan_id = 11,
        subject_user_id = 7,
        generic_name = "阿托伐他汀",
        dose_text = "20mg",
        schedule_times = listOf("20:00"),
        meal_relation = "after_meal",
        course_start = "2026-08-01",
        course_end = "2026-09-01",
        source_type = "manual",
        source_ref = "user",
        status = "active",
        version = 3,
        confirmed_at = "2026-08-01T08:00:00+08:00",
        trust_state = "user_confirmed",
        reminder_management = "client_managed",
        reminder_default_enabled = false,
        server_notification_scheduled = false,
        inventory = MedicationInventoryEstimate(
            is_estimate = true,
            label = "预计剩余",
            estimated_remaining = 20.0,
            inventory_unit = "片",
            basis = "user_confirmed_taken_events_only",
        ),
    )
}
