package com.xjie.app.core.network.api

import com.xjie.app.core.model.AISummaryResponse
import com.xjie.app.core.model.DocumentListResponse
import com.xjie.app.core.model.HealthDataSummary
import com.xjie.app.core.model.HealthDocument
import com.xjie.app.core.model.IndicatorExplanation
import com.xjie.app.core.model.IndicatorListResponse
import com.xjie.app.core.model.IndicatorTrendResponse
import com.xjie.app.core.model.PatientHistoryProfile
import com.xjie.app.core.model.PatientHistoryUpdateBody
import com.xjie.app.core.model.SummaryTaskResponse
import com.xjie.app.core.model.WatchedListResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface HealthDataApi {
    @Multipart
    @POST("api/health-data/upload")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part docType: MultipartBody.Part,
        @Part name: MultipartBody.Part,
    ): HealthDocument

    @GET("api/health-data/documents")
    suspend fun documents(@Query("doc_type") docType: String? = null): DocumentListResponse

    @GET("api/health-data/documents/{id}")
    suspend fun document(@Path("id") id: String): HealthDocument

    @DELETE("api/health-data/documents/{id}")
    suspend fun deleteDocument(@Path("id") id: String)

    @GET("api/health-data/summary")
    suspend fun summary(): HealthDataSummary

    @POST("api/health-data/summary/generate")
    suspend fun generateSummary(): AISummaryResponse

    @POST("api/health-data/summary/generate-async")
    suspend fun generateSummaryAsync(): SummaryTaskResponse

    @GET("api/health-data/summary/task/{taskId}")
    suspend fun summaryTaskStatus(@Path("taskId") taskId: String): SummaryTaskResponse

    @GET("api/health-data/indicators")
    suspend fun indicators(): IndicatorListResponse

    @GET("api/health-data/indicators/watched")
    suspend fun watched(): WatchedListResponse

    @GET("api/health-data/indicators/trend")
    suspend fun trend(@Query("names") names: String): IndicatorTrendResponse

    @POST("api/health-data/indicators/watch")
    suspend fun watch(@Body body: WatchBody)

    @DELETE("api/health-data/indicators/watch/{name}")
    suspend fun unwatch(@Path("name") name: String)

    @GET("api/health-data/indicators/{name}/explain")
    suspend fun explain(@Path("name") name: String): IndicatorExplanation

    @GET("api/health-data/patient-history")
    suspend fun patientHistory(): PatientHistoryProfile

    @PUT("api/health-data/patient-history")
    suspend fun savePatientHistory(@Body body: PatientHistoryUpdateBody): PatientHistoryProfile
}

@kotlinx.serialization.Serializable
data class WatchBody(val indicator_name: String)
