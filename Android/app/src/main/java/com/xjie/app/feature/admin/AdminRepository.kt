package com.xjie.app.feature.admin

import com.xjie.app.core.model.AdminFeatureFlag
import com.xjie.app.core.model.AdminFeatureFlagUpdate
import com.xjie.app.core.network.api.AdminApi
import com.xjie.app.core.network.api.AdminConversationItem
import com.xjie.app.core.network.api.AdminOmicsItem
import com.xjie.app.core.network.api.AdminStats
import com.xjie.app.core.network.api.AdminTokenDetails
import com.xjie.app.core.network.api.AdminTokenStats
import com.xjie.app.core.network.api.AdminUserItem
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val api: AdminApi,
    private val json: Json,
) {
    suspend fun stats(): AdminStats? =
        runCatching { safeApiCall(json) { api.stats() } }.getOrNull()

    suspend fun users(page: Int = 1, size: Int = 50): List<AdminUserItem> =
        runCatching { safeApiCall(json) { api.users(page, size) } }.getOrDefault(emptyList())

    suspend fun conversations(page: Int = 1, size: Int = 50): List<AdminConversationItem> =
        runCatching { safeApiCall(json) { api.conversations(page, size) } }.getOrDefault(emptyList())

    suspend fun omics(page: Int = 1, size: Int = 50): List<AdminOmicsItem> =
        runCatching { safeApiCall(json) { api.omics(page, size) } }.getOrDefault(emptyList())

    suspend fun tokenStats(): AdminTokenStats? =
        runCatching { safeApiCall(json) { api.tokenStats() } }.getOrNull()

    suspend fun tokenDetails(): AdminTokenDetails? =
        runCatching { safeApiCall(json) { api.tokenStatsDetails() } }.getOrNull()

    suspend fun listFeatureFlags(): List<AdminFeatureFlag> =
        runCatching { safeApiCall(json) { api.listFeatureFlags() }.flags }.getOrDefault(emptyList())

    suspend fun toggleFeatureFlag(id: Int, enabled: Boolean): AdminFeatureFlag =
        safeApiCall(json) {
            api.updateFeatureFlag(id, AdminFeatureFlagUpdate(enabled = enabled))
        }
}
