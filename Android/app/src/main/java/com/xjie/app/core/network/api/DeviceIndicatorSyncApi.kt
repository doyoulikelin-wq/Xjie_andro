package com.xjie.app.core.network.api

import com.xjie.app.core.model.DeviceIndicatorSyncBody
import com.xjie.app.core.model.DeviceIndicatorSyncResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Account-bound endpoint. The caller supplies a captured bearer token and this API is created with
 * a client that has no shared auth interceptor or authenticator, so an account switch cannot
 * redirect an in-flight Health Connect payload.
 */
interface DeviceIndicatorSyncApi {
    @POST("api/health-data/indicators/device-sync")
    suspend fun sync(
        @Header("Authorization") authorization: String,
        @Body body: DeviceIndicatorSyncBody,
    ): DeviceIndicatorSyncResponse
}
