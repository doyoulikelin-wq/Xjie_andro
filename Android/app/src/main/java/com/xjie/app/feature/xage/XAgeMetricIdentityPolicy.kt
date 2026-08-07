package com.xjie.app.feature.xage

import java.util.Locale

/**
 * Stable server-indicator identities copied from the current iOS XAGE contract.
 *
 * A server trend and its local/Health Connect candidate must resolve to one ID. Otherwise the
 * dashboard can show duplicate cards and route the real observation to a generic detail page.
 */
internal object XAgeMetricIdentityPolicy {
    private val registeredIds = mapOf(
        "步数" to "steps",
        "步行+跑步距离" to "distance",
        "运动分钟" to "exerciseMinutes",
        "活动分钟数" to "activeMinutes",
        "活动能量" to "activeEnergy",
        "静息能量" to "basalEnergy",
        "爬楼层数" to "flights",
        "骑行距离" to "cyclingDistance",
        "游泳距离" to "swimmingDistance",
        "划水次数" to "swimmingStrokes",
        "推轮椅距离" to "wheelchairDistance",
        "心肺适能" to "vo2Max",
        "身高" to "bodyHeight",
        "体重" to "bodyWeight",
        "bmi" to "bodyMassIndex",
        "体脂率" to "bodyFat",
        "瘦体重" to "leanBodyMass",
        "腰围" to "waistCircumference",
        "体温" to "bodyTemperature",
        "基础体温" to "basalBodyTemperature",
        "心率" to "heartRate",
        "静息心率" to "restingHeartRate",
        "步行心率平均值" to "walkingHeartRateAverage",
        "心率变异性" to "hrv",
        "心率恢复" to "heartRateRecovery",
        "收缩压" to "systolicBloodPressure",
        "舒张压" to "diastolicBloodPressure",
        "睡眠" to "sleep",
        "睡眠评分" to "sleepScore",
        "卧床时间" to "timeInBed",
        "呼吸频率" to "respiratoryRate",
        "血氧" to "bloodOxygen",
        "吸入器使用次数" to "inhalerUsage",
        "血糖波动" to "glucose",
        "血糖" to "bloodGlucose",
        "胰岛素输注" to "insulinDelivery",
        "膳食能量" to "dietaryEnergy",
        "水" to "dietaryWater",
        "碳水化合物" to "dietaryCarbs",
        "蛋白质" to "dietaryProtein",
        "总脂肪" to "dietaryFat",
        "膳食纤维" to "dietaryFiber",
        "咖啡因" to "dietaryCaffeine",
        "正念分钟" to "mindfulMinutes",
        "日照时间" to "daylight",
        "环境噪声级别" to "environmentalAudio",
        "耳机音量" to "headphoneAudio",
        "紫外线指数" to "uvExposure",
        "经期" to "menstrualFlow",
        "点滴出血" to "intermenstrualBleeding",
        "宫颈黏液质量" to "cervicalMucus",
        "排卵测试结果" to "ovulationTest",
        "性活动" to "sexualActivity",
        "症状" to "symptoms",
    )

    fun canonicalId(indicatorName: String): String {
        val normalized = titleKey(indicatorName)
        registeredIds[normalized]?.let { return it }
        return when {
            "hrv" in normalized || "心率变异" in normalized -> "hrv"
            "睡眠" in normalized -> "sleep"
            "血糖" in normalized || "葡萄糖" in normalized -> "glucose"
            "体温" in normalized -> "temp"
            "步数" in normalized -> "steps"
            "步行+跑步距离" in normalized || "步行跑步距离" in normalized -> "distance"
            "活动能量" in normalized -> "activeEnergy"
            "运动分钟" in normalized -> "exerciseMinutes"
            "爬楼" in normalized -> "flights"
            "静息心率" in normalized -> "restingHeartRate"
            "呼吸频率" in normalized -> "respiratoryRate"
            "血氧" in normalized -> "bloodOxygen"
            "收缩压" in normalized -> "systolicBloodPressure"
            "舒张压" in normalized -> "diastolicBloodPressure"
            "体重" in normalized -> "bodyWeight"
            "体脂" in normalized -> "bodyFat"
            "正念" in normalized -> "mindfulMinutes"
            "日照" in normalized -> "daylight"
            else -> "server-$indicatorName"
        }
    }

    fun titleKey(indicatorName: String): String =
        indicatorName.trim().lowercase(Locale.ROOT)

    fun isLegacyCombinedBloodPressure(indicatorName: String): Boolean =
        indicatorName.trim() == "血压"
}
