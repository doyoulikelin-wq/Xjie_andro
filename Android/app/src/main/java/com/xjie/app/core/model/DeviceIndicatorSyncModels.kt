package com.xjie.app.core.model

import kotlinx.serialization.Serializable

/** Wire contract for `POST /api/health-data/indicators/device-sync`. */
@Serializable
data class DeviceIndicatorSyncBody(
    val source: String = SOURCE_DEVICE,
    val values: List<DeviceIndicatorSyncValue>,
) {
    init {
        require(source == SOURCE_DEVICE) { "Android Health Connect must use source=device" }
        require(values.isNotEmpty()) { "device-sync values cannot be empty" }
        require(values.size <= MAX_VALUES) { "device-sync accepts at most $MAX_VALUES values" }
    }

    companion object {
        const val SOURCE_DEVICE = "device"
        const val MAX_VALUES = 200
    }
}

@Serializable
data class DeviceIndicatorSyncValue(
    val indicator_name: String,
    val value: Double,
    val unit: String,
    val measured_at: String,
    val source_metric: String,
    val source_id: String,
    val value_kind: String = "numeric",
    val source_local_date: String,
    val timezone_offset_minutes: Int,
    val notes: String = "Health Connect 只读同步",
)

@Serializable
data class DeviceIndicatorSyncIssue(
    val index: Int,
    val code: String,
)

@Serializable
data class DeviceIndicatorSyncResponse(
    val total: Int,
    val inserted: Int,
    val updated: Int,
    val unchanged: Int,
    val rejected: Int,
    val issues: List<DeviceIndicatorSyncIssue> = emptyList(),
    val skipped: Int,
)
