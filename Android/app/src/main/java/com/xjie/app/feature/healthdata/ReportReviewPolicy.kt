package com.xjie.app.feature.healthdata

import com.xjie.app.core.model.HealthReportCandidate
import com.xjie.app.core.model.HealthReportConfirmBody
import com.xjie.app.core.model.HealthReportDecisionBody
import com.xjie.app.core.model.HealthReportManualCandidateBody
import com.xjie.app.core.model.HealthReportReview

enum class ReportDecisionAction(val wireValue: String) {
    Confirm("confirm"),
    Correct("correct"),
    Reject("reject"),
}

data class ReportDecisionDraft(
    val action: ReportDecisionAction,
    val correctedValue: String = "",
    val correctedUnit: String = "",
)

data class ManualReportCandidateDraft(
    val name: String = "",
    val value: String = "",
    val unit: String = "",
    val referenceLow: String = "",
    val referenceHigh: String = "",
    val referenceText: String = "",
) {
    val isDirty: Boolean
        get() = listOf(name, value, unit, referenceLow, referenceHigh, referenceText)
            .any { it.isNotBlank() }
}

/** Pure client validation; workflow authority and admission remain on the server. */
internal object ReportReviewPolicy {
    fun hasUnsavedDrafts(drafts: Map<Int, ReportDecisionDraft>): Boolean = drafts.isNotEmpty()

    fun isSameRevision(previous: HealthReportReview, current: HealthReportReview): Boolean =
        previous.workflow_id == current.workflow_id &&
            previous.subject_user_id == current.subject_user_id &&
            previous.version == current.version &&
            previous.candidates
                .map { it.candidate_id to it.version }
                .sortedBy { it.first } == current.candidates
                .map { it.candidate_id to it.version }
                .sortedBy { it.first }

    fun requiredCandidateIds(review: HealthReportReview): Set<Int> = review.candidates
        .filter { it.review_status == "pending_review" && it.requires_review }
        .mapTo(linkedSetOf()) { it.candidate_id }

    fun buildManualCandidateRequest(
        review: HealthReportReview,
        clientEventId: String,
        draft: ManualReportCandidateDraft,
    ): HealthReportManualCandidateBody {
        require(review.status == "awaiting_confirmation" ||
            (review.status == "failed" && review.failure_recovery?.allows_manual_candidate == true)
        ) { "report does not allow manual candidate recovery" }
        require(clientEventId.length in 1..80) { "client event id must contain 1 to 80 characters" }
        val name = draft.name.trim()
        val value = draft.value.trim()
        require(name.isNotEmpty()) { "indicator name is required" }
        require(name.length <= 160) { "indicator name is too long" }
        require(value.isNotEmpty()) { "indicator value is required" }
        val referenceLow = draft.referenceLow.trim().takeIf(String::isNotEmpty)
            ?.toDoubleOrNull()?.takeIf(Double::isFinite)
        val referenceHigh = draft.referenceHigh.trim().takeIf(String::isNotEmpty)
            ?.toDoubleOrNull()?.takeIf(Double::isFinite)
        require(draft.referenceLow.isBlank() || referenceLow != null) { "reference lower bound is invalid" }
        require(draft.referenceHigh.isBlank() || referenceHigh != null) { "reference upper bound is invalid" }
        require(referenceLow == null || referenceHigh == null || referenceLow <= referenceHigh) {
            "reference lower bound cannot exceed upper bound"
        }
        val numeric = value.toDoubleOrNull()?.takeIf(Double::isFinite)
        return HealthReportManualCandidateBody(
            subject_user_id = review.subject_user_id,
            workflow_version = review.version,
            client_event_id = clientEventId,
            canonical_name = name,
            raw_name = name,
            value_numeric = numeric,
            value_text = value.takeIf { numeric == null },
            unit = draft.unit.trim().takeIf(String::isNotEmpty),
            reference_low = referenceLow,
            reference_high = referenceHigh,
            reference_text = draft.referenceText.trim().takeIf(String::isNotEmpty),
        )
    }

    fun canSubmit(
        review: HealthReportReview,
        clientEventId: String,
        drafts: Map<Int, ReportDecisionDraft>,
    ): Boolean = when (review.status) {
        "awaiting_confirmation" ->
            review.can_confirm && clientEventId.isNotBlank() &&
                requiredCandidateIds(review).all(drafts::containsKey) &&
                drafts.values.all(::isValid)
        "committing" ->
            !review.confirmation_client_event_id.isNullOrBlank() &&
                review.confirmation_client_event_id == clientEventId
        else -> false
    }

    fun buildRequest(
        review: HealthReportReview,
        clientEventId: String,
        drafts: Map<Int, ReportDecisionDraft>,
    ): HealthReportConfirmBody {
        require(clientEventId.length in 1..80) { "client event id must contain 1 to 80 characters" }
        require(canSubmit(review, clientEventId, drafts)) { "report review is incomplete or stale" }

        val candidateById = review.candidates.associateBy { it.candidate_id }
        val decisions = drafts.entries.sortedBy { it.key }.map { (candidateId, draft) ->
            val candidate = requireNotNull(candidateById[candidateId]) {
                "decision candidate does not belong to this workflow"
            }
            draft.toBody(candidate)
        }
        return HealthReportConfirmBody(
            subject_user_id = review.subject_user_id,
            client_event_id = clientEventId,
            workflow_version = review.version,
            decisions = decisions,
        )
    }

    private fun isValid(draft: ReportDecisionDraft): Boolean =
        draft.action != ReportDecisionAction.Correct || draft.correctedValue.trim().isNotEmpty()

    private fun ReportDecisionDraft.toBody(candidate: HealthReportCandidate): HealthReportDecisionBody {
        if (action != ReportDecisionAction.Correct) {
            return HealthReportDecisionBody(
                candidate_id = candidate.candidate_id,
                candidate_version = candidate.version,
                action = action.wireValue,
            )
        }

        val value = correctedValue.trim()
        val numeric = value.toDoubleOrNull()
        return HealthReportDecisionBody(
            candidate_id = candidate.candidate_id,
            candidate_version = candidate.version,
            action = action.wireValue,
            value_numeric = numeric,
            value_text = if (numeric == null) value else null,
            unit = correctedUnit.trim().takeIf(String::isNotEmpty),
        )
    }
}
