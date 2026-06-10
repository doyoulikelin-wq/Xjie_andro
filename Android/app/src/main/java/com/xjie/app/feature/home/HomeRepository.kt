package com.xjie.app.feature.home

import com.xjie.app.core.model.DashboardHealth
import com.xjie.app.core.model.HealthTreeSummary
import com.xjie.app.core.network.api.DashboardApi
import com.xjie.app.core.network.api.ElderlyApi
import com.xjie.app.core.network.api.HealthDataApi
import com.xjie.app.core.network.api.HealthPlanApi
import com.xjie.app.core.network.safeApiCall
import com.xjie.app.core.storage.OfflineCache
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val dashboardApi: DashboardApi,
    private val healthPlanApi: HealthPlanApi,
    private val healthDataApi: HealthDataApi,
    private val elderlyApi: ElderlyApi,
    private val cache: OfflineCache,
    private val json: Json,
) {
    companion object {
        const val DASHBOARD_CACHE_KEY = "dashboard_health"
    }

    /** Returns (dashboard, fromCache). */
    suspend fun loadDashboard(): Pair<DashboardHealth?, Boolean> {
        return try {
            val d = safeApiCall(json) { dashboardApi.health() }
            cache.save(DASHBOARD_CACHE_KEY, d, DashboardHealth.serializer())
            d to false
        } catch (_: Throwable) {
            val cached = cache.load(DASHBOARD_CACHE_KEY, DashboardHealth.serializer())
            cached to (cached != null)
        }
    }

    suspend fun loadTreeSummary(): HealthTreeSummary? =
        runCatching { safeApiCall(json) { healthPlanApi.treeSummary() } }.getOrNull()

    suspend fun loadContextPrecision(): ContextPrecisionSummary {
        val records = runCatching { safeApiCall(json) { healthDataApi.documents("record") } }.getOrNull()
        val exams = runCatching { safeApiCall(json) { healthDataApi.documents("exam") } }.getOrNull()
        val summary = runCatching { safeApiCall(json) { healthDataApi.summary() } }.getOrNull()
        val indicators = runCatching { safeApiCall(json) { healthDataApi.indicators() } }.getOrNull()
        val history = runCatching { safeApiCall(json) { elderlyApi.list(days = 30, limit = 100) } }.getOrNull()?.items.orEmpty()

        return ContextPrecisionSummary(
            healthRecordCount = records?.items?.size ?: 0,
            healthExamCount = exams?.items?.size ?: 0,
            healthIndicatorCount = indicators?.indicators?.size ?: 0,
            hasHealthSummary = !summary?.summary_text.isNullOrBlank(),
            historyFeedbackCount = history.size,
            historyMoodCount = history.count { !it.mood.isNullOrBlank() },
            historyBodyCount = history.count { !it.body_feeling.isNullOrBlank() },
            omicsCategoryCount = 0,
            omicsItemCount = 0,
        )
    }

}
