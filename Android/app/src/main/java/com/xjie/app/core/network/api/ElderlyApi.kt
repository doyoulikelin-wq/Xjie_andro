package com.xjie.app.core.network.api

import com.xjie.app.core.model.ElderlyCheckin
import com.xjie.app.core.model.ElderlyCheckinBody
import com.xjie.app.core.model.ElderlyCheckinList
import com.xjie.app.core.model.ElderlyTodayStatus
import com.xjie.app.core.model.SimpleOk
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ElderlyApi {
    @GET("api/elderly/today")
    suspend fun today(): ElderlyTodayStatus

    @POST("api/elderly/checkin")
    suspend fun create(@Body body: ElderlyCheckinBody): ElderlyCheckin

    @GET("api/elderly")
    suspend fun list(
        @Query("days") days: Int = 30,
        @Query("limit") limit: Int = 100,
    ): ElderlyCheckinList

    @DELETE("api/elderly/{id}")
    suspend fun delete(@Path("id") id: Long): SimpleOk
}
