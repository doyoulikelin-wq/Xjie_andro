package com.xjie.app.core.network.api

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.AISummaryResponse
import com.xjie.app.core.model.DocumentListResponse
import com.xjie.app.core.model.HealthDataSummary
import com.xjie.app.core.model.HealthDocument
import com.xjie.app.core.model.HealthReportConfirmBody
import com.xjie.app.core.model.HealthReportInterpretation
import com.xjie.app.core.model.HealthReportManualCandidateBody
import com.xjie.app.core.model.HealthReportReview
import com.xjie.app.core.model.HealthProfileCandidateReviewBody
import com.xjie.app.core.model.HealthProfileFactRetractBody
import com.xjie.app.core.model.HealthProfileFactUpsertBody
import com.xjie.app.core.model.HealthProfileGoalCreateBody
import com.xjie.app.core.model.HealthProfileGoalStatusBody
import com.xjie.app.core.model.HealthProfileGoalUpdateBody
import com.xjie.app.core.model.HealthProfileLongTermMedicationSummary
import com.xjie.app.core.model.HealthProfileRevisionList
import com.xjie.app.core.model.HealthProfileTrustProfile
import com.xjie.app.core.model.IndicatorExplanation
import com.xjie.app.core.model.IndicatorListResponse
import com.xjie.app.core.model.IndicatorTrendResponse
import com.xjie.app.core.model.MedicalAssistantOverview
import com.xjie.app.core.model.PatientHistoryProfile
import com.xjie.app.core.model.PatientHistoryUpdateBody
import com.xjie.app.core.model.SummaryTaskResponse
import com.xjie.app.core.model.WatchedListResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Tag

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

    @GET("api/health-data/documents")
    suspend fun documentsForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("doc_type") docType: String? = null,
    ): DocumentListResponse

    @GET("api/health-data/documents/{id}")
    suspend fun document(@Path("id") id: String): HealthDocument

    @DELETE("api/health-data/documents/{id}")
    suspend fun deleteDocument(@Path("id") id: String)

    @GET("api/health-data/medical-assistant/overview")
    suspend fun medicalAssistantOverview(
        @Tag owner: AuthManager.AccountScopeSnapshot,
    ): MedicalAssistantOverview

    @POST("api/health-data/medical-assistant/overview/generate")
    suspend fun generateMedicalAssistantOverview(
        @Tag owner: AuthManager.AccountScopeSnapshot,
    ): MedicalAssistantOverview

    @GET("api/health-data/report-workflows/{workflowId}/review")
    suspend fun reportReview(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("workflowId") workflowId: Int,
        @Query("subject_user_id") subjectUserId: Long,
    ): HealthReportReview

    @GET("api/health-data/report-workflows/{workflowId}/interpretation")
    suspend fun reportInterpretation(
        @Path("workflowId") workflowId: Int,
        @Query("subject_user_id") subjectUserId: Long,
    ): HealthReportInterpretation

    @Streaming
    @GET("api/health-data/documents/{id}/file")
    suspend fun documentFile(@Path("id") id: String): ResponseBody

    @POST("api/health-data/report-workflows/{workflowId}/confirm")
    suspend fun confirmReport(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("workflowId") workflowId: Int,
        @Body body: HealthReportConfirmBody,
    ): HealthReportReview

    @POST("api/health-data/report-workflows/{workflowId}/manual-candidates")
    suspend fun addManualReportCandidate(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("workflowId") workflowId: Int,
        @Body body: HealthReportManualCandidateBody,
    ): HealthReportReview

    @GET("api/health-data/profile-trust")
    suspend fun healthProfileTrust(
        @Tag owner: AuthManager.AccountScopeSnapshot,
    ): HealthProfileTrustProfile

    @GET("api/medications/trust/long-term-summary")
    suspend fun healthProfileLongTermMedicationSummary(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("subject_user_id") subjectUserId: Long,
    ): HealthProfileLongTermMedicationSummary

    @GET("api/health-data/profile-trust/facts/{factId}/revisions")
    suspend fun healthProfileFactRevisions(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("factId") factId: Long,
        @Query("subject_user_id") subjectUserId: Long,
        @Query("limit") limit: Int = 50,
        @Query("after_revision_id") afterRevisionId: Long? = null,
    ): HealthProfileRevisionList

    @GET("api/health-data/profile-trust/goals/{goalId}/revisions")
    suspend fun healthProfileGoalRevisions(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("goalId") goalId: Long,
        @Query("subject_user_id") subjectUserId: Long,
        @Query("limit") limit: Int = 50,
        @Query("after_revision_id") afterRevisionId: Long? = null,
    ): HealthProfileRevisionList

    @POST("api/health-data/profile-trust/candidates/{candidateId}/review")
    suspend fun reviewHealthProfileCandidate(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("candidateId") candidateId: Long,
        @Body body: HealthProfileCandidateReviewBody,
    ): HealthProfileTrustProfile

    @POST("api/health-data/profile-trust/facts")
    suspend fun upsertHealthProfileFact(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: HealthProfileFactUpsertBody,
    ): HealthProfileTrustProfile

    @POST("api/health-data/profile-trust/facts/{factId}/retract")
    suspend fun retractHealthProfileFact(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("factId") factId: Long,
        @Body body: HealthProfileFactRetractBody,
    ): HealthProfileTrustProfile

    @POST("api/health-data/profile-trust/goals")
    suspend fun createHealthProfileGoal(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: HealthProfileGoalCreateBody,
    ): HealthProfileTrustProfile

    @PATCH("api/health-data/profile-trust/goals/{goalId}")
    suspend fun updateHealthProfileGoal(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("goalId") goalId: Long,
        @Body body: HealthProfileGoalUpdateBody,
    ): HealthProfileTrustProfile

    @POST("api/health-data/profile-trust/goals/{goalId}/status")
    suspend fun updateHealthProfileGoalStatus(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("goalId") goalId: Long,
        @Body body: HealthProfileGoalStatusBody,
    ): HealthProfileTrustProfile

    @GET("api/health-data/summary")
    suspend fun summary(): HealthDataSummary

    @GET("api/health-data/summary")
    suspend fun summaryForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
    ): HealthDataSummary

    @POST("api/health-data/summary/generate")
    suspend fun generateSummary(): AISummaryResponse

    @POST("api/health-data/summary/generate-async")
    suspend fun generateSummaryAsync(): SummaryTaskResponse

    @GET("api/health-data/summary/task/{taskId}")
    suspend fun summaryTaskStatus(@Path("taskId") taskId: String): SummaryTaskResponse

    @GET("api/health-data/indicators")
    suspend fun indicators(): IndicatorListResponse

    @GET("api/health-data/indicators")
    suspend fun indicatorsForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
    ): IndicatorListResponse

    @GET("api/health-data/indicators/watched")
    suspend fun watched(): WatchedListResponse

    @GET("api/health-data/indicators/watched")
    suspend fun watchedForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
    ): WatchedListResponse

    @GET("api/health-data/indicators/trend")
    suspend fun trend(@Query("names") names: String): IndicatorTrendResponse

    @GET("api/health-data/indicators/trend")
    suspend fun trendForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("names") names: String,
    ): IndicatorTrendResponse

    @POST("api/health-data/indicators/watch")
    suspend fun watch(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: WatchBody,
    )

    @DELETE("api/health-data/indicators/watch/{name}")
    suspend fun unwatch(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("name") name: String,
    )

    @GET("api/health-data/indicators/{name}/explain")
    suspend fun explain(@Path("name") name: String): IndicatorExplanation

    @GET("api/health-data/patient-history")
    suspend fun patientHistory(): PatientHistoryProfile

    @PUT("api/health-data/patient-history")
    suspend fun savePatientHistory(@Body body: PatientHistoryUpdateBody): PatientHistoryProfile
}

@kotlinx.serialization.Serializable
data class WatchBody(val indicator_name: String)
