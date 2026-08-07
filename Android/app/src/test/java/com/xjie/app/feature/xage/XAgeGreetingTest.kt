package com.xjie.app.feature.xage

import org.junit.Assert.assertEquals
import org.junit.Test

class XAgeGreetingTest {
    @Test
    fun welcomeHeadline_usesDeviceLocalHourAcrossAllGreetingBoundaries() {
        val expected = mapOf(
            0 to "夜深了，想问什么？",
            4 to "夜深了，想问什么？",
            5 to "早上好，想问什么？",
            10 to "早上好，想问什么？",
            11 to "中午好，想问什么？",
            13 to "中午好，想问什么？",
            14 to "下午好，想问什么？",
            17 to "下午好，想问什么？",
            18 to "晚上好，想问什么？",
            22 to "晚上好，想问什么？",
            23 to "夜深了，想问什么？",
        )

        expected.forEach { (hour, headline) ->
            assertEquals("hour=$hour", headline, XAgeGreeting.headlineAt(hour))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun welcomeHeadline_rejectsInvalidHour() {
        XAgeGreeting.headlineAt(24)
    }
}
