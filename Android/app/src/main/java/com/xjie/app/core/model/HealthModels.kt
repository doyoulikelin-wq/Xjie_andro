package com.xjie.app.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HealthDataSummary(
    val summary_text: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class SummaryTaskResponse(
    val task_id: String,
    val status: String,           // pending | running | done | failed
    val stage: String? = null,    // l1 | l2 | l3
    val stage_current: Int? = null,
    val stage_total: Int? = null,
    val progress_pct: Double? = null,
    val token_used: Int? = null,
    val error_message: String? = null,
)

@Serializable
data class DocumentListResponse(
    val items: List<HealthDocument>? = null,
    val total: Int? = null,
)

@Serializable
data class HealthDocument(
    val id: String,
    val name: String? = null,
    val doc_type: String? = null,
    val source_type: String? = null,
    val extraction_status: String? = null,
    /** Structured history metadata; do not infer the hospital from a display title. */
    val hospital: String? = null,
    val doc_date: String? = null,
    /** Fallback ordering evidence when the medical date is not yet confirmed. */
    val created_at: String? = null,
    val csv_data: CsvData? = null,
    val abnormal_flags: List<AbnormalFlag>? = null,
    val ai_brief: String? = null,
    val ai_summary: String? = null,
    val file_url: String? = null,
    /**
     * Optional trust-workflow fields are additive so older servers and cached documents remain
     * decodable. A legacy `extraction_status=done` without a workflow is deliberately unverified.
     */
    val report_workflow_id: Int? = null,
    val report_workflow_status: String? = null,
    val report_subject_user_id: Long? = null,
    val report_duplicate: Boolean = false,
)

@Serializable
data class HealthReportCandidate(
    val candidate_id: Int,
    val candidate_key: String,
    val version: Int,
    val canonical_code: String? = null,
    val canonical_name: String,
    val raw_name: String,
    val raw_value: String? = null,
    val raw_unit: String? = null,
    val normalized_value: Double? = null,
    val normalized_text: String? = null,
    val normalized_unit: String? = null,
    val reference_low: Double? = null,
    val reference_high: Double? = null,
    val reference_text: String? = null,
    val abnormal_state: String,
    val confidence: Double? = null,
    val low_confidence: Boolean = false,
    val conflict_reasons: List<String> = emptyList(),
    val effective_at: String? = null,
    val source_locator: Map<String, JsonElement> = emptyMap(),
    val review_status: String,
    val requires_review: Boolean,
)

@Serializable
data class HealthReportReview(
    val workflow_id: Int,
    val legacy_document_id: Int? = null,
    val subject_user_id: Long,
    val status: String,
    val version: Int,
    val report_type: String,
    val document_fingerprint: String? = null,
    val recognized_at: String? = null,
    val confirmed_at: String? = null,
    val completed_at: String? = null,
    val confirmation_client_event_id: String? = null,
    val failure_code: String? = null,
    val failure_detail: String? = null,
    val failure_recovery: HealthReportFailureRecovery? = null,
    val pending_review_count: Int,
    val auto_accepted_count: Int,
    val admitted_observation_count: Int,
    val requires_report_confirmation: Boolean,
    val can_confirm: Boolean,
    val document: HealthDocument? = null,
    val candidates: List<HealthReportCandidate> = emptyList(),
)

@Serializable
data class HealthReportConfirmationEvent(
    val event_id: Int,
    val candidate_id: Int,
    val event_type: String,
    val candidate_version: Int,
    val before_data: Map<String, JsonElement> = emptyMap(),
    val after_data: Map<String, JsonElement> = emptyMap(),
    val created_at: String,
)

@Serializable
data class HealthReportObservation(
    val observation_id: Int,
    val source_candidate_id: Int,
    val confirmation_event_id: Int,
    val canonical_code: String? = null,
    val canonical_name: String,
    val value_numeric: Double? = null,
    val value_text: String? = null,
    val unit: String? = null,
    val reference_low: Double? = null,
    val reference_high: Double? = null,
    val reference_text: String? = null,
    val abnormal_state: String,
    val effective_at: String,
    val confirmed_at: String,
)

@Serializable
data class HealthReportProfileImpact(
    val profile_candidate_id: Int,
    val source_id: Int,
    val source_observation_id: Int,
    val fact_key: String,
    val category: String,
    val proposed_value: Map<String, JsonElement> = emptyMap(),
    val review_status: String,
    val confidence: Double? = null,
)

@Serializable
data class HealthReportScoreSnapshot(
    val snapshot_id: Int,
    val score_kind: String,
    val algorithm_id: String,
    val algorithm_version: String,
    val before_value: Double? = null,
    val after_value: Double? = null,
    val before_confidence: Double? = null,
    val after_confidence: Double? = null,
    val score_direction: String? = null,
    val semantic_outcome: String? = null,
    val calculation_status: String,
    val evidence: Map<String, JsonElement> = emptyMap(),
    val missing_inputs: Map<String, JsonElement> = emptyMap(),
    val failure_code: String? = null,
    val computed_at: String? = null,
)

@Serializable
data class HealthReportFollowUp(
    val available: Boolean,
    val items: List<String> = emptyList(),
    val unavailable_reason: String? = null,
)

@Serializable
data class HealthReportInterpretation(
    val workflow_id: Int,
    val subject_user_id: Long,
    val status: String,
    val available: Boolean,
    val unavailable_reason: String? = null,
    val non_diagnostic_notice: String,
    val document: HealthDocument? = null,
    val candidates: List<HealthReportCandidate> = emptyList(),
    val confirmation_events: List<HealthReportConfirmationEvent> = emptyList(),
    val structured_additions: List<HealthReportObservation> = emptyList(),
    val major_abnormalities: List<HealthReportObservation> = emptyList(),
    val follow_up: HealthReportFollowUp,
    val profile_impacts: List<HealthReportProfileImpact> = emptyList(),
    val score_state: String,
    val score_pending: Boolean,
    val score_snapshots: List<HealthReportScoreSnapshot> = emptyList(),
)

@Serializable
data class HealthReportFailureRecovery(
    val failure_code: String,
    val recovery_action: String,
    val retryable: Boolean,
    val allows_manual_candidate: Boolean,
)

@Serializable
data class HealthReportManualCandidateBody(
    val subject_user_id: Long,
    val workflow_version: Int,
    val client_event_id: String,
    val canonical_code: String? = null,
    val canonical_name: String,
    val raw_name: String,
    val value_numeric: Double? = null,
    val value_text: String? = null,
    val unit: String? = null,
    val reference_low: Double? = null,
    val reference_high: Double? = null,
    val reference_text: String? = null,
    val effective_at: String? = null,
)

@Serializable
data class HealthReportDecisionBody(
    val candidate_id: Int,
    val candidate_version: Int,
    val action: String,
    val value_numeric: Double? = null,
    val value_text: String? = null,
    val unit: String? = null,
)

@Serializable
data class HealthReportConfirmBody(
    val subject_user_id: Long,
    val client_event_id: String,
    val workflow_version: Int,
    val decisions: List<HealthReportDecisionBody> = emptyList(),
)

@Serializable
data class CsvData(
    val columns: List<String>? = null,
    val rows: List<List<String>>? = null,
)

@Serializable
data class AbnormalFlag(
    val field: String? = null,
    val name: String? = null,
    val value: String? = null,
    val unit: String? = null,
    val ref_range: String? = null,
)

@Serializable
data class IndicatorExplanation(
    val name: String,
    val brief: String,
    val detail: String,
    val normal_range: String? = null,
    val clinical_meaning: String? = null,
    val source: String,
)

@Serializable
data class TodayBriefing(
    val glucose_status: GlucoseStatus? = null,
    val daily_plan: DailyPlan? = null,
    val pending_rescues: List<RescueItem>? = null,
    val recent_actions: List<ActionItem>? = null,
)

@Serializable
data class GlucoseStatus(
    val current_mgdl: Double? = null,
    val trend: String? = null,
    val tir_24h: Double? = null,
)

@Serializable
data class DailyPlan(val payload: DailyPlanPayload)

@Serializable
data class DailyPlanPayload(
    val title: String? = null,
    val risk_windows: List<RiskWindow>? = null,
    val today_goals: List<String>? = null,
)

@Serializable
data class RiskWindow(
    val start: String? = null,
    val end: String? = null,
    val risk: String? = null,
)

@Serializable
data class RescueItem(
    val id: String,
    val payload: RescuePayload? = null,
)

@Serializable
data class RescuePayload(
    val title: String? = null,
    val risk_level: String? = null,
)

@Serializable
data class ActionItem(
    val id: String,
    val action_type: String? = null,
    val created_ts: String? = null,
)

@Serializable
data class HealthReports(
    val initial: HealthReportEntry? = null,
    val final: HealthReportEntry? = null,
)

@Serializable
data class HealthReportEntry(val date: String? = null)

@Serializable
data class AISummaryResponse(val summary: String? = null)

@Serializable
data class IndicatorInfo(
    val name: String,
    val category: String? = null,
    val count: Int,
)

@Serializable
data class IndicatorListResponse(val indicators: List<IndicatorInfo>)

@Serializable
data class TrendPoint(
    val date: String,
    val value: Double,
    val abnormal: Boolean,
    /** Optional provenance fields are additive so legacy trend responses still decode. */
    val source: String? = null,
    val measured_at: String? = null,
    val source_metric: String? = null,
    val source_id: String? = null,
    val value_kind: String? = null,
    val display_value: String? = null,
    val source_local_date: String? = null,
    val timezone_offset_minutes: Int? = null,
)

@Serializable
data class IndicatorTrend(
    val name: String,
    val unit: String? = null,
    val ref_low: Double? = null,
    val ref_high: Double? = null,
    val points: List<TrendPoint>,
)

@Serializable
data class IndicatorTrendResponse(val indicators: List<IndicatorTrend>)

@Serializable
data class WatchedIndicatorItem(
    val indicator_name: String,
    val category: String? = null,
    val display_order: Int,
)

@Serializable
data class WatchedListResponse(val items: List<WatchedIndicatorItem>)

@Serializable
data class HealthPlanListResponse(
    val items: List<HealthPlan> = emptyList(),
)

@Serializable
data class HealthPlan(
    val id: String,
    val plan_code: String? = null,
    val title: String,
    val goal: String? = null,
    val background: String? = null,
    val start_date: String,
    val end_date: String,
    val status: String,
    val source_conversation_id: String? = null,
    val source_message_id: String? = null,
    val created_by: String,
    val created_at: String,
    val updated_at: String,
    val task_count: Int = 0,
    val completed_task_count: Int = 0,
)

@Serializable
data class HealthPlanDetail(
    val id: String,
    val plan_code: String? = null,
    val title: String,
    val goal: String? = null,
    val background: String? = null,
    val start_date: String,
    val end_date: String,
    val status: String,
    val source_conversation_id: String? = null,
    val source_message_id: String? = null,
    val created_by: String,
    val created_at: String,
    val updated_at: String,
    val task_count: Int = 0,
    val completed_task_count: Int = 0,
    val raw_content: String? = null,
    val tasks: List<PlanTask> = emptyList(),
)

@Serializable
data class PlanTask(
    val id: String,
    val plan_id: String? = null,
    val date: String,
    val task_type: String,
    val title: String,
    val description: String? = null,
    val status: String,
    val target_count: Int,
    val completed_count: Int,
    val target_value: Double? = null,
    val completed_value: Double? = null,
    val unit: String? = null,
    val reminder_time: String? = null,
    val source_type: String,
    val source_ref: String,
)

@Serializable
data class HealthPlanFromChatRequest(
    val content: String,
    val analysis: String? = null,
    val conversation_id: String? = null,
    val message_id: String? = null,
    val title: String? = null,
)

@Serializable
data class HealthPlanQuestionnaireRequest(
    val target: String,
    val duration_days: Int,
    val frequency: String,
    val contents: List<String> = emptyList(),
    val medication_needed: Boolean = false,
    val notes: String? = null,
    val title: String? = null,
)

@Serializable
data class TubeWeek(
    val week_start: String,
    val week_end: String,
    val today: String,
    val has_omics_data: Boolean = false,
    val has_medication_need: Boolean = false,
    val task_types: List<String> = emptyList(),
    val days: List<TubeDay> = emptyList(),
)

@Serializable
data class TubeDay(
    val date: String,
    val weekday: Int,
    val is_today: Boolean,
    val is_future: Boolean,
    val completion_ratio: Double,
    val tasks: List<TubeTaskProgress> = emptyList(),
)

@Serializable
data class TubeTaskProgress(
    val task_type: String,
    val label: String,
    val title: String? = null,
    val description: String? = null,
    val summary: String? = null,
    val details: List<String> = emptyList(),
    val completed: Int,
    val target: Int,
    val completed_value: Double? = null,
    val target_value: Double? = null,
    val unit: String? = null,
    val ratio: Double,
    val plan_ids: List<String> = emptyList(),
    val plan_codes: List<String> = emptyList(),
    val source_task_ids: List<String> = emptyList(),
)

@Serializable
data class TubeCompleteRequest(
    val date: String,
    val task_type: String,
    val amount: Int = 1,
    val value: Double? = null,
)

@Serializable
data class TubeCompleteResponse(
    val day: TubeDay,
)

@Serializable
data class HealthTreeSummary(
    val trees_grown: Int = 0,
    val fruiting_count: Int = 0,
    val active_plan_count: Int = 0,
)

@Serializable
data class PlanTaskUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    val target_count: Int? = null,
    val target_value: Double? = null,
    val unit: String? = null,
    val reminder_time: String? = null,
)

