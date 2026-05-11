package com.xjie.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val intervention_level: String? = null,
    val daily_reminder_limit: Int? = null,
    val glucose_unit: String? = null,
)

@Serializable
data class UpdateSettingsBody(
    val intervention_level: String? = null,
    val glucose_unit: String? = null,
)

@Serializable
data class UpdateConsentBody(
    val allow_ai_chat: Boolean? = null,
    val allow_data_upload: Boolean? = null,
)
