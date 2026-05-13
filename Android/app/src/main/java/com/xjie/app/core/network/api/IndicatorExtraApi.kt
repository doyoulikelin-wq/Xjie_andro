package com.xjie.app.core.network.api

import com.xjie.app.core.model.ExerciseBody
import com.xjie.app.core.model.ExerciseItem
import com.xjie.app.core.model.ExerciseListResponse
import com.xjie.app.core.model.IndicatorSearchResponse
import com.xjie.app.core.model.ManualIndicatorBody
import com.xjie.app.core.model.ManualIndicatorItem
import com.xjie.app.core.model.ManualIndicatorListResponse
import com.xjie.app.core.model.SimpleOk
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface IndicatorExtraApi {
    @GET("api/health-data/indicators/search")
    suspend fun search(
        @Query("q") q: String,
        @Query("limit") limit: Int = 20,
    ): IndicatorSearchResponse

    @GET("api/health-data/indicators/manual")
    suspend fun listManual(
        @Query("indicator_name") indicatorName: String? = null,
    ): ManualIndicatorListResponse

    @POST("api/health-data/indicators/manual")
    suspend fun createManual(@Body body: ManualIndicatorBody): ManualIndicatorItem

    @DELETE("api/health-data/indicators/manual/{id}")
    suspend fun deleteManual(@Path("id") id: Long): SimpleOk

    @POST("api/health-data/indicators/seed-common")
    suspend fun seedCommon(): SimpleOk
}

interface ExerciseApi {
    @GET("api/exercise")
    suspend fun list(
        @Query("date") date: String? = null,
        @Query("limit") limit: Int = 100,
    ): ExerciseListResponse

    @POST("api/exercise")
    suspend fun create(@Body body: ExerciseBody): ExerciseItem

    @DELETE("api/exercise/{id}")
    suspend fun delete(@Path("id") id: Long): SimpleOk
}
