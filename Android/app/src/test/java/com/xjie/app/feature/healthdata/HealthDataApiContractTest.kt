package com.xjie.app.feature.healthdata

import com.xjie.app.core.model.HealthReportConfirmBody
import com.xjie.app.core.model.HealthReportManualCandidateBody
import com.xjie.app.core.network.api.HealthDataApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Tag

class HealthDataApiContractTest {
    @Test
    fun reportReviewEndpointPinsSubjectQueryAndWorkflowPath() {
        val method = HealthDataApi::class.java.declaredMethods.single { it.name == "reportReview" }

        assertEquals(
            "api/health-data/report-workflows/{workflowId}/review",
            method.getAnnotation(GET::class.java).value,
        )
        assertTrue(method.parameterAnnotations.flatten().filterIsInstance<Path>().any {
            it.value == "workflowId"
        })
        assertTrue(method.parameterAnnotations.flatten().filterIsInstance<Query>().any {
            it.value == "subject_user_id"
        })

        val interpretation = HealthDataApi::class.java.declaredMethods.single {
            it.name == "reportInterpretation"
        }
        assertEquals(
            "api/health-data/report-workflows/{workflowId}/interpretation",
            interpretation.getAnnotation(GET::class.java).value,
        )
        assertTrue(interpretation.parameterAnnotations.flatten().filterIsInstance<Path>().any {
            it.value == "workflowId"
        })
        assertTrue(interpretation.parameterAnnotations.flatten().filterIsInstance<Query>().any {
            it.value == "subject_user_id"
        })

        val original = HealthDataApi::class.java.declaredMethods.single { it.name == "documentFile" }
        assertEquals(
            "api/health-data/documents/{id}/file",
            original.getAnnotation(GET::class.java).value,
        )
        assertTrue(original.isAnnotationPresent(Streaming::class.java))
    }

    @Test
    fun reportConfirmEndpointPinsWorkflowPathAndTypedBody() {
        val method = HealthDataApi::class.java.declaredMethods.single { it.name == "confirmReport" }

        assertEquals(
            "api/health-data/report-workflows/{workflowId}/confirm",
            method.getAnnotation(POST::class.java).value,
        )
        assertTrue(method.parameterAnnotations.flatten().filterIsInstance<Path>().any {
            it.value == "workflowId"
        })
    }

    @Test
    fun reportConfirmBodyCannotSetActorOrServerConfirmationTime() {
        val encoded = Json.encodeToString(
            HealthReportConfirmBody(
                subject_user_id = 99,
                client_event_id = "android-safe-event",
                workflow_version = 2,
            ),
        )

        assertFalse(encoded.contains("actor"))
        assertFalse(encoded.contains("confirmed_at"))
        assertFalse(encoded.contains("confirmed_by"))
        assertTrue(encoded.contains("client_event_id"))
    }

    @Test
    fun manualCandidateEndpointPinsWorkflowPathAndCannotClaimAdmission() {
        val method = HealthDataApi::class.java.declaredMethods.single {
            it.name == "addManualReportCandidate"
        }
        assertEquals(
            "api/health-data/report-workflows/{workflowId}/manual-candidates",
            method.getAnnotation(POST::class.java).value,
        )
        assertTrue(method.parameterAnnotations.flatten().filterIsInstance<Path>().any {
            it.value == "workflowId"
        })
        assertTrue(method.parameterAnnotations.flatten().any { it is Body })

        val encoded = Json.encodeToString(
            HealthReportManualCandidateBody(
                subject_user_id = 99,
                workflow_version = 3,
                client_event_id = "manual-safe-event",
                canonical_name = "hsCRP",
                raw_name = "超敏 C 反应蛋白",
                value_numeric = 8.2,
            ),
        )
        for (forbidden in listOf("admitted", "confirmed_at", "actor", "score")) {
            assertFalse(encoded.contains(forbidden))
        }
    }

    @Test
    fun activeReportAndIndicatorMutationsCarryImmutableOwnerTags() {
        val mutationNames = setOf(
            "confirmReport",
            "addManualReportCandidate",
            "watch",
            "unwatch",
        )
        val methods = HealthDataApi::class.java.declaredMethods
            .filter { it.name in mutationNames }

        assertEquals(mutationNames, methods.map { it.name }.toSet())
        assertTrue(methods.all { method ->
            method.parameterAnnotations.flatten().any { it is Tag }
        })
    }
}
