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
    val prompt_type: String? = "combined",
)

/** 关怀签到的快捷类型：决定弹窗的标题、选项及是否显示心情/身体感觉。 */
enum class ElderlyCheckinKind(
    val apiValue: String,
    val emoji: String,
    val displayName: String,
    val title: String,
    val subtitle: String,
    val activitySection: String,
    val showBodyFeeling: Boolean,
    val showMood: Boolean,
    val options: List<String>,
) {
    COMBINED(
        "combined", "综合", "综合签到",
        "现在感觉如何？", "请简单告诉我们您的状态，方便家人和医生及时关心。",
        "您正在做什么？", true, true,
        COMMON_ACTIVITIES,
    ),
    MEDICATION(
        "medication", "药", "用药",
        "用药签到", "今日的药物是否已按时服用？如有不适请记录下来。",
        "今日服药情况", true, false,
        listOf("已按时服药", "忘记服药", "推迟服药", "出现副作用", "暂未到服药时间"),
    ),
    SLEEP(
        "sleep", "眠", "睡眠",
        "睡眠复查", "昨晚睡得怎么样？是否容易入睡、是否中途醒来？",
        "昨夜睡眠情况", false, true,
        listOf("睡得很好", "入睡困难", "夜间多次醒", "睡眠较短", "睡眠充足"),
    ),
    WATER(
        "water", "水", "饮水",
        "饮水复查", "今天大概喝了多少水？身体是否口渴？",
        "今日饮水情况", false, false,
        listOf("饮水充足", "饮水偏少", "口渴明显", "几乎没喝水", "正常补水"),
    ),
    ACTIVITY(
        "activity", "动", "活动",
        "活动复查", "今天有没有出门活动？散步或简单运动了多久？",
        "今日活动情况", false, false,
        listOf("今日散步", "做家务", "外出办事", "在家休息", "锻炼/拉伸"),
    );

    companion object {
        fun fromApi(v: String?): ElderlyCheckinKind =
            entries.firstOrNull { it.apiValue == v } ?: COMBINED
    }
}

enum class BodyFeeling(val raw: String, val emoji: String, val label: String) {
    GREAT("great", "棒", "很好"),
    GOOD("good", "好", "不错"),
    OK("ok", "平", "一般"),
    UNCOMFORTABLE("uncomfortable", "不", "不舒服"),
    BAD("bad", "差", "很差");
    companion object { fun fromRaw(r: String?) = entries.firstOrNull { it.raw == r } }
}

enum class MoodChoice(val raw: String, val emoji: String, val label: String) {
    HAPPY("happy", "乐", "开心"),
    CALM("calm", "静", "平静"),
    ANXIOUS("anxious", "虑", "焦虑"),
    SAD("sad", "低", "难过"),
    ANGRY("angry", "怒", "生气");
    companion object { fun fromRaw(r: String?) = entries.firstOrNull { it.raw == r } }
}

val COMMON_ACTIVITIES = listOf(
    "散步", "休息", "看电视", "吃饭",
    "做家务", "吃药", "买菜", "陪家人",
)
