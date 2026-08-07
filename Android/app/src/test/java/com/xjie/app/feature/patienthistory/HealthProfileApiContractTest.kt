package com.xjie.app.feature.patienthistory

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.HealthProfileGoalCreateBody
import com.xjie.app.core.model.HealthProfileGoalMetricBody
import com.xjie.app.core.network.api.HealthDataApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

class HealthProfileApiContractTest {
    @Test
    fun everyProfileRequestIsAccountTaggedAndReadKeepsServerCanonicalSubject() {
        val names = setOf(
            "healthProfileTrust",
            "healthProfileLongTermMedicationSummary",
            "healthProfileFactRevisions",
            "healthProfileGoalRevisions",
            "reviewHealthProfileCandidate",
            "upsertHealthProfileFact",
            "retractHealthProfileFact",
            "createHealthProfileGoal",
            "updateHealthProfileGoal",
            "updateHealthProfileGoalStatus",
        )
        val methods = HealthDataApi::class.java.declaredMethods.filter { it.name in names }
        assertEquals(names, methods.map { it.name }.toSet())
        methods.forEach { method ->
            assertTrue(
                "${method.name} must carry AccountScopeSnapshot @Tag",
                method.parameterAnnotations.indices.any { index ->
                    method.parameterAnnotations[index].any { it is Tag } &&
                        method.parameterTypes.getOrNull(index) == AuthManager.AccountScopeSnapshot::class.java
                },
            )
        }
        val read = methods.single { it.name == "healthProfileTrust" }
        assertEquals(
            "api/health-data/profile-trust",
            requireNotNull(read.getAnnotation(GET::class.java)).value,
        )
        assertFalse(read.parameterAnnotations.flatten().filterIsInstance<Query>().any { it.value == "subject_user_id" })
    }

    @Test
    fun medicationRevisionAndMultiGoalPathsMatchVersionedIosContract() {
        fun method(name: String) = HealthDataApi::class.java.declaredMethods.single { it.name == name }

        assertEquals(
            "api/medications/trust/long-term-summary",
            requireNotNull(
                method("healthProfileLongTermMedicationSummary").getAnnotation(GET::class.java),
            ).value,
        )
        assertEquals(
            "api/health-data/profile-trust/facts/{factId}/revisions",
            requireNotNull(method("healthProfileFactRevisions").getAnnotation(GET::class.java)).value,
        )
        assertEquals(
            "api/health-data/profile-trust/goals/{goalId}/revisions",
            requireNotNull(method("healthProfileGoalRevisions").getAnnotation(GET::class.java)).value,
        )
        listOf("healthProfileFactRevisions", "healthProfileGoalRevisions").forEach { name ->
            val annotations = method(name).parameterAnnotations.flatten()
            assertTrue(annotations.filterIsInstance<Query>().any { it.value == "subject_user_id" })
            assertTrue(annotations.filterIsInstance<Query>().any { it.value == "after_revision_id" })
            assertTrue(annotations.filterIsInstance<Path>().isNotEmpty())
        }
        assertEquals(
            "api/health-data/profile-trust/goals",
            requireNotNull(method("createHealthProfileGoal").getAnnotation(POST::class.java)).value,
        )
        assertEquals(
            "api/health-data/profile-trust/goals/{goalId}",
            requireNotNull(method("updateHealthProfileGoal").getAnnotation(PATCH::class.java)).value,
        )
        assertEquals(
            "api/health-data/profile-trust/goals/{goalId}/status",
            requireNotNull(method("updateHealthProfileGoalStatus").getAnnotation(POST::class.java)).value,
        )
    }

    @Test
    fun goalMutationCannotClaimServerConfirmationCompletenessOrActor() {
        val encoded = Json.encodeToString(
            HealthProfileGoalCreateBody(
                subject_user_id = 9,
                client_event_id = "android-profile-stable",
                name = "改善睡眠",
                started_on = "2026-08-07",
                metrics = listOf(HealthProfileGoalMetricBody("sleep_duration", "睡眠时长")),
            ),
        )
        listOf("actor", "confirmed_at", "completeness", "version").forEach {
            assertFalse(encoded.contains(it))
        }
        assertTrue(encoded.contains("client_event_id"))
        assertTrue(encoded.contains("metric_key"))
    }
}
