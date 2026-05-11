package com.xjie.app.core.model

import kotlinx.serialization.Serializable

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

@Serializable
data class MealUploadTicket(
    val upload_url: String? = null,
    val object_key: String? = null,
)

@Serializable
data class MealCreateBody(
    val meal_ts: String,
    val meal_ts_source: String,
    val kcal: Int,
    val tags: List<String>,
    val notes: String,
)

@Serializable
data class MealUpdateBody(
    val kcal: Int? = null,
    val notes: String? = null,
    val tags: List<String>? = null,
)

@Serializable
data class PhotoUploadBody(
    val filename: String,
    val content_type: String,
)

@Serializable
data class PhotoCompleteBody(val object_key: String)
