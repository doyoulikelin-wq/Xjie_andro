package com.xjie.app.core.model

import kotlinx.serialization.Serializable

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
    val doc_date: String? = null,
    val csv_data: CsvData? = null,
    val abnormal_flags: List<AbnormalFlag>? = null,
    val ai_brief: String? = null,
    val ai_summary: String? = null,
    val file_url: String? = null,
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
