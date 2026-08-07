package com.xjie.app.feature.xage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XAgeMetricIdentityPolicyTest {
    @Test
    fun serverAndCandidateMetricsShareTheCompleteIosIdentityPolicyWithoutWeightDuplicates() {
        val exactIosMappings = mapOf(
            "  体重  " to "bodyWeight",
            "血糖" to "bloodGlucose",
            "血糖波动" to "glucose",
            "基础体温" to "basalBodyTemperature",
            "心率变异性" to "hrv",
            "步行+跑步距离" to "distance",
            "睡眠评分" to "sleepScore",
            "BMI" to "bodyMassIndex",
            "经期" to "menstrualFlow",
            "症状" to "symptoms",
        )
        exactIosMappings.forEach { (name, expected) ->
            assertEquals(name, expected, XAgeMetricIdentityPolicy.canonicalId(name))
        }
        assertEquals("bodyWeight", XAgeMetricIdentityPolicy.canonicalId("家用体重计"))
        assertEquals("server-肌酣", XAgeMetricIdentityPolicy.canonicalId("肌酣"))
        assertTrue(XAgeMetricIdentityPolicy.isLegacyCombinedBloodPressure(" 血压 "))

        val visibleIds = listOf(
            XAgeMetricIdentityPolicy.canonicalId("体重"),
            "bodyWeight",
        ).distinct()
        assertEquals(listOf("bodyWeight"), visibleIds)
    }
}
