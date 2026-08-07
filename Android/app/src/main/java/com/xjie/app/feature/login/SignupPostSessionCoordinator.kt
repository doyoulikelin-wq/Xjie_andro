package com.xjie.app.feature.login

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.HealthPlanQuestionnaireRequest
import com.xjie.app.core.model.OnboardingNeedsRequest
import com.xjie.app.core.network.api.HealthPlanApi
import com.xjie.app.core.network.api.UserApi
import com.xjie.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

internal data class SignupPostSessionRequest(
    val target: String?,
    val contents: List<String>,
    val generatePlan: Boolean,
    val medicationNeeded: Boolean,
)

/**
 * Owns optional signup follow-up work outside LoginViewModel. Navigation may destroy the login
 * screen immediately after credentials are published, but the exact account generation remains
 * bound to every follow-up request.
 */
@Singleton
class SignupPostSessionCoordinator @Inject constructor(
    private val userApi: UserApi,
    private val healthPlanApi: HealthPlanApi,
    private val authManager: AuthManager,
    private val json: Json,
) {
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal fun enqueue(
        owner: AuthManager.AccountScopeSnapshot,
        request: SignupPostSessionRequest,
    ): Job = lifecycleScope.launch {
        complete(owner, request)
    }

    internal suspend fun complete(
        owner: AuthManager.AccountScopeSnapshot,
        request: SignupPostSessionRequest,
    ) {
        if (!authManager.isCurrent(owner)) return
        val contents = request.contents.distinct().sorted()
        runCatching {
            safeApiCall(json) {
                userApi.updateOnboardingForOwner(
                    owner,
                    OnboardingNeedsRequest(
                        target = request.target,
                        contents = contents,
                        generate_plan = request.generatePlan,
                        completed = true,
                    ),
                )
            }
        }
        if (!request.generatePlan || !authManager.isCurrent(owner)) return
        val planContents = contents.filterNot { it in PLAN_MANAGED_CONTENTS }
        runCatching {
            safeApiCall(json) {
                healthPlanApi.createFromQuestionnaireForOwner(
                    owner,
                    HealthPlanQuestionnaireRequest(
                        target = request.target ?: "控糖稳定",
                        duration_days = 7,
                        frequency = "daily",
                        contents = planContents,
                        medication_needed = contents.contains("medication") && request.medicationNeeded,
                        notes = "注册末步生成的首个健康计划",
                        title = "${request.target ?: "控糖稳定"}健康计划",
                    ),
                )
            }
        }
    }

    private companion object {
        val PLAN_MANAGED_CONTENTS = setOf("glucose", "weight", "blood_pressure")
    }
}