@Serializable
data class PlanRevisionGenerateRequest(
    val date: String? = null,
    val purpose: String? = null,
)

@Serializable
data class PlanRevisionApplyRequest(
    val accepted_task_keys: List<String> = emptyList(),
    val accept_all: Boolean = false,
    val reject_all: Boolean = false,
)

@Serializable
data class PlanRevisionProposal(
    val id: String,
    val date: String,
    val status: String,
    val purpose: String,
    val original_items: List<PlanRevisionItem> = emptyList(),
    val revised_items: List<PlanRevisionItem> = emptyList(),
    val reasons: List<PlanRevisionReason> = emptyList(),
    val context_summary: String? = null,
    val daily_limit_used: Boolean = false,
    val created_at: String,
    val applied_at: String? = null,
)

@Serializable
data class PlanRevisionItem(
    val task_key: String,
    val task_type: String,
    val label: String,
    val title: String,
    val description: String? = null,
    val target_count: Int = 1,
    val target_value: Double? = null,
    val unit: String? = null,
    val reminder_time: String? = null,
    val plan_ids: List<String> = emptyList(),
    val plan_codes: List<String> = emptyList(),
    val source_task_ids: List<String> = emptyList(),
    val summary: String? = null,
)

@Serializable
data class PlanRevisionReason(
    val task_key: String,
    val reason: String,
    val evidence: String? = null,
)

