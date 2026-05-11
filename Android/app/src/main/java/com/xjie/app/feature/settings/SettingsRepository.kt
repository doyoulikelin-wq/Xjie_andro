package com.xjie.app.feature.settings

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.GlucoseUnit
import com.xjie.app.core.model.UpdateConsentBody
import com.xjie.app.core.model.UpdateProfileBody
import com.xjie.app.core.model.UpdateSettingsBody
import com.xjie.app.core.model.UserInfo
import com.xjie.app.core.model.UserProfile
import com.xjie.app.core.model.UserSettings
import com.xjie.app.core.network.api.AuthApi
import com.xjie.app.core.network.api.UserApi
import com.xjie.app.core.network.safeApiCall
import com.xjie.app.core.storage.PreferencesStore
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val userApi: UserApi,
    private val authApi: AuthApi,
    private val authManager: AuthManager,
    private val prefs: PreferencesStore,
    private val json: Json,
) {
    suspend fun me(): UserInfo? = runCatching {
        safeApiCall(json) { userApi.me() }
    }.getOrNull()

    suspend fun settings(): UserSettings? = runCatching {
        safeApiCall(json) { userApi.settings() }
    }.getOrNull()

    suspend fun updateLevel(level: String): UserSettings = safeApiCall(json) {
        userApi.updateSettings(UpdateSettingsBody(intervention_level = level))
    }

    suspend fun updateGlucoseUnit(unit: GlucoseUnit): UserSettings {
        prefs.setGlucoseUnit(unit)
        return safeApiCall(json) {
            userApi.updateSettings(UpdateSettingsBody(glucose_unit = unit.raw))
        }
    }

    suspend fun toggleAiChat(current: Boolean) = safeApiCall(json) {
        userApi.updateConsent(UpdateConsentBody(allow_ai_chat = !current))
    }

    suspend fun toggleDataUpload(current: Boolean) = safeApiCall(json) {
        userApi.updateConsent(UpdateConsentBody(allow_data_upload = !current))
    }

    suspend fun updateProfile(
        sex: String?,
        age: Int?,
        heightCm: Double?,
        weightKg: Double?,
    ): UserProfile = safeApiCall(json) {
        userApi.updateProfile(
            UpdateProfileBody(
                sex = sex,
                age = age,
                height_cm = heightCm,
                weight_kg = weightKg,
            )
        )
    }

    suspend fun setOmicsDemo(enabled: Boolean) = prefs.setOmicsDemoEnabled(enabled)

    suspend fun logout() {
        runCatching { safeApiCall(json) { authApi.logout() } }
        authManager.logout()
    }
}
