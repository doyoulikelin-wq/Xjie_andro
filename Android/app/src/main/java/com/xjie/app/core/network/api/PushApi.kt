package com.xjie.app.core.network.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Query

interface PushApi {
    @POST("api/push/device-token")
    suspend fun register(@Body body: RegisterDeviceTokenBody)

    @DELETE("api/push/device-token")
    suspend fun unregister(@Query("token") token: String)
}

@Serializable
data class RegisterDeviceTokenBody(
    val token: String,
    val platform: String = "android",
    val provider: String? = null,
    val extras: String? = null,
)
