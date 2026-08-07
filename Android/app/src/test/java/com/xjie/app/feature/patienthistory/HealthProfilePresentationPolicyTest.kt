package com.xjie.app.feature.patienthistory

import com.xjie.app.core.model.HealthProfileLongTermMedicationSummaryItem
import com.xjie.app.core.model.HealthProfileTrustCandidate
import com.xjie.app.core.model.HealthProfileTrustFact
import com.xjie.app.core.model.HealthProfileTrustSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthProfilePresentationPolicyTest {
    @Test
    fun editableCatalogExcludesWeightPlanProjectionMedicationAndLegacySingleGoalFact() {
        val keys = HealthProfileTrustPolicy.fields.map { it.factKey }.toSet()
        assertFalse("basic.weight" in keys)
        assertFalse("long_term_health.linked_plan" in keys)
        assertFalse("medication.long_term_summary" in keys)
        assertFalse("goal.primary" in keys)
        assertEquals("goal.primary", HealthProfileTrustPolicy.goalRequirement.factKey)
        assertEquals(7, HealthProfileTrustPolicy.fields.count { it.category == "safety" })
        assertTrue(HealthProfileTrustPolicy.fields.filter { it.category == "safety" }.all { it.safetyCritical })
    }

    @Test
    fun goalAndSafetyCandidatesCanOnlyBeRejectedAndNoCandidateCanAutoConfirm() {
        val ordinary = candidate("long_term_health", false)
        val goal = candidate("goal", false)
        val safety = candidate("safety", false)
        val critical = candidate("long_term_health", true)

        listOf(ordinary, goal, safety, critical).forEach {
            assertFalse(HealthProfileTrustPolicy.canAutoConfirm(it))
        }
        assertTrue(HealthProfileTrustPolicy.canReviewCandidate(ordinary, "accept"))
        assertFalse(HealthProfileTrustPolicy.canReviewCandidate(goal, "accept"))
        assertFalse(HealthProfileTrustPolicy.canReviewCandidate(safety, "accept"))
        assertFalse(HealthProfileTrustPolicy.canReviewCandidate(critical, "accept"))
        assertTrue(HealthProfileTrustPolicy.canReviewCandidate(goal, "reject"))
        assertTrue(HealthProfileTrustPolicy.canReviewCandidate(safety, "reject"))
    }

    @Test
    fun medicationSummaryRendererCannotContainDoseReminderOrTakenActions() {
        val item = HealthProfileLongTermMedicationSummaryItem(
            medication_name = "二甲双胍",
            purpose = "血糖管理",
            started_on = "2026-01-01",
            is_still_taking = true,
            source = "prescription",
            last_confirmed_at = "2026-07-20T08:00:00Z",
        )
        val fields = HealthProfileTrustPolicy.medicationDisplayFields(item)
        assertEquals(
            listOf("药名", "用途", "开始时间", "是否仍在服用", "来源", "最近确认"),
            fields.map { it.title },
        )
        val encoded = Json.encodeToString(item)
        listOf("dose", "dosage", "reminder", "taken", "adherence").forEach {
            assertFalse(encoded.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun multipleGoalInputMapsKnownMetricsRejectsUnknownDisplayTextAndPinsStatusTransitions() {
        val metrics = requireNotNull(HealthProfileTrustPolicy.goalMetricRequests("睡眠时长、HRV, glucose"))
        assertEquals(listOf("sleep_duration", "hrv", "glucose"), metrics.map { it.metric_key })
        assertNull(HealthProfileTrustPolicy.goalMetricRequests("改善一点"))
        assertTrue(HealthProfileTrustPolicy.isValidGoalDate("2026-08-07"))
        assertFalse(HealthProfileTrustPolicy.isValidGoalDate("2026-02-30"))
        assertTrue(
            HealthProfileTrustPolicy.allowsGoalAction(
                HealthProfileGoalAction.Pause,
                HealthProfileGoalStatus.Active,
            ),
        )
        assertFalse(
            HealthProfileTrustPolicy.allowsGoalAction(
                HealthProfileGoalAction.Resume,
                HealthProfileGoalStatus.Completed,
            ),
        )
    }

    @Test
    fun bmiRequiresConfirmedUnitBearingMeasurementsAndKeepsProvenance() {
        val source = HealthProfileTrustSource(
            source_id = 1,
            source_type = "device",
            source_ref = "apple-health:body-mass",
            created_at = "2026-07-15T08:00:00Z",
        )
        val height = fact(1, "basic.height", "170 cm", source)
        val weight = fact(2, "basic.weight", "65 kg", source)
        val bmi = HealthProfileTrustPolicy.derivedBmi(listOf(height, weight))
        assertEquals(22.49, requireNotNull(bmi.value), 0.01)
        assertTrue(bmi.sourceDescription.contains("Apple Health"))
        assertNull(
            HealthProfileTrustPolicy.derivedBmi(
                listOf(height, weight.copy(value_data = value("65"))),
            ).value,
        )
        assertNull(
            HealthProfileTrustPolicy.derivedBmi(
                listOf(height.copy(confirmation_method = "automatic"), weight),
            ).value,
        )
    }

    private fun candidate(category: String, critical: Boolean) = HealthProfileTrustCandidate(
        candidate_id = 3,
        fact_key = "$category.sample",
        category = category,
        proposed_value = value("候选"),
        is_safety_critical = critical,
        review_status = "pending_review",
        version = 1,
        created_at = "2026-07-15T08:00:00Z",
        updated_at = "2026-07-15T08:00:00Z",
    )

    private fun fact(id: Long, key: String, raw: String, source: HealthProfileTrustSource) =
        HealthProfileTrustFact(
            fact_id = id,
            fact_key = key,
            category = "basic",
            value_data = value(raw),
            is_safety_critical = false,
            confirmation_method = "user",
            version = 1,
            updated_at = "2026-07-15T08:00:00Z",
            sources = listOf(source),
        )

    private fun value(raw: String) = JsonObject(
        mapOf("response_state" to JsonPrimitive("value"), "value" to JsonPrimitive(raw)),
    )
}
