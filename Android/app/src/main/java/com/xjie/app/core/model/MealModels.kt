package com.xjie.app.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Legacy read-only projection. Trusted dietary writes must never use /api/meals.
@Serializable
data class MealItem(
    val id: String? = null,
    val meal_ts: String? = null,
    val meal_ts_source: String? = null,
    val kcal: Double? = null,
    val tags: List<String>? = null,
    val notes: String? = null,
)

@Serializable
data class MealVisionItem(
    val name: String? = null,
    val portion_text: String? = null,
    val kcal: Double? = null,
)

@Serializable
data class MealVisionJson(
    val items: List<MealVisionItem> = emptyList(),
    val total_kcal: Double? = null,
    val confidence: Double? = null,
    val notes: String? = null,
    val is_food: Boolean? = null,
)

@Serializable
data class MealPhoto(
    val id: String? = null,
    val status: String? = null,
    val calorie_estimate_kcal: Double? = null,
    val confidence: Double? = null,
    val uploaded_at: String? = null,
    val vision_json: MealVisionJson? = null,
)

@Serializable data class MealUploadTicket(val upload_url: String? = null, val object_key: String? = null)
@Serializable data class MealCreateBody(
    val meal_ts: String,
    val meal_ts_source: String,
    val kcal: Int,
    val tags: List<String>,
    val notes: String,
)
@Serializable data class MealUpdateBody(
    val kcal: Int? = null,
    val notes: String? = null,
    val tags: List<String>? = null,
)
@Serializable data class PhotoUploadBody(val filename: String, val content_type: String)
@Serializable data class PhotoCompleteBody(val object_key: String)

enum class DietaryEntrySource(val wireValue: String, val title: String) {
    Camera("camera", "拍照记录"),
    PhotoLibrary("photo_library", "从相册选择"),
    Text("text", "文字描述"),
    Voice("voice", "粘贴语音转写"),
    Recent("recent", "最近餐食"),
    Chat("chat", "粘贴问答草稿"),
    Manual("manual", "手动描述"),
    Unknown("unknown", "其他来源");

