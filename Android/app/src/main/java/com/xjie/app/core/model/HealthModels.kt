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
