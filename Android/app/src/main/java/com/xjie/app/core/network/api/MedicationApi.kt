package com.xjie.app.core.network.api

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.Medication
import com.xjie.app.core.model.MedicationBody
import com.xjie.app.core.model.MedicationList
import com.xjie.app.core.model.MedicationRecognizeBody
import com.xjie.app.core.model.MedicationRecognizeResult
import com.xjie.app.core.model.MedicationDoseActionBody
import com.xjie.app.core.model.MedicationDoseEvent
import com.xjie.app.core.model.MedicationPlanConfirmBody
import com.xjie.app.core.model.MedicationPlanReviseBody
import com.xjie.app.core.model.MedicationPlanStatusBody
import com.xjie.app.core.model.MedicationPrefillCandidate
import com.xjie.app.core.model.MedicationPrefillList
import com.xjie.app.core.model.MedicationPrefillRejectBody
import com.xjie.app.core.model.MedicationReaction
import com.xjie.app.core.model.MedicationReactionCorrectBody
import com.xjie.app.core.model.MedicationReactionCreateBody
import com.xjie.app.core.model.MedicationReactionList
import com.xjie.app.core.model.MedicationReactionRetractBody
import com.xjie.app.core.model.MedicationRecognizePrefillBody
import com.xjie.app.core.model.MedicationRecognizePrefillResult
import com.xjie.app.core.model.MedicationTodaySummary
import com.xjie.app.core.model.TrustedMedicationPlan
import com.xjie.app.core.model.TrustedMedicationPlanList
import com.xjie.app.core.model.SimpleOk
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

interface MedicationApi {
    @GET("api/medications")
    suspend fun list(@Tag owner: AuthManager.AccountScopeSnapshot): MedicationList

    @POST("api/medications")
    suspend fun create(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: MedicationBody,
    ): Medication

    @PATCH("api/medications/{id}")
    suspend fun update(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("id") id: Long,
        @Body body: MedicationBody,
    ): Medication

    @DELETE("api/medications/{id}")
    suspend fun delete(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("id") id: Long,
    ): SimpleOk

    @POST("api/medications/recognize")
    suspend fun recognize(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: MedicationRecognizeBody,
    ): MedicationRecognizeResult

    @GET("api/medications/trust/today")
    suspend fun trustedToday(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("subject_user_id") subjectUserId: Long? = null,
        @Query("local_date") localDate: String,
        @Query("timezone_offset_minutes") timezoneOffsetMinutes: Int,
    ): MedicationTodaySummary

    @GET("api/medications/trust/plans")
    suspend fun trustedPlans(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("subject_user_id") subjectUserId: Long? = null,
    ): TrustedMedicationPlanList

    @GET("api/medications/trust/prefill-candidates")
    suspend fun trustedPrefills(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("subject_user_id") subjectUserId: Long? = null,
    ): MedicationPrefillList

    @GET("api/medications/trust/reactions")
    suspend fun trustedReactions(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("subject_user_id") subjectUserId: Long? = null,
    ): MedicationReactionList

    @POST("api/medications/recognize")
    suspend fun recognizePrefill(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: MedicationRecognizePrefillBody,
    ): MedicationRecognizePrefillResult

    @POST("api/medications/trust/plans/confirm")
    suspend fun confirmTrustedPlan(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: MedicationPlanConfirmBody,
    ): TrustedMedicationPlan

    @POST("api/medications/trust/plans/{planId}/revise")
    suspend fun reviseTrustedPlan(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("planId") planId: Long,
        @Body body: MedicationPlanReviseBody,
    ): TrustedMedicationPlan

    @POST("api/medications/trust/plans/{planId}/status")
    suspend fun updateTrustedPlanStatus(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("planId") planId: Long,
        @Body body: MedicationPlanStatusBody,
    ): TrustedMedicationPlan

    @POST("api/medications/trust/prefill-candidates/{candidateId}/reject")
    suspend fun rejectPrefill(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("candidateId") candidateId: Long,
        @Body body: MedicationPrefillRejectBody,
    ): MedicationPrefillCandidate

    @POST("api/medications/trust/dose-events")
    suspend fun recordDoseAction(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: MedicationDoseActionBody,
    ): MedicationDoseEvent

    @POST("api/medications/trust/reactions")
    suspend fun createReaction(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: MedicationReactionCreateBody,
    ): MedicationReaction

    @POST("api/medications/trust/reactions/{reactionKey}/correct")
    suspend fun correctReaction(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("reactionKey") reactionKey: String,
        @Body body: MedicationReactionCorrectBody,
    ): MedicationReaction

    @POST("api/medications/trust/reactions/{reactionKey}/retract")
    suspend fun retractReaction(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("reactionKey") reactionKey: String,
        @Body body: MedicationReactionRetractBody,
    ): MedicationReaction
}
