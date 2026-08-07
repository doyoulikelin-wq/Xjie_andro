package com.xjie.app.feature.login

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.auth.TestAuthManagerFactory
import com.xjie.app.core.model.HealthPlanDetail
import com.xjie.app.core.model.UserSettings
import com.xjie.app.core.network.api.HealthPlanApi
import com.xjie.app.core.network.api.UserApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class SignupPostSessionCoordinatorTest {
    @Test
    fun signupFollowUpUsesTheCapturedOwnerForOnboardingAndPlan() = runTest {
        val auth = authManager("account-a", "first")
        val owner = requireNotNull(auth.captureAccountScope())
        val userApi = mockk<UserApi>()
        val planApi = mockk<HealthPlanApi>()
        coEvery { userApi.updateOnboardingForOwner(owner, any()) } returns UserSettings()
        coEvery { planApi.createFromQuestionnaireForOwner(owner, any()) } returns mockk<HealthPlanDetail>()
        val coordinator = coordinator(userApi, planApi, auth)

        coordinator.complete(
            owner,
            SignupPostSessionRequest(
                target = "改善睡眠",
                contents = listOf("medication", "fitness", "weight", "fitness"),
                generatePlan = true,
                medicationNeeded = true,
            ),
        )

        coVerify(exactly = 1) {
            userApi.updateOnboardingForOwner(
                owner,
                match { it.contents == listOf("fitness", "medication", "weight") && it.completed },
            )
        }
        coVerify(exactly = 1) {
            planApi.createFromQuestionnaireForOwner(
                owner,
                match {
                    it.target == "改善睡眠" &&
                        it.contents == listOf("fitness", "medication") &&
                        it.medication_needed
                },
            )
        }
    }

    @Test
    fun accountSwitchDuringOnboardingPreventsPlanFromWritingIntoReplacementSession() = runTest {
        val auth = authManager("account-a", "first")
        val owner = requireNotNull(auth.captureAccountScope())
        val userApi = mockk<UserApi>()
        val planApi = mockk<HealthPlanApi>(relaxed = true)
        coEvery { userApi.updateOnboardingForOwner(owner, any()) } coAnswers {
            auth.establishSession(jwt("account-b", "replacement"), "refresh-b", "")
            UserSettings()
        }
        val coordinator = coordinator(userApi, planApi, auth)

        coordinator.complete(
            owner,
            SignupPostSessionRequest(
                target = "控糖稳定",
                contents = listOf("fitness"),
                generatePlan = true,
                medicationNeeded = false,
            ),
        )

        coVerify(exactly = 1) { userApi.updateOnboardingForOwner(owner, any()) }
        coVerify(exactly = 0) { planApi.createFromQuestionnaireForOwner(any(), any()) }
    }

    private fun coordinator(
        userApi: UserApi,
        planApi: HealthPlanApi,
        auth: AuthManager,
    ) = SignupPostSessionCoordinator(
        userApi = userApi,
        healthPlanApi = planApi,
        authManager = auth,
        json = Json { ignoreUnknownKeys = true },
    )

    private fun authManager(account: String, nonce: String): AuthManager =
        TestAuthManagerFactory.create().also {
            it.establishSession(jwt(account, nonce), "refresh", "")
        }

    private fun jwt(subject: String, nonce: String): String {
        fun segment(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        return "${segment("""{"alg":"none"}""")}." +
            "${segment("""{"sub":"$subject","nonce":"$nonce"}""")}.signature"
    }
}
