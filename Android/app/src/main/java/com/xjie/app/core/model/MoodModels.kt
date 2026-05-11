package com.xjie.app.core.model

import kotlinx.serialization.Serializable

enum class MoodSegment(val key: String, val label: String, val window: String) {
    MORNING("morning", "早晨", "06–10"),
    NOON("noon", "中午", "10–14"),
    AFTERNOON("afternoon", "下午", "14–17"),
    EVENING("evening", "傍晚", "17–21"),
    NIGHT("night", "夜间", "21–02");

    companion object {
        fun fromKey(k: String?): MoodSegment? = entries.firstOrNull { it.key == k }
    }
}

enum class MoodLevel(val value: Int, val emoji: String, val label: String) {
    ANGRY(1, "😡", "愤怒"),
    SAD(2, "😢", "低落"),
    ANXIOUS(3, "😟", "焦虑"),
    NEUTRAL(4, "😐", "平静"),
    HAPPY(5, "😀", "愉快");

    companion object {
        fun fromValue(v: Int?): MoodLevel? = entries.firstOrNull { it.value == v }
    }
}

@Serializable
data class MoodLogIn(
    val ts: String,
    val segment: String,
    val mood_level: Int,
    val note: String? = null,
)

@Serializable
data class MoodLogOut(
    val id: Int,
    val ts: String,
    val ts_date: String,
    val segment: String,
    val mood_level: Int,
    val note: String? = null,
)

@Serializable
data class MoodDay(
    val date: String,
    val morning: Int? = null,
    val noon: Int? = null,
    val afternoon: Int? = null,
    val evening: Int? = null,
    val night: Int? = null,
    val avg: Double? = null,
) {
    fun level(s: MoodSegment): Int? = when (s) {
        MoodSegment.MORNING -> morning
        MoodSegment.NOON -> noon
        MoodSegment.AFTERNOON -> afternoon
        MoodSegment.EVENING -> evening
        MoodSegment.NIGHT -> night
    }
}

@Serializable
data class MoodGlucoseCorrelation(
    val days: Int,
    val paired_samples: Int,
    val pearson_r: Double? = null,
    val p_value: Double? = null,
    val interpretation: String,
)
