package com.xjie.app.feature.weight

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.IndicatorTrend
import com.xjie.app.core.model.TrendPoint
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Raw account-bound response. No value is displayable until [WeightDashboardPolicy] admits it. */
data class WeightNetworkSnapshot(
    val trends: List<IndicatorTrend>,
    val profileHeightCm: Double?,
)

enum class WeightEvidenceSource(val label: String) {
    Manual("手动记录"),
    Device("Health Connect / 设备同步"),
    AppleHealth("Apple 健康同步"),
    ConfirmedReport("已确认报告"),
}

data class WeightTrendSample(
    val id: String,
    val date: LocalDate,
    val measuredAt: Instant,
    val weightKg: Double,
    val source: WeightEvidenceSource,
)

data class WeightAxisDomain(
    val lowerKg: Double,
    val upperKg: Double,
) {
    init {
        require(lowerKg.isFinite() && upperKg.isFinite() && lowerKg < upperKg)
    }
}

data class WeightDashboardPresentation(
    val latestWeightKg: Double?,
    val latestDate: LocalDate?,
    val latestSource: WeightEvidenceSource?,
    val heightCm: Double?,
    val bmi: Double?,
    val recentSamples: List<WeightTrendSample>,
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val sourceLabels: List<String>,
) {
    val hasWeight: Boolean get() = latestWeightKg != null
    val needsHeight: Boolean get() = heightCm == null
}

data class WeightPickerSelection(val integer: Int, val tenth: Int)

/**
 * Pure parity and trust boundary shared by the screen, state machine, and JVM regressions.
 *
 * The trend endpoint is server account-scoped. `document` rows are admitted observations because
 * the backend projects only confirmed report observations into that endpoint. Device families
 * additionally require stable source identity so an uncontrolled/legacy row cannot impersonate a
 * Health Connect round trip.
 */
object WeightDashboardPolicy {
    const val WEIGHT_INDICATOR = "体重"
    const val HEIGHT_INDICATOR = "身高"
    const val WEIGHT_SOURCE_METRIC = "bodyWeight"
    const val HEIGHT_SOURCE_METRIC = "bodyHeight"
    const val REQUEST_NAMES = "$WEIGHT_INDICATOR,$HEIGHT_INDICATOR"
    const val HEIGHT_ERROR = "数据范围异常，请填写正确数字。"
    const val WEIGHT_ERROR = "体重范围异常，请选择 20.0–250.9 公斤。"

    val validHeightRange: IntRange = 60..210
    val weightIntegerRange: IntRange = 20..250
    val weightTenthRange: IntRange = 0..9

    fun presentation(
        snapshot: WeightNetworkSnapshot,
        today: LocalDate,
    ): WeightDashboardPresentation {
        val allWeights = admittedWeightSamples(
            snapshot.trends.firstOrNull { it.name.trim() == WEIGHT_INDICATOR },
            today,
        )
        val latest = allWeights.lastOrNull()
        val height = admittedProfileHeight(snapshot.profileHeightCm)
            ?: admittedHeightCentimeters(
                snapshot.trends.firstOrNull { it.name.trim() == HEIGHT_INDICATOR },
                today,
            )
        val start = today.minusMonths(3)
        val recent = allWeights.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
        val sources = allWeights.map { it.source.label }.distinct()

        return WeightDashboardPresentation(
            latestWeightKg = latest?.weightKg,
            latestDate = latest?.date,
            latestSource = latest?.source,
            heightCm = height,
            bmi = bodyMassIndex(latest?.weightKg, height),
            recentSamples = recent,
            windowStart = start,
            windowEnd = today,
            sourceLabels = sources,
        )
    }