    companion object {
        fun fromWire(value: String): DietaryEntrySource =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

enum class DietaryMealType(val wireValue: String, val title: String) {
    Breakfast("breakfast", "早餐"),
    Lunch("lunch", "午餐"),
    Dinner("dinner", "晚餐"),
    Snack("snack", "加餐"),
    Unknown("unknown", "未选择餐次");

    companion object {
        fun fromWire(value: String?): DietaryMealType =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

@Serializable
data class DietaryFoodItem(
    val item_id: String? = null,
    val name: String,
    val portion_text: String? = null,
    val categories: List<String> = emptyList(),
    val confidence: Double? = null,
    val is_estimated: Boolean = true,
) {
    val isLowConfidence: Boolean get() = (confidence ?: 1.0) < 0.7

    companion object {
        const val MAX_NAME_LENGTH = 160
        const val MAX_DESCRIPTION_LENGTH = 4_000
    }
}

@Serializable
data class DietaryMealDraft(
    val draft_id: Long,
    val subject_user_id: Long,
    val source_type: String,
    val source_ref: String? = null,
    val diet_date: String,
    val timezone: String = DietaryBusinessDay.TIME_ZONE,
    val meal_type: String? = null,
    val eaten_at: String,
    val food_items: List<DietaryFoodItem> = emptyList(),
    val portion_text: String? = null,
    val structure: Map<String, JsonElement> = emptyMap(),
    val estimated_nutrition: Map<String, JsonElement> = emptyMap(),
    val field_confidences: Map<String, Double> = emptyMap(),
    val recognition_confidence: Double? = null,
    val recognition_status: String = "not_required",
    val recognition_cache_reused: Boolean = false,
    val low_confidence_fields: List<String> = emptyList(),
    val status: String,
    val version: Int,
    val requires_user_confirmation: Boolean,
    val formal_record_created: Boolean = false,
    val created_at: String? = null,
    val updated_at: String? = null,
) {
    val source: DietaryEntrySource get() = DietaryEntrySource.fromWire(source_type)
    val mealType: DietaryMealType get() = DietaryMealType.fromWire(meal_type)
    val recognitionFailed: Boolean
        get() = recognition_status == "failed_manual_entry_available" || recognition_status == "failed"
    val isEditable: Boolean
        get() = status == "pending_confirmation" && requires_user_confirmation && !formal_record_created
    val canRetryRecognition: Boolean
        get() = isEditable && recognitionFailed && source in setOf(
            DietaryEntrySource.Camera,
            DietaryEntrySource.PhotoLibrary,
        )
}

@Serializable
data class DietaryMealRecord(
    val record_id: Long,
    val source_draft_id: Long? = null,
    val subject_user_id: Long,
    val diet_date: String,
    val timezone: String = DietaryBusinessDay.TIME_ZONE,
    val meal_type: String,
    val eaten_at: String,
    val source_type: String,
    val source_ref: String? = null,
    val food_items: List<DietaryFoodItem> = emptyList(),
    val portion_text: String? = null,
    val structure: Map<String, JsonElement> = emptyMap(),
    val estimated_nutrition: Map<String, JsonElement> = emptyMap(),
    val field_confidences: Map<String, Double> = emptyMap(),
    val confidence: Double? = null,
    val status: String,
    val version: Int,
    val trust_state: String? = null,
    val confirmed_at: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
) {
    val mealType: DietaryMealType get() = DietaryMealType.fromWire(meal_type)
    val source: DietaryEntrySource get() = DietaryEntrySource.fromWire(source_type)
    val foodSummary: String get() = food_items.map { it.name }.filter { it.isNotBlank() }.joinToString("、")
}

@Serializable
data class DietaryDailySummary(
    val summary_id: Long,
    val subject_user_id: Long,
    val diet_date: String,
    val close_method: String,
    val record_complete: Boolean,
    val confirmed_meal_count: Int,
    val pending_count: Int,
    val structure_summary: Map<String, JsonElement> = emptyMap(),
    val conclusion: String,
    val today_suggestion: String,
    val confidence: Double,
    val evidence: Map<String, JsonElement> = emptyMap(),
    val rule_version: String,
    val template_version: String,
    val record_version: Int,
    val recalculated_after_edit: Boolean = false,
    val generated_at: String? = null,
)

@Serializable
data class DietaryWeeklyReview(
    val window_start: String,
    val window_end: String,
    val recorded_day_count: Int,
    val complete_day_count: Int,
    val protein_low_days: Int,
    val vegetables_adequate_days: Int,
    val uses_score: Boolean = false,
)

@Serializable
data class DietaryDashboardResponse(
    val subject_user_id: Long,
    val selected_date: String,
    val is_today: Boolean,
    val recorded_meal_count: Int,
    val pending_count: Int,
    val streak_days: Int,
    val day_state: String,
    val records: List<DietaryMealRecord> = emptyList(),
    val pending_drafts: List<DietaryMealDraft> = emptyList(),
    val selected_day_summary: DietaryDailySummary? = null,
    val displayed_summary: DietaryDailySummary? = null,
    val displayed_summary_date: String,
    val weekly_review: DietaryWeeklyReview? = null,
)

@Serializable
data class DietaryRecentResponse(
    val subject_user_id: Long,
    val items: List<DietaryMealRecord> = emptyList(),
)

@Serializable
data class DietaryDayCompletionResponse(
    val subject_user_id: Long,
    val diet_date: String,
    val state: String,
    val record_version: Int,
    val close_method: String? = null,
    val record_complete: Boolean,
    val confirmed_meal_count: Int,
    val pending_count: Int,
    val summary: DietaryDailySummary? = null,
)

@Serializable
data class DietaryDailySummaryDisplay(
    val conclusion: String,
    val today_suggestion: String,
    val confirmed_meal_count: Int,
    val confidence: Double,
    val generation_source: String,
    val retry_pending: Boolean,
    val generated_at: String,
)

@Serializable
data class DietaryDailySummaryStatus(
    val status: String,
    val target_date: String,
    val message: String? = null,
    val summary: DietaryDailySummaryDisplay? = null,
)

@Serializable
data class DietaryDraftCreateBody(
    val subject_user_id: Long? = null,
    val client_event_id: String,
    val source_type: String,
    val diet_date: String,
    val timezone: String,
    val meal_type: String,
    val eaten_at: String,
    val food_items: List<DietaryFoodItem> = emptyList(),
    val portion_text: String? = null,
    val structure: Map<String, JsonElement> = emptyMap(),
    val estimated_nutrition: Map<String, JsonElement> = emptyMap(),
    val field_confidences: Map<String, Double> = emptyMap(),
    val recognition_confidence: Double? = null,
    val source_ref: String? = null,
    val raw_input: String? = null,
)

@Serializable
data class DietaryDraftConfirmBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
    val timezone: String,
    val diet_date: String,
    val meal_type: String,
    val eaten_at: String,
    val food_items: List<DietaryFoodItem>,
    val portion_text: String? = null,
    val structure: Map<String, JsonElement> = emptyMap(),
    val estimated_nutrition: Map<String, JsonElement> = emptyMap(),
    val field_confidences: Map<String, Double> = emptyMap(),
    val recognition_confidence: Double? = null,
)

@Serializable
data class DietaryDraftRetryBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
)

@Serializable
data class DietaryRecordUpdateBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
    val timezone: String,
    val diet_date: String,
    val meal_type: String,
    val eaten_at: String,
    val food_items: List<DietaryFoodItem>,
    val portion_text: String? = null,
    val structure: Map<String, JsonElement> = emptyMap(),
    val estimated_nutrition: Map<String, JsonElement> = emptyMap(),
    val field_confidences: Map<String, Double> = emptyMap(),
    val recognition_confidence: Double? = null,
)

@Serializable
data class DietaryMutationBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
)

