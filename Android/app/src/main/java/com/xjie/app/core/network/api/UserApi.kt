package com.xjie.app.core.network.api

import com.xjie.app.core.model.ConsentResponse
import com.xjie.app.core.model.UpdateConsentBody
import com.xjie.app.core.model.UpdateProfileBody
import com.xjie.app.core.model.UpdateSettingsBody
import com.xjie.app.core.model.UserInfo
import com.xjie.app.core.model.UserProfile
import com.xjie.app.core.model.UserSettings
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

interface UserApi {
    @GET("api/users/me")
    suspend fun me(): UserInfo

    @GET("api/users/settings")
    suspend fun settings(): UserSettings

    @PATCH("api/users/settings")
    suspend fun updateSettings(@Body body: UpdateSettingsBody): UserSettings

    @PATCH("api/users/consent")
    suspend fun updateConsent(@Body body: UpdateConsentBody): ConsentResponse

    @GET("api/users/profile")
    suspend fun getProfile(): UserProfile

    @PATCH("api/users/profile")
    suspend fun updateProfile(@Body body: UpdateProfileBody): UserProfile
}
