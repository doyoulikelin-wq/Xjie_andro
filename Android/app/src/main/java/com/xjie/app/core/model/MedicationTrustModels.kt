package com.xjie.app.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class MedicationInventoryEstimate(
    val is_estimate: Boolean,
    val label: String,
    val estimated_remaining: Double? = null,
    val estimated_consumed: Double? = null,
    val inventory_unit: String? = null,
    val basis: String,
    val unavailable_reason: String? = null,
)

@Serializable
data class TrustedMedicationPlan(
    val plan_id: Long,
    val subject_user_id: Long,
    val generic_name: String,
    val brand_name: String? = null,
    val strength: String? = null,
    val dose_text: String? = null,
    val dose_quantity: Double? = null,
    val frequency: String? = null,
    val schedule_times: List<String> = emptyList(),
    val meal_relation: String = "unspecified",
    val instructions: String? = null,
    val course_start: String? = null,
    val course_end: String? = null,
    val prescriber: String? = null,
    val initial_quantity: Double? = null,
    val inventory_unit: String? = null,
    val is_long_term: Boolean = false,
    val source_type: String,
    val source_ref: String,
    val status: String,
    val version: Int,
    val confirmed_at: String,
    val trust_state: String,
    val reminder_management: String,
    val reminder_default_enabled: Boolean,
    val server_notification_scheduled: Boolean,
    val inventory: MedicationInventoryEstimate,
)

@Serializable
data class TrustedMedicationPlanList(
    val subject_user_id: Long,
    val items: List<TrustedMedicationPlan> = emptyList(),
)

@Serializable
data class MedicationTodayTask(
    val occurrence_key: String,
    val plan_id: Long,
    val plan_version: Int,
    val generic_name: String,
    val brand_name: String? = null,
    val dose_text: String? = null,
    val scheduled_local_date: String,
    val scheduled_time: String,
    val scheduled_at: String,
    val status: String,
    val status_label: String,
    val status_assertion: String,
    val occurrence_version: Int,
    val latest_event_id: Long? = null,
    val snoozed_until: String? = null,
    val confirmed_at: String? = null,
    val possibly_missed_is_not_confirmation: Boolean,
    val notification_schedule_status: String,
)

@Serializable
data class MedicationTodaySummary(
    val subject_user_id: Long,
    val local_date: String,
    val planned_count: Int,
    val taken_count: Int,
    val awaiting_confirmation_count: Int,
    val possibly_missed_count: Int,
    val skipped_count: Int,
    val snoozed_count: Int,
    val adverse_reaction_count: Int,
    val next_task: MedicationTodayTask? = null,
    val tasks: List<MedicationTodayTask> = emptyList(),
    val empty_state: String? = null,
    val missed_assertion_policy: String,
)

@Serializable
data class MedicationPrefillCandidate(
    val candidate_id: Long,
    val subject_user_id: Long,
    val client_event_id: String,
    val source_type: String,
    val source_ref: String,
    val extracted_data: JsonObject,
    val field_confidences: Map<String, Double> = emptyMap(),
    val low_confidence_fields: List<String> = emptyList(),
    val review_status: String,
    val version: Int,
    val trust_state: String,
    val requires_user_confirmation: Boolean,
    val plan_created: Boolean,
    val confirmation_endpoint: String,
)

@Serializable
data class MedicationPrefillList(
    val subject_user_id: Long,
    val items: List<MedicationPrefillCandidate> = emptyList(),
)

@Serializable
data class MedicationRecognizePrefillBody(
    val raw_text: String,
    val subject_user_id: Long,
    val client_event_id: String,
)

@Serializable
data class MedicationRecognizePrefillResult(
    val name: String? = null,
    val dosage: String? = null,
    val frequency: String? = null,
    val instructions: String? = null,
    val schedule_times: List<String> = emptyList(),
    val candidate_id: Long,
    val candidate_version: Int,
    val client_event_id: String,
    val field_confidences: Map<String, Double> = emptyMap(),
    val low_confidence_fields: List<String> = emptyList(),
    val trust_state: String,
    val requires_user_confirmation: Boolean,
    val plan_created: Boolean,
    val confirmation_endpoint: String,
)

