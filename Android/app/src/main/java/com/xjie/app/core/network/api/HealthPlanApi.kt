package com.xjie.app.core.network.api

import com.xjie.app.core.model.HealthPlanDetail
import com.xjie.app.core.model.HealthPlanFromChatRequest
import com.xjie.app.core.model.HealthPlanQuestionnaireRequest
import com.xjie.app.core.model.HealthPlanListResponse
import com.xjie.app.core.model.HealthTreeSummary
import com.xjie.app.core.model.TubeCompleteRequest
import com.xjie.app.core.model.TubeCompleteResponse
import com.xjie.app.core.model.TubeWeek
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HealthPlanApi {
    @GET("api/health-plans")
    suspend fun listPlans(@Query("status") status: String? = "active"): HealthPlanListResponse

    @GET("api/health-plans/{id}")
    suspend fun planDetail(@Path("id") id: String): HealthPlanDetail

    @GET("api/health-plans/week")
    suspend fun week(@Query("week_start") weekStart: String): TubeWeek

    @GET("api/health-plans/tree-summary")
    suspend fun treeSummary(): HealthTreeSummary

    @POST("api/health-plans/from-chat")
    suspend fun createFromChat(@Body body: HealthPlanFromChatRequest): HealthPlanDetail

    @POST("api/health-plans/questionnaire")
    suspend fun createFromQuestionnaire(@Body body: HealthPlanQuestionnaireRequest): HealthPlanDetail

    @POST("api/health-plans/tube/complete")
    suspend fun completeTubeTask(@Body body: TubeCompleteRequest): TubeCompleteResponse
}
