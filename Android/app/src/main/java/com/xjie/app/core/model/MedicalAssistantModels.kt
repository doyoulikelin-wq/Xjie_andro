package com.xjie.app.core.model

import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable

/** Server-owned metadata for one recent medical document. */
@Serializable
data class MedicalAssistantRecentDocument(
    val document_id: String,
    val title: String,
    val hospital: String? = null,
    val document_date: String? = null,
    val uploaded_at: String,
    val status: String,
)

/** Complete server snapshot rendered by the medical-assistant dashboard. */
@Serializable
data class MedicalAssistantOverview(
    val subject_user_id: Long,
    val summary: String,
    val generated_at: String? = null,
    val latest_report_uploaded_at: String? = null,
    val report_count_last_year: Int,
    val recent_documents: List<MedicalAssistantRecentDocument> = emptyList(),
    val generation_result: String,
) {
    val hasSummary: Boolean get() = summary.isNotBlank()
    val generationResult: MedicalAssistantGenerationResult
        get() = MedicalAssistantGenerationResult.fromWire(generation_result)

    /**
     * Presentation-only freshness evidence. The server remains authoritative for whether a
     * generation is allowed. Invalid timestamps fail closed instead of inventing freshness.
     */
    val hasNewerUpload: Boolean
        get() = MedicalAssistantOverviewPolicy.hasNewerUpload(
            uploadedAt = latest_report_uploaded_at,
            generatedAt = generated_at,
        )
}

sealed interface MedicalAssistantGenerationResult {
    data object Loaded : MedicalAssistantGenerationResult
    data object Generated : MedicalAssistantGenerationResult
    data object NoInformationUpdate : MedicalAssistantGenerationResult
    data object NoReports : MedicalAssistantGenerationResult
    data object ReportProcessing : MedicalAssistantGenerationResult
    data class Unknown(val rawValue: String) : MedicalAssistantGenerationResult

    companion object {
        fun fromWire(value: String): MedicalAssistantGenerationResult = when (value) {
            "loaded" -> Loaded
            "generated" -> Generated
            "no_information_update" -> NoInformationUpdate
            "no_reports" -> NoReports
            "report_processing" -> ReportProcessing
            else -> Unknown(value)
        }
    }
}

/** Pure, testable server-result and timestamp rules shared by the UI state machine. */
object MedicalAssistantOverviewPolicy {
    fun hasNewerUpload(uploadedAt: String?, generatedAt: String?): Boolean {
        val uploaded = parseInstant(uploadedAt) ?: return false
        if (generatedAt.isNullOrBlank()) return true
        val generated = parseInstant(generatedAt) ?: return false
        return uploaded.isAfter(generated)
    }

    fun accepts(result: MedicalAssistantGenerationResult): Boolean =
        result !is MedicalAssistantGenerationResult.Unknown

    fun parseInstant(value: String?): Instant? {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return runCatching { Instant.parse(normalized) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(normalized).toInstant() }.getOrNull()
    }
}