    fun admittedWeightSamples(
        trend: IndicatorTrend?,
        today: LocalDate,
    ): List<WeightTrendSample> {
        if (trend == null || trend.name.trim() != WEIGHT_INDICATOR) return emptyList()
        if (!isWeightUnit(trend.unit)) return emptyList()

        return trend.points.mapIndexedNotNull { index, point ->
            val evidence = evidenceSource(point, WEIGHT_SOURCE_METRIC) ?: return@mapIndexedNotNull null
            if (!isNumeric(point) || !validWeight(point.value)) return@mapIndexedNotNull null
            val date = localDate(point) ?: return@mapIndexedNotNull null
            if (date.isAfter(today)) return@mapIndexedNotNull null
            val measuredAt = measuredAt(point, date) ?: return@mapIndexedNotNull null
            WeightTrendSample(
                id = sampleIdentity(point, evidence, measuredAt, index),
                date = date,
                measuredAt = measuredAt,
                weightKg = point.value,
                source = evidence,
            )
        }
            .sortedWith(
                compareBy<WeightTrendSample>(WeightTrendSample::date)
                    .thenBy(WeightTrendSample::measuredAt)
                    .thenBy(WeightTrendSample::id),
            )
            .distinctBy(WeightTrendSample::id)
    }

    fun admittedHeightCentimeters(
        trend: IndicatorTrend?,
        today: LocalDate,
    ): Double? {
        if (trend == null || trend.name.trim() != HEIGHT_INDICATOR) return null
        if (!isHeightUnit(trend.unit)) return null

        return trend.points.mapIndexedNotNull { index, point ->
            val evidence = evidenceSource(point, HEIGHT_SOURCE_METRIC) ?: return@mapIndexedNotNull null
            if (!isNumeric(point) || !validHeight(point.value)) return@mapIndexedNotNull null
            val date = localDate(point) ?: return@mapIndexedNotNull null
            if (date.isAfter(today)) return@mapIndexedNotNull null
            val measuredAt = measuredAt(point, date) ?: return@mapIndexedNotNull null
            HeightSample(
                id = sampleIdentity(point, evidence, measuredAt, index),
                measuredAt = measuredAt,
                date = date,
                heightCm = point.value,
            )
        }
            .sortedWith(
                compareBy<HeightSample>(HeightSample::date)
                    .thenBy(HeightSample::measuredAt)
                    .thenBy(HeightSample::id),
            )
            .lastOrNull()
            ?.heightCm
    }

    fun bodyMassIndex(weightKg: Double?, heightCm: Double?): Double? {
        if (weightKg == null || !validWeight(weightKg)) return null
        if (heightCm == null || !validHeight(heightCm)) return null
        val heightMeters = heightCm / 100.0
        return weightKg / (heightMeters * heightMeters)
    }

    fun weightAxisDomain(valuesKg: List<Double>): WeightAxisDomain {
        val values = valuesKg.filter { it.isFinite() }
        if (values.isEmpty()) return WeightAxisDomain(45.0, 75.0)
        return WeightAxisDomain(
            lowerKg = requireNotNull(values.minOrNull()) - 5.0,
            upperKg = requireNotNull(values.maxOrNull()) + 5.0,
        )
    }

    fun weightAxisTicks(valuesKg: List<Double>): List<Int> {
        val domain = weightAxisDomain(valuesKg)
        val step = (domain.upperKg - domain.lowerKg) / 3.0
        return (0..3).map { index -> (domain.lowerKg + step * index).roundToInt() }
    }

    /** Mirrors iOS: approximately fifteen calendar days per visible chart viewport. */
    fun chartContentWidthDp(
        windowStart: LocalDate,
        windowEnd: LocalDate,
        viewportWidthDp: Float,
    ): Float {
        val viewport = viewportWidthDp.coerceAtLeast(1f)
        val days = ChronoUnit.DAYS.between(windowStart, windowEnd).coerceAtLeast(15L)
        return (viewport * days.toFloat() / 15f).coerceIn(viewport, 6_000f)
    }

    fun pickerSelection(weightKg: Double?): WeightPickerSelection {
        if (weightKg == null || !weightKg.isFinite()) return WeightPickerSelection(65, 0)
        val tenths = (weightKg * 10.0).roundToInt().coerceIn(
            weightIntegerRange.first * 10,
            weightIntegerRange.last * 10 + weightTenthRange.last,
        )
        return WeightPickerSelection(tenths / 10, tenths % 10)
    }

