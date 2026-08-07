package com.xjie.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiEndpointPolicyTest {
    @Test
    fun `legacy api base never duplicates the route prefix`() {
        assertEquals(
            "https://www.jianjieaitech.com/api/users/me",
            ApiEndpointPolicy.endpoint(
                "https://www.jianjieaitech.com/api",
                "api/users/me",
            ),
        )
    }

    @Test
    fun `origin and repeated legacy api segments share one canonical base`() {
        assertEquals(
            "https://www.jianjieaitech.com/",
            ApiEndpointPolicy.retrofitBaseUrl("https://www.jianjieaitech.com"),
        )
        assertEquals(
            "https://www.jianjieaitech.com/",
            ApiEndpointPolicy.retrofitBaseUrl("https://www.jianjieaitech.com/api/api/"),
        )
    }

    @Test
    fun `non api base paths fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiEndpointPolicy.retrofitBaseUrl("https://www.jianjieaitech.com/proxy")
        }
    }

    @Test
    fun `absolute or non api routes fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiEndpointPolicy.endpoint("https://www.jianjieaitech.com", "/api/users/me")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApiEndpointPolicy.endpoint("https://www.jianjieaitech.com", "users/me")
        }
    }
}
