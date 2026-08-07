package com.xjie.app.feature.healthconnect

import com.xjie.app.core.model.DeviceIndicatorSyncBody
import java.time.Clock

/** Platform-independent fail-closed orchestration, covered by local JVM tests. */
class HealthConnectSyncEngine(
    private val dataSource: HealthConnectDataSource,
    private val sink: DeviceIndicatorSyncSink,
    private val sessionSource: HealthConnectSessionSource,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun availability(): HealthConnectAvailability = dataSource.availability()

    suspend fun prepare(): HealthConnectPreparation {
        if (sessionSource.capture() == null) {
            return HealthConnectPreparation.Blocked(HealthConnectSyncBlock.LoggedOut)
        }
        when (dataSource.availability()) {
            HealthConnectAvailability.ProviderUpdateRequired -> return HealthConnectPreparation.Blocked(
                HealthConnectSyncBlock.ProviderUpdateRequired,
            )
            HealthConnectAvailability.Unavailable -> return HealthConnectPreparation.Blocked(
                HealthConnectSyncBlock.SdkUnavailable,
            )
            HealthConnectAvailability.Available -> Unit
        }
        val missing = dataSource.requiredReadPermissions - dataSource.grantedPermissions()
        return if (missing.isEmpty()) {
            HealthConnectPreparation.Ready
        } else {
            HealthConnectPreparation.PermissionRequired(missing)
        }
    }

    suspend fun sync(days: Long = HealthConnectReadWindow.MAX_DAYS): HealthConnectSyncResult {
        val session = sessionSource.capture()
            ?: blocked(HealthConnectSyncBlock.LoggedOut, "登录后才能同步 Health Connect")
        when (dataSource.availability()) {
            HealthConnectAvailability.ProviderUpdateRequired -> blocked(
                HealthConnectSyncBlock.ProviderUpdateRequired,
                "Health Connect 需要安装或更新",
            )
            HealthConnectAvailability.Unavailable -> blocked(
                HealthConnectSyncBlock.SdkUnavailable,
                "当前设备不支持 Health Connect",
            )
            HealthConnectAvailability.Available -> Unit
        }
        requirePermissions()
        val samples = dataSource.read(HealthConnectReadWindow.endingAt(clock.instant(), days))
        if (!sessionSource.isCurrent(session)) {
            blocked(HealthConnectSyncBlock.AccountChanged, "账号已切换，本次同步已取消")
        }
        // Permission may be revoked while the bounded read is in flight.
        requirePermissions()
        if (samples.isEmpty()) {
            blocked(HealthConnectSyncBlock.NoData, "所选时间范围内没有可同步的 Health Connect 数据")
        }

        val values = HealthConnectPayloadPolicy.values(samples)
        var inserted = 0
        var updated = 0
        var unchanged = 0
        values.chunked(DeviceIndicatorSyncBody.MAX_VALUES).forEach { batch ->
            if (!sessionSource.isCurrent(session)) {
                blocked(HealthConnectSyncBlock.AccountChanged, "账号已切换，本次同步已取消")
            }
            requirePermissions()
            val response = sink.upload(session, DeviceIndicatorSyncBody(values = batch))
            if (!sessionSource.isCurrent(session)) {
                blocked(HealthConnectSyncBlock.AccountChanged, "账号已切换，未展示旧账号的同步结果")
            }
            val counted = response.inserted + response.updated + response.unchanged + response.rejected
            if (
                response.total != batch.size || counted != response.total ||
                response.skipped != response.unchanged + response.rejected ||
                response.rejected > 0 || response.issues.isNotEmpty()
            ) {
                blocked(HealthConnectSyncBlock.ServerRejectedSamples, "服务器拒绝或遗漏了 Health Connect 样本")
            }
            inserted += response.inserted
            updated += response.updated
            unchanged += response.unchanged
        }
        return HealthConnectSyncResult(
            readCount = samples.size,
            uploadedCount = values.size,
            inserted = inserted,
            updated = updated,
            unchanged = unchanged,
        )
    }

    private suspend fun requirePermissions() {
        val missing = dataSource.requiredReadPermissions - dataSource.grantedPermissions()
        if (missing.isNotEmpty()) {
            blocked(HealthConnectSyncBlock.PermissionMissing, "Health Connect 读取权限不完整")
        }
    }

    private fun blocked(reason: HealthConnectSyncBlock, message: String): Nothing =
        throw HealthConnectSyncBlockedException(reason, message)
}
