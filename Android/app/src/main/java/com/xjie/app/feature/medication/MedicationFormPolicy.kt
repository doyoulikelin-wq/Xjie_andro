package com.xjie.app.feature.medication

import java.time.LocalDate

internal data class MedicationFormValidation(
    val normalizedTimes: List<String> = emptyList(),
    val error: String? = null,
) {
    val isValid: Boolean get() = error == null
}

internal object MedicationFormPolicy {
    private val timePattern = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    fun validate(times: List<String>, courseStart: String, courseEnd: String): MedicationFormValidation {
        val normalizedTimes = times.map(String::trim).filter(String::isNotEmpty)
        val invalid = normalizedTimes.filterNot(timePattern::matches)
        if (invalid.isNotEmpty()) {
            return MedicationFormValidation(
                error = "提醒时间格式不正确：${invalid.joinToString("、")}。请使用 24 小时 HH:MM。",
            )
        }

        val start = parseDate(courseStart)
        if (courseStart.trim().isNotEmpty() && start == null) {
            return MedicationFormValidation(error = "开始日期格式不正确，请使用 YYYY-MM-DD。")
        }
        val end = parseDate(courseEnd)
        if (courseEnd.trim().isNotEmpty() && end == null) {
            return MedicationFormValidation(error = "结束日期格式不正确，请使用 YYYY-MM-DD。")
        }
        if (start != null && end != null && end.isBefore(start)) {
            return MedicationFormValidation(error = "结束日期不能早于开始日期。")
        }
        return MedicationFormValidation(normalizedTimes = normalizedTimes.distinct())
    }

    private fun parseDate(value: String): LocalDate? = value.trim().takeIf(String::isNotEmpty)?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }
}
