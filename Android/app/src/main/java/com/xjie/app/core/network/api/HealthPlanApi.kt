package com.xjie.app.core.network.api

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.HealthPlanDetail
import com.xjie.app.core.model.HealthPlanFromChatRequest
import com.xjie.app.core.model.HealthPlanQuestionnaireRequest
import com.xjie.app.core.model.HealthPlanListResponse
import com.xjie.app.core.model.HealthTreeSummary
import com.xjie.app.core.model.PlanRevisionApplyRequest
import com.xjie.app.core.model.PlanRevisionGenerateRequest
import com.xjie.app.core.model.PlanRevisionProposal
import com.xjie.app.core.model.PlanTask
import com.xjie.app.core.model.PlanTaskUpdateRequest
import com.xjie.app.core.model.TubeCompleteRequest
import com.xjie.app.core.model.TubeCompleteResponse
import com.xjie.app.core.model.TubeWeek
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

interface HealthPlanApi {
    @GET("api/health-plans")
    suspend fun listPlans(@Query("status") status: String? = "active"): HealthPlanListResponse

    @GET("api/health-plans")
    suspend fun listPlansForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("status") status: String? = "active",
    ): HealthPlanListResponse

    @GET("api/health-plans/{id}")
    suspend fun planDetail(@Path("id") id: String): HealthPlanDetail

    @GET("api/health-plans/week")
    suspend fun week(@Query("week_start") weekStart: String): TubeWeek

    @GET("api/health-plans/tree-summary")
    suspend fun treeSummary(): HealthTreeSummary

    @POST("api/health-plans/from-chat")
    suspend fun createFromChat(@Body body: HealthPlanFromChatRequest): HealthPlanDetail

    @POST("api/health-plans/from-chat")
    suspend fun createFromChatForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: HealthPlanFromChatRequest,
    ): HealthPlanDetail

    @POST("api/health-plans/questionnaire")
    suspend fun createFromQuestionnaire(@Body body: HealthPlanQuestionnaireRequest): HealthPlanDetail

    @POST("api/health-plans/questionnaire")
    suspend fun createFromQuestionnaireForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: HealthPlanQuestionnaireRequest,
    ): HealthPlanDetail

    @POST("api/health-plans/tube/complete")
    suspend fun completeTubeTask(@Body body: TubeCompleteRequest): TubeCompleteResponse

    @PATCH("api/health-plans/tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body body: PlanTaskUpdateRequest): PlanTask

    @POST("api/health-plans/revision/generate")
    suspend fun generateRevision(@Body body: PlanRevisionGenerateRequest): PlanRevisionProposal

    @POST("api/health-plans/revision/{id}/apply")
    suspend fun applyRevision(@Path("id") id: String, @Body body: PlanRevisionApplyRequest): PlanRevisionProposal
}
