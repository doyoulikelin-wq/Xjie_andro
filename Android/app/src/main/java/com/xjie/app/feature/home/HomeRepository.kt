package com.xjie.app.feature.home

import com.xjie.app.core.model.DashboardHealth
import com.xjie.app.core.model.ProactiveMessage
import com.xjie.app.core.model.UpdateSettingsBody
import com.xjie.app.core.model.UserSettings
import com.xjie.app.core.network.api.AgentApi
import com.xjie.app.core.network.api.DashboardApi
import com.xjie.app.core.network.api.UserApi
import com.xjie.app.core.network.safeApiCall
import com.xjie.app.core.storage.OfflineCache
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val dashboardApi: DashboardApi,
    private val agentApi: AgentApi,
    private val userApi: UserApi,
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

    suspend fun loadProactive(): ProactiveMessage? =
        runCatching { safeApiCall(json) { agentApi.proactive() } }.getOrNull()

    suspend fun loadSettings(): UserSettings? =
        runCatching { safeApiCall(json) { userApi.settings() } }.getOrNull()

    suspend fun updateInterventionLevel(level: String) {
        safeApiCall(json) {
            userApi.updateSettings(UpdateSettingsBody(intervention_level = level))
        }
    }
}
