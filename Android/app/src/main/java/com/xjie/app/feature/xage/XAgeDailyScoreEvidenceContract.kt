package com.xjie.app.feature.xage

/**
 * Fail-closed admission and normalization for every daily-score input.
 *
 * Raw OCR/report fields never reach this contract. Server values must already be admitted trend
 * observations and must still pass source, unit, range, date, and freshness validation here.
 */
object XAgeDailyScoreEvidenceContract {
    const val MINIMUM_USABLE_CONFIDENCE = 0.05

    private val admittedServerSources = setOf(
        "document",
        "manual",
        "device",
        "cgm",
        "apple_health",
    )

    private val unitMultipliers: Map<String, Map<String, Double>> = mapOf(
        "HRV/PRV" to mapOf("ms" to 1.0, "毫秒" to 1.0, "s" to 1_000.0, "秒" to 1_000.0),
        "静息心率" to mapOf("bpm" to 1.0, "次/分" to 1.0, "count/min" to 1.0, "1/min" to 1.0),
        "呼吸" to mapOf("bpm" to 1.0, "次/分" to 1.0, "count/min" to 1.0, "1/min" to 1.0),
        "睡眠" to mapOf(
            "h" to 1.0,
            "hr" to 1.0,
            "hour" to 1.0,
            "小时" to 1.0,
            "min" to 1.0 / 60.0,
            "分钟" to 1.0 / 60.0,
        ),
        "hsCRP" to mapOf("mg/l" to 1.0, "mg/dl" to 10.0, "g/l" to 1_000.0),
        "炎症因子" to mapOf("pg/ml" to 1.0, "ng/l" to 1.0),
        "步数" to mapOf("步" to 1.0, "count" to 1.0, "次" to 1.0),
        "运动分钟" to mapOf(
            "min" to 1.0,
            "分钟" to 1.0,
            "h" to 60.0,
            "hr" to 60.0,
            "小时" to 60.0,
        ),
        "活动能量" to mapOf("kcal" to 1.0),
        "体重" to mapOf("kg" to 1.0, "g" to 0.001),
    )

    private val validRanges: Map<String, ClosedFloatingPointRange<Double>> = mapOf(
        "HRV/PRV" to 1.0..500.0,
        "静息心率" to 20.0..250.0,
        "呼吸" to 4.0..80.0,
        "体温" to 30.0..45.0,
        "睡眠" to 0.0..24.0,
        "hsCRP" to 0.0..500.0,
        "WBC" to 0.0..200.0,
        "NLR" to 0.0..100.0,
        "炎症因子" to 0.0..10_000.0,
        "血氧" to 50.0..100.0,
        "体脂率" to 1.0..80.0,
        "步数" to 0.0..200_000.0,
        "运动分钟" to 0.0..1_440.0,
        "活动能量" to 0.0..20_000.0,
        "体重" to 10.0..500.0,
    )

    fun admitsServerSource(source: String): Boolean =
        source.trim().lowercase() in admittedServerSources

    /** Returns the algorithm's canonical unit, or null when the value must not affect a score. */
    fun canonicalValue(value: Double, unit: String?, title: String): Double? {
        if (!value.isFinite()) return null
        val validRange = validRanges[title] ?: return null
        val converted = convert(value, normalizedUnit(unit), title) ?: return null
        return converted.takeIf { it in validRange }
    }

    private fun normalizedUnit(unit: String?): String = unit.orEmpty()
        .trim()
        .lowercase()
        .replace(" ", "")
        .replace("×", "x")
        .replace("⁹", "^9")
        .replace("³", "^3")
        .replace("μ", "u")
        .replace("µ", "u")
        .replace("*", "x")

    private fun convert(value: Double, unit: String, title: String): Double? {
        unitMultipliers[title]?.get(unit)?.let { return value * it }
        return when {
            title == "体温" && unit in setOf("°c", "℃", "c") -> value
            title == "体温" && unit in setOf("°f", "℉", "f") -> (value - 32.0) * 5.0 / 9.0
            title == "WBC" && (
                unit.contains("10^9/l") ||
                    unit.contains("10x9/l") ||
                    unit in setOf("10^3/ul", "k/ul")
                ) -> value
            title == "WBC" && unit in setOf("/ul", "cells/ul") -> value / 1_000.0
            title == "NLR" && (unit.isEmpty() || unit in setOf("ratio", "比值", "1")) -> value
            title in setOf("血氧", "体脂率") && unit in setOf("%", "percent") ->
                if (value <= 1.2) value * 100.0 else value
            else -> null
        }
    }
}
