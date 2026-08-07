package com.xjie.app.feature.healthconnect

import com.xjie.app.core.model.DeviceIndicatorSyncBody
import com.xjie.app.core.model.DeviceIndicatorSyncValue
import com.xjie.app.core.network.api.DeviceIndicatorSyncApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Header
import retrofit2.http.POST

class DeviceIndicatorSyncApiContractTest {
    @Test
    fun endpointUsesCapturedAuthorizationAndDeviceSource() {
        val method = DeviceIndicatorSyncApi::class.java.declaredMethods.single { it.name == "sync" }
        val post = requireNotNull(method.getAnnotation(POST::class.java))
        val headers = method.parameterAnnotations.flatten().filterIsInstance<Header>()
        val body = DeviceIndicatorSyncBody(values = listOf(value()))

        assertEquals("api/health-data/indicators/device-sync", post.value)
        assertEquals(listOf("Authorization"), headers.map { it.value })
        assertEquals("device", body.source)
    }

    @Test
    fun payloadSerializesStableIdentityTimeAndNoAccountSelector() {
        val encoded = Json { encodeDefaults = true }
            .encodeToString(DeviceIndicatorSyncBody(values = listOf(value())))

        assertTrue(encoded.contains("\"source\":\"device\""))
        assertTrue(encoded.contains("\"source_id\":\"steps-record\""))
        assertTrue(encoded.contains("\"source_local_date\":\"2026-07-14\""))
        assertTrue(encoded.contains("\"timezone_offset_minutes\":480"))
        assertFalse(encoded.contains("user_id"))
        assertFalse(encoded.contains("subject_user_id"))
        assertFalse(encoded.contains("access_token"))
    }

    private fun value() = DeviceIndicatorSyncValue(
        indicator_name = "步数",
        value = 1000.0,
        unit = "步",
        measured_at = "2026-07-14T20:00:00+08:00",
        source_metric = "steps",
        source_id = "steps-record",
        source_local_date = "2026-07-14",
        timezone_offset_minutes = 480,
    )
}
