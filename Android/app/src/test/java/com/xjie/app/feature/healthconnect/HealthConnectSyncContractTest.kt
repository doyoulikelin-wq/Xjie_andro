package com.xjie.app.feature.healthconnect

import com.xjie.app.core.model.DeviceIndicatorSyncBody
import com.xjie.app.core.model.DeviceIndicatorSyncResponse
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectSyncContractTest {
    @Test
    fun permissions_areExactlyTheSixRequestedReadTypesAndNeverWrite() {
        val permissions = HealthConnectPermissionCatalog.requiredReadPermissions

        assertEquals(6, permissions.size)
        assertEquals(
            setOf(
                "android.permission.health.READ_STEPS",
                "android.permission.health.READ_DISTANCE",
                "android.permission.health.READ_SLEEP",
                "android.permission.health.READ_HEART_RATE_VARIABILITY",
                "android.permission.health.READ_RESTING_HEART_RATE",
                "android.permission.health.READ_WEIGHT",
            ),
            permissions,
        )
        assertTrue(permissions.all { ".READ_" in it })
        assertFalse(permissions.any { ".WRITE_" in it })
    }

    @Test
    fun payload_hasStableSourceIdentityLocalDateAndHistoricalTimezoneOffset() {
        val instant = Instant.parse("2026-10-25T01:30:00Z")
        val berlinOffset = ZoneId.of("Europe/Berlin").rules.getOffset(instant)
        val sample = HealthConnectRawSample(
            metric = HealthConnectMetric.HrvRmssd,
            recordId = "123e4567-e89b-12d3-a456-426614174000",
            value = 42.5,
            measuredAt = instant,
            sourceLocalDate = instant.atZone(ZoneId.of("Europe/Berlin")).toLocalDate(),
            timezoneOffsetMinutes = berlinOffset.totalSeconds / 60,
        )

        val first = HealthConnectPayloadPolicy.value(sample)
        val second = HealthConnectPayloadPolicy.value(sample)

        assertEquals(first, second)
        assertEquals("hrv-123e4567-e89b-12d3-a456-426614174000", first.source_id)
        assertEquals("hrv", first.source_metric)
        assertEquals("2026-10-25", first.source_local_date)
        assertEquals(60, first.timezone_offset_minutes)
        assertTrue(first.measured_at.endsWith("+01:00"))
        assertEquals(
            DeviceIndicatorSyncBody.SOURCE_DEVICE,
            DeviceIndicatorSyncBody(values = listOf(first)).source,
        )
    }

    @Test
    fun nonUuidRecordIdentityIsDeterministicHashedAndMetricScoped() {
        val first = HealthConnectPayloadPolicy.stableSourceId("steps", "provider-record-1")
        val repeated = HealthConnectPayloadPolicy.stableSourceId("steps", "provider-record-1")
        val otherMetric = HealthConnectPayloadPolicy.stableSourceId("distance", "provider-record-1")

        assertEquals(first, repeated)
        assertNotEquals(first, otherMetric)
        assertTrue(first.startsWith("steps-hc-"))
        assertEquals(73, first.length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun readWindowRejectsMoreThanThirtyDays() {
        HealthConnectReadWindow(
            start = Instant.parse("2026-06-14T00:00:00Z"),
            end = Instant.parse("2026-07-15T00:00:01Z"),
        )
    }

    @Test
    fun metricReadFailureIsExplicitAndFailsClosed() {
        val error = runCatching {
            HealthConnectReadFailurePolicy.rethrow(
                HealthConnectMetric.RestingHeartRate,
                UnsupportedOperationException("record type unavailable"),
            )
        }.exceptionOrNull()

        assertEquals(
            HealthConnectSyncBlock.MetricUnavailable,
            (error as HealthConnectSyncBlockedException).reason,
        )
        assertTrue(error.message.orEmpty().contains(HealthConnectMetric.RestingHeartRate.indicatorName))
        assertTrue(
            HealthConnectUiPolicy.blockedMessage(error.reason, error.message)
                .contains(HealthConnectMetric.RestingHeartRate.indicatorName),
        )
        assertTrue(
            HealthConnectUiPolicy.blockedMessage(error.reason, error.message)
                .contains("未上传任何数据"),
        )
    }

    @Test
    fun syncRevalidatesPermissionsBeforeReadAndBeforeEveryUploadBatch() = kotlinx.coroutines.test.runTest {
        val samples = (1..201).map { index -> sample("record-$index") }
        val data = FakeDataSource(samples)
        val sink = FakeSink()
        val sessions = FakeSessionSource()
        val engine = HealthConnectSyncEngine(data, sink, sessions, FIXED_CLOCK)

        val result = engine.sync()

        assertEquals(201, result.uploadedCount)
        assertEquals(listOf(200, 1), sink.batches)
        assertEquals(4, data.permissionChecks)
        assertEquals(1, data.readCalls)
    }

    @Test
    fun accountSwitchAfterReadFailsClosedBeforeAnyUpload() = kotlinx.coroutines.test.runTest {
        val data = FakeDataSource(listOf(sample("old-account-record")))
        val sink = FakeSink()
        val sessions = FakeSessionSource().apply { switchAfterFirstCurrentCheck = true }
        val engine = HealthConnectSyncEngine(data, sink, sessions, FIXED_CLOCK)

        val error = runCatching { engine.sync() }.exceptionOrNull()

        assertEquals(HealthConnectSyncBlock.AccountChanged, (error as HealthConnectSyncBlockedException).reason)
        assertTrue(sink.batches.isEmpty())
    }

    @Test
    fun permissionRevokedAfterReadFailsClosedBeforeAnyUpload() = kotlinx.coroutines.test.runTest {
        val permissions = setOf("read-a", "read-b")
        val data = FakeDataSource(
            samples = listOf(sample("revoked-record")),
            grantSequence = listOf(permissions, setOf("read-a")),
        )
        val sink = FakeSink()
        val engine = HealthConnectSyncEngine(data, sink, FakeSessionSource(), FIXED_CLOCK)

        val error = runCatching { engine.sync() }.exceptionOrNull()

        assertEquals(
            HealthConnectSyncBlock.PermissionMissing,
            (error as HealthConnectSyncBlockedException).reason,
        )
        assertTrue(sink.batches.isEmpty())
    }

    @Test
    fun uploadUsesCapturedSessionAndRejectsIncompleteServerAccounting() = kotlinx.coroutines.test.runTest {
        val data = FakeDataSource(listOf(sample("stable-record")))
        val sessions = FakeSessionSource()
        val sink = FakeSink(
            responseFactory = { size -> response(total = size, inserted = 0) },
        )
        val engine = HealthConnectSyncEngine(data, sink, sessions, FIXED_CLOCK)

        val error = runCatching { engine.sync() }.exceptionOrNull()

        assertEquals("old-token", sink.sessions.single().bearerToken)
        assertEquals(
            HealthConnectSyncBlock.ServerRejectedSamples,
            (error as HealthConnectSyncBlockedException).reason,
        )
    }

    private fun sample(id: String) = HealthConnectRawSample(
        metric = HealthConnectMetric.Steps,
        recordId = id,
        value = 100.0,
        measuredAt = Instant.parse("2026-07-14T12:00:00Z"),
        sourceLocalDate = LocalDate.parse("2026-07-14"),
        timezoneOffsetMinutes = 480,
    )

    private class FakeDataSource(
        private val samples: List<HealthConnectRawSample>,
        private val grantSequence: List<Set<String>> = emptyList(),
    ) : HealthConnectDataSource {
        override val requiredReadPermissions = setOf("read-a", "read-b")
        var permissionChecks = 0
        var readCalls = 0
        override fun availability() = HealthConnectAvailability.Available
        override suspend fun grantedPermissions(): Set<String> {
            permissionChecks += 1
            return grantSequence.getOrElse(permissionChecks - 1) {
                grantSequence.lastOrNull() ?: requiredReadPermissions
            }
        }
        override suspend fun read(window: HealthConnectReadWindow): List<HealthConnectRawSample> {
            readCalls += 1
            assertEquals(HealthConnectReadWindow.MAX_DURATION, java.time.Duration.between(window.start, window.end))
            return samples
        }
    }

    private class FakeSessionSource : HealthConnectSessionSource {
        private val original = HealthConnectAccountSession("old-token", "subject-a")
        var switchAfterFirstCurrentCheck = false
        private var currentChecks = 0
        override fun capture(): HealthConnectAccountSession = original
        override fun isCurrent(session: HealthConnectAccountSession): Boolean {
            currentChecks += 1
            return !switchAfterFirstCurrentCheck || currentChecks == 0
        }
    }

    private class FakeSink(
        private val responseFactory: (Int) -> DeviceIndicatorSyncResponse = { size ->
            response(total = size, inserted = size)
        },
    ) : DeviceIndicatorSyncSink {
        val batches = mutableListOf<Int>()
        val sessions = mutableListOf<HealthConnectAccountSession>()
        override suspend fun upload(
            session: HealthConnectAccountSession,
            body: DeviceIndicatorSyncBody,
        ): DeviceIndicatorSyncResponse {
            sessions += session
            batches += body.values.size
            return responseFactory(body.values.size)
        }
    }

    companion object {
        private val FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-15T00:00:00Z"),
            java.time.ZoneOffset.UTC,
        )

        private fun response(total: Int, inserted: Int) = DeviceIndicatorSyncResponse(
            total = total,
            inserted = inserted,
            updated = 0,
            unchanged = 0,
            rejected = 0,
            skipped = 0,
        )
    }
}
