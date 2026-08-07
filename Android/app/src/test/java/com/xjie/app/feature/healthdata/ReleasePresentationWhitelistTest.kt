package com.xjie.app.feature.healthdata

import com.xjie.app.core.model.HealthReportCandidate
import com.xjie.app.core.model.HealthReportConfirmationEvent
import com.xjie.app.core.model.HealthReportObservation
import com.xjie.app.core.model.HealthReportProfileImpact
import com.xjie.app.core.model.HealthReportScoreSnapshot
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePresentationWhitelistTest {
    @Test
    fun everyReportProjectionOmitsInternalIdsAlgorithmsAndRawDictionaries() {
        val observation = HealthReportReleasePresentation.observation(
            HealthReportObservation(
                observation_id = 101,
                source_candidate_id = 201,
                confirmation_event_id = 301,
                canonical_name = "observation_id=101",
                value_text = "trace_id=trace-secret",
                unit = "storage_key=private/object",
                reference_text = "failure_code=private_failure",
                abnormal_state = "abnormal",
                effective_at = "2026-08-01",
                confirmed_at = "2026-08-02",
            ),
        )
        val candidate = HealthReportReleasePresentation.candidate(
            HealthReportCandidate(
                candidate_id = 201,
                candidate_key = "candidate-secret",
                version = 2,
                canonical_name = "candidate_id=201",
                raw_name = "private_raw_name",
                raw_value = "storage_id=asset-secret",
                raw_unit = "object_key=private/object",
                normalized_text = "空腹血糖偏高",
                normalized_unit = "mmol/L",
                abnormal_state = "abnormal",
                review_status = "confirmed",
                requires_review = false,
            ),
        )
        val confirmation = HealthReportReleasePresentation.confirmation(
            HealthReportConfirmationEvent(
                event_id = 301,
                candidate_id = 201,
                event_type = "correct",
                candidate_version = 2,
                before_data = mapOf(
                    "trace_id" to JsonPrimitive("trace-secret"),
                    "value_text" to JsonPrimitive("failure_id=901"),
                ),
                after_data = mapOf(
                    "candidate_id" to JsonPrimitive(201),
                    "value_numeric" to JsonPrimitive(5.6),
                    "unit" to JsonPrimitive("mmol/L"),
                ),
                created_at = "2026-08-02T08:00:00Z",
            ),
        )
        val profile = HealthReportReleasePresentation.profileCandidate(
            listOf(
                HealthReportProfileImpact(
                    profile_candidate_id = 401,
                    source_id = 501,
                    source_observation_id = 101,
                    fact_key = "fact.secret",
                    category = "safety",
                    proposed_value = mapOf(
                        "canonical_name" to JsonPrimitive("fact_key=private.fact"),
                        "value_text" to JsonPrimitive("storage_key=private/object"),
                        "occurrence_count" to JsonPrimitive(1),
                        "raw_json" to JsonPrimitive("{\"trace_id\":\"secret\"}"),
                    ),
                    review_status = "pending_review",
                ),
            ),
        )
        val score = HealthReportReleasePresentation.score(
            HealthReportScoreSnapshot(
                snapshot_id = 601,
                score_kind = "stress",
                algorithm_id = "algorithm-secret",
                algorithm_version = "version-secret",
                before_value = 60.0,
                after_value = 55.0,
                score_direction = "future_internal_direction",
                semantic_outcome = "future_internal_outcome",
                calculation_status = "failed",
                evidence = mapOf(
                    "observation_ids" to JsonPrimitive("101,102"),
                    "trace_id" to JsonPrimitive("trace-secret"),
                ),
                missing_inputs = mapOf("fact_key" to JsonPrimitive("private.fact")),
                failure_code = "private_failure_code",
            ),
        )

        val releaseText = listOf(
            observation,
            candidate,
            confirmation,
            profile,
            score,
        ).joinToString(" | ")

        FORBIDDEN_RELEASE_FRAGMENTS.forEach { forbidden ->
            assertFalse("Release text leaked $forbidden: $releaseText", releaseText.contains(forbidden))
        }
        assertTrue(releaseText.contains("已确认报告指标"))
        assertTrue(releaseText.contains("空腹血糖偏高"))
        assertTrue(releaseText.contains("5.6 mmol/L"))
        assertTrue(releaseText.contains("健康安全信息候选"))
        assertTrue(releaseText.contains("已依据本次报告中已确认的数据进行计算"))
        assertTrue(releaseText.contains("本项评分暂未完成"))
    }

    @Test
    fun arbitraryServerTextAndUnknownEnumsFailClosedToStableUserCopy() {
        assertEquals(
            "本解读仅供健康管理参考，不构成诊断或治疗建议。",
            HealthReportReleasePresentation.notice("trace_id=private-trace"),
        )
        assertEquals(
            "安全提示",
            HealthReportReleasePresentation.unavailableReason("failure_code=private", "安全提示"),
        )
        assertEquals(
            listOf("三个月后复查"),
            HealthReportReleasePresentation.followUpItems(
                listOf("三个月后复查", "storage_key=private", "{\"raw\":true}"),
            ),
        )
        assertEquals(
            "报告处理未完成，请重试或联系客服。",
            HealthReportReleasePresentation.failureMessage("future_internal_failure"),
        )
    }

    @Test
    fun knownReviewSemanticsRemainReadableWithoutEchoingWireTokens() {
        val candidate = HealthReportReleasePresentation.candidate(
            HealthReportCandidate(
                candidate_id = 1,
                candidate_key = "key-1",
                version = 1,
                canonical_name = "糖化血红蛋白",
                raw_name = "HbA1c",
                raw_value = "6.1",
                raw_unit = "%",
                normalized_value = 6.1,
                normalized_unit = "%",
                abnormal_state = "normal",
                review_status = "future_wire_status",
                requires_review = true,
            ),
        )

        assertEquals("状态待刷新", candidate.status)
        assertFalse(candidate.toString().contains("future_wire_status"))
    }

    private companion object {
        val FORBIDDEN_RELEASE_FRAGMENTS = listOf(
            "101",
            "201",
            "301",
            "401",
            "501",
            "601",
            "algorithm-secret",
            "version-secret",
            "candidate-secret",
            "private_failure_code",
            "private/object",
            "private.fact",
            "trace-secret",
            "observation_id",
            "candidate_id",
            "event_id",
            "profile_candidate_id",
            "source_observation_id",
            "trace_id",
            "storage_id",
            "storage_key",
            "object_key",
            "failure_id",
            "failure_code",
            "algorithm_id",
            "algorithm_version",
            "fact_key",
            "proposed_value",
            "missing_inputs",
            "raw_json",
            "future_internal_direction",
            "future_internal_outcome",
        )
    }
}
