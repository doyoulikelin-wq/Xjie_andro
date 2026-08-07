package com.xjie.app.feature.xage

import java.time.LocalTime

/** Device-local greeting used by every XAGE welcome entry point. */
internal object XAgeGreeting {
    fun currentHeadline(): String = headlineAt(LocalTime.now().hour)

    fun headlineAt(hourOfDay: Int): String {
        require(hourOfDay in 0..23) { "hourOfDay must be between 0 and 23" }
        val salutation = when (hourOfDay) {
            in 0..4, 23 -> "夜深了"
            in 5..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..17 -> "下午好"
            else -> "晚上好"
        }
        return "$salutation，想问什么？"
    }
}
