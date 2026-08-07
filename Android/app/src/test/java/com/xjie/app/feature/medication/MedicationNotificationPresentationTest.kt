package com.xjie.app.feature.medication

import com.xjie.app.core.model.MedicationInventoryEstimate
import com.xjie.app.core.model.MedicationTodayTask
import com.xjie.app.core.model.TrustedMedicationPlan
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationNotificationPresentationTest {
    @Test
    fun scheduledLabelRequiresPermissionExactAccessCurrentOwnerAndActualCount() {
        val owner = MedicationReminderOwner("account-a", "7", 4)
        val plan = plan()
        val setting = TrustedMedicationReminderPolicy.defaults(
            plan = plan,
            today = LocalDate.of(2026, 8, 7),
            owner = owner,
            timezoneId = "Asia/Shanghai",
        ).copy(enabled = true)

        val scheduled = MedicationReminderPresentation.resolve(
            task = task(),
            plans = listOf(plan),
            settings = setting,
            notificationPermission = MedicationNotificationPermissionState.Allowed,
            exactAlarmAccess = MedicationExactAlarmAccessState.Allowed,
            scheduledCount = 1,
            owner = owner,
            timezoneId = "Asia/Shanghai",
        )
        val noActualAlarm = MedicationReminderPresentation.resolve(
            task = task(),
            plans = listOf(plan),
            settings = setting,
            notificationPermission = MedicationNotificationPermissionState.Allowed,
            exactAlarmAccess = MedicationExactAlarmAccessState.Allowed,
            scheduledCount = 0,
            owner = owner,
            timezoneId = "Asia/Shanghai",
        )

        assertEquals("提醒已安排", scheduled.compactTitle)
        assertEquals(MedicationReminderTone.Active, scheduled.tone)
        assertEquals("提醒未安排", noActualAlarm.compactTitle)
        assertEquals(MedicationReminderTone.Warning, noActualAlarm.tone)
    }

    @Test
    fun generationOrTimezoneChangeStopsOldPresentation() {
        val oldOwner = MedicationReminderOwner("account-a", "7", 4)
        val plan = plan()
        val setting = TrustedMedicationReminderPolicy.defaults(
            plan = plan,
            owner = oldOwner,
            timezoneId = "Asia/Shanghai",
        ).copy(enabled = true)

        val changed = MedicationReminderPresentation.resolve(
            task = task(),
            plans = listOf(plan),
            settings = setting,
            notificationPermission = MedicationNotificationPermissionState.Allowed,
            exactAlarmAccess = MedicationExactAlarmAccessState.Allowed,
            scheduledCount = 3,
            owner = oldOwner.copy(authGeneration = 5),
            timezoneId = "Europe/Berlin",
        )

        assertEquals("需重设提醒", changed.compactTitle)
        assertTrue(changed.detail.contains("旧版本通知已停止"))
    }

    @Test
    fun lockScreenCopyHidesMedicationNameUnlessUserExplicitlyOptsIn() {
        val hidden = MedicationNotificationPresentationPolicy.resolve(
            genericName = "阿托伐他汀",
            doseText = "20mg",
            instructions = "饭后服用",
            showMedicationNameOnLockScreen = false,
        )
        val exposed = MedicationNotificationPresentationPolicy.resolve(
            genericName = "阿托伐他汀",
            doseText = "20mg",
            instructions = "饭后服用",
            showMedicationNameOnLockScreen = true,
        )

        assertFalse(hidden.title.contains("阿托伐他汀"))
        assertFalse(hidden.body.contains("20mg"))
        assertFalse(hidden.publicTitle.contains("阿托伐他汀"))
        assertFalse(hidden.exposePrivateContentOnLockScreen)
        assertTrue(exposed.publicTitle.contains("阿托伐他汀"))
        assertTrue(exposed.exposePrivateContentOnLockScreen)
    }

    private fun task() = MedicationTodayTask(
        occurrence_key = "dose:v1:11:2026-08-07:20:00",
        plan_id = 11,
        plan_version = 3,
        generic_name = "阿托伐他汀",
        dose_text = "20mg",
        scheduled_local_date = "2026-08-07",
        scheduled_time = "20:00",
        scheduled_at = "2026-08-07T20:00:00+08:00",
        status = "upcoming",
        status_label = "尚未到计划时间",
        status_assertion = "schedule_derived",
        occurrence_version = 1,
        possibly_missed_is_not_confirmation = false,
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
            basis = "user_confirmed_taken_events_only",
        ),
    )
}
