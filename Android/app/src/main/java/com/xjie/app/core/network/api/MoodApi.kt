package com.xjie.app.core.network.api

import com.xjie.app.core.model.MoodDay
import com.xjie.app.core.model.MoodGlucoseCorrelation
import com.xjie.app.core.model.MoodLogIn
import com.xjie.app.core.model.MoodLogOut
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface MoodApi {
    @GET("api/mood/days")
    suspend fun days(@Query("days") days: Int = 14): List<MoodDay>

    @GET("api/mood/correlation")
    suspend fun correlation(@Query("days") days: Int = 14): MoodGlucoseCorrelation

    @POST("api/mood/logs")
    suspend fun create(@Body body: MoodLogIn): MoodLogOut
}
