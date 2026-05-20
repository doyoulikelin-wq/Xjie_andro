package com.xjie.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ElderlyCheckin(
    val id: Long,
    val prompt_type: String? = null,
    val activity: String? = null,
    val body_feeling: String? = null,
    val mood: String? = null,
    val note: String? = null,
    val source: String? = null,
    val created_at: String,
)

@Serializable
data class ElderlyCheckinList(
    val items: List<ElderlyCheckin> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class ElderlyTodayStatus(
    val enabled: Boolean = false,
    val interval_min: Int = 180,
    val today_count: Int = 0,
    val last_checkin_at: String? = null,
    val should_prompt: Boolean = false,
    val minutes_since_last: Int? = null,
)

@Serializable
data class ElderlyCheckinBody(
    val activity: String? = null,
    val body_feeling: String? = null,
    val mood: String? = null,
    val note: String? = null,
    val source: String? = "auto_prompt",
)

enum class BodyFeeling(val raw: String, val emoji: String, val label: String) {
    GREAT("great", "😄", "很好"),
    GOOD("good", "🙂", "不错"),
    OK("ok", "😐", "一般"),
    UNCOMFORTABLE("uncomfortable", "😣", "不舒服"),
    BAD("bad", "😫", "很差");
    companion object { fun fromRaw(r: String?) = entries.firstOrNull { it.raw == r } }
}

enum class MoodChoice(val raw: String, val emoji: String, val label: String) {
    HAPPY("happy", "😊", "开心"),
    CALM("calm", "😌", "平静"),
    ANXIOUS("anxious", "😟", "焦虑"),
    SAD("sad", "😢", "难过"),
    ANGRY("angry", "😠", "生气");
    companion object { fun fromRaw(r: String?) = entries.firstOrNull { it.raw == r } }
}

val COMMON_ACTIVITIES = listOf(
    "🚶 散步", "🛌 休息", "📺 看电视", "🍽 吃饭",
    "🧘 做家务", "💊 吃药", "🛒 买菜", "👨‍👩‍👧 陪家人",
)
