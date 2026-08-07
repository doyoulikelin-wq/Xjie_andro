package com.xjie.app.feature.weight

import com.xjie.app.core.network.api.WeightApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Tag

class WeightApiContractTest {
    @Test
    fun everyWeightRequestPinsTheCapturedAccountOwnerAndCanonicalEndpoint() {
        val methods = WeightApi::class.java.declaredMethods.associateBy { it.name }
        val trends = requireNotNull(methods["trends"])
        val user = requireNotNull(methods["currentUser"])
        val manual = requireNotNull(methods["createManualIndicator"])

        assertEquals("api/health-data/indicators/trend", trends.getAnnotation(GET::class.java).value)
        assertEquals("api/users/me", user.getAnnotation(GET::class.java).value)
        assertEquals(
            "api/health-data/indicators/manual",
            manual.getAnnotation(POST::class.java).value,
        )
        listOf(trends, user, manual).forEach { method ->
            assertTrue(method.parameterAnnotations.flatten().any { it is Tag })
        }
        assertTrue(trends.parameterAnnotations.flatten().any { it is Query && it.value == "names" })
    }

    @Test
    fun manualWeightBodyCannotClaimAccountSubjectSourceOrConfirmation() {
        val fields = com.xjie.app.core.model.ManualIndicatorBody::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .map { it.name }
            .toSet()

        assertEquals(
            setOf("indicator_name", "value", "unit", "measured_at", "notes"),
            fields,
        )
        listOf("user_id", "subject_user_id", "source", "confirmed_at", "actor_user_id").forEach {
            assertFalse(it in fields)
        }
    }
}
