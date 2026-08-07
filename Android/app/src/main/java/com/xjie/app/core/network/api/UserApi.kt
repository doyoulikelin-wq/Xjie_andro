package com.xjie.app.core.network.api

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.ConsentResponse
import com.xjie.app.core.model.FeedbackCreate
import com.xjie.app.core.model.FeedbackOut
import com.xjie.app.core.model.OnboardingNeedsRequest
import com.xjie.app.core.model.UpdateConsentBody
import com.xjie.app.core.model.UpdateProfileBody
import com.xjie.app.core.model.UpdateSettingsBody
import com.xjie.app.core.model.UserInfo
import com.xjie.app.core.model.UserProfile
import com.xjie.app.core.model.UserSettings
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Tag

interface UserApi {
    @GET("api/users/me")
    suspend fun me(): UserInfo

    @GET("api/users/me")
    suspend fun meForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
    ): UserInfo

    @GET("api/users/settings")
    suspend fun settings(): UserSettings

    @GET("api/users/settings")
    suspend fun settingsForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
    ): UserSettings

    @PATCH("api/users/settings")
    suspend fun updateSettings(@Body body: UpdateSettingsBody): UserSettings

    @PATCH("api/users/settings")
    suspend fun updateSettingsForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: UpdateSettingsBody,
    ): UserSettings

    @PATCH("api/users/consent")
    suspend fun updateConsent(@Body body: UpdateConsentBody): ConsentResponse

    @PATCH("api/users/consent")
    suspend fun updateConsentForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: UpdateConsentBody,
    ): ConsentResponse

    @GET("api/users/profile")
    suspend fun getProfile(): UserProfile

    @PATCH("api/users/profile")
    suspend fun updateProfile(@Body body: UpdateProfileBody): UserProfile

    @PATCH("api/users/profile")
    suspend fun updateProfileForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: UpdateProfileBody,
    ): UserProfile

    @PUT("api/users/onboarding")
    suspend fun updateOnboarding(@Body body: OnboardingNeedsRequest): UserSettings

    @PUT("api/users/onboarding")
    suspend fun updateOnboardingForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: OnboardingNeedsRequest,
    ): UserSettings

    @POST("api/feedback")
    suspend fun submitFeedback(@Body body: FeedbackCreate): FeedbackOut

    @POST("api/feedback")
    suspend fun submitFeedbackForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: FeedbackCreate,
    ): FeedbackOut

    @DELETE("api/users/me")
    suspend fun deleteMe()

    @DELETE("api/users/me")
    suspend fun deleteMeForOwner(
        @Tag owner: AuthManager.AccountScopeSnapshot,
    )
}
