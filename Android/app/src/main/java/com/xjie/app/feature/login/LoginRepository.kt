package com.xjie.app.feature.login

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.AuthResponse
import com.xjie.app.core.model.LoginPhoneBody
import com.xjie.app.core.model.LoginSubjectBody
import com.xjie.app.core.model.SubjectItem
import com.xjie.app.core.network.api.AuthApi
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginRepository @Inject constructor(
    private val authApi: AuthApi,
    private val authManager: AuthManager,
    private val signupPostSessionCoordinator: SignupPostSessionCoordinator,
    private val json: Json,
) {
    suspend fun loadSubjects(): List<SubjectItem> =
        safeApiCall(json) { authApi.listSubjects() }

    suspend fun loginSubject(subjectId: String) {
        val res: AuthResponse = safeApiCall(json) {
            authApi.loginSubject(LoginSubjectBody(subjectId))
        }
        authManager.establishSession(
            accessToken = res.access_token,
            refreshToken = res.refresh_token.orEmpty(),
            subjectId = subjectId,
        )
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
        onboardingTarget: String? = null,
        onboardingContents: List<String> = emptyList(),
        onboardingGeneratePlan: Boolean = false,
        medicationNeeded: Boolean = false,
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
        // Phone login replaces the complete account session. It must not inherit a health
        // subject or AI consent from whichever subject/account was active before login.
        authManager.establishSession(
            accessToken = res.access_token,
            refreshToken = res.refresh_token.orEmpty(),
            subjectId = "",
        )
        if (signup) {
            val owner = authManager.captureAccountScope() ?: return
            signupPostSessionCoordinator.enqueue(
                owner,
                SignupPostSessionRequest(
                    target = onboardingTarget,
                    contents = onboardingContents,
                    generatePlan = onboardingGeneratePlan,
                    medicationNeeded = medicationNeeded,
                ),
            )
        }
    }
}
