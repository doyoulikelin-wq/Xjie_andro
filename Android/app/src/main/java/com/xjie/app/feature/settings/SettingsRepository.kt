package com.xjie.app.feature.settings

import com.xjie.app.BuildConfig
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.FeedbackCreate
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun captureOwner(): AuthManager.AccountScopeSnapshot? = authManager.captureAccountScope()

    fun isCurrent(owner: AuthManager.AccountScopeSnapshot): Boolean = authManager.isCurrent(owner)

    suspend fun me(owner: AuthManager.AccountScopeSnapshot): UserInfo? {
        requireCurrent(owner)
        val result = runCatching { safeApiCall(json) { userApi.meForOwner(owner) } }
        requireCurrent(owner)
        return result.getOrNull()
    }

    suspend fun settings(owner: AuthManager.AccountScopeSnapshot): UserSettings? {
        requireCurrent(owner)
        val result = runCatching { safeApiCall(json) { userApi.settingsForOwner(owner) } }
        requireCurrent(owner)
        return result.getOrNull()
    }

    suspend fun updateLevel(
        owner: AuthManager.AccountScopeSnapshot,
        level: String,
    ): UserSettings = accountMutation(owner) {
        userApi.updateSettingsForOwner(owner, UpdateSettingsBody(intervention_level = level))
    }

    suspend fun updateGlucoseUnit(
        owner: AuthManager.AccountScopeSnapshot,
        unit: GlucoseUnit,
    ): UserSettings {
        val updated = accountMutation(owner) {
            userApi.updateSettingsForOwner(owner, UpdateSettingsBody(glucose_unit = unit.raw))
        }
        requireCurrent(owner)
        prefs.setGlucoseUnit(unit)
        requireCurrent(owner)
        return updated
    }

    suspend fun updateElderlyMode(
        owner: AuthManager.AccountScopeSnapshot,
        enabled: Boolean,
    ): UserSettings = accountMutation(owner) {
        userApi.updateSettingsForOwner(owner, UpdateSettingsBody(elderly_mode = enabled))
    }

    suspend fun updateElderlyInterval(
        owner: AuthManager.AccountScopeSnapshot,
        min: Int,
    ): UserSettings = accountMutation(owner) {
        userApi.updateSettingsForOwner(owner, UpdateSettingsBody(elderly_checkin_interval_min = min))
    }

    suspend fun toggleAiChat(
        owner: AuthManager.AccountScopeSnapshot,
        current: Boolean,
    ) = accountMutation(owner) {
        userApi.updateConsentForOwner(owner, UpdateConsentBody(allow_ai_chat = !current))
    }

    suspend fun toggleDataUpload(
        owner: AuthManager.AccountScopeSnapshot,
        current: Boolean,
    ) = accountMutation(owner) {
        userApi.updateConsentForOwner(owner, UpdateConsentBody(allow_data_upload = !current))
    }

    suspend fun updateProfile(
        owner: AuthManager.AccountScopeSnapshot,
        sex: String?,
        age: Int?,
        heightCm: Double?,
        weightKg: Double?,
        displayName: String? = null,
    ): UserProfile = accountMutation(owner) {
        userApi.updateProfileForOwner(
            owner,
            UpdateProfileBody(
                sex = sex,
                age = age,
                height_cm = heightCm,
                weight_kg = weightKg,
                display_name = displayName,
            )
        )
    }

    suspend fun setOmicsDemo(enabled: Boolean) = prefs.setOmicsDemoEnabled(enabled)

    suspend fun submitFeedback(
        owner: AuthManager.AccountScopeSnapshot,
        category: String,
        content: String,
        contact: String?,
    ) = accountMutation(owner) {
        userApi.submitFeedbackForOwner(
            owner,
            FeedbackCreate(
                category = category,
                content = content,
                contact = contact,
                app_platform = "android",
                app_version = BuildConfig.VERSION_NAME,
            )
        )
    }

    suspend fun deleteAccount(owner: AuthManager.AccountScopeSnapshot): Boolean {
        requireCurrent(owner)
        safeApiCall(json) { userApi.deleteMeForOwner(owner) }
        return authManager.logoutIfCurrent(owner)
    }

    /** Local logout is synchronous; server revocation cannot later clear a replacement session. */
    fun clearLocalSessionAndCaptureToken(): String? {
        val owner = authManager.captureAccountScope() ?: run {
            authManager.logout()
            return null
        }
        val token = authManager.accessTokenIfCurrent(owner)
        authManager.logoutIfCurrent(owner)
        return token
    }

    fun logout() {
        val token = clearLocalSessionAndCaptureToken()
        lifecycleScope.launch { revokeCapturedLogoutToken(token) }
    }

    suspend fun revokeCapturedLogoutToken(token: String?) {
        if (token.isNullOrBlank()) return
        runCatching {
            safeApiCall(json) { authApi.logoutWithCapturedToken("Bearer $token") }
        }
    }

    private suspend inline fun <T> accountMutation(
        owner: AuthManager.AccountScopeSnapshot,
        crossinline request: suspend () -> T,
    ): T {
        requireCurrent(owner)
        val result = safeApiCall(json) { request() }
        requireCurrent(owner)
        return result
    }

    private fun requireCurrent(owner: AuthManager.AccountScopeSnapshot) {
        check(authManager.isCurrent(owner)) {
            "账号或健康主体已变化，本次设置操作已取消。"
        }
    }
}