@Serializable
data class MedicationPlanConfirmBody(
    val subject_user_id: Long,
    val client_request_id: String,
    val client_event_id: String,
    val candidate_id: Long? = null,
    val candidate_version: Int? = null,
    val generic_name: String,
    val brand_name: String? = null,
    val strength: String? = null,
    val dose_text: String? = null,
    val dose_quantity: Double? = null,
    val frequency: String? = null,
    val schedule_times: List<String> = emptyList(),
    val meal_relation: String = "unspecified",
    val instructions: String? = null,
    val course_start: String? = null,
    val course_end: String? = null,
    val prescriber: String? = null,
    val initial_quantity: Double? = null,
    val inventory_unit: String? = null,
    val is_long_term: Boolean = false,
    val source_type: String,
    val source_ref: String? = null,
)

@Serializable
data class MedicationPlanReviseBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
    val generic_name: String,
    val brand_name: String? = null,
    val strength: String? = null,
    val dose_text: String? = null,
    val dose_quantity: Double? = null,
    val frequency: String? = null,
    val schedule_times: List<String> = emptyList(),
    val meal_relation: String = "unspecified",
    val instructions: String? = null,
    val course_start: String? = null,
    val course_end: String? = null,
    val prescriber: String? = null,
    val initial_quantity: Double? = null,
    val inventory_unit: String? = null,
    val is_long_term: Boolean = false,
    val source_type: String,
    val source_ref: String? = null,
)

@Serializable
data class MedicationPlanStatusBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
    val action: String,
    val reason: String? = null,
)

@Serializable
data class MedicationPrefillRejectBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
)

@Serializable
data class MedicationDoseActionBody(
    val subject_user_id: Long,
    val plan_id: Long,
    val expected_plan_version: Int,
    val client_event_id: String,
    val scheduled_local_date: String,
    val scheduled_time: String,
    val expected_occurrence_version: Int,
    val action: String,
    val corrected_status: String? = null,
    val correction_of_event_id: Long? = null,
    val snoozed_until: String? = null,
    val taken_quantity: Double? = null,
    val reason: String? = null,
)

@Serializable
data class MedicationDoseEvent(
    val event_id: Long,
    val occurrence_key: String,
    val occurrence_version: Int,
    val action: String,
    val effective_status: String,
    val supersedes_event_id: Long? = null,
    val snoozed_until: String? = null,
    val taken_quantity: Double? = null,
    val reason: String? = null,
    val confirmed_at: String,
    val trust_state: String,
    val notification_schedule_status: String,
    val reminder_management: String,
)

@Serializable
data class MedicationReaction(
    val reaction_key: String,
    val reaction_version: Int,
    val plan_id: Long,
    val symptoms: String,
    val onset_at: String,
    val severity: String,
    val duration_minutes: Int? = null,
    val related_occurrence_key: String? = null,
    val notes: String? = null,
    val status: String,
    val causal_attribution: String,
    val user_facing_causality: String,
    val safety_guidance: String,
    val confirmed_at: String,
)

@Serializable
data class MedicationReactionList(
    val subject_user_id: Long,
    val items: List<MedicationReaction> = emptyList(),
)

@Serializable
data class MedicationReactionCreateBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val reaction_key: String,
    val plan_id: Long,
    val symptoms: String,
    val onset_at: String,
    val severity: String,
    val duration_minutes: Int? = null,
    val related_occurrence_key: String? = null,
    val notes: String? = null,
)

@Serializable
data class MedicationReactionCorrectBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
    val plan_id: Long,
    val symptoms: String,
    val onset_at: String,
    val severity: String,
    val duration_minutes: Int? = null,
    val related_occurrence_key: String? = null,
    val notes: String? = null,
)

@Serializable
data class MedicationReactionRetractBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val expected_version: Int,
)
