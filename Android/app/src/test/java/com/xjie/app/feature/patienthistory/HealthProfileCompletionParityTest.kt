package com.xjie.app.feature.patienthistory

import com.xjie.app.core.model.HealthProfileTrustProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Named regression for the server-authoritative iOS/Android completion contract. */
class HealthProfileCompletionParityTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun serverCompletenessPrimaryActionMultipleGoalsAndPlansAreDecodedWithoutClientRecomputation() {
        val profile = json.decodeFromString<HealthProfileTrustProfile>(
            """{
              "subject_user_id":9,
              "profile_status":"needs_attention",
              "overview":{
                "completeness_percent":73,
                "resolved_required_weight":11,
                "total_required_weight":15,
                "missing_required_fact_keys":["goal.primary"],
                "pending_update_count":1,
                "independent_source_count":4,
                "primary_action":{
                  "kind":"review_updates",
                  "item_count":7,
                  "localization_key":"health_profile.primary_action.review_updates",
                  "route":"profile_updates"
                }
              },
              "facts":[
                {"fact_id":1,"fact_key":"safety.medication_allergy","category":"safety","value_data":{"response_state":"none"},"is_safety_critical":true,"confirmation_method":"user","version":1,"updated_at":"2026-07-15T08:00:00Z"},
                {"fact_id":2,"fact_key":"basic.blood_type","category":"basic","value_data":{"response_state":"prefer_not_to_answer"},"is_safety_critical":false,"confirmation_method":"user","version":1,"updated_at":"2026-07-15T08:00:00Z"}
              ],
              "candidates":[{
                "candidate_id":3,"fact_key":"long_term_health.recent_findings","category":"long_term_health",
                "proposed_value":{"response_state":"value","value":"LDL 多次偏高"},"is_safety_critical":false,
                "review_status":"pending_review","version":1,"created_at":"2026-07-15T08:00:00Z","updated_at":"2026-07-15T08:00:00Z"
              }],
              "goals":[
                {"goal_id":10,"name":"改善睡眠","status":"active","started_on":"2026-07-01","version":2,"confirmed_at":"2026-07-20T08:00:00Z","metrics":[{"metric_key":"sleep_duration","display_label":"睡眠时长"}]},
                {"goal_id":11,"name":"稳定血糖","status":"paused","started_on":"2026-07-02","version":1,"confirmed_at":"2026-07-20T08:00:00Z","metrics":[{"metric_key":"glucose"}]}
              ],
              "management_plans":[
                {"plan_id":20,"title":"七天稳糖计划","goal":"稳定餐后血糖","start_date":"2026-07-15","end_date":"2026-07-21","status":"active","created_by":"questionnaire","updated_at":"2026-07-20T08:00:00Z","task_count":7,"completed_task_count":3},
                {"plan_id":21,"title":"睡眠节律计划","goal":null,"start_date":"2026-07-22","end_date":"2026-08-05","status":"active","created_by":"user","updated_at":"2026-07-22T08:00:00Z","task_count":14,"completed_task_count":1}
              ]
            }""",
        )

        assertEquals(73, profile.overview.completeness_percent)
        assertEquals(7, profile.overview.primary_action?.item_count)
        assertEquals(1, profile.candidates.size)
        assertEquals(1, profile.overview.missing_required_fact_keys.size)
        assertEquals("检查 7 项更新", HealthProfileTrustPolicy.primaryActionTitle(profile.overview.primary_action))
        assertTrue(HealthProfileTrustPolicy.isSupportedPrimaryAction(profile.overview.primary_action))
        assertEquals(2, profile.goals.size)
        assertEquals(2, profile.management_plans.size)
        assertEquals("glucose", profile.goals.last().metrics.single().metric_key)
    }

    @Test
    fun explicitNonValueAnswersAreAnsweredWhileMissingOrMalformedFactsStayUnanswered() {
        val profile = json.decodeFromString<HealthProfileTrustProfile>(
            """{
              "subject_user_id":9,
              "overview":{"completeness_percent":100,"resolved_required_weight":15,"total_required_weight":15,"missing_required_fact_keys":[],"pending_update_count":0,"independent_source_count":1},
              "facts":[
                {"fact_id":1,"fact_key":"safety.medication_allergy","category":"safety","value_data":{"response_state":"none"},"is_safety_critical":true,"confirmation_method":"user","version":1,"updated_at":"2026-07-15T08:00:00Z"},
                {"fact_id":2,"fact_key":"basic.blood_type","category":"basic","value_data":{"response_state":"not_applicable"},"is_safety_critical":false,"confirmation_method":"user","version":1,"updated_at":"2026-07-15T08:00:00Z"},
                {"fact_id":3,"fact_key":"basic.region","category":"basic","value_data":{"response_state":"prefer_not_to_answer"},"is_safety_critical":false,"confirmation_method":"user","version":1,"updated_at":"2026-07-15T08:00:00Z"},
                {"fact_id":4,"fact_key":"basic.lifestyle","category":"basic","value_data":{"value":"久坐"},"is_safety_critical":false,"confirmation_method":"user","version":1,"updated_at":"2026-07-15T08:00:00Z"}
              ],"candidates":[]
            }""",
        )

        assertEquals("明确没有", HealthProfileTrustPolicy.displayValue(profile.facts[0].value_data))
        assertEquals("不适用", HealthProfileTrustPolicy.displayValue(profile.facts[1].value_data))
        assertEquals("暂不回答", HealthProfileTrustPolicy.displayValue(profile.facts[2].value_data))
        assertTrue(profile.facts.take(3).all {
            HealthProfileTrustPolicy.answerState(it) == HealthProfileAnswerState.Answered
        })
        assertEquals(HealthProfileAnswerState.Unanswered, HealthProfileTrustPolicy.answerState(profile.facts[3]))
        assertEquals(HealthProfileAnswerState.Unanswered, HealthProfileTrustPolicy.answerState(null))
        assertFalse(HealthProfileTrustPolicy.isSupportedPrimaryAction(null))
    }
}
