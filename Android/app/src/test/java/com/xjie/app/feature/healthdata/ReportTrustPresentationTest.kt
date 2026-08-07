package com.xjie.app.feature.healthdata

import com.xjie.app.core.model.HealthDocument
import com.xjie.app.core.model.HealthReportFollowUp
import com.xjie.app.core.model.HealthReportInterpretation
import com.xjie.app.core.model.HealthReportProfileImpact
import com.xjie.app.core.model.HealthReportScoreSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTrustPresentationTest {
    @Test
    fun unconfirmedAndLegacyReportsNeverClaimAdmissionOrAiConsumption() {
        val documents = listOf(
            report(extraction = "pending", workflow = "recognizing"),
            report(extraction = "done", workflow = "awaiting_confirmation"),
            report(extraction = "done", workflow = "committing"),
            report(extraction = "done", workflow = null),
        )

        documents.forEach { document ->
            assertFalse(ReportTrustPresentation.isAdmitted(document))
            val nextStep = ReportTrustPresentation.nextStep(document)
            assertTrue(
                "pre-admission copy must explain confirmation or exclusion: $nextStep",
                nextStep.contains("确认") || nextStep.contains("不会进入"),
            )
        }
    }

    @Test
    fun completedScorePendingIsAdmittedButNeverClaimsScoreCompletion() {
        val document = report(extraction = "done", workflow = "completed_score_pending")
        val interpretation = HealthReportInterpretation(
            workflow_id = 7,
            subject_user_id = 99,
            status = "completed_score_pending",
            available = true,
            non_diagnostic_notice = "本解读不构成诊断。",
            follow_up = HealthReportFollowUp(
                available = false,
                unavailable_reason = "没有经过确认的随访数据。",
            ),
            profile_impacts = listOf(
                HealthReportProfileImpact(
                    profile_candidate_id = 301,
                    source_id = 501,
                    source_observation_id = 101,
                    fact_key = "metabolic.glucose_risk",
                    category = "metabolic",
                    review_status = "pending_review",
                ),
                HealthReportProfileImpact(
                    profile_candidate_id = 301,
                    source_id = 502,
                    source_observation_id = 102,
                    fact_key = "metabolic.glucose_risk",
                    category = "metabolic",
                    review_status = "pending_review",
                ),
            ),
            score_state = "partial_failed",
            score_pending = true,
            score_snapshots = listOf(
                HealthReportScoreSnapshot(
                    snapshot_id = 401,
                    score_kind = "stress",
                    algorithm_id = "stress-v1",
                    algorithm_version = "1.0",
                    before_value = 58.0,
                    after_value = 54.0,
                    before_confidence = 0.8,
                    after_confidence = 0.85,
                    score_direction = "lower_is_better",
                    calculation_status = "completed",
                ),
            ),
        )

        assertTrue(ReportTrustPresentation.isAdmitted(document))
        assertEquals("已确认入库，评分待更新", ReportTrustPresentation.title(document))
        assertTrue(ReportTrustPresentation.nextStep(document).contains("评分仍在更新"))
        assertEquals(
            "查看本次解读",
            ReportTrustPresentation.interpretationPrimaryAction("completed_score_pending"),
        )
        assertTrue(ReportTrustPresentation.scoreHeadline(interpretation).startsWith("评分仍待更新"))
        assertEquals(1, ReportTrustPresentation.profileImpactCount(interpretation))
        assertEquals(
            "80% → 85%",
            ReportTrustPresentation.scoreConfidenceLabel(interpretation.score_snapshots.single()),
        )
        assertEquals(
            "服务端定义：数值越低越好",
            ReportTrustPresentation.scoreDirectionLabel("lower_is_better"),
        )
        assertFalse(interpretation.follow_up.available)
    }

    @Test
    fun duplicateUploadReusesTheOriginalWorkflowWithoutClaimingFailureOrReadmission() {
        val duplicate = report(
            extraction = "done",
            workflow = "awaiting_confirmation",
            duplicate = true,
        )

        assertEquals(
            ReportTrustPresentation.Stage.AwaitingConfirmation,
            ReportTrustPresentation.stage(duplicate),
        )
        assertTrue(ReportTrustPresentation.nextStep(duplicate).contains("不会重复入库"))
        assertFalse(ReportTrustPresentation.nextStep(duplicate).contains("失败"))
    }

    @Test
    fun serverWorkflowStateTakesAuthorityOverLegacyExtractionDone() {
        val document = report(extraction = "done", workflow = "recognizing")

        assertEquals(
            ReportTrustPresentation.Stage.Recognizing,
            ReportTrustPresentation.stage(document),
        )
        assertFalse(ReportTrustPresentation.isAdmitted(document))
    }

    @Test
    fun uploadResponseDecodesOptionalTrustWorkflowWithoutBreakingLegacyFields() {
        val document = Json { ignoreUnknownKeys = true }.decodeFromString<HealthDocument>(
            """{
              "id":"42",
              "name":"体检报告.pdf",
              "doc_type":"exam",
              "extraction_status":"pending",
              "hospital":"协和医院",
              "doc_date":"2026-07-15",
              "created_at":"2026-07-15T08:00:00Z",
              "report_workflow_id":9,
              "report_workflow_status":"recognizing",
              "report_subject_user_id":12345678901,
              "report_duplicate":true
            }""",
        )

        assertEquals(9, document.report_workflow_id)
        assertEquals("协和医院", document.hospital)
        assertEquals("2026-07-15", document.doc_date)
        assertEquals("2026-07-15T08:00:00Z", document.created_at)
        assertEquals("2026-07-15 · 协和医院 · 报告", document.reportHistoryMetadata())
        assertEquals(12_345_678_901L, document.report_subject_user_id)
        assertTrue(document.report_duplicate)
        assertFalse(ReportTrustPresentation.isAdmitted(document))
    }

    private fun report(
        extraction: String?,
        workflow: String?,
        duplicate: Boolean = false,
    ) = HealthDocument(
        id = "1",
        extraction_status = extraction,
        report_workflow_id = workflow?.let { 7 },
        report_workflow_status = workflow,
        report_subject_user_id = workflow?.let { 99L },
        report_duplicate = duplicate,
    )
}
