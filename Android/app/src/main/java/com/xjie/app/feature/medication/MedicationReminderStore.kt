package com.xjie.app.feature.medication

import android.content.Context
import android.util.Log
import com.xjie.app.core.model.Medication
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class MedicationScheduleSnapshot(
    val id: Long,
    val name: String,
    val dosage: String? = null,
    val instructions: String? = null,
    val scheduleTimes: List<String> = emptyList(),
    val courseStart: String? = null,
    val courseEnd: String? = null,
    val enabled: Boolean = true,
) {
    companion object {
        fun from(medication: Medication) = MedicationScheduleSnapshot(
            id = medication.id,
            name = medication.name,
            dosage = medication.dosage,
            instructions = medication.instructions,
            scheduleTimes = medication.schedule_times,
            courseStart = medication.course_start,
            courseEnd = medication.course_end,
            enabled = medication.enabled,
        )
    }
}

internal data class ElderlyScheduleSnapshot(
    val enabled: Boolean,
    val intervalMinutes: Int,
)

/** App-private recovery snapshot used after reboot, app update, and system clock changes. */
internal class MedicationReminderStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadMedicationSchedules(): List<MedicationScheduleSnapshot> {
        val raw = preferences.getString(KEY_MEDICATIONS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<MedicationScheduleSnapshot>>(raw) }
            .onFailure { Log.e(TAG, "Unable to decode medication reminder snapshot", it) }
            .getOrDefault(emptyList())
    }

    fun saveMedicationSchedules(schedules: List<MedicationScheduleSnapshot>) {
        val raw = json.encodeToString(schedules)
        if (!preferences.edit().putString(KEY_MEDICATIONS, raw).commit()) {
            Log.e(TAG, "Unable to persist medication reminder snapshot")
        }
    }

    fun loadElderlySchedule(): ElderlyScheduleSnapshot = ElderlyScheduleSnapshot(
        enabled = preferences.getBoolean(KEY_ELDERLY_ENABLED, false),
        intervalMinutes = preferences.getInt(KEY_ELDERLY_INTERVAL, 0),
    )

    fun saveElderlySchedule(schedule: ElderlyScheduleSnapshot) {
        if (!preferences.edit()
                .putBoolean(KEY_ELDERLY_ENABLED, schedule.enabled)
                .putInt(KEY_ELDERLY_INTERVAL, schedule.intervalMinutes)
                .commit()
        ) {
            Log.e(TAG, "Unable to persist elderly reminder snapshot")
        }
    }

    fun loadTrustedReminderSettings(): List<TrustedMedicationReminderSettings> =
        decodeList(KEY_TRUSTED_REMINDERS, "trusted medication reminders")

    fun saveTrustedReminderSettings(settings: List<TrustedMedicationReminderSettings>): Boolean =
        saveList(KEY_TRUSTED_REMINDERS, settings, "trusted medication reminders")

    fun loadTrustedSnoozes(): List<TrustedMedicationSnoozeSnapshot> =
        decodeList(KEY_TRUSTED_SNOOZES, "trusted medication snoozes")

    fun saveTrustedSnoozes(snoozes: List<TrustedMedicationSnoozeSnapshot>): Boolean =
        saveList(KEY_TRUSTED_SNOOZES, snoozes, "trusted medication snoozes")

    fun wasNotificationPermissionRequested(): Boolean =
        preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)

    fun markNotificationPermissionRequested(): Boolean = preferences.edit()
        .putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true)
        .commit()

    fun isLegacyAlarmMigrationComplete(): Boolean =
        preferences.getBoolean(KEY_LEGACY_ALARM_MIGRATION_COMPLETE, false)

    /** Returns false so callers can retry the sweep if durable marker persistence fails. */
    fun markLegacyAlarmMigrationComplete(): Boolean = preferences.edit()
        .putBoolean(KEY_LEGACY_ALARM_MIGRATION_COMPLETE, true)
        .commit()

    private inline fun <reified T> decodeList(key: String, label: String): List<T> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<T>>(raw) }
            .onFailure { Log.e(TAG, "Unable to decode $label snapshot", it) }
            .getOrDefault(emptyList())
    }

    private inline fun <reified T> saveList(key: String, values: List<T>, label: String): Boolean {
        val raw = json.encodeToString(values)
        return preferences.edit().putString(key, raw).commit().also { saved ->
            if (!saved) Log.e(TAG, "Unable to persist $label snapshot")
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "xjie_reminder_schedule_v1"
        const val KEY_MEDICATIONS = "medications"
        const val KEY_ELDERLY_ENABLED = "elderly_enabled"
        const val KEY_ELDERLY_INTERVAL = "elderly_interval_minutes"
        const val KEY_TRUSTED_REMINDERS = "trusted_medication_reminders"
        const val KEY_TRUSTED_SNOOZES = "trusted_medication_snoozes"
        const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        const val KEY_LEGACY_ALARM_MIGRATION_COMPLETE = "legacy_alarm_migration_complete"
        const val TAG = "ReminderStore"
    }
}
