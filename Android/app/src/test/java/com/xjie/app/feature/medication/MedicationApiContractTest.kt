package com.xjie.app.feature.medication

import com.xjie.app.core.model.MedicationDoseActionBody
import com.xjie.app.core.model.MedicationPlanConfirmBody
import com.xjie.app.core.model.MedicationReactionCreateBody
import com.xjie.app.core.model.MedicationRecognizePrefillBody
import com.xjie.app.core.network.api.MedicationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

class MedicationApiContractTest {
    @Test
    fun todayEndpointPinsLocalDateTimezoneAndOptionalServerSubject() {
        val method = MedicationApi::class.java.declaredMethods.single { it.name == "trustedToday" }

        assertEquals("api/medications/trust/today", method.getAnnotation(GET::class.java)!!.value)
        val queries = method.parameterAnnotations.flatten().filterIsInstance<Query>().map { it.value }
        assertEquals(
            setOf("subject_user_id", "local_date", "timezone_offset_minutes"),
            queries.toSet(),
        )
    }

    @Test
    fun trustedRevisionRejectionAndReactionEndpointsRemainVersionedPosts() {
        val expected = mapOf(
            "reviseTrustedPlan" to "api/medications/trust/plans/{planId}/revise",
            "updateTrustedPlanStatus" to "api/medications/trust/plans/{planId}/status",
            "rejectPrefill" to "api/medications/trust/prefill-candidates/{candidateId}/reject",
            "correctReaction" to "api/medications/trust/reactions/{reactionKey}/correct",
            "retractReaction" to "api/medications/trust/reactions/{reactionKey}/retract",
        )
        expected.forEach { (name, path) ->
            val method = MedicationApi::class.java.declaredMethods.single { it.name == name }
            assertEquals(path, method.getAnnotation(POST::class.java)!!.value)
        }
    }

    @Test
    fun doseEventPinsVersionsAndCannotClaimServerConfirmation() {
        val method = MedicationApi::class.java.declaredMethods.single { it.name == "recordDoseAction" }
        assertEquals("api/medications/trust/dose-events", method.getAnnotation(POST::class.java)!!.value)

        val encoded = Json.encodeToString(
            MedicationDoseActionBody(
                subject_user_id = 7,
                plan_id = 11,
                expected_plan_version = 3,
                client_event_id = "android-dose-stable",
                scheduled_local_date = "2026-07-15",
                scheduled_time = "20:00",
                expected_occurrence_version = 2,
                action = "taken",
            ),
        )
        assertTrue(encoded.contains("expected_plan_version"))
        assertTrue(encoded.contains("expected_occurrence_version"))
        assertTrue(encoded.contains("client_event_id"))
        assertFalse(encoded.contains("confirmed_at"))
        assertFalse(encoded.contains("status_assertion"))
    }

    @Test
    fun recognizeAcceptsRawTextOnlyAndCannotCreateAPlan() {
        val method = MedicationApi::class.java.declaredMethods.single { it.name == "recognizePrefill" }
        assertEquals("api/medications/recognize", method.getAnnotation(POST::class.java)!!.value)

        val encoded = Json.encodeToString(
            MedicationRecognizePrefillBody(
                raw_text = "药盒 OCR 文字",
                subject_user_id = 7,
                client_event_id = "android-ocr-stable",
            ),
        )
        assertTrue(encoded.contains("raw_text"))
        assertFalse(encoded.contains("image"))
        assertFalse(encoded.contains("photo"))
        assertFalse(encoded.contains("plan_created"))
        assertFalse(encoded.contains("confirmed"))
    }

    @Test
    fun planConfirmationPinsCandidateVersionButCannotSetTrustState() {
        val method = MedicationApi::class.java.declaredMethods.single { it.name == "confirmTrustedPlan" }
        assertEquals("api/medications/trust/plans/confirm", method.getAnnotation(POST::class.java)!!.value)

        val encoded = Json.encodeToString(
            MedicationPlanConfirmBody(
                subject_user_id = 7,
                client_request_id = "request-event",
                client_event_id = "event",
                candidate_id = 8,
                candidate_version = 2,
                generic_name = "药品",
                source_type = "ocr",
            ),
        )
        assertTrue(encoded.contains("candidate_version"))
        assertFalse(encoded.contains("trust_state"))
        assertFalse(encoded.contains("confirmed_at"))
        assertFalse(encoded.contains("reminder_default_enabled"))
    }

    @Test
    fun reactionRequestRecordsEvidenceButCannotAssertCausality() {
        val method = MedicationApi::class.java.declaredMethods.single { it.name == "createReaction" }
        assertEquals("api/medications/trust/reactions", method.getAnnotation(POST::class.java)!!.value)

        val encoded = Json.encodeToString(
            MedicationReactionCreateBody(
                subject_user_id = 7,
                client_event_id = "reaction-event",
                reaction_key = "reaction-key",
                plan_id = 11,
                symptoms = "头晕",
                onset_at = "2026-07-15T20:30:00+08:00",
                severity = "moderate",
            ),
        )
        assertTrue(encoded.contains("symptoms"))
        assertFalse(encoded.contains("causal_attribution"))
        assertFalse(encoded.contains("caused_by"))
    }
}
