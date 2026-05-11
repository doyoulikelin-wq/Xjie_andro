package com.xjie.app.core.model

/** 血糖单位 — 与 iOS [Units.swift] 对齐。后端始终 mg/dL，仅展示层换算。 */
enum class GlucoseUnit(val raw: String, val label: String) {
    MGDL("mg_dl", "mg/dL"),
    MMOL("mmol_l", "mmol/L");

    companion object {
        const val MGDL_PER_MMOL = 18.018
        fun fromRaw(s: String?): GlucoseUnit = entries.firstOrNull { it.raw == s } ?: MMOL
    }
}

object GlucoseFormat {
    fun mgdlToMmol(mgdl: Double): Double = mgdl / GlucoseUnit.MGDL_PER_MMOL

    fun format(mgdl: Double?, unit: GlucoseUnit, withUnit: Boolean = true): String {
        if (mgdl == null || mgdl.isNaN()) return "--"
        return when (unit) {
            GlucoseUnit.MMOL -> {
                val s = String.format(java.util.Locale.US, "%.1f", mgdlToMmol(mgdl))
                if (withUnit) "$s mmol/L" else s
            }
            GlucoseUnit.MGDL -> {
                val s = String.format(java.util.Locale.US, "%.0f", mgdl)
                if (withUnit) "$s mg/dL" else s
            }
        }
    }

    fun threshold(mgdl: Double, unit: GlucoseUnit): String =
        format(mgdl, unit, withUnit = false)
}
