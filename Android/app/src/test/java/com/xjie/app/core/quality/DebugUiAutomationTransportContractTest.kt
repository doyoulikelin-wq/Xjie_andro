package com.xjie.app.core.quality

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugUiAutomationTransportContractTest {
    @Test
    fun userSettingsRequiresExactAuthenticatedGetAndMatchesIosFixture() {
        DebugUiAutomationTransport.activate()
        val client = DebugUiAutomationTransport.installOn(OkHttpClient.Builder()).build()
        val valid = request(
            url = "https://www.jianjieaitech.com/api/users/settings",
            authorization = EXACT_AUTHORIZATION,
        )
        client.newCall(valid).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals(
                """{"intervention_level":"balanced","daily_reminder_limit":3,"glucose_unit":"mmol/L","elderly_mode":false,"elderly_checkin_interval_min":180}""",
                response.body?.string(),
            )
        }

        val malformed = listOf(
            request(
                "https://www.jianjieaitech.com/api/users/settings?unexpected=1",
                EXACT_AUTHORIZATION,
            ),
            request(
                "https://www.jianjieaitech.com/api/users/settings",
                EXACT_AUTHORIZATION,
                method = "POST",
            ),
            request("https://wrong.example/api/users/settings", EXACT_AUTHORIZATION),
            request("http://www.jianjieaitech.com/api/users/settings", EXACT_AUTHORIZATION),
            request("https://www.jianjieaitech.com/api/users/settings", null),
            request("https://www.jianjieaitech.com/api/users/settings", "Bearer wrong-token"),
        )
        malformed.forEach { candidate ->
            client.newCall(candidate).execute().use { response ->
                assertEquals(candidate.url.toString(), 418, response.code)
            }
        }

        val snapshot = DebugUiAutomationTransport.snapshot()
        assertEquals(malformed.size, snapshot.unknownRequests.size)
        assertTrue(snapshot.unknownRequests.all { it.endsWith("/api/users/settings") })
    }

    private fun request(
        url: String,
        authorization: String?,
        method: String = "GET",
    ): Request = Request.Builder()
        .url(url)
        .apply {
            if (authorization != null) header("Authorization", authorization)
            if (method == "POST") {
                post("{}".toRequestBody("application/json".toMediaType()))
            }
        }
        .build()

    private companion object {
        const val EXACT_AUTHORIZATION =
            "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiI3In0.debug"
    }
}
