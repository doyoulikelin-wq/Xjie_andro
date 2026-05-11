package com.xjie.app.core.network.api

import com.xjie.app.core.model.FeatureFlagClientResponse
import retrofit2.http.GET

interface FeatureFlagsApi {
    @GET("api/feature-flags")
    suspend fun list(): FeatureFlagClientResponse
}
