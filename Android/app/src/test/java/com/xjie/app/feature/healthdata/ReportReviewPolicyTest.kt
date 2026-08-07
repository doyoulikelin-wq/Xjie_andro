package com.xjie.app.feature.healthdata

import com.xjie.app.core.model.HealthReportCandidate
import com.xjie.app.core.model.HealthReportReview
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportReviewPolicyTest {
    @Test
    fun anyExplicitDecisionIsAnUnsavedDraftUntilReportConfirmation() {
        assertFalse(ReportReviewPolicy.hasUnsavedDrafts(emptyMap()))
        assertTrue(
            ReportReviewPolicy.hasUnsavedDrafts(
                mapOf(7 to ReportDecisionDraft(ReportDecisionAction.Correct, "12.5", "mg/L")),
            ),
        )
    }

    @Test
    fun reloadInvalidatesDraftsWhenWorkflowOrCandidateRevisionChanges() {
        val original = review(candidates = listOf(candidate(1)))
        assertTrue(ReportReviewPolicy.isSameRevision(original, original.copy()))
        assertFalse(ReportReviewPolicy.isSameRevision(original, original.copy(version = 2)))
        assertFalse(
            ReportReviewPolicy.isSameRevision(
                original,
                original.copy(candidates = listOf(candidate(1).copy(version = 2))),
            ),
        )
    }

    @Test
    fun reportConfirmationRequiresAnExplicitDecisionForEveryRequiredCandidate() {
        val review = review(candidates = listOf(candidate(1), candidate(2)))
        val partial = mapOf(1 to ReportDecisionDraft(ReportDecisionAction.Confirm))

        assertFalse(ReportReviewPolicy.canSubmit(review, "event-1", partial))
        assertTrue(
            ReportReviewPolicy.canSubmit(
                review,
                "event-1",
                partial + (2 to ReportDecisionDraft(ReportDecisionAction.Reject)),
            ),
        )
    }

    @Test
    fun correctedDecisionSendsExactlyOneNumericOrTextValue() {
        val review = review(candidates = listOf(candidate(7)))

        val numeric = ReportReviewPolicy.buildRequest(
            review,
            "event-number",
            mapOf(
                7 to ReportDecisionDraft(
                    ReportDecisionAction.Correct,
                    correctedValue = "12.50",
                    correctedUnit = "mg/L",
                ),
            ),
        ).decisions.single()
        assertEquals(12.5, numeric.value_numeric!!, 0.0001)
        assertNull(numeric.value_text)

        val text = ReportReviewPolicy.buildRequest(
            review,
            "event-text",
            mapOf(7 to ReportDecisionDraft(ReportDecisionAction.Correct, "未检出")),
        ).decisions.single()
        assertNull(text.value_numeric)
        assertEquals("未检出", text.value_text)
    }

    @Test
    fun manualCandidateRequestUsesServerRevisionAndExactlyOneValue() {
        val review = review().copy(version = 7)
        val numeric = ReportReviewPolicy.buildManualCandidateRequest(
            review,
            "manual-event",
            ManualReportCandidateDraft(
                name = "超敏 C 反应蛋白",
                value = "8.20",
                unit = "mg/L",
                referenceLow = "0",
                referenceHigh = "3",
            ),
        )
        assertEquals(7, numeric.workflow_version)
        assertEquals(8.2, numeric.value_numeric!!, 0.0001)
        assertNull(numeric.value_text)
        assertEquals(review.subject_user_id, numeric.subject_user_id)

        val text = ReportReviewPolicy.buildManualCandidateRequest(
            review,
            "manual-text",
            ManualReportCandidateDraft(name = "定性结果", value = "未检出"),
        )
        assertNull(text.value_numeric)
        assertEquals("未检出", text.value_text)
    }

    @Test
    fun autoAcceptedFieldsStillRequireReportLevelConfirmation() {
        val auto = candidate(
            id = 3,
            requiresReview = false,
            reviewStatus = "auto_accepted",
        )
        val review = review(candidates = listOf(auto))

        assertTrue(ReportReviewPolicy.requiredCandidateIds(review).isEmpty())
        assertTrue(ReportReviewPolicy.canSubmit(review, "report-confirm", emptyMap()))
        assertTrue(
            ReportReviewPolicy.buildRequest(review, "report-confirm", emptyMap()).decisions.isEmpty(),
        )
    }

    @Test
    fun committingWorkflowOnlyResumesWithTheServerRecordedEvent() {
        val review = review(
            status = "committing",
            canConfirm = false,
            confirmationEventId = "stable-event",
        )

        assertFalse(ReportReviewPolicy.canSubmit(review, "new-event", emptyMap()))
        assertTrue(ReportReviewPolicy.canSubmit(review, "stable-event", emptyMap()))
        assertEquals(
            "stable-event",
            ReportReviewPolicy.buildRequest(review, "stable-event", emptyMap()).client_event_id,
        )
    }

    @Test
    fun reviewResponseUsesServerLowConfidenceAndConflictFlagsWithoutClientGuessing() {
        val review = Json.decodeFromString<HealthReportReview>(
            """{
              "workflow_id":8,
              "subject_user_id":99,
              "status":"awaiting_confirmation",
              "version":2,
              "report_type":"lab",
              "pending_review_count":1,
              "auto_accepted_count":0,
              "admitted_observation_count":0,
              "requires_report_confirmation":true,
              "can_confirm":true,
              "candidates":[{
                "candidate_id":5,
                "candidate_key":"hscrp-1",
                "version":1,
                "canonical_name":"hsCRP",
                "raw_name":"超敏C反应蛋白",
                "raw_value":"8.2",
                "normalized_value":8.2,
                "normalized_unit":"mg/L",
                "abnormal_state":"abnormal",
                "confidence":0.91,
                "low_confidence":false,
                "conflict_reasons":["unit_conflict"],
                "source_locator":{"page":2,"box":[1,2,3,4]},
                "review_status":"pending_review",
                "requires_review":true
              }]
            }""",
        )

        val candidate = review.candidates.single()
        assertFalse(candidate.low_confidence)
        assertEquals(listOf("unit_conflict"), candidate.conflict_reasons)
        assertEquals("2", candidate.source_locator.getValue("page").toString())
    }

    private fun review(
        status: String = "awaiting_confirmation",
        canConfirm: Boolean = true,
        confirmationEventId: String? = null,
        candidates: List<HealthReportCandidate> = emptyList(),
    ) = HealthReportReview(
        workflow_id = 4,
        subject_user_id = 99,
        status = status,
        version = 1,
        report_type = "lab",
        confirmation_client_event_id = confirmationEventId,
        pending_review_count = candidates.count { it.review_status == "pending_review" },
        auto_accepted_count = candidates.count { it.review_status == "auto_accepted" },
        admitted_observation_count = 0,
        requires_report_confirmation = true,
        can_confirm = canConfirm,
        candidates = candidates,
    )

    private fun candidate(
        id: Int,
        requiresReview: Boolean = true,
        reviewStatus: String = "pending_review",
    ) = HealthReportCandidate(
        candidate_id = id,
        candidate_key = "candidate-$id",
        version = 1,
        canonical_name = "指标$id",
        raw_name = "指标$id",
        raw_value = "$id",
        normalized_value = id.toDouble(),
        abnormal_state = if (requiresReview) "abnormal" else "normal",
        review_status = reviewStatus,
        requires_review = requiresReview,
    )
}
