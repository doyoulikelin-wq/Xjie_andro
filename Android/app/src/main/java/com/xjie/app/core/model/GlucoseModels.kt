package com.xjie.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DashboardHealth(
    val glucose: GlucoseDashboard? = null,
    val kcal_today: Double? = null,
    val meals_today: List<MealItem>? = null,
    val data_quality: DataQuality? = null,
    val metabolic_state: MetabolicState? = null,
    val weekly_validation: WeeklyValidation? = null,
    val cgm_quality: CGMQuality? = null,
)

@Serializable
data class DataQuality(
    val glucose_gaps_hours: Double? = null,
    val variability: String? = null,
)

@Serializable
data class GlucoseDashboard(
    val last_24h: GlucoseSummary? = null,
    val last_7d: GlucoseSummary? = null,
)

@Serializable
data class GlucoseSummary(
    val window: String? = null,
    val avg: Double? = null,
    val tir_70_180_pct: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val variability: String? = null,
    val gaps_hours: Double? = null,
)

@Serializable
data class ProactiveMessage(
    val message: String? = null,
    val has_rescue: Boolean? = null,
)

@Serializable
data class GlucosePoint(
    val ts: String,
    val glucose_mgdl: Double,
)

@Serializable
data class GlucoseRange(
    val min_ts: String? = null,
    val max_ts: String? = null,
)

@Serializable
data class MetabolicState(
    val title: String? = null,
    val date: String,
    val level: String,
    val score: Int,
    val confidence: String? = null,
    val confidence_label: String? = null,
    val data_sources: List<String>? = null,
    val missing_sources: List<String>? = null,
    val primary_basis: String? = null,
    val headline: String,
    val reason: String,
    val action: String,
    val metrics: MetabolicMetrics? = null,
    val overview: List<MetabolicDayState> = emptyList(),
)

@Serializable
data class MetabolicMetrics(
    val avg: Double? = null,
    val tir_70_180_pct: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val variability: String? = null,
    val reading_count: Int? = null,
    val meals_count: Int? = null,
    val kcal_today: Double? = null,
    val tasks_total: Int? = null,
    val tasks_completed: Int? = null,
    val exercise_minutes: Int? = null,
    val mood_count: Int? = null,
    val care_count: Int? = null,
)

@Serializable
data class MetabolicDayState(
    val date: String,
    val level: String,
    val score: Int,
    val headline: String,
    val reason: String,
    val action: String,
    val confidence: String? = null,
    val data_sources: List<String>? = null,
    val missing_sources: List<String>? = null,
    val avg: Double? = null,
    val tir_70_180_pct: Double? = null,
    val reading_count: Int = 0,
)

@Serializable
data class WeeklyValidation(
    val headline: String,
    val adherence_pct: Int,
    val completed_actions: Int,
    val total_actions: Int,
    val tir_delta_pct: Double? = null,
    val avg_delta_mgdl: Double? = null,
    val summary: String,
)

@Serializable
data class CGMQuality(
    val window_days: Int,
    val active_days: Int,
    val reading_count: Int,
    val expected_readings: Int,
    val completeness_pct: Int,
    val gap_hours: Double,
    val latest_ts: String? = null,
    val status: String,
    val message: String,
)