    fun weightFromPicker(integer: Int, tenth: Int): Double? {
        if (integer !in weightIntegerRange || tenth !in weightTenthRange) return null
        return integer.toDouble() + tenth.toDouble() / 10.0
    }

    fun validatedHeight(input: String): Int? {
        if (input.length !in 2..3 || input.any { !it.isDigit() }) return null
        return input.toIntOrNull()?.takeIf(validHeightRange::contains)
    }

    fun appendHeightDigit(input: String, digit: Int): String {
        if (digit !in 0..9 || input.length >= 3) return input
        return if (input == "0") digit.toString() else input + digit
    }

    fun deleteHeightDigit(input: String): String = input.dropLast(1)

    fun validWeight(value: Double): Boolean =
        value.isFinite() && value in 20.0..250.9

    fun validHeight(value: Double): Boolean =
        value.isFinite() && value >= validHeightRange.first && value <= validHeightRange.last

    private fun admittedProfileHeight(value: Double?): Double? = value?.takeIf(::validHeight)

    private fun isWeightUnit(raw: String?): Boolean = normalizeUnit(raw) in setOf("kg", "公斤", "千克")

    private fun isHeightUnit(raw: String?): Boolean = normalizeUnit(raw) in setOf("cm", "厘米")

    private fun normalizeUnit(raw: String?): String = raw.orEmpty()
        .trim()
        .lowercase(Locale.ROOT)
        .replace(" ", "")

    private fun isNumeric(point: TrendPoint): Boolean =
        point.value_kind.isNullOrBlank() || point.value_kind.equals("numeric", ignoreCase = true)

    private fun evidenceSource(
        point: TrendPoint,
        requiredSourceMetric: String,
    ): WeightEvidenceSource? = when (point.source?.trim()?.lowercase(Locale.ROOT)) {
        "manual" -> WeightEvidenceSource.Manual
        "document" -> WeightEvidenceSource.ConfirmedReport
        "device", "health_connect" -> if (hasStableDeviceIdentity(point, requiredSourceMetric)) {
            WeightEvidenceSource.Device
        } else {
            null
        }
        "apple_health" -> if (hasStableDeviceIdentity(point, requiredSourceMetric)) {
            WeightEvidenceSource.AppleHealth
        } else {
            null
        }
        else -> null
    }

    private fun hasStableDeviceIdentity(point: TrendPoint, requiredSourceMetric: String): Boolean =
        !point.source_id.isNullOrBlank() &&
            point.source_metric?.trim()?.equals(requiredSourceMetric, ignoreCase = true) == true

    private fun localDate(point: TrendPoint): LocalDate? {
        val raw = point.source_local_date?.trim()?.takeIf { it.isNotEmpty() }
            ?: point.date.trim()
        if (raw.length < 10) return null
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    private fun measuredAt(point: TrendPoint, fallbackDate: LocalDate): Instant? {
        val raw = point.measured_at?.trim().orEmpty()
        if (raw.isEmpty()) return fallbackDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    }

    private fun sampleIdentity(
        point: TrendPoint,
        evidence: WeightEvidenceSource,
        measuredAt: Instant,
        index: Int,
    ): String = point.source_id?.trim()?.takeIf { it.isNotEmpty() }?.let {
        "${evidence.name}:$it"
    } ?: "${evidence.name}:$measuredAt:${point.value}:$index"

    private data class HeightSample(
        val id: String,
        val measuredAt: Instant,
        val date: LocalDate,
        val heightCm: Double,
    )
}

/** Single active request token; owner equality includes subject and monotonic auth generation. */
data class WeightRequestToken(
    val owner: AuthManager.AccountScopeSnapshot,
    val sequence: Long,
) {
    fun accepts(
        active: WeightRequestToken?,
        currentOwner: AuthManager.AccountScopeSnapshot?,
    ): Boolean = this == active && owner == currentOwner
}
