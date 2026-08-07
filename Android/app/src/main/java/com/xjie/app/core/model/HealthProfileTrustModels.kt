package com.xjie.app.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class HealthProfileTrustSource(
    val source_id: Long,
    val source_type: String,
    val source_ref: String,
    val confidence: Double? = null,
    val source_snapshot: JsonObject = JsonObject(emptyMap()),
    val created_at: String,
)

@Serializable
data class HealthProfileTrustFact(
    val fact_id: Long,
    val fact_key: String,
    val category: String,
    val value_data: JsonObject,
    val is_safety_critical: Boolean,
    val confirmation_method: String,
    val version: Int,
    val confirmed_at: String? = null,
    val updated_at: String,
    val sources: List<HealthProfileTrustSource> = emptyList(),
)

@Serializable
data class HealthProfileTrustCandidate(
    val candidate_id: Long,
    val fact_key: String,
    val category: String,
    val proposed_value: JsonObject,
    val is_safety_critical: Boolean,
    val review_status: String,
    val conflict_with_fact_id: Long? = null,
    val confidence: Double? = null,
    val version: Int,
    val created_at: String,
    val updated_at: String,
    val sources: List<HealthProfileTrustSource> = emptyList(),
)

@Serializable
data class HealthProfileTrustOverview(
    val completeness_percent: Int,
    val resolved_required_weight: Int,
    val total_required_weight: Int,
    val missing_required_fact_keys: List<String> = emptyList(),
    val pending_update_count: Int,
    val independent_source_count: Int,
    val primary_action: HealthProfilePrimaryAction? = null,
)

@Serializable
data class HealthProfilePrimaryAction(
    val kind: String,
    val item_count: Int,
    val localization_key: String,
    val route: String,
)

@Serializable
data class HealthProfileGoalMetric(
    val metric_key: String,
    val display_label: String? = null,
)

@Serializable
data class HealthProfileGoal(
    val goal_id: Long,
    val name: String,
    val status: String,
    val started_on: String,
    val version: Int,
    val confirmed_at: String,
    val metrics: List<HealthProfileGoalMetric> = emptyList(),
)

/** Read-only projection. Plan editing and task execution stay in HealthPlan. */
@Serializable
data class HealthProfileManagementPlan(
    val plan_id: Long,
    val title: String,
    val goal: String? = null,
    val start_date: String,
    val end_date: String,
    val status: String,
    val created_by: String,
    val updated_at: String,
    val task_count: Int,
    val completed_task_count: Int,
)

@Serializable
data class HealthProfileTrustProfile(
    val subject_user_id: Long,
    val profile_status: String? = null,
    val overview: HealthProfileTrustOverview,
    val facts: List<HealthProfileTrustFact> = emptyList(),
    val candidates: List<HealthProfileTrustCandidate> = emptyList(),
    val goals: List<HealthProfileGoal> = emptyList(),
    val management_plans: List<HealthProfileManagementPlan> = emptyList(),
)

@Serializable
data class HealthProfileCandidateReviewBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val candidate_version: Int,
    val action: String,
)

@Serializable
data class HealthProfileFactUpsertBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val fact_key: String,
    val category: String,
    val response_state: String,
    val value: JsonElement? = null,
    val is_safety_critical: Boolean = false,
    val expected_version: Int? = null,
)

@Serializable
data class HealthProfileFactRetractBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
)

@Serializable
data class HealthProfileGoalMetricBody(
    val metric_key: String,
    val display_label: String? = null,
)

@Serializable
data class HealthProfileGoalCreateBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val name: String,
    val started_on: String,
    val metrics: List<HealthProfileGoalMetricBody>,
)

@Serializable
data class HealthProfileGoalUpdateBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
    val name: String,
    val started_on: String,
    val metrics: List<HealthProfileGoalMetricBody>,
)

@Serializable
data class HealthProfileGoalStatusBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
    val action: String,
)

@Serializable
data class HealthProfileRevisionItem(
    val revision_id: Long,
    val event_type: String,
    val target_version: Int,
    val actor_user_id: Long? = null,
    val before_data: JsonObject = JsonObject(emptyMap()),
    val after_data: JsonObject = JsonObject(emptyMap()),
    val created_at: String,
)

@Serializable
data class HealthProfileRevisionList(
    val subject_user_id: Long,
    val target_kind: String,
    val target_id: Long,
    val items: List<HealthProfileRevisionItem> = emptyList(),
    val next_after_revision_id: Long? = null,
)

@Serializable
data class HealthProfileLongTermMedicationSummary(
    val subject_user_id: Long,
    val items: List<HealthProfileLongTermMedicationSummaryItem> = emptyList(),
)

/**
 * Deliberately contains no dose, reminder or adherence fields. Those operations belong to the
 * medication module and therefore cannot accidentally be rendered or mutated by Profile.
 */
@Serializable
data class HealthProfileLongTermMedicationSummaryItem(
    val medication_name: String,
    val purpose: String? = null,
    val started_on: String? = null,
    val is_still_taking: Boolean,
    val source: String,
    val last_confirmed_at: String,
)
