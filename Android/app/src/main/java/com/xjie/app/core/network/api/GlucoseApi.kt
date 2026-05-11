package com.xjie.app.core.network.api

import com.xjie.app.core.model.GlucosePoint
import retrofit2.http.GET
import retrofit2.http.Query

interface GlucoseApi {
    @GET("api/glucose")
    suspend fun list(
        @Query("hours") hours: Int? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): List<GlucosePoint>

    /** 后端 GlucoseRange */
    @GET("api/glucose/range")
    suspend fun range(): com.xjie.app.core.model.GlucoseRange
}
