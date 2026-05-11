package com.xjie.app.core.util

import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 对应 iOS [Utils.swift] 的日期/格式化辅助 */
object DateUtils {
    private val zhDateTime: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply {
            timeZone = TimeZone.getDefault()
        }
    }
    private val timeOnly: SimpleDateFormat by lazy {
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    fun parseISO(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        return runCatching {
            // Allow trailing 'Z', fractional seconds, or +08:00
            val normalized = dateStr.replace("Z", "+00:00")
            val odt = OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            Date.from(odt.toInstant())
        }.getOrNull() ?: runCatching {
            // Server may emit naive timestamps — assume local
            val odt = OffsetDateTime.parse(dateStr + "+00:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            Date.from(odt.toInstant())
        }.getOrNull()
    }

    fun formatDateTime(dateStr: String?): String {
        val d = parseISO(dateStr) ?: return dateStr.orEmpty()
        return zhDateTime.format(d)
    }

    fun formatTime(dateStr: String?): String {
        val d = parseISO(dateStr) ?: return dateStr.orEmpty()
        return timeOnly.format(d)
    }
}

object NumberUtils {
    fun toFixed(num: Double?, digits: Int = 1): String =
        if (num == null || num.isNaN()) "--" else String.format(Locale.US, "%.${digits}f", num)
}
