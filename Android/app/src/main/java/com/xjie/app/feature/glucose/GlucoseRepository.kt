package com.xjie.app.feature.glucose

import com.xjie.app.core.model.DashboardHealth
import com.xjie.app.core.model.GlucosePoint
import com.xjie.app.core.model.GlucoseRange
import com.xjie.app.core.network.api.DashboardApi
import com.xjie.app.core.network.api.GlucoseApi
import com.xjie.app.core.network.safeApiCall
import com.xjie.app.core.util.DateUtils
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

enum class GlucoseWindow(val raw: String, val label: String) {
    H24("24h", "24h"),
    D7("7d", "7 天"),
    ALL("all", "全部");

    companion object {
        fun fromRaw(raw: String): GlucoseWindow = entries.firstOrNull { it.raw == raw } ?: H24
    }
}

@Singleton
class GlucoseRepository @Inject constructor(
    private val api: GlucoseApi,
    private val dashboardApi: DashboardApi,
    private val json: Json,
) {
    suspend fun range(): GlucoseRange = safeApiCall(json) { api.range() }

    suspend fun points(window: GlucoseWindow, range: GlucoseRange?): List<GlucosePoint> {
        val now = Instant.now()
        val from: Instant = when (window) {
            GlucoseWindow.H24 -> now.minusSeconds(24 * 3600L)
            GlucoseWindow.D7 -> now.minusSeconds(7 * 24 * 3600L)
            GlucoseWindow.ALL -> range?.min_ts
                ?.let { DateUtils.parseISO(it)?.toInstant() }
                ?: now.minusSeconds(30 * 24 * 3600L)
        }
        val fmt = DateTimeFormatter.ISO_INSTANT
        return safeApiCall(json) {
            api.list(from = fmt.format(from), to = fmt.format(now))
        }
    }

    suspend fun dashboard(): DashboardHealth? =
        runCatching { safeApiCall(json) { dashboardApi.health() } }.getOrNull()
}
