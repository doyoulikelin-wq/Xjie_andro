package com.xjie.app.feature.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

object HealthConnectPermissionCatalog {
    val readByMetric: Map<HealthConnectMetric, String> = linkedMapOf(
        HealthConnectMetric.Steps to HealthPermission.getReadPermission(StepsRecord::class),
        HealthConnectMetric.Distance to HealthPermission.getReadPermission(DistanceRecord::class),
        HealthConnectMetric.Sleep to HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthConnectMetric.HrvRmssd to HealthPermission.getReadPermission(
            HeartRateVariabilityRmssdRecord::class,
        ),
        HealthConnectMetric.RestingHeartRate to HealthPermission.getReadPermission(
            RestingHeartRateRecord::class,
        ),
        HealthConnectMetric.Weight to HealthPermission.getReadPermission(WeightRecord::class),
    )
    val requiredReadPermissions: Set<String> = readByMetric.values.toSet()
}

@Singleton
class AndroidHealthConnectGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectDataSource {
    private val permissionByMetric = HealthConnectPermissionCatalog.readByMetric

    override val requiredReadPermissions: Set<String> =
        HealthConnectPermissionCatalog.requiredReadPermissions

    private val client: HealthConnectClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        check(availability() == HealthConnectAvailability.Available) {
            "Health Connect client requested while SDK is unavailable"
        }
        HealthConnectClient.getOrCreate(context)
    }

    override fun availability(): HealthConnectAvailability = when (
        HealthConnectClient.getSdkStatus(context)
    ) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            HealthConnectAvailability.ProviderUpdateRequired
        else -> HealthConnectAvailability.Unavailable
    }

    override suspend fun grantedPermissions(): Set<String> {
        check(availability() == HealthConnectAvailability.Available) {
            "Health Connect permissions requested while SDK is unavailable"
        }
        return client.permissionController.getGrantedPermissions()
    }

    override suspend fun read(window: HealthConnectReadWindow): List<HealthConnectRawSample> {
        check(availability() == HealthConnectAvailability.Available) {
            "Health Connect read requested while SDK is unavailable"
        }
        val result = buildList {
            addAll(readSteps(window))
            addAll(readDistance(window))
            addAll(readSleep(window))
            addAll(readHrv(window))
            addAll(readRestingHeartRate(window))
            addAll(readWeight(window))
        }
        return result.sortedWith(
            compareBy(HealthConnectRawSample::measuredAt)
                .thenBy { it.metric.sourceMetric }
                .thenBy(HealthConnectRawSample::recordId),
        )
    }

    private suspend fun readSteps(window: HealthConnectReadWindow) =
        records(StepsRecord::class, HealthConnectMetric.Steps, window).map { record ->
            intervalSample(
                metric = HealthConnectMetric.Steps,
                recordId = record.metadata.id,
                value = record.count.toDouble(),
                start = record.startTime,
                end = record.endTime,
                startOffset = record.startZoneOffset,
                endOffset = record.endZoneOffset,
            )
        }

    private suspend fun readDistance(window: HealthConnectReadWindow) =
        records(DistanceRecord::class, HealthConnectMetric.Distance, window).map { record ->
            intervalSample(
                metric = HealthConnectMetric.Distance,
                recordId = record.metadata.id,
                value = record.distance.inMeters / 1_000.0,
                start = record.startTime,
                end = record.endTime,
                startOffset = record.startZoneOffset,
                endOffset = record.endZoneOffset,
            )
        }

    private suspend fun readSleep(window: HealthConnectReadWindow) =
        records(SleepSessionRecord::class, HealthConnectMetric.Sleep, window).map { record ->
            intervalSample(
                metric = HealthConnectMetric.Sleep,
                recordId = record.metadata.id,
                value = Duration.between(record.startTime, record.endTime).toMillis() / 3_600_000.0,
                start = record.startTime,
                end = record.endTime,
                startOffset = record.startZoneOffset,
                endOffset = record.endZoneOffset,
            )
        }

    private suspend fun readHrv(window: HealthConnectReadWindow) =
        records(
            HeartRateVariabilityRmssdRecord::class,
            HealthConnectMetric.HrvRmssd,
            window,
        ).map { record ->
            instantSample(
                metric = HealthConnectMetric.HrvRmssd,
                recordId = record.metadata.id,
                value = record.heartRateVariabilityMillis,
                time = record.time,
                offset = record.zoneOffset,
            )
        }

    private suspend fun readRestingHeartRate(window: HealthConnectReadWindow) =
        records(
            RestingHeartRateRecord::class,
            HealthConnectMetric.RestingHeartRate,
            window,
        ).map { record ->
            instantSample(
                metric = HealthConnectMetric.RestingHeartRate,
                recordId = record.metadata.id,
                value = record.beatsPerMinute.toDouble(),
                time = record.time,
                offset = record.zoneOffset,
            )
        }

    private suspend fun readWeight(window: HealthConnectReadWindow) =
        records(WeightRecord::class, HealthConnectMetric.Weight, window).map { record ->
            instantSample(
                metric = HealthConnectMetric.Weight,
                recordId = record.metadata.id,
                value = record.weight.inKilograms,
                time = record.time,
                offset = record.zoneOffset,
            )
        }

    private suspend fun <T : Record> records(
        recordType: KClass<T>,
        metric: HealthConnectMetric,
        window: HealthConnectReadWindow,
    ): List<T> {
        val permission = permissionByMetric.getValue(metric)
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            // Permissions can be revoked at any time; check before every SDK operation/page.
            ensurePermission(permission)
            val response = try {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = recordType,
                        timeRangeFilter = TimeRangeFilter.between(window.start, window.end),
                        ascendingOrder = true,
                        pageSize = PAGE_SIZE,
                        pageToken = pageToken,
                    ),
                )
            } catch (error: Exception) {
                HealthConnectReadFailurePolicy.rethrow(metric, error)
            }
            records += response.records
            if (records.size > MAX_RECORDS_PER_METRIC) {
                throw IllegalStateException(
                    "Health Connect ${metric.sourceMetric} exceeded the bounded record limit",
                )
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return records
    }

    private suspend fun ensurePermission(permission: String) {
        if (permission !in grantedPermissions()) {
            throw HealthConnectSyncBlockedException(
                HealthConnectSyncBlock.PermissionMissing,
                "Health Connect 读取权限在同步过程中被撤销",
            )
        }
    }

    private fun intervalSample(
        metric: HealthConnectMetric,
        recordId: String,
        value: Double,
        start: Instant,
        end: Instant,
        startOffset: ZoneOffset?,
        endOffset: ZoneOffset?,
    ): HealthConnectRawSample {
        val localStartOffset = offsetAt(start, startOffset)
        val localEndOffset = offsetAt(end, endOffset)
        return HealthConnectRawSample(
            metric = metric,
            recordId = recordId,
            value = value,
            measuredAt = end,
            sourceLocalDate = start.atOffset(localStartOffset).toLocalDate(),
            timezoneOffsetMinutes = localEndOffset.totalSeconds / 60,
        )
    }

    private fun instantSample(
        metric: HealthConnectMetric,
        recordId: String,
        value: Double,
        time: Instant,
        offset: ZoneOffset?,
    ): HealthConnectRawSample {
        val effectiveOffset = offsetAt(time, offset)
        return HealthConnectRawSample(
            metric = metric,
            recordId = recordId,
            value = value,
            measuredAt = time,
            sourceLocalDate = time.atOffset(effectiveOffset).toLocalDate(),
            timezoneOffsetMinutes = effectiveOffset.totalSeconds / 60,
        )
    }

    private fun offsetAt(instant: Instant, recorded: ZoneOffset?): ZoneOffset =
        recorded ?: ZoneId.systemDefault().rules.getOffset(instant)

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_RECORDS_PER_METRIC = 5_000
    }
}
