package com.xjie.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DashboardHealth(
    val glucose: GlucoseDashboard? = null,
    val kcal_today: Double? = null,
    val meals_today: List<MealItem>? = null,
    val data_quality: DataQuality? = null,
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
