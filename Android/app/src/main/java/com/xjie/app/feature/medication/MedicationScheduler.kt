package com.xjie.app.feature.medication

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xjie.app.MainActivity
import com.xjie.app.R
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.Medication
import com.xjie.app.core.model.MedicationTodayTask
import com.xjie.app.core.model.TrustedMedicationPlan
import com.xjie.app.core.push.NotificationChannels
import com.xjie.app.core.storage.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

internal enum class TrustedSnoozeScheduleResult {
    Scheduled,
    NotificationPermissionRequired,
    ScheduleFailed,
}

internal enum class TrustedReminderSaveResult {
    Disabled,
    Scheduled,
    NotificationPermissionRequired,
    ExactAlarmPermissionRequired,
    NoUpcomingTrigger,
    ScheduleFailed,
}

internal data class TrustedReminderSaveEvidence(
    val result: TrustedReminderSaveResult,
    val scheduledCount: Int,
    val persistedSettings: TrustedMedicationReminderSettings,
)

internal data class TrustedReminderReconcileEvidence(
    val settings: Map<Long, TrustedMedicationReminderSettings>,
    val scheduledCountByPlan: Map<Long, Int>,
    val notificationPermission: MedicationNotificationPermissionState,
    val exactAlarmAccess: MedicationExactAlarmAccessState,
)

/**
 * Local reminder scheduler.
 *
 * Daily reminders are deliberately implemented as a chain of one-shot alarms: every delivery
 * recalculates tomorrow's occurrence in the device's current time zone. A small app-private
 * snapshot lets boot, app-update, time, and time-zone broadcasts rebuild the same alarms.
 */
