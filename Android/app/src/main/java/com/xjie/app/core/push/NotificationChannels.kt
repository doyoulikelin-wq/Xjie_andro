package com.xjie.app.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val MEDICATION = "medication"
    const val ELDERLY = "elderly_care"
    const val GLUCOSE_ALERT = "glucose_alert"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(MEDICATION, "用药提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "按疗程与服药时间提醒"
            },
            NotificationChannel(ELDERLY, "关怀复查", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "关怀模式按间隔定时提醒"
            },
            NotificationChannel(GLUCOSE_ALERT, "血糖异常", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "血糖异常时实时提醒"
            },
        )
        channels.forEach { mgr.createNotificationChannel(it) }
    }
}
