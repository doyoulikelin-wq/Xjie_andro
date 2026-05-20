package com.xjie.app.feature.medication

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xjie.app.MainActivity
import com.xjie.app.R
import com.xjie.app.core.model.Medication
import com.xjie.app.core.push.NotificationChannels
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用药 / 关怀本地提醒调度器（基于 AlarmManager + BroadcastReceiver）。
 *
 * • 用药：按每个 Medication 的 schedule_times 安排每日整点闹钟，疗程结束自动取消。
 * • 关怀：按用户设置的间隔（min）在 08:00–22:00 内安排重复闹钟。
 */
@Singleton
class MedicationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun rescheduleAll(medications: List<Medication>) {
        NotificationChannels.ensure(context)
        cancelAllMedicationAlarms()
        val today = LocalDate.now()
        medications.filter { it.enabled }.forEach { med ->
            if (!isCourseActive(med, today)) return@forEach
            med.schedule_times.forEachIndexed { idx, t ->
                val parsed = runCatching { LocalTime.parse(t) }.getOrNull() ?: return@forEachIndexed
                scheduleDaily(med, idx, parsed)
            }
        }
    }

    fun cancelAllMedicationAlarms() {
        // 取消已知 id*100+idx 的闹钟（最多支持 100 个药 x 32 时间点）
        for (i in 0 until 100 * 32) {
            val pi = pendingIntentForMedication(i, create = false) ?: continue
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun isCourseActive(m: Medication, today: LocalDate): Boolean {
        val start = m.course_start?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = m.course_end?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (start != null && today.isBefore(start)) return false
        if (end != null && today.isAfter(end)) return false
        return true
    }

    @SuppressLint("MissingPermission")
    private fun scheduleDaily(med: Medication, idx: Int, time: LocalTime) {
        val requestCode = (med.id.toInt() and 0xFFFF) * 32 + idx
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
            putExtra("medication_id", med.id)
            putExtra("name", med.name)
            putExtra("dosage", med.dosage ?: "")
            putExtra("instructions", med.instructions ?: "")
            putExtra("request_code", requestCode)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            // setAlarmClock 被系统当作“用户可见闹钟”，在 vivo/Xiaomi/Huawei 等召回严格的 ROM 上依然能准时触发。
            val showPi = PendingIntent.getActivity(
                context, requestCode,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(cal.timeInMillis, showPi), pi,
            )
            Log.i(TAG, "medAlarm scheduled id=${med.id} idx=$idx at=${cal.time}")
        } catch (e: SecurityException) {
            Log.w(TAG, "setAlarmClock SecurityException, fallback setRepeating", e)
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                AlarmManager.INTERVAL_DAY, pi,
            )
        }
    }

    private fun pendingIntentForMedication(requestCode: Int, create: Boolean): PendingIntent? {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
        }
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    // ---- 关怀复查（按间隔在 08:00 – 22:00 之间，每日重复）----
    fun scheduleElderlyReminders(intervalMin: Int, enabled: Boolean) {
        cancelElderlyReminders()
        if (!enabled || intervalMin <= 0) return
        NotificationChannels.ensure(context)
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        // 在每天 08:00 – 22:00 内按间隔铺点；每个点注册 daily 重复闹钟，
        // 若该点已过则锚定到明天的同一时间，确保启动后总能在下一个时刻触发。
        var slot = today.atTime(8, 0)
        val end = today.atTime(22, 0)
        var idx = 0
        while (!slot.isAfter(end) && idx < 32) {
            val anchor = if (slot.isAfter(now)) slot else slot.plusDays(1)
            val triggerAt = anchor.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val req = ELDERLY_BASE + idx
            val intent = Intent(context, ElderlyReminderReceiver::class.java).apply {
                action = ACTION_ELDERLY_REMINDER
            }
            val pi = PendingIntent.getBroadcast(
                context, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            try {
                val showPi = PendingIntent.getActivity(
                    context, req,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showPi), pi,
                )
                Log.i(TAG, "elderlyAlarm idx=$idx at=$anchor")
            } catch (_: SecurityException) {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP, triggerAt, AlarmManager.INTERVAL_DAY, pi,
                )
            }
            idx++
            slot = slot.plusMinutes(intervalMin.toLong())
        }
    }

    /**
     * 立即弹一条测试通知（直接走 NotificationManager，不经过广播/闹钟），
     * 用于快速验证通知通道、权限、ROM 限制是否到位。
     */
    @SuppressLint("MissingPermission")
    fun fireTestNotification() {
        NotificationChannels.ensure(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "fireTestNotification: POST_NOTIFICATIONS not granted")
            return
        }
        val openPi = PendingIntent.getActivity(
            context, 999_001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, NotificationChannels.ELDERLY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("💗 测试通知")
            .setContentText("如果你看到这条，说明通知通道与权限正常。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .build()
        NotificationManagerCompat.from(context).notify(999_001, n)
        Log.i(TAG, "fireTestNotification posted")
    }

    /** 安排一个 10 秒后触发的关怀闹钟，用于验证 AlarmManager 能否在后台/锁屏唤起。 */
    fun scheduleTestAlarm(delaySeconds: Int = 10) {
        val triggerAt = System.currentTimeMillis() + delaySeconds * 1000L
        val intent = Intent(context, ElderlyReminderReceiver::class.java).apply {
            action = ACTION_ELDERLY_REMINDER
        }
        val pi = PendingIntent.getBroadcast(
            context, 999_002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showPi = PendingIntent.getActivity(
            context, 999_002,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, showPi), pi,
            )
            Log.i(TAG, "scheduleTestAlarm in ${delaySeconds}s")
        } catch (e: SecurityException) {
            Log.w(TAG, "scheduleTestAlarm fallback set()", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelElderlyReminders() {
        for (i in 0 until 64) {
            val intent = Intent(context, ElderlyReminderReceiver::class.java).apply {
                action = ACTION_ELDERLY_REMINDER
            }
            val pi = PendingIntent.getBroadcast(
                context, ELDERLY_BASE + i, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: continue
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    companion object {
        const val ACTION_MEDICATION_REMINDER = "com.xjie.app.MEDICATION_REMINDER"
        const val ACTION_ELDERLY_REMINDER = "com.xjie.app.ELDERLY_REMINDER"
        private const val ELDERLY_BASE = 900_000
        private const val TAG = "MedScheduler"
    }
}

/** 用药提醒接收器 —— 弹通知。 */
class MedicationReminderReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("MedReceiver", "med onReceive action=${intent.action}")
        NotificationChannels.ensure(context)
        val name = intent.getStringExtra("name") ?: "用药提醒"
        val dosage = intent.getStringExtra("dosage").orEmpty()
        val instructions = intent.getStringExtra("instructions").orEmpty()
        val title = "💊 该服药了：$name"
        val body = buildString {
            if (dosage.isNotBlank()) append("剂量：$dosage")
            if (instructions.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(instructions)
            }
            if (isEmpty()) append("请按医嘱及时服用。")
        }
        val n = NotificationCompat.Builder(context, NotificationChannels.MEDICATION)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context)
            .notify(intent.getIntExtra("request_code", 0), n)
    }
}

/** 关怀复查提醒接收器。 */
class ElderlyReminderReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("ElderlyReceiver", "elderly onReceive action=${intent.action}")
        NotificationChannels.ensure(context)
        val n = NotificationCompat.Builder(context, NotificationChannels.ELDERLY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("💗 关怀复查")
            .setContentText("现在感觉如何？记一笔今天的状态吧。")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(800_000, n)
    }
}
