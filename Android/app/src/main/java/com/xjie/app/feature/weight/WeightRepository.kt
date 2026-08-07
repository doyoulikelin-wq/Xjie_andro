package com.xjie.app.feature.weight

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.ManualIndicatorBody
import com.xjie.app.core.model.ManualIndicatorItem
import com.xjie.app.core.network.api.WeightApi
import com.xjie.app.core.network.safeApiCall
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import retrofit2.Retrofit

@Singleton
class WeightRepository @Inject constructor(
    retrofit: Retrofit,
    private val json: Json,
    private val authManager: AuthManager,
) {
    private val api = retrofit.create(WeightApi::class.java)

    suspend fun load(owner: AuthManager.AccountScopeSnapshot): WeightNetworkSnapshot = coroutineScope {
        requireCurrent(owner)
        val trendRequest = async {
            safeApiCall(json) { api.trends(owner, WeightDashboardPolicy.REQUEST_NAMES) }
        }
        // Profile height is an optional, explicit user source. A profile read failure must not hide
        // otherwise valid server trend data; admitted height trend remains the safe fallback.
        val userRequest = async {
            runCatching { safeApiCall(json) { api.currentUser(owner) } }.getOrNull()
        }
        val trends = trendRequest.await()
        val user = userRequest.await()
        requireCurrent(owner)
        WeightNetworkSnapshot(
            trends = trends.indicators,
            profileHeightCm = user?.profile?.height_cm,
        )
    }

    suspend fun recordWeight(
        owner: AuthManager.AccountScopeSnapshot,
        valueKg: Double,
        measuredAt: Instant,
    ): WeightNetworkSnapshot {
        require(WeightDashboardPolicy.validWeight(valueKg)) { WeightDashboardPolicy.WEIGHT_ERROR }
        createManual(
            owner = owner,
            indicatorName = WeightDashboardPolicy.WEIGHT_INDICATOR,
            value = valueKg,
            unit = "kg",
            measuredAt = measuredAt,
        )
        return load(owner)
    }

    suspend fun recordHeight(
        owner: AuthManager.AccountScopeSnapshot,
        valueCm: Int,
        measuredAt: Instant,
    ): WeightNetworkSnapshot {
        require(valueCm in WeightDashboardPolicy.validHeightRange) { WeightDashboardPolicy.HEIGHT_ERROR }
        createManual(
            owner = owner,
            indicatorName = WeightDashboardPolicy.HEIGHT_INDICATOR,
            value = valueCm.toDouble(),
            unit = "cm",
            measuredAt = measuredAt,
        )
        return load(owner)
    }

    private suspend fun createManual(
        owner: AuthManager.AccountScopeSnapshot,
        indicatorName: String,
        value: Double,
        unit: String,
        measuredAt: Instant,
    ) {
        requireCurrent(owner)
        val body = ManualIndicatorBody(
            indicator_name = indicatorName,
            value = value,
            unit = unit,
            measured_at = DateTimeFormatter.ISO_INSTANT.format(measuredAt),
            notes = null,
        )
        val response = safeApiCall(json) { api.createManualIndicator(owner, body) }
        requireCurrent(owner)
        requireValidMutationResponse(body, response)
    }

    private fun requireValidMutationResponse(
        request: ManualIndicatorBody,
        response: ManualIndicatorItem,
    ) {
        check(
            response.id > 0L &&
                response.source.equals("manual", ignoreCase = true) &&
                response.indicator_name == request.indicator_name &&
                response.unit?.trim()?.equals(request.unit, ignoreCase = true) == true &&
                response.value.isFinite() &&
                abs(response.value - request.value) < 0.000_001,
        ) { "服务器未确认本次手动记录，页面不会显示保存成功。" }
    }

    private fun requireCurrent(owner: AuthManager.AccountScopeSnapshot) {
        check(authManager.isCurrent(owner)) { "账号或健康主体已变化，本次体重操作已取消。" }
    }
}
