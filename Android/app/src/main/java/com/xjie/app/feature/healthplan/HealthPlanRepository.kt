package com.xjie.app.feature.healthplan

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.HealthPlanDetail
import com.xjie.app.core.model.HealthPlanFromChatRequest
import com.xjie.app.core.model.HealthPlanQuestionnaireRequest
import com.xjie.app.core.model.HealthPlanListResponse
import com.xjie.app.core.model.HealthTreeSummary
import com.xjie.app.core.model.PlanRevisionApplyRequest
import com.xjie.app.core.model.PlanRevisionGenerateRequest
import com.xjie.app.core.model.PlanRevisionProposal
import com.xjie.app.core.model.PlanTask
import com.xjie.app.core.model.PlanTaskUpdateRequest
import com.xjie.app.core.model.TubeCompleteRequest
import com.xjie.app.core.model.TubeCompleteResponse
import com.xjie.app.core.model.TubeWeek
import com.xjie.app.core.network.api.HealthPlanApi
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthPlanRepository @Inject constructor(
    private val api: HealthPlanApi,
    private val json: Json,
) {
    suspend fun plans(): HealthPlanListResponse =
        safeApiCall(json) { api.listPlans() }

    suspend fun plans(owner: AuthManager.AccountScopeSnapshot): HealthPlanListResponse =
        safeApiCall(json) { api.listPlansForOwner(owner) }

    suspend fun detail(id: String): HealthPlanDetail =
        safeApiCall(json) { api.planDetail(id) }

    suspend fun week(weekStart: String): TubeWeek =
        safeApiCall(json) { api.week(weekStart) }

    suspend fun treeSummary(): HealthTreeSummary =
        safeApiCall(json) { api.treeSummary() }

    suspend fun createFromChat(body: HealthPlanFromChatRequest): HealthPlanDetail =
        safeApiCall(json) { api.createFromChat(body) }

    suspend fun createFromQuestionnaire(body: HealthPlanQuestionnaireRequest): HealthPlanDetail =
        safeApiCall(json) { api.createFromQuestionnaire(body) }

    suspend fun complete(date: String, taskType: String): TubeCompleteResponse =
        safeApiCall(json) {
            api.completeTubeTask(TubeCompleteRequest(date = date, task_type = taskType))
        }

    suspend fun updateTask(id: String, body: PlanTaskUpdateRequest): PlanTask =
        safeApiCall(json) { api.updateTask(id, body) }

    suspend fun generateRevision(date: String?): PlanRevisionProposal =
        safeApiCall(json) {
            api.generateRevision(
                PlanRevisionGenerateRequest(
                    date = date,
                    purpose = "根据用户基本信息、近期健康数据、病史和执行反馈修正整个计划",
                ),
            )
        }

    suspend fun applyRevision(id: String, body: PlanRevisionApplyRequest): PlanRevisionProposal =
        safeApiCall(json) { api.applyRevision(id, body) }
}
