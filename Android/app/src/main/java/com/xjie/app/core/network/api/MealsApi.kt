package com.xjie.app.core.network.api

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.MealItem
import com.xjie.app.core.model.DietaryDailySummaryStatus
import com.xjie.app.core.model.DietaryDashboardResponse
import com.xjie.app.core.model.DietaryDayCompleteBody
import com.xjie.app.core.model.DietaryDayCompletionResponse
import com.xjie.app.core.model.DietaryDraftConfirmBody
import com.xjie.app.core.model.DietaryDraftCreateBody
import com.xjie.app.core.model.DietaryDraftRetryBody
import com.xjie.app.core.model.DietaryMealDraft
import com.xjie.app.core.model.DietaryMealRecord
import com.xjie.app.core.model.DietaryMutationBody
import com.xjie.app.core.model.DietaryRecentResponse
import com.xjie.app.core.model.DietaryRecordReuseBody
import com.xjie.app.core.model.DietaryRecordUpdateBody
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag
import retrofit2.http.PUT

interface MealsApi {
    // Legacy endpoints remain read-compatible only. New Android writes use dietary-records below.
    @GET("api/meals")
    suspend fun list(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): List<MealItem>

    @GET("api/dietary-records/dashboard")
    suspend fun dietaryDashboard(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("diet_date") dietDate: String,
        @Query("timezone") timezone: String = "Asia/Shanghai",
        @Query("subject_user_id") subjectUserId: Long? = null,
    ): DietaryDashboardResponse

    @GET("api/dietary-records/daily-summary")
    suspend fun dietaryDailySummary(
        @Tag owner: AuthManager.AccountScopeSnapshot,
    ): DietaryDailySummaryStatus

    @POST("api/dietary-records/drafts")
    suspend fun createDietaryDraft(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: DietaryDraftCreateBody,
    ): DietaryMealDraft

    @Multipart
    @PUT("api/dietary-records/drafts/photo")
    suspend fun createDietaryPhotoDraft(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Part file: MultipartBody.Part,
        @Part("client_event_id") clientEventId: RequestBody,
        @Part("diet_date") dietDate: RequestBody,
        @Part("meal_type") mealType: RequestBody,
        @Part("eaten_at") eatenAt: RequestBody,
        @Part("source") source: RequestBody,
        @Part("timezone") timezone: RequestBody,
        @Part("subject_user_id") subjectUserId: RequestBody? = null,
    ): DietaryMealDraft

    @POST("api/dietary-records/drafts/{draftId}/retry-recognition")
    suspend fun retryDietaryRecognition(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("draftId") draftId: Long,
        @Body body: DietaryDraftRetryBody,
    ): DietaryMealDraft

    @POST("api/dietary-records/drafts/{draftId}/confirm")
    suspend fun confirmDietaryDraft(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("draftId") draftId: Long,
        @Body body: DietaryDraftConfirmBody,
    ): DietaryMealRecord

    @PATCH("api/dietary-records/records/{recordId}")
    suspend fun updateDietaryRecord(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("recordId") recordId: Long,
        @Body body: DietaryRecordUpdateBody,
    ): DietaryMealRecord

    @HTTP(method = "DELETE", path = "api/dietary-records/records/{recordId}", hasBody = true)
    suspend fun deleteDietaryRecord(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("recordId") recordId: Long,
        @Body body: DietaryMutationBody,
    ): DietaryMealRecord

    @POST("api/dietary-records/records/{recordId}/reuse")
    suspend fun reuseDietaryRecord(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("recordId") recordId: Long,
        @Body body: DietaryRecordReuseBody,
    ): DietaryMealDraft

    @GET("api/dietary-records/recent")
    suspend fun recentDietaryRecords(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("limit") limit: Int = 12,
        @Query("subject_user_id") subjectUserId: Long? = null,
    ): DietaryRecentResponse

    @POST("api/dietary-records/days/{dietDate}/complete")
    suspend fun completeDietaryDay(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("dietDate") dietDate: String,
        @Body body: DietaryDayCompleteBody,
    ): DietaryDayCompletionResponse
}
