package com.xjie.app.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Canonical public API URL policy.
 *
 * Every Retrofit declaration owns the `api/` route prefix. Historical Android configuration also
 * ended the base URL in `/api`, which produced `/api/api/...` and depended on an nginx compatibility
 * rewrite. Accept that legacy input, but always reduce it to the origin before resolving a route.
 */
object ApiEndpointPolicy {
    fun retrofitBaseUrl(configuredBaseUrl: String): String {
        val parsed = configuredBaseUrl.trim().toHttpUrl()
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
            "API base URL must not contain credentials"
        }
        require(parsed.query == null && parsed.fragment == null) {
            "API base URL must not contain a query or fragment"
        }

        val pathSegments = parsed.encodedPathSegments.filter { it.isNotEmpty() }
        require(pathSegments.all { it == "api" }) {
            "API base URL path must be empty or contain only legacy /api segments"
        }

        return parsed.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }

    fun endpoint(configuredBaseUrl: String, apiRoute: String): String {
        require(apiRoute.startsWith("api/") && !apiRoute.startsWith("/")) {
            "API route must be a relative api/ path"
        }
        return retrofitBaseUrl(configuredBaseUrl) + apiRoute
    }
}