@Singleton
class MedicationScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val store = MedicationReminderStore(appContext)

    fun rescheduleAll(medications: List<Medication>) {
        ensureLegacyAlarmMigration()
        NotificationChannels.ensure(appContext)
        val previous = store.loadMedicationSchedules()
        val current = medications.map(MedicationScheduleSnapshot::from)

        cancelMedicationAlarms(previous)
        // Remove operations created by the pre-chain implementation for every schedule we know.
        cancelLegacyMedicationAlarms(previous + current)
        store.saveMedicationSchedules(current)
        scheduleMedicationSnapshots(current)
    }

    /** Rebuilds every persisted daily alarm after reboot, app update, or a clock-zone change. */
    fun rescheduleStored() {
        ensureLegacyAlarmMigration()
        NotificationChannels.ensure(appContext)
        val medications = store.loadMedicationSchedules()
        cancelMedicationAlarms(medications)
        scheduleMedicationSnapshots(medications)

        val trustedSettings = store.loadTrustedReminderSettings()
        cancelTrustedPlanAlarms(trustedSettings)
        val owner = currentOwner()
        val timezoneId = ZoneId.systemDefault().id
        val currentSettings = if (owner == null) {
            emptyList()
        } else {
            trustedSettings.mapNotNull { settings ->
                when {
                    settings.accountScope != owner.accountScope ||
                        settings.selectedSubjectId != owner.selectedSubjectId ||
                        settings.authGeneration != owner.authGeneration -> null
                    settings.timezoneId != timezoneId -> settings.copy(
                        enabled = false,
                        timezoneId = timezoneId,
                    )
                    else -> settings
                }
            }
        }
        store.saveTrustedReminderSettings(currentSettings)
        if (notificationPermissionState() == MedicationNotificationPermissionState.Allowed &&
            exactAlarmAccessState() == MedicationExactAlarmAccessState.Allowed
        ) {
            currentSettings.filter { it.enabled }.forEach(::scheduleTrustedPlanSettings)
        }
        rescheduleTrustedSnoozes(owner, timezoneId)

        val elderly = store.loadElderlySchedule()
        cancelElderlyReminders()
        scheduleElderlySnapshot(elderly)
    }

    fun cancelAllMedicationAlarms() {
        ensureLegacyAlarmMigration()
        val medications = store.loadMedicationSchedules()
        cancelMedicationAlarms(medications)
        cancelLegacyMedicationAlarms(medications)
        store.saveMedicationSchedules(emptyList())
    }

    /** Makes an obsolete account/subject generation inert immediately when the screen observes it. */
    internal fun invalidateTrustedOwner(owner: MedicationReminderOwner) {
        val settings = store.loadTrustedReminderSettings()
        val obsolete = settings.filter {
            it.accountScope == owner.accountScope &&
                it.selectedSubjectId == owner.selectedSubjectId &&
                it.authGeneration == owner.authGeneration
        }
        cancelTrustedPlanAlarms(obsolete)
        store.saveTrustedReminderSettings(settings - obsolete.toSet())

        val snoozes = store.loadTrustedSnoozes()
        val obsoleteSnoozes = snoozes.filter {
            it.accountScope == owner.accountScope &&
                it.selectedSubjectId == owner.selectedSubjectId &&
                it.authGeneration == owner.authGeneration
        }
        obsoleteSnoozes.forEach { snapshot ->
            trustedSnoozePendingIntent(snapshot, create = false)?.let { operation ->
                alarmManager.cancel(operation)
                operation.cancel()
            }
        }
        store.saveTrustedSnoozes(snoozes - obsoleteSnoozes.toSet())
    }

    internal fun reconcileTrustedPlans(
        plans: List<TrustedMedicationPlan>,
        owner: MedicationReminderOwner,
    ): TrustedReminderReconcileEvidence {
        val timezoneId = ZoneId.systemDefault().id
        val previous = store.loadTrustedReminderSettings()
        cancelTrustedPlanAlarms(previous)
        val previousByPlan = previous
            .filter { TrustedMedicationReminderPolicy.isOwnedBy(it, owner, timezoneId) }
            .associateBy { it.planId }

        val visible = linkedMapOf<Long, TrustedMedicationReminderSettings>()
        val persisted = mutableListOf<TrustedMedicationReminderSettings>()
        plans.forEach { plan ->
            val old = previousByPlan[plan.plan_id]
            val settings = if (old != null &&
                TrustedMedicationReminderPolicy.isCurrentForPlan(old, plan)
            ) {
                old
            } else {
                TrustedMedicationReminderPolicy.defaults(
                    plan = plan,
                    owner = owner,
                    timezoneId = timezoneId,
                ).copy(
                    advanceMinutes = old?.advanceMinutes ?: 0,
                    snoozeMinutes = old?.snoozeMinutes ?: 15,
                    soundEnabled = old?.soundEnabled ?: true,
                    showMedicationNameOnLockScreen =
                        old?.showMedicationNameOnLockScreen ?: false,
                )
            }
            visible[plan.plan_id] = settings
            persisted += settings
        }
        store.saveTrustedReminderSettings(persisted.sortedBy { it.planId })

        val notificationPermission = notificationPermissionState()
        val exactAlarmAccess = exactAlarmAccessState()
        val scheduledCountByPlan = visible.mapValues { (_, settings) ->
            if (settings.enabled &&
                notificationPermission == MedicationNotificationPermissionState.Allowed &&
                exactAlarmAccess == MedicationExactAlarmAccessState.Allowed
            ) {
                scheduleTrustedPlanSettings(settings)
            } else {
                0
            }
        }
        reconcileTrustedSnoozes(owner, timezoneId, plans)
        return TrustedReminderReconcileEvidence(
            settings = visible,
            scheduledCountByPlan = scheduledCountByPlan,
            notificationPermission = notificationPermission,
            exactAlarmAccess = exactAlarmAccess,
        )
    }

    internal fun saveTrustedReminder(
        settings: TrustedMedicationReminderSettings,
        owner: MedicationReminderOwner,
    ): TrustedReminderSaveEvidence {
        val validation = TrustedMedicationReminderPolicy.validate(settings)
        val timezoneId = ZoneId.systemDefault().id
        if (!validation.isValid ||
            !TrustedMedicationReminderPolicy.isOwnedBy(settings, owner, timezoneId)
        ) {
            return TrustedReminderSaveEvidence(
                result = TrustedReminderSaveResult.ScheduleFailed,
                scheduledCount = 0,
                persistedSettings = settings.copy(enabled = false),
            )
        }

        val all = store.loadTrustedReminderSettings()
            .filter { TrustedMedicationReminderPolicy.isOwnedBy(it, owner, timezoneId) }
            .toMutableList()
        all.filter { it.planId == settings.planId }.forEach(::cancelTrustedPlanAlarms)
        all.removeAll { it.planId == settings.planId }
        val disabled = settings.copy(enabled = false)
        if (!settings.enabled) {
            all += disabled
            val persisted = store.saveTrustedReminderSettings(all.sortedBy { it.planId })
            return TrustedReminderSaveEvidence(
                result = if (persisted) {
                    TrustedReminderSaveResult.Disabled
                } else {
                    TrustedReminderSaveResult.ScheduleFailed
                },
                scheduledCount = 0,
                persistedSettings = disabled,
            )
        }

        val permission = notificationPermissionState()
        val exactAlarmAccess = exactAlarmAccessState()
        val upcomingCount = validation.normalizedTimes.count { time ->
            TrustedMedicationReminderPolicy.nextTrigger(
                settings = settings,
                now = ZonedDateTime.now(ZoneId.systemDefault()),
                doseTime = time,
            ) != null
        }
        val preflight = MedicationReminderSchedulingPolicy.resolve(
            requestedEnabled = true,
            notificationPermission = permission,
            exactAlarmAccess = exactAlarmAccess,
            upcomingTriggerCount = upcomingCount,
            successfulScheduleCount = 0,
            persistenceSucceeded = true,
        )
        if (preflight != MedicationReminderSchedulingState.ScheduleFailed &&
            preflight != MedicationReminderSchedulingState.Scheduled
        ) {
            all += disabled
            val persisted = store.saveTrustedReminderSettings(all.sortedBy { it.planId })
            val result = when (preflight) {
                MedicationReminderSchedulingState.NotificationPermissionRequired ->
                    TrustedReminderSaveResult.NotificationPermissionRequired
                MedicationReminderSchedulingState.ExactAlarmPermissionRequired ->
                    TrustedReminderSaveResult.ExactAlarmPermissionRequired
                MedicationReminderSchedulingState.NoUpcomingTrigger ->
                    TrustedReminderSaveResult.NoUpcomingTrigger
                else -> TrustedReminderSaveResult.ScheduleFailed
            }
            return TrustedReminderSaveEvidence(
                result = if (persisted) result else TrustedReminderSaveResult.ScheduleFailed,
                scheduledCount = 0,
                persistedSettings = disabled,
            )
        }
        val outcomes = validation.normalizedTimes.map { time ->
            scheduleTrustedPlan(settings, time)
        }
        val scheduledCount = outcomes.count { it == true }
        val schedulingState = MedicationReminderSchedulingPolicy.resolve(
            requestedEnabled = true,
            notificationPermission = permission,
            exactAlarmAccess = exactAlarmAccess,
            upcomingTriggerCount = upcomingCount,
            successfulScheduleCount = scheduledCount,
            persistenceSucceeded = true,
        )
        if (schedulingState != MedicationReminderSchedulingState.Scheduled) {
            cancelTrustedPlanAlarms(settings)
            all += disabled
            store.saveTrustedReminderSettings(all.sortedBy { it.planId })
            return TrustedReminderSaveEvidence(
                result = TrustedReminderSaveResult.ScheduleFailed,
                scheduledCount = 0,
                persistedSettings = disabled,
            )
        }
        all += settings
        if (!store.saveTrustedReminderSettings(all.sortedBy { it.planId })) {
            cancelTrustedPlanAlarms(settings)
            return TrustedReminderSaveEvidence(
                result = TrustedReminderSaveResult.ScheduleFailed,
                scheduledCount = 0,
                persistedSettings = disabled,
            )
        }
        return TrustedReminderSaveEvidence(
            result = TrustedReminderSaveResult.Scheduled,
            scheduledCount = scheduledCount,
            persistedSettings = settings,
        )
    }

    internal fun snoozeMinutesForPlan(planId: Long): Int =
        store.loadTrustedReminderSettings()
            .firstOrNull { it.planId == planId }
            ?.snoozeMinutes
            ?.takeIf { it in 5..120 }
            ?: 15

    /** Schedules only the one occurrence the user explicitly snoozed; it never enables a plan. */
    internal fun scheduleTrustedSnooze(
        task: MedicationTodayTask,
        triggerAtMillis: Long,
        owner: MedicationReminderOwner,
    ): TrustedSnoozeScheduleResult {
        if (triggerAtMillis <= System.currentTimeMillis()) {
            return TrustedSnoozeScheduleResult.ScheduleFailed
        }
        if (notificationPermissionState() != MedicationNotificationPermissionState.Allowed) {
            return TrustedSnoozeScheduleResult.NotificationPermissionRequired
        }
        if (exactAlarmAccessState() != MedicationExactAlarmAccessState.Allowed) {
            return TrustedSnoozeScheduleResult.ScheduleFailed
        }
        NotificationChannels.ensure(appContext)
        val timezoneId = ZoneId.systemDefault().id
        val planSettings = store.loadTrustedReminderSettings().firstOrNull {
            it.planId == task.plan_id &&
                it.planVersion == task.plan_version &&
                it.subjectUserId > 0 &&
                TrustedMedicationReminderPolicy.isOwnedBy(it, owner, timezoneId)
        }
        val snapshot = TrustedMedicationSnoozeSnapshot(
            occurrenceKey = task.occurrence_key,
            planId = task.plan_id,
            genericName = task.generic_name,
            doseText = task.dose_text,
            scheduledTime = task.scheduled_time,
            triggerAtMillis = triggerAtMillis,
            soundEnabled = planSettings?.soundEnabled ?: true,
            showMedicationNameOnLockScreen =
                planSettings?.showMedicationNameOnLockScreen ?: false,
            subjectUserId = planSettings?.subjectUserId ?: return TrustedSnoozeScheduleResult.ScheduleFailed,
            planVersion = task.plan_version,
            accountScope = owner.accountScope,
            selectedSubjectId = owner.selectedSubjectId,
            authGeneration = owner.authGeneration,
            timezoneId = timezoneId,
        )
        cancelTrustedSnooze(task.occurrence_key)
        val snoozes = store.loadTrustedSnoozes().toMutableList().apply { add(snapshot) }
        if (!store.saveTrustedSnoozes(snoozes.sortedBy { it.occurrenceKey })) {
            return TrustedSnoozeScheduleResult.ScheduleFailed
        }
        val operation = trustedSnoozePendingIntent(snapshot, create = true) ?: run {
            store.saveTrustedSnoozes(
                store.loadTrustedSnoozes().filterNot { it.occurrenceKey == task.occurrence_key },
            )
            return TrustedSnoozeScheduleResult.ScheduleFailed
        }
        val requestCode = MedicationReminderPolicy.trustedSnoozeRequestCode(snapshot)
        val showIntent = PendingIntent.getActivity(
            appContext,
            requestCode,
            Intent(appContext, MedicationNotificationActivity::class.java).apply {
                data = Uri.Builder()
                    .scheme("xjie")
                    .authority("medication-reminder")
                    .appendPath("show-trusted-snooze")
                    .appendPath(task.occurrence_key)
                    .build()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!scheduleVisibleOneShot(
                triggerAtMillis = triggerAtMillis,
                operation = operation,
                showIntent = showIntent,
                logLabel = "trusted snooze occurrence=${task.occurrence_key}",
                requireExact = true,
            )
        ) {
            store.saveTrustedSnoozes(
                store.loadTrustedSnoozes().filterNot { it.occurrenceKey == task.occurrence_key },
            )
            return TrustedSnoozeScheduleResult.ScheduleFailed
        }
        return if (hasNotificationPermission()) {
            TrustedSnoozeScheduleResult.Scheduled
        } else {
            TrustedSnoozeScheduleResult.NotificationPermissionRequired
        }
    }

    internal fun cancelTrustedSnooze(occurrenceKey: String) {
        val snapshots = store.loadTrustedSnoozes()
        snapshots.filter { it.occurrenceKey == occurrenceKey }.forEach { snapshot ->
            trustedSnoozePendingIntent(snapshot, create = false)?.let { operation ->
                alarmManager.cancel(operation)
                operation.cancel()
            }
        }
        val remaining = snapshots.filterNot { it.occurrenceKey == occurrenceKey }
        store.saveTrustedSnoozes(remaining)
    }

    internal fun consumeTrustedSnooze(
        occurrenceKey: String,
        expectedPlanVersion: Int,
        expectedAccountScope: String,
        expectedSelectedSubjectId: String,
        expectedAuthGeneration: Long,
        expectedTimezoneId: String,
    ): TrustedMedicationSnoozeSnapshot? {
        val snoozes = store.loadTrustedSnoozes()
        val owner = currentOwner() ?: return null
        val timezoneId = ZoneId.systemDefault().id
        val snapshot = snoozes.firstOrNull {
            it.occurrenceKey == occurrenceKey &&
                it.planVersion == expectedPlanVersion &&
                it.accountScope == expectedAccountScope &&
                it.selectedSubjectId == expectedSelectedSubjectId &&
                it.authGeneration == expectedAuthGeneration &&
                it.timezoneId == expectedTimezoneId &&
                TrustedMedicationReminderPolicy.isOwnedBy(it, owner, timezoneId)
        } ?: return null
        store.saveTrustedSnoozes(snoozes.filterNot { it.occurrenceKey == occurrenceKey })
        return snapshot
    }

    /** Called by [MedicationReminderReceiver] before notification permission is evaluated. */
    fun onMedicationAlarmFired(medicationId: Long, scheduleTime: String?) {
        val time = scheduleTime
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: return
        val medication = store.loadMedicationSchedules()
            .firstOrNull { it.id == medicationId && it.enabled }
            ?: return
        if (time !in MedicationReminderPolicy.normalizedTimes(medication.scheduleTimes)) return
        scheduleMedication(medication, time)
    }

    internal fun onTrustedPlanAlarmFired(
        planId: Long,
        scheduleTime: String?,
        expectedPlanVersion: Int,
        expectedAccountScope: String,
        expectedSelectedSubjectId: String,
        expectedAuthGeneration: Long,
        expectedTimezoneId: String,
    ): TrustedMedicationReminderSettings? {
        val time = scheduleTime
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: return null
        val settings = store.loadTrustedReminderSettings().firstOrNull {
            it.planId == planId &&
                it.planVersion == expectedPlanVersion &&
                it.accountScope == expectedAccountScope &&
                it.selectedSubjectId == expectedSelectedSubjectId &&
                it.authGeneration == expectedAuthGeneration &&
                it.timezoneId == expectedTimezoneId &&
                it.enabled &&
                currentOwner()?.let { owner ->
                    TrustedMedicationReminderPolicy.isOwnedBy(
                        it,
                        owner,
                        ZoneId.systemDefault().id,
                    )
                } == true
        } ?: return null
        val validation = TrustedMedicationReminderPolicy.validate(settings)
        if (!validation.isValid || time !in validation.normalizedTimes) return null
        scheduleTrustedPlan(settings, time)
        return settings
    }

    private fun scheduleTrustedPlanSettings(settings: TrustedMedicationReminderSettings): Int {
        val validation = TrustedMedicationReminderPolicy.validate(settings)
        if (!validation.isValid || !settings.enabled) return 0
        return validation.normalizedTimes.count { time -> scheduleTrustedPlan(settings, time) == true }
    }

    /** null means the confirmed course has no future occurrence; false is a scheduler failure. */
    private fun scheduleTrustedPlan(
        settings: TrustedMedicationReminderSettings,
        time: LocalTime,
    ): Boolean? {
        val trigger = TrustedMedicationReminderPolicy.nextTrigger(
            settings = settings,
            now = ZonedDateTime.now(ZoneId.systemDefault()),
            doseTime = time,
        ) ?: run {
            cancelTrustedPlanAlarm(settings, time)
            return null
        }
        val operation = trustedPlanPendingIntent(settings, time, create = true) ?: return false
        return scheduleVisibleOneShot(
            triggerAtMillis = trigger.toInstant().toEpochMilli(),
            operation = operation,
            showIntent = trustedPlanShowPendingIntent(settings, time),
            logLabel = "trusted medication plan=${settings.planId} " +
                "time=${MedicationReminderPolicy.canonicalTime(time)}",
            requireExact = true,
        )
    }

    private fun cancelTrustedPlanAlarms(settings: List<TrustedMedicationReminderSettings>) {
        settings.forEach(::cancelTrustedPlanAlarms)
    }

    private fun cancelTrustedPlanAlarms(settings: TrustedMedicationReminderSettings) {
        MedicationReminderPolicy.normalizedTimes(settings.scheduleTimes).forEach { time ->
            cancelTrustedPlanAlarm(settings, time)
        }
    }

    private fun cancelTrustedPlanAlarm(
        settings: TrustedMedicationReminderSettings,
        time: LocalTime,
    ) {
        trustedPlanPendingIntent(settings, time, create = false)?.let { operation ->
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }

    private fun trustedPlanPendingIntent(
        settings: TrustedMedicationReminderSettings,
        time: LocalTime,
        create: Boolean,
    ): PendingIntent? {
        val canonicalTime = MedicationReminderPolicy.canonicalTime(time)
        val requestCode = MedicationReminderPolicy.trustedPlanRequestCode(settings, time)
        val identity = MedicationReminderPolicy.trustedPlanAlarmIdentity(settings, time)
        val intent = Intent(appContext, MedicationReminderReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
            data = Uri.Builder()
                .scheme("xjie")
                .authority("medication-reminder")
                .appendPath("trusted-plan")
                .appendPath(identity)
                .build()
            putExtra(EXTRA_REMINDER_KIND, REMINDER_KIND_TRUSTED_PLAN)
            putExtra(EXTRA_TRUSTED_PLAN_ID, settings.planId)
            putExtra(EXTRA_TRUSTED_PLAN_VERSION, settings.planVersion)
            putExtra(EXTRA_ACCOUNT_SCOPE, settings.accountScope)
            putExtra(EXTRA_AUTH_GENERATION, settings.authGeneration)
            putExtra(EXTRA_SELECTED_SUBJECT_ID, settings.selectedSubjectId)
            putExtra(EXTRA_TIMEZONE_ID, settings.timezoneId)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_SCHEDULE_TIME, canonicalTime)
        }
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(appContext, requestCode, intent, flags)
    }

    private fun trustedPlanShowPendingIntent(
        settings: TrustedMedicationReminderSettings,
        time: LocalTime,
    ): PendingIntent {
        val requestCode = MedicationReminderPolicy.trustedPlanRequestCode(settings, time)
        return PendingIntent.getActivity(
            appContext,
            requestCode,
            Intent(appContext, MedicationNotificationActivity::class.java).apply {
                data = Uri.Builder()
                    .scheme("xjie")
                    .authority("medication-reminder")
                    .appendPath("show-trusted-plan")
                    .appendPath(MedicationReminderPolicy.trustedPlanAlarmIdentity(settings, time))
                    .build()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun trustedSnoozePendingIntent(
        snapshot: TrustedMedicationSnoozeSnapshot,
        create: Boolean,
    ): PendingIntent? {
        val requestCode = MedicationReminderPolicy.trustedSnoozeRequestCode(snapshot)
        val intent = Intent(appContext, MedicationReminderReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
            data = Uri.Builder()
                .scheme("xjie")
                .authority("medication-reminder")
                .appendPath("trusted-snooze")
                .appendPath(snapshot.occurrenceKey)
                .build()
            putExtra(EXTRA_REMINDER_KIND, REMINDER_KIND_TRUSTED_SNOOZE)
            putExtra(EXTRA_OCCURRENCE_KEY, snapshot.occurrenceKey)
            putExtra(EXTRA_TRUSTED_PLAN_ID, snapshot.planId)
            putExtra(EXTRA_TRUSTED_PLAN_VERSION, snapshot.planVersion)
            putExtra(EXTRA_ACCOUNT_SCOPE, snapshot.accountScope)
            putExtra(EXTRA_AUTH_GENERATION, snapshot.authGeneration)
            putExtra(EXTRA_SELECTED_SUBJECT_ID, snapshot.selectedSubjectId)
            putExtra(EXTRA_TIMEZONE_ID, snapshot.timezoneId)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
        }
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(appContext, requestCode, intent, flags)
    }

    private fun rescheduleTrustedSnoozes(
        owner: MedicationReminderOwner?,
        timezoneId: String,
    ) {
        val now = System.currentTimeMillis()
        val active = mutableListOf<TrustedMedicationSnoozeSnapshot>()
        store.loadTrustedSnoozes().forEach { snapshot ->
            trustedSnoozePendingIntent(snapshot, create = false)?.let { operation ->
                alarmManager.cancel(operation)
                operation.cancel()
            }
            if (owner == null ||
                !TrustedMedicationReminderPolicy.isOwnedBy(snapshot, owner, timezoneId) ||
                snapshot.triggerAtMillis <= now ||
                notificationPermissionState() != MedicationNotificationPermissionState.Allowed ||
                exactAlarmAccessState() != MedicationExactAlarmAccessState.Allowed
            ) return@forEach
            val operation = trustedSnoozePendingIntent(snapshot, create = true)
                ?: return@forEach
            val requestCode = MedicationReminderPolicy.trustedSnoozeRequestCode(snapshot)
            val showIntent = PendingIntent.getActivity(
                appContext,
                requestCode,
                Intent(appContext, MedicationNotificationActivity::class.java).apply {
                    data = Uri.Builder()
                        .scheme("xjie")
                        .authority("medication-reminder")
                        .appendPath("show-trusted-snooze")
                        .appendPath(snapshot.occurrenceKey)
                        .build()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (scheduleVisibleOneShot(
                    triggerAtMillis = snapshot.triggerAtMillis,
                    operation = operation,
                    showIntent = showIntent,
                    logLabel = "restored trusted snooze occurrence=${snapshot.occurrenceKey}",
                    requireExact = true,
                )
            ) {
                active += snapshot
            }
        }
        store.saveTrustedSnoozes(active.sortedBy { it.occurrenceKey })
    }

    private fun reconcileTrustedSnoozes(
        owner: MedicationReminderOwner,
        timezoneId: String,
        plans: List<TrustedMedicationPlan>,
    ) {
        val validVersions = plans
            .filter { it.status == "active" }
            .mapTo(mutableSetOf()) { Triple(it.subject_user_id, it.plan_id, it.version) }
        val retained = mutableListOf<TrustedMedicationSnoozeSnapshot>()
        store.loadTrustedSnoozes().forEach { snapshot ->
            val valid = TrustedMedicationReminderPolicy.isOwnedBy(snapshot, owner, timezoneId) &&
                Triple(snapshot.subjectUserId, snapshot.planId, snapshot.planVersion) in validVersions &&
                snapshot.triggerAtMillis > System.currentTimeMillis()
            if (valid) {
                retained += snapshot
            } else {
                trustedSnoozePendingIntent(snapshot, create = false)?.let { operation ->
                    alarmManager.cancel(operation)
                    operation.cancel()
                }
            }
        }
        store.saveTrustedSnoozes(retained.sortedBy { it.occurrenceKey })
    }

    internal fun notificationPermissionState(): MedicationNotificationPermissionState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return MedicationNotificationPermissionState.Allowed
        }
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return MedicationNotificationPermissionState.Allowed
        }
        return if (store.wasNotificationPermissionRequested()) {
            MedicationNotificationPermissionState.Denied
        } else {
            MedicationNotificationPermissionState.NotDetermined
        }
    }

    internal fun markNotificationPermissionRequested() {
        store.markNotificationPermissionRequested()
    }

    internal fun exactAlarmAccessState(): MedicationExactAlarmAccessState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return MedicationExactAlarmAccessState.Allowed
        }
        return runCatching { alarmManager.canScheduleExactAlarms() }
            .fold(
                onSuccess = { allowed ->
                    if (allowed) {
                        MedicationExactAlarmAccessState.Allowed
                    } else {
                        MedicationExactAlarmAccessState.Required
                    }
                },
                onFailure = { MedicationExactAlarmAccessState.Unavailable },
            )
    }

    private fun hasNotificationPermission(): Boolean =
        notificationPermissionState() == MedicationNotificationPermissionState.Allowed

    private fun currentOwner(): MedicationReminderOwner? = runCatching {
        val tokenStore = TokenStore(appContext)
        val accountScope = AuthManager.accountScopeFromJwt(tokenStore.accessToken)
            ?: return@runCatching null
        MedicationReminderOwner(
            accountScope = accountScope,
            selectedSubjectId = tokenStore.subjectId,
            authGeneration = tokenStore.authGeneration,
        )
    }.getOrNull()

    private fun scheduleMedicationSnapshots(medications: List<MedicationScheduleSnapshot>) {
        medications.asSequence()
            .filter { it.enabled }
            .forEach { medication ->
                MedicationReminderPolicy.normalizedTimes(medication.scheduleTimes).forEach { time ->
                    scheduleMedication(medication, time)
                }
            }
    }

    private fun scheduleMedication(medication: MedicationScheduleSnapshot, time: LocalTime) {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val trigger = MedicationReminderPolicy.nextDailyTrigger(
            now = now,
            time = time,
            courseStart = MedicationReminderPolicy.parseDate(medication.courseStart),
            courseEnd = MedicationReminderPolicy.parseDate(medication.courseEnd),
        ) ?: run {
            cancelMedicationAlarm(medication, time)
            return
        }
        val operation = medicationPendingIntent(medication, time, create = true) ?: return
        val showIntent = medicationShowPendingIntent(medication.id, time)
        scheduleVisibleOneShot(
            triggerAtMillis = trigger.toInstant().toEpochMilli(),
            operation = operation,
            showIntent = showIntent,
            logLabel = "medication id=${medication.id} time=${MedicationReminderPolicy.canonicalTime(time)}",
        )
    }

    private fun cancelMedicationAlarms(medications: List<MedicationScheduleSnapshot>) {
        medications.forEach { medication ->
            MedicationReminderPolicy.normalizedTimes(medication.scheduleTimes).forEach { time ->
                cancelMedicationAlarm(medication, time)
            }
        }
    }

    private fun cancelMedicationAlarm(medication: MedicationScheduleSnapshot, time: LocalTime) {
        medicationPendingIntent(medication, time, create = false)?.let { operation ->
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }

    private fun cancelLegacyMedicationAlarms(medications: List<MedicationScheduleSnapshot>) {
        medications.forEach { medication ->
            medication.scheduleTimes.forEachIndexed { index, _ ->
                val requestCode = (medication.id.toInt() and 0xFFFF) * LEGACY_MAX_SLOTS + index
                val intent = Intent(appContext, MedicationReminderReceiver::class.java).apply {
                    action = ACTION_MEDICATION_REMINDER
                }
                PendingIntent.getBroadcast(
                    appContext,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )?.let { operation ->
                    alarmManager.cancel(operation)
                    operation.cancel()
                }
            }
        }
    }

    /**
     * One-time upgrade sweep for alarms created before reminder identities included Intent data.
     * The marker is committed only after every former request code has been checked successfully;
     * an exception or failed commit leaves migration pending so the next entry point retries it.
     */
    private fun ensureLegacyAlarmMigration() {
        if (!MedicationReminderPolicy.shouldRunLegacyMigration(
                completed = store.isLegacyAlarmMigrationComplete(),
            )
        ) {
            return
        }

        val swept = runCatching {
            MedicationReminderPolicy.legacyMigrationRequestCodes().forEach { requestCode ->
                val intent = Intent(appContext, MedicationReminderReceiver::class.java).apply {
                    action = ACTION_MEDICATION_REMINDER
                }
                PendingIntent.getBroadcast(
                    appContext,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )?.let { operation ->
                    alarmManager.cancel(operation)
                    operation.cancel()
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "Legacy medication alarm migration sweep failed; will retry", error)
        }.isSuccess

        if (!swept) return
        if (store.markLegacyAlarmMigrationComplete()) {
            Log.i(TAG, "Legacy medication alarm migration sweep completed")
        } else {
            Log.e(TAG, "Legacy medication alarm migration marker failed; will retry")
        }
    }

    private fun medicationPendingIntent(
        medication: MedicationScheduleSnapshot,
        time: LocalTime,
        create: Boolean,
    ): PendingIntent? {
        val canonicalTime = MedicationReminderPolicy.canonicalTime(time)
        val requestCode = MedicationReminderPolicy.medicationRequestCode(medication.id, time)
        val intent = Intent(appContext, MedicationReminderReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
            data = medicationIntentData(medication.id, time, "deliver")
            putExtra(EXTRA_MEDICATION_ID, medication.id)
            putExtra(EXTRA_NAME, medication.name)
            putExtra(EXTRA_DOSAGE, medication.dosage.orEmpty())
            putExtra(EXTRA_INSTRUCTIONS, medication.instructions.orEmpty())
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_SCHEDULE_TIME, canonicalTime)
        }
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(appContext, requestCode, intent, flags)
    }

    private fun medicationShowPendingIntent(medicationId: Long, time: LocalTime): PendingIntent {
        val requestCode = MedicationReminderPolicy.medicationRequestCode(medicationId, time)
        val intent = Intent(appContext, MainActivity::class.java).apply {
            data = medicationIntentData(medicationId, time, "show")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun medicationIntentData(medicationId: Long, time: LocalTime, purpose: String): Uri =
        Uri.Builder()
            .scheme("xjie")
            .authority("medication-reminder")
            .appendPath(purpose)
            .appendPath(MedicationReminderPolicy.medicationAlarmIdentity(medicationId, time))
            .build()

    // ---- Elderly check-in: daily one-shot chain between 08:00 and 22:00 ----

    fun scheduleElderlyReminders(intervalMin: Int, enabled: Boolean) {
        cancelElderlyReminders()
        val snapshot = ElderlyScheduleSnapshot(
            enabled = enabled && intervalMin > 0,
            intervalMinutes = intervalMin.coerceAtLeast(0),
        )
        store.saveElderlySchedule(snapshot)
        scheduleElderlySnapshot(snapshot)
    }

    private fun scheduleElderlySnapshot(snapshot: ElderlyScheduleSnapshot) {
        if (!snapshot.enabled || snapshot.intervalMinutes <= 0) return
        NotificationChannels.ensure(appContext)
        MedicationReminderPolicy.elderlySlots(snapshot.intervalMinutes, MAX_ELDERLY_SLOTS)
            .forEachIndexed { index, time -> scheduleElderly(index, time) }
    }

    /** Called by [ElderlyReminderReceiver] to continue only a still-current persisted slot. */
    fun onElderlyAlarmFired(index: Int, scheduleTime: String?) {
        if (index !in 0 until MAX_ELDERLY_SLOTS) return
        val firedTime = scheduleTime
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: return
        val snapshot = store.loadElderlySchedule()
        if (!snapshot.enabled || snapshot.intervalMinutes <= 0) return
        val currentTime = MedicationReminderPolicy
            .elderlySlots(snapshot.intervalMinutes, MAX_ELDERLY_SLOTS)
            .getOrNull(index)
        if (currentTime != firedTime) return
        scheduleElderly(index, firedTime)
    }

    private fun scheduleElderly(index: Int, time: LocalTime) {
        val trigger = MedicationReminderPolicy.nextDailyTrigger(
            now = ZonedDateTime.now(ZoneId.systemDefault()),
            time = time,
        ) ?: return
        val operation = elderlyPendingIntent(index, time, create = true) ?: return
        val showIntent = PendingIntent.getActivity(
            appContext,
            ELDERLY_BASE + index,
            Intent(appContext, MainActivity::class.java).apply {
                data = Uri.parse("xjie://elderly-reminder/show/$index")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleVisibleOneShot(
            triggerAtMillis = trigger.toInstant().toEpochMilli(),
            operation = operation,
            showIntent = showIntent,
            logLabel = "elderly index=$index time=${MedicationReminderPolicy.canonicalTime(time)}",
        )
    }

    private fun elderlyPendingIntent(
        index: Int,
        time: LocalTime,
        create: Boolean,
    ): PendingIntent? {
        val intent = Intent(appContext, ElderlyReminderReceiver::class.java).apply {
            action = ACTION_ELDERLY_REMINDER
            putExtra(EXTRA_ELDERLY_INDEX, index)
            putExtra(EXTRA_SCHEDULE_TIME, MedicationReminderPolicy.canonicalTime(time))
        }
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(appContext, ELDERLY_BASE + index, intent, flags)
    }

    fun cancelElderlyReminders() {
        for (index in 0 until MAX_ELDERLY_SLOTS) {
            val intent = Intent(appContext, ElderlyReminderReceiver::class.java).apply {
                action = ACTION_ELDERLY_REMINDER
            }
            PendingIntent.getBroadcast(
                appContext,
                ELDERLY_BASE + index,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let { operation ->
                alarmManager.cancel(operation)
                operation.cancel()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun scheduleVisibleOneShot(
        triggerAtMillis: Long,
        operation: PendingIntent,
        showIntent: PendingIntent,
        logLabel: String,
        requireExact: Boolean = false,
    ): Boolean {
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        if (exactAllowed) {
            try {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    operation,
                )
                Log.i(TAG, "$logLabel scheduled exact at=$triggerAtMillis")
                return true
            } catch (error: SecurityException) {
                Log.w(TAG, "$logLabel exact alarm denied; using inexact idle-safe alarm", error)
            }
        } else {
            Log.w(TAG, "$logLabel exact alarm access unavailable; using inexact idle-safe alarm")
        }

        if (requireExact) {
            Log.w(TAG, "$logLabel rejected because exact alarm access is required")
            return false
        }

        return runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operation,
            )
        }.onSuccess {
            Log.i(TAG, "$logLabel scheduled inexact at=$triggerAtMillis")
        }.onFailure { error ->
            Log.e(TAG, "$logLabel could not be scheduled", error)
        }.isSuccess
    }

    /** Immediate notification diagnostic; it intentionally does not alter persisted schedules. */
    @SuppressLint("MissingPermission")
    fun fireTestNotification() {
        NotificationChannels.ensure(appContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "fireTestNotification: POST_NOTIFICATIONS not granted")
            return
        }
        val openIntent = PendingIntent.getActivity(
            appContext,
            TEST_NOTIFICATION_REQUEST,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, NotificationChannels.ELDERLY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("测试通知")
            .setContentText("如果你看到这条，说明通知通道与权限正常。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        NotificationManagerCompat.from(appContext)
            .notify(TEST_NOTIFICATION_REQUEST, notification)
        Log.i(TAG, "fireTestNotification posted")
    }

    /** One-shot diagnostic alarm; unlike daily reminders, it is not rescheduled after delivery. */
    fun scheduleTestAlarm(delaySeconds: Int = 10) {
        scheduleCustomAlarm(System.currentTimeMillis() + delaySeconds * 1000L)
    }

    /** One-shot user-selected alarm; past times remain rejected by the UI entry point. */
    fun scheduleCustomAlarm(triggerAtMillis: Long) {
        val operation = PendingIntent.getBroadcast(
            appContext,
            CUSTOM_ALARM_REQUEST,
            Intent(appContext, ElderlyReminderReceiver::class.java).apply {
                action = ACTION_ELDERLY_REMINDER
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showIntent = PendingIntent.getActivity(
            appContext,
            CUSTOM_ALARM_REQUEST,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleVisibleOneShot(
            triggerAtMillis = triggerAtMillis,
            operation = operation,
            showIntent = showIntent,
            logLabel = "custom alarm",
        )
    }

    companion object {
        const val ACTION_MEDICATION_REMINDER = "com.xjie.app.MEDICATION_REMINDER"
        const val ACTION_ELDERLY_REMINDER = "com.xjie.app.ELDERLY_REMINDER"

        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_NAME = "name"
        const val EXTRA_DOSAGE = "dosage"
        const val EXTRA_INSTRUCTIONS = "instructions"
        const val EXTRA_REQUEST_CODE = "request_code"
        const val EXTRA_SCHEDULE_TIME = "schedule_time"
        const val EXTRA_ELDERLY_INDEX = "elderly_index"
        const val EXTRA_REMINDER_KIND = "reminder_kind"
        const val EXTRA_TRUSTED_PLAN_ID = "trusted_plan_id"
        const val EXTRA_TRUSTED_PLAN_VERSION = "trusted_plan_version"
        const val EXTRA_OCCURRENCE_KEY = "occurrence_key"
        const val EXTRA_ACCOUNT_SCOPE = "account_scope"
        const val EXTRA_AUTH_GENERATION = "auth_generation"
        const val EXTRA_SELECTED_SUBJECT_ID = "selected_subject_id"
        const val EXTRA_TIMEZONE_ID = "timezone_id"
        const val REMINDER_KIND_TRUSTED_PLAN = "trusted_plan"
        const val REMINDER_KIND_TRUSTED_SNOOZE = "trusted_snooze"

        private const val LEGACY_MAX_SLOTS = 32
        private const val MAX_ELDERLY_SLOTS = 32
        private const val ELDERLY_BASE = 900_000
        private const val TEST_NOTIFICATION_REQUEST = 999_001
        private const val CUSTOM_ALARM_REQUEST = 999_002
        private const val TAG = "MedScheduler"
    }
}

/** Delivers a medication notification, then continues its persisted daily chain. */
class MedicationReminderReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MedicationScheduler.ACTION_MEDICATION_REMINDER) return
        Log.i("MedReceiver", "medication reminder received")
        val scheduler = MedicationScheduler(context.applicationContext)
        val payload = when (intent.getStringExtra(MedicationScheduler.EXTRA_REMINDER_KIND)) {
            MedicationScheduler.REMINDER_KIND_TRUSTED_PLAN -> {
                val settings = scheduler.onTrustedPlanAlarmFired(
                    planId = intent.getLongExtra(MedicationScheduler.EXTRA_TRUSTED_PLAN_ID, -1L),
                    scheduleTime = intent.getStringExtra(MedicationScheduler.EXTRA_SCHEDULE_TIME),
                    expectedPlanVersion = intent.getIntExtra(
                        MedicationScheduler.EXTRA_TRUSTED_PLAN_VERSION,
                        -1,
                    ),
                    expectedAccountScope = intent.getStringExtra(
                        MedicationScheduler.EXTRA_ACCOUNT_SCOPE,
                    ).orEmpty(),
                    expectedSelectedSubjectId = intent.getStringExtra(
                        MedicationScheduler.EXTRA_SELECTED_SUBJECT_ID,
                    ).orEmpty(),
                    expectedAuthGeneration = intent.getLongExtra(
                        MedicationScheduler.EXTRA_AUTH_GENERATION,
                        -1L,
                    ),
                    expectedTimezoneId = intent.getStringExtra(
                        MedicationScheduler.EXTRA_TIMEZONE_ID,
                    ).orEmpty(),
                ) ?: return
                MedicationNotificationPayload(
                    name = settings.genericName,
                    dosage = settings.doseText.orEmpty(),
                    instructions = listOfNotNull(
                        settings.instructions,
                        mealRelationReminder(settings.mealRelation),
                        settings.advanceMinutes.takeIf { it > 0 }
                            ?.let { "这是提前 $it 分钟的提醒。" },
                    ).joinToString("\n"),
                    soundEnabled = settings.soundEnabled,
                    showMedicationName = settings.showMedicationNameOnLockScreen,
                )
            }
            MedicationScheduler.REMINDER_KIND_TRUSTED_SNOOZE -> {
                val occurrenceKey = intent.getStringExtra(MedicationScheduler.EXTRA_OCCURRENCE_KEY)
                    ?: return
                val snapshot = scheduler.consumeTrustedSnooze(
                    occurrenceKey = occurrenceKey,
                    expectedPlanVersion = intent.getIntExtra(
                        MedicationScheduler.EXTRA_TRUSTED_PLAN_VERSION,
                        -1,
                    ),
                    expectedAccountScope = intent.getStringExtra(
                        MedicationScheduler.EXTRA_ACCOUNT_SCOPE,
                    ).orEmpty(),
                    expectedSelectedSubjectId = intent.getStringExtra(
                        MedicationScheduler.EXTRA_SELECTED_SUBJECT_ID,
                    ).orEmpty(),
                    expectedAuthGeneration = intent.getLongExtra(
                        MedicationScheduler.EXTRA_AUTH_GENERATION,
                        -1L,
                    ),
                    expectedTimezoneId = intent.getStringExtra(
                        MedicationScheduler.EXTRA_TIMEZONE_ID,
                    ).orEmpty(),
                ) ?: return
                MedicationNotificationPayload(
                    name = snapshot.genericName,
                    dosage = snapshot.doseText.orEmpty(),
                    instructions = "这是你稍后提醒的一次服药任务，请确认后再记录状态。",
                    soundEnabled = snapshot.soundEnabled,
                    showMedicationName = snapshot.showMedicationNameOnLockScreen,
                )
            }
            else -> {
                scheduler.onMedicationAlarmFired(
                    medicationId = intent.getLongExtra(MedicationScheduler.EXTRA_MEDICATION_ID, -1L),
                    scheduleTime = intent.getStringExtra(MedicationScheduler.EXTRA_SCHEDULE_TIME),
                )
                MedicationNotificationPayload(
                    name = intent.getStringExtra(MedicationScheduler.EXTRA_NAME) ?: "用药提醒",
                    dosage = intent.getStringExtra(MedicationScheduler.EXTRA_DOSAGE).orEmpty(),
                    instructions = intent.getStringExtra(
                        MedicationScheduler.EXTRA_INSTRUCTIONS,
                    ).orEmpty(),
                    soundEnabled = true,
                    showMedicationName = false,
                )
            }
        }
        NotificationChannels.ensure(context)
        val presentation = MedicationNotificationPresentationPolicy.resolve(
            genericName = payload.name,
            doseText = payload.dosage,
            instructions = payload.instructions,
            showMedicationNameOnLockScreen = payload.showMedicationName,
        )
        val channel = if (payload.soundEnabled) {
            NotificationChannels.MEDICATION
        } else {
            NotificationChannels.MEDICATION_SILENT
        }
        val requestCode = intent.getIntExtra(MedicationScheduler.EXTRA_REQUEST_CODE, 0)
        val openIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MedicationNotificationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicNotification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(presentation.publicTitle)
            .setContentText(presentation.publicBody)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(!payload.soundEnabled)
            .build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(presentation.title)
            .setContentText(presentation.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(presentation.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(
                if (presentation.exposePrivateContentOnLockScreen) {
                    NotificationCompat.VISIBILITY_PUBLIC
                } else {
                    NotificationCompat.VISIBILITY_PRIVATE
                },
            )
            .setPublicVersion(publicNotification)
            .setSilent(!payload.soundEnabled)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(
            requestCode,
            notification,
        )
    }
}

private data class MedicationNotificationPayload(
    val name: String,
    val dosage: String,
    val instructions: String,
    val soundEnabled: Boolean,
    val showMedicationName: Boolean,
)

private fun mealRelationReminder(value: String): String? = when (value) {
    "before_meal" -> "按已确认计划在饭前服用。"
    "after_meal" -> "按已确认计划在饭后服用。"
    "with_meal" -> "按已确认计划随餐服用。"
    else -> null
}

/** Delivers an elderly check-in notification, then continues its persisted daily slot chain. */
class ElderlyReminderReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MedicationScheduler.ACTION_ELDERLY_REMINDER) return
        Log.i("ElderlyReceiver", "elderly reminder received")

        MedicationScheduler(context.applicationContext).onElderlyAlarmFired(
            index = intent.getIntExtra(MedicationScheduler.EXTRA_ELDERLY_INDEX, -1),
            scheduleTime = intent.getStringExtra(MedicationScheduler.EXTRA_SCHEDULE_TIME),
        )

        NotificationChannels.ensure(context)
        val notification = NotificationCompat.Builder(context, NotificationChannels.ELDERLY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("关怀复查")
            .setContentText("现在感觉如何？记一笔今天的状态吧。")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(800_000, notification)
    }
}

/** Restores local daily chains after system events that invalidate AlarmManager state. */
class MedicationScheduleChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTORE_ACTIONS) return
        Log.i("ReminderRestore", "rescheduling after ${intent.action}")
        MedicationScheduler(context.applicationContext).rescheduleStored()
    }

    private companion object {
        val RESTORE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
