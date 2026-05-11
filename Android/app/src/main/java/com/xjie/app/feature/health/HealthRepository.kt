package com.xjie.app.feature.health

import com.xjie.app.core.model.HealthDataSummary
import com.xjie.app.core.model.HealthReports
import com.xjie.app.core.model.SummaryTaskResponse
import com.xjie.app.core.model.TodayBriefing
import com.xjie.app.core.network.api.AgentApi
import com.xjie.app.core.network.api.HealthDataApi
import com.xjie.app.core.network.api.HealthReportsApi
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRepository @Inject constructor(
    private val agentApi: AgentApi,
    private val reportsApi: HealthReportsApi,
    private val healthDataApi: HealthDataApi,
    private val json: Json,
) {
    suspend fun todayBriefing(): TodayBriefing? = runCatching {
        safeApiCall(json) { agentApi.today() }
    }.getOrNull()

    suspend fun healthReports(): HealthReports? = runCatching {
        safeApiCall(json) { reportsApi.reports() }
    }.getOrNull()

    suspend fun summary(): HealthDataSummary? = runCatching {
        safeApiCall(json) { healthDataApi.summary() }
    }.getOrNull()

    suspend fun startSummaryTask(): SummaryTaskResponse =
        safeApiCall(json) { healthDataApi.generateSummaryAsync() }

    suspend fun taskStatus(taskId: String): SummaryTaskResponse =
        safeApiCall(json) { healthDataApi.summaryTaskStatus(taskId) }
}
