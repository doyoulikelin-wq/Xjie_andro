package com.xjie.app.feature.healthconnect

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.DeviceIndicatorSyncBody
import com.xjie.app.core.model.DeviceIndicatorSyncResponse
import com.xjie.app.core.model.DeviceIndicatorSyncValue
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CancellationException

enum class HealthConnectAvailability {
    Available,
    ProviderUpdateRequired,
    Unavailable,
}

enum class HealthConnectMetric(
    val sourceMetric: String,
    val indicatorName: String,
    val unit: String,
) {
    Steps("steps", "步数", "步"),
    Distance("distance", "距离", "km"),
    Sleep("sleepDuration", "睡眠时长", "小时"),
    HrvRmssd("hrv", "心率变异性（RMSSD）", "ms"),
    RestingHeartRate("restingHeartRate", "静息心率", "bpm"),
    Weight("bodyWeight", "体重", "kg"),
}

data class HealthConnectReadWindow(
    val start: Instant,
    val end: Instant,
) {
    init {
        require(start.isBefore(end)) { "Health Connect read window must be positive" }
        require(Duration.between(start, end) <= MAX_DURATION) {
            "Health Connect read window cannot exceed $MAX_DAYS days"
        }
    }

    companion object {
        const val MAX_DAYS = 30L
        val MAX_DURATION: Duration = Duration.ofDays(MAX_DAYS)

        fun endingAt(end: Instant, days: Long = MAX_DAYS): HealthConnectReadWindow {
            require(days in 1..MAX_DAYS) { "days must be between 1 and $MAX_DAYS" }
            return HealthConnectReadWindow(end.minus(Duration.ofDays(days)), end)
        }
    }
}

data class HealthConnectRawSample(
    val metric: HealthConnectMetric,
    val recordId: String,
    val value: Double,
    val measuredAt: Instant,
    val sourceLocalDate: LocalDate,
    val timezoneOffsetMinutes: Int,
)

object HealthConnectPayloadPolicy {
    fun body(samples: List<HealthConnectRawSample>): DeviceIndicatorSyncBody {
        require(samples.isNotEmpty()) { "Health Connect sync cannot upload an empty body" }
        return DeviceIndicatorSyncBody(values = values(samples))
    }

    fun values(samples: List<HealthConnectRawSample>): List<DeviceIndicatorSyncValue> = samples
        .map(::value)
        .distinctBy { it.source_id }
        .sortedWith(compareBy(DeviceIndicatorSyncValue::source_metric, DeviceIndicatorSyncValue::source_id))

    fun value(sample: HealthConnectRawSample): DeviceIndicatorSyncValue {
        require(sample.recordId.isNotBlank()) { "Health Connect record is missing a stable ID" }
        require(sample.value.isFinite()) { "Health Connect record value must be finite" }
        require(sample.timezoneOffsetMinutes in -840..840) { "timezone offset is out of range" }
        val offset = ZoneOffset.ofTotalSeconds(sample.timezoneOffsetMinutes * 60)
        return DeviceIndicatorSyncValue(
            indicator_name = sample.metric.indicatorName,
            value = sample.value,
            unit = sample.metric.unit,
            measured_at = sample.measuredAt.atOffset(offset).toString(),
            source_metric = sample.metric.sourceMetric,
            source_id = stableSourceId(sample.metric.sourceMetric, sample.recordId),
            source_local_date = sample.sourceLocalDate.toString(),
            timezone_offset_minutes = sample.timezoneOffsetMinutes,
        )
    }

    fun stableSourceId(sourceMetric: String, recordId: String): String {
        require(sourceMetric.matches(Regex("[A-Za-z][A-Za-z0-9]{0,63}"))) {
            "source metric is not stable"
        }
        val trimmedId = recordId.trim()
        require(trimmedId.isNotEmpty()) { "Health Connect record is missing a stable ID" }
        val uuid = UUID_PATTERN.matchEntire(trimmedId)?.value?.lowercase()
        if (uuid != null) return "$sourceMetric-$uuid"

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(trimmedId.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "$sourceMetric-hc-$digest"
    }

    private val UUID_PATTERN = Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-" +
            "[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}",
    )
}

class HealthConnectAccountSession internal constructor(
    internal val bearerToken: String,
    val subjectId: String,
    internal val accountOwner: AuthManager.AccountScopeSnapshot? = null,
) {
    override fun toString(): String = "HealthConnectAccountSession(token=<redacted>, subjectId=$subjectId)"
}

interface HealthConnectSessionSource {
    fun capture(): HealthConnectAccountSession?
    fun isCurrent(session: HealthConnectAccountSession): Boolean
}

interface HealthConnectDataSource {
    val requiredReadPermissions: Set<String>
    fun availability(): HealthConnectAvailability
    suspend fun grantedPermissions(): Set<String>
    suspend fun read(window: HealthConnectReadWindow): List<HealthConnectRawSample>
}

interface DeviceIndicatorSyncSink {
    suspend fun upload(
        session: HealthConnectAccountSession,
        body: DeviceIndicatorSyncBody,
    ): DeviceIndicatorSyncResponse
}

sealed interface HealthConnectPreparation {
    data object Ready : HealthConnectPreparation
    data class PermissionRequired(val permissions: Set<String>) : HealthConnectPreparation
    data class Blocked(val reason: HealthConnectSyncBlock) : HealthConnectPreparation
}

enum class HealthConnectSyncBlock {
    LoggedOut,
    ProviderUpdateRequired,
    SdkUnavailable,
    AccountChanged,
    PermissionMissing,
    MetricUnavailable,
    NoData,
    ServerRejectedSamples,
}

data class HealthConnectSyncResult(
    val readCount: Int,
    val uploadedCount: Int,
    val inserted: Int,
    val updated: Int,
    val unchanged: Int,
)

class HealthConnectSyncBlockedException(
    val reason: HealthConnectSyncBlock,
    message: String,
) : IllegalStateException(message)

/** Converts every SDK read failure into an explicit fail-closed domain result. */
object HealthConnectReadFailurePolicy {
    fun rethrow(metric: HealthConnectMetric, error: Exception): Nothing = when (error) {
        is CancellationException -> throw error
        is HealthConnectSyncBlockedException -> throw error
        is SecurityException -> throw HealthConnectSyncBlockedException(
            HealthConnectSyncBlock.PermissionMissing,
            "Health Connect 读取权限在读取期间失效",
        )
        else -> throw HealthConnectSyncBlockedException(
            HealthConnectSyncBlock.MetricUnavailable,
            "Health Connect 当前无法读取“${metric.indicatorName}”",
        )
    }
}
