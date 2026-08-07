package com.xjie.app.feature.medication

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationReminderSchedulingStateTest {
    @Test
    fun enabledRequestFailsClosedUntilBothAndroidPermissionsExist() {
        assertEquals(
            MedicationReminderSchedulingState.NotificationPermissionRequired,
            resolve(notification = MedicationNotificationPermissionState.Denied),
        )
        assertEquals(
            MedicationReminderSchedulingState.ExactAlarmPermissionRequired,
            resolve(exact = MedicationExactAlarmAccessState.Required),
        )
    }

    @Test
    fun enabledRequestRequiresEveryUpcomingAlarmToActuallyScheduleAndPersist() {
        assertEquals(
            MedicationReminderSchedulingState.NoUpcomingTrigger,
            resolve(upcoming = 0, successful = 0),
        )
        assertEquals(
            MedicationReminderSchedulingState.ScheduleFailed,
            resolve(upcoming = 2, successful = 1),
        )
        assertEquals(
            MedicationReminderSchedulingState.ScheduleFailed,
            resolve(upcoming = 2, successful = 2, persisted = false),
        )
        assertEquals(
            MedicationReminderSchedulingState.Scheduled,
            resolve(upcoming = 2, successful = 2),
        )
    }

    @Test
    fun disabledSettingNeverNeedsNotificationOrExactAlarmPermission() {
        assertEquals(
            MedicationReminderSchedulingState.Disabled,
            MedicationReminderSchedulingPolicy.resolve(
                requestedEnabled = false,
                notificationPermission = MedicationNotificationPermissionState.Denied,
                exactAlarmAccess = MedicationExactAlarmAccessState.Required,
                upcomingTriggerCount = 0,
                successfulScheduleCount = 0,
                persistenceSucceeded = true,
            ),
        )
    }

    private fun resolve(
        notification: MedicationNotificationPermissionState =
            MedicationNotificationPermissionState.Allowed,
        exact: MedicationExactAlarmAccessState = MedicationExactAlarmAccessState.Allowed,
        upcoming: Int = 1,
        successful: Int = upcoming,
        persisted: Boolean = true,
    ): MedicationReminderSchedulingState = MedicationReminderSchedulingPolicy.resolve(
        requestedEnabled = true,
        notificationPermission = notification,
        exactAlarmAccess = exact,
        upcomingTriggerCount = upcoming,
        successfulScheduleCount = successful,
        persistenceSucceeded = persisted,
    )
}
