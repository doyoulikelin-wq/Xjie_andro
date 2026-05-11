package com.xjie.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FeatureFlagClientResponse(val flags: Map<String, Boolean>)

@Serializable
data class AdminFeatureFlag(
    val id: Int,
    val key: String,
    val enabled: Boolean,
    val description: String,
    val rollout_pct: Int,
    val updated_at: String? = null,
)

@Serializable
data class AdminFeatureFlagList(val flags: List<AdminFeatureFlag>)

@Serializable
data class AdminFeatureFlagUpdate(
    val enabled: Boolean? = null,
    val description: String? = null,
    val rollout_pct: Int? = null,
)

@Serializable
data class AdminFeatureFlagCreate(
    val key: String,
    val enabled: Boolean = true,
    val description: String = "",
    val rollout_pct: Int = 100,
)

@Serializable
data class AdminSkill(
    val id: Int,
    val key: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val priority: Int,
    val trigger_hint: String,
    val prompt_template: String,
    val updated_at: String? = null,
)

@Serializable
data class AdminSkillList(val skills: List<AdminSkill>)

@Serializable
data class AdminSkillUpdate(
    val name: String? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
    val priority: Int? = null,
    val trigger_hint: String? = null,
    val prompt_template: String? = null,
)

@Serializable
data class AdminSkillCreate(
    val key: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val priority: Int = 100,
    val trigger_hint: String = "",
    val prompt_template: String = "",
)
