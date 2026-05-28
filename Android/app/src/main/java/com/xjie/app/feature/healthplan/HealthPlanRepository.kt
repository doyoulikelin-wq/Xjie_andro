package com.xjie.app.feature.healthplan

import com.xjie.app.core.model.HealthPlanDetail
import com.xjie.app.core.model.HealthPlanFromChatRequest
import com.xjie.app.core.model.HealthPlanListResponse
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

    suspend fun detail(id: String): HealthPlanDetail =
        safeApiCall(json) { api.planDetail(id) }

    suspend fun week(weekStart: String): TubeWeek =
        safeApiCall(json) { api.week(weekStart) }

    suspend fun createFromChat(body: HealthPlanFromChatRequest): HealthPlanDetail =
        safeApiCall(json) { api.createFromChat(body) }

    suspend fun complete(date: String, taskType: String): TubeCompleteResponse =
        safeApiCall(json) {
            api.completeTubeTask(TubeCompleteRequest(date = date, task_type = taskType))
        }
}