@Serializable
data class PatientHistoryField(
    val value: String = "",
    val date_label: String? = null,
    val status: String = "missing",
    val source_type: String = "user",
    val source_ref: String? = null,
    val verified_by_user: Boolean = false,
)

@Serializable
data class PatientHistoryMetric(
    val name: String,
    val value: String,
    val unit: String? = null,
    val date_label: String? = null,
    val status: String = "pending_review",
    val source_type: String? = null,
    val source_ref: String? = null,
    val focus: String = "exams",
)

@Serializable
data class PatientHistoryEvidenceOverview(
    val record_count: Int = 0,
    val exam_count: Int = 0,
    val latest_record_date: String? = null,
    val latest_exam_date: String? = null,
)

@Serializable
data class MissingSectionItem(
    val key: String,
    val label: String,
)

@Serializable
data class PatientHistoryProfile(
    val doctor_summary: String = "",
    val sections: Map<String, PatientHistoryField> = emptyMap(),
    val key_metrics: List<PatientHistoryMetric> = emptyList(),
    val evidence_overview: PatientHistoryEvidenceOverview = PatientHistoryEvidenceOverview(),
    val missing_sections: List<MissingSectionItem> = emptyList(),
    val completeness: Double = 0.0,
    val updated_at: String? = null,
    val verified_at: String? = null,
)

@Serializable
data class PatientHistoryUpdateBody(
    val doctor_summary: String = "",
    val sections: Map<String, PatientHistoryField> = emptyMap(),
    val verified_at: String? = null,
)