@Serializable
data class DietaryRecordReuseBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
    val timezone: String,
    val diet_date: String,
    val meal_type: String,
    val eaten_at: String,
)

@Serializable
data class DietaryDayCompleteBody(
    val timezone: String,
    val subject_user_id: Long,
    val client_event_id: String,
    val complete_with_confirmed_only: Boolean,
)

data class DietaryDraftEditor(
    val original: DietaryMealDraft,
    val mealType: DietaryMealType,
    val eatenAt: String,
    val foodItems: List<DietaryFoodItem>,
    val portionText: String,
) {
    val isValid: Boolean
        get() = mealType != DietaryMealType.Unknown && foodItems.any { it.name.trim().isNotEmpty() }

    companion object {
        fun from(draft: DietaryMealDraft): DietaryDraftEditor = DietaryDraftEditor(
            original = draft,
            mealType = draft.mealType.takeUnless { it == DietaryMealType.Unknown }
                ?: DietaryMealType.Snack,
            eatenAt = draft.eaten_at,
            foodItems = draft.food_items.ifEmpty {
                listOf(DietaryFoodItem(name = "", is_estimated = false))
            },
            portionText = draft.portion_text.orEmpty(),
        )
    }
}

data class DietaryRecordEditor(
    val original: DietaryMealRecord,
    val mealType: DietaryMealType,
    val eatenAt: String,
    val foodItems: List<DietaryFoodItem>,
    val portionText: String,
) {
    val isValid: Boolean
        get() = mealType != DietaryMealType.Unknown && foodItems.any { it.name.trim().isNotEmpty() }

    companion object {
        fun from(record: DietaryMealRecord): DietaryRecordEditor = DietaryRecordEditor(
            original = record,
            mealType = record.mealType,
            eatenAt = record.eaten_at,
            foodItems = record.food_items.ifEmpty {
                listOf(DietaryFoodItem(name = "", is_estimated = false))
            },
            portionText = record.portion_text.orEmpty(),
        )
    }
}

object DietaryBusinessDay {
    const val TIME_ZONE = "Asia/Shanghai"
    private val zone: ZoneId = ZoneId.of(TIME_ZONE)

    fun date(instant: Instant): LocalDate =
        instant.atZone(zone).minusHours(4).toLocalDate()

    fun dateKey(instant: Instant): String = date(instant).toString()

    fun clampSelection(requested: LocalDate, now: Instant): LocalDate =
        minOf(requested, date(now))

    fun offsetDateTime(instant: Instant): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(zone))

    fun inferredMealType(instant: Instant): DietaryMealType =
        when (instant.atZone(zone).hour) {
            in 4..9 -> DietaryMealType.Breakfast
            in 10..14 -> DietaryMealType.Lunch
            in 17..21 -> DietaryMealType.Dinner
            else -> DietaryMealType.Snack
        }

    fun timestampOnDate(selected: LocalDate, now: Instant): String {
        val current = now.atZone(zone)
        return ZonedDateTime.of(selected, current.toLocalTime(), zone)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}

object DietaryAdmissionPolicy {
    fun acceptsPendingDraft(
        draft: DietaryMealDraft,
        expectedSubject: Long? = null,
    ): Boolean =
        draft.draft_id > 0L &&
            draft.subject_user_id > 0L &&
            (expectedSubject == null || draft.subject_user_id == expectedSubject) &&
            draft.version >= 1 &&
            draft.status == "pending_confirmation" &&
            draft.requires_user_confirmation &&
            !draft.formal_record_created

    fun acceptsFormalRecord(
        record: DietaryMealRecord,
        expectedSubject: Long,
        minimumVersion: Int = 1,
    ): Boolean =
        record.record_id > 0L &&
            record.subject_user_id == expectedSubject &&
            record.version >= minimumVersion &&
            record.status in setOf("user_confirmed", "modified") &&
            record.trust_state == "user_confirmed"

    fun reuseRemainsPending(draft: DietaryMealDraft, expectedSubject: Long): Boolean =
        acceptsPendingDraft(draft, expectedSubject) && draft.source_type == "recent"
}
