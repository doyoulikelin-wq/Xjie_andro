package com.xjie.app.feature.login

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.AuthResponse
import com.xjie.app.core.model.LoginPhoneBody
import com.xjie.app.core.model.LoginSubjectBody
import com.xjie.app.core.model.SubjectItem
import com.xjie.app.core.model.UpdateConsentBody
import com.xjie.app.core.network.api.AuthApi
import com.xjie.app.core.network.api.UserApi
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginRepository @Inject constructor(
    private val authApi: AuthApi,
    private val userApi: UserApi,
    private val authManager: AuthManager,
    private val json: Json,
) {
    suspend fun loadSubjects(): List<SubjectItem> =
        safeApiCall(json) { authApi.listSubjects() }

    suspend fun loginSubject(subjectId: String) {
        val res: AuthResponse = safeApiCall(json) {
            authApi.loginSubject(LoginSubjectBody(subjectId))
        }
        authManager.setAuth(res.access_token, res.refresh_token.orEmpty())
        authManager.setSubject(subjectId)
    }

    suspend fun loginOrSignupPhone(
        phone: String,
        username: String,
        password: String,
        signup: Boolean,
        sex: String? = null,
        age: Int? = null,
        heightCm: Double? = null,
        weightKg: Double? = null,
    ) {
        val body = LoginPhoneBody(
            phone = phone,
            username = if (signup) username else phone,
            password = password,
            sex = if (signup) sex else null,
            age = if (signup) age else null,
            height_cm = if (signup) heightCm else null,
            weight_kg = if (signup) weightKg else null,
        )
        val res: AuthResponse = safeApiCall(json) {
            if (signup) authApi.signup(body) else authApi.login(body)
        }
        authManager.setAuth(res.access_token, res.refresh_token.orEmpty())
        // 自动开启 AI 聊天授权（与 iOS 行为一致，失败忽略）
        runCatching {
            safeApiCall(json) {
                userApi.updateConsent(UpdateConsentBody(allow_ai_chat = true))
            }
        }
    }
}
