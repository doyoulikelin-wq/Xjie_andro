package com.xjie.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SimpleOk(val ok: Boolean = true, val message: String? = null)

@Serializable
data class PasswordChangeBody(
    val old_password: String,
    val new_password: String,
)

@Serializable
data class PasswordResetRequestBody(val phone: String)

@Serializable
data class PasswordResetConfirmBody(
    val phone: String,
    val code: String,
    val new_password: String,
)

// ── Indicator search & manual entry ──

@Serializable
data class IndicatorSearchItem(
    val name: String,
    val alias: String? = null,
    val category: String? = null,
    val brief: String? = null,
    val normal_range: String? = null,
    val unit: String? = null,
    val score: Double = 0.0,
)

@Serializable
data class IndicatorSearchResponse(val items: List<IndicatorSearchItem> = emptyList())

@Serializable
data class ManualIndicatorBody(
    val indicator_name: String,
    val value: Double,
    val unit: String? = null,
    val measured_at: String,
    val notes: String? = null,
)

@Serializable
data class ManualIndicatorItem(
    val id: Long,
    val indicator_name: String,
    val value: Double,
    val unit: String? = null,
    val measured_at: String,
    val notes: String? = null,
    val source: String = "manual",
)

@Serializable
data class ManualIndicatorListResponse(val items: List<ManualIndicatorItem> = emptyList())

// ── Exercise log ──

@Serializable
data class ExerciseBody(
    val activity_type: String,
    val duration_minutes: Int,
    val intensity: String? = null,
    val calories_kcal: Double? = null,
    val notes: String? = null,
    val started_at: String? = null,
)

@Serializable
data class ExerciseItem(
    val id: Long,
    val activity_type: String,
    val duration_minutes: Int,
    val intensity: String? = null,
    val calories_kcal: Double? = null,
    val notes: String? = null,
    val started_at: String,
    val created_at: String,
)

@Serializable
data class ExerciseListResponse(
    val items: List<ExerciseItem> = emptyList(),
    val total_minutes: Int = 0,
    val total_kcal: Double = 0.0,
)
