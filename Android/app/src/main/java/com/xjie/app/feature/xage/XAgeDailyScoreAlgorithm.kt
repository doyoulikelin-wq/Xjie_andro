package com.xjie.app.feature.xage

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Deterministic local algorithm for the three versioned daily reference estimates. */
object XAgeDailyScoreAlgorithm {
    private data class Evidence(
        val title: String,
        val value: Double,
        val displayValue: String,
        val confidence: Double,
        val measuredAt: Instant?,
        val rawName: String?,
        val unit: String?,
        val source: String?,
    )

    private data class WeightedFeature(
        val title: String,
        val score: Double,
        val confidence: Double,
        val weight: Double,
        val displayValue: String,
        val note: String,
    ) {
        val field: XAgeScoreField
            get() = XAgeScoreField(title, displayValue)
        val driver: XAgeScoreDriver
            get() = XAgeScoreDriver(title, displayValue, note)
        val driverStrength: Double
            get() = abs(score - 50.0) * confidence * weight
    }

    private data class WeightedResult(
        val score: Double,
        val confidence: Int,
        val drivers: List<XAgeScoreDriver>,
        val fields: List<XAgeScoreField>,
        val availableSignalCount: Int,
        val targetSignalCount: Int,
    )

    fun compute(context: XAgeAlgorithmContext): XAgeCompositeScores = XAgeCompositeScores(
        pressure = makePressure(context),
        recovery = makeRecovery(context),
        inflammation = makeInflammation(context),
        // This module deliberately never computes or exposes a local XAge value.
        xAge = XAgeTrustedScorePresentationPolicy.unavailable.xAge,
    )

    private fun makePressure(context: XAgeAlgorithmContext): XAgeMetricScore {
        val features = buildList {
            evidence(context, "hrv", listOf("心率变异性", "hrv", "sdnn", "rmssd"), "HRV/PRV")?.let {
                add(feature("HRV/PRV", hrvSuppressionBad(it.value), it, 18.0, "HRV/PRV 越低，算法把交感负荷子分打得越高。"))
            }
            evidence(context, "restingHeartRate", listOf("静息心率", "rhr", "restingheartrate"), "静息心率")?.let {
                add(feature("静息心率", rhrBad(it.value), it, 18.0, "静息心率高于基线时，压力子分上调。"))
            }
            evidence(context, "respiratoryRate", listOf("呼吸频率", "呼吸率", "respiratory", "respiration"), "呼吸")?.let {
                add(feature("呼吸", respirationBad(it.value), it, 10.0, "呼吸频率偏离个人常态时，压力子分按偏离幅度上调。"))
            }
            evidence(context, "bodyTemperature", listOf("体温", "temperature", "temp"), "体温")?.let {
                add(feature("体温", temperatureBad(it.value), it, 6.0, "体温偏离按低权重进入压力分。", confidenceScale = 0.86))
            }
            activityLoad(context)?.let {
                add(WeightedFeature("活动负荷", it.score, it.confidence, 8.0, it.displayValue, "活动负荷越高，短期压力子分越高。"))
            }
            evidence(context, "sleep", listOf("睡眠", "sleep"), "睡眠")?.let {
                add(feature("睡眠债", sleepDebtBad(it.value), it, 8.0, "睡眠低于 7 小时时，睡眠债子分上调压力分。"))
            }
        }
        val result = weightedResult(features, 68.0, 6.0, 3.0, null, 50.0)
        val value = result.score.roundToInt()
        val hasAutonomic = features.any { it.title == "HRV/PRV" || it.title == "静息心率" }
        val ready = result.confidence >= 35 && features.size >= 3 && hasAutonomic
        val needsSupport = result.confidence < XAgeScoreStatusPresentation.LOW_CONFIDENCE_THRESHOLD
        val missing = missingSignalLabels(
            features,
            listOf(
                "HRV" to setOf("HRV/PRV"),
                "静息心率" to setOf("静息心率"),
                "睡眠" to setOf("睡眠债"),
                "活动" to setOf("活动负荷"),
                "呼吸" to setOf("呼吸"),
                "体温" to setOf("体温"),
            ),
        )
        return XAgeMetricScore(
            value = value,
            confidence = result.confidence,
            isReady = ready,
            badgeLabel = if (needsSupport) "低置信参考" else pressureBadge(value),
            stateLabel = if (needsSupport) "压力低置信参考分" else pressureState(value),
            summary = if (needsSupport) "当前仍给出压力参考分，但相关近期数据覆盖或质量不足，结果可能明显变化。" else pressureSummary(value),
            simpleExplanation = "压力分看的是身体是否处在“紧绷和占用恢复资源”的状态。HRV 降低、静息心率升高、睡眠不足或负荷过高时，分数会上升；数据不足时仍给参考分，并用外环提示低置信度。",
            explanation = "压力分先把 HRV/PRV 抑制、静息心率、呼吸频率、睡眠债、活动负荷和体温偏移换算为 0-100 子分，再按权重加权平均。",
            nextAction = if (needsSupport) {
                "当前是低置信参考分。先同步 Health Connect 中的近期相关数据；如果没有可穿戴数据，可以在指标详情里手动记录。"
            } else if (value >= 70) {
                "先降低刺激并做 2 分钟延长呼气，再复测心率和 HRV；这些输入会直接改变下一次压力分。"
            } else {
                "保持当前睡眠、补水和短时走动节律；这些输入会把 HRV、心率和睡眠债维持在低负荷区间。"
            },
            fields = scoreFields(result.fields, result.confidence, needsSupport, missing),
            drivers = scoreDrivers(result.drivers, needsSupport, "补齐压力输入", "每补齐一类近期相关信号，外环完整度都会提高；单次 HRV 或心率只支撑低置信参考分。"),
            isProxy = false,
            confidenceSignalCount = coveredSignalCount(result.targetSignalCount, missing),
            confidenceSignalTarget = result.targetSignalCount,
            confidenceMissingSignals = missing,
        )
    }

    private fun makeRecovery(context: XAgeAlgorithmContext): XAgeMetricScore {
        val hrv = evidence(context, "hrv", listOf("心率变异性", "hrv", "sdnn", "rmssd"), "HRV/PRV")
        val sleep = evidence(context, "sleep", listOf("睡眠", "sleep"), "睡眠")
        val features = buildList {
            hrv?.let { add(feature("HRV/PRV", hrvGood(it.value), it, 25.0, "HRV/PRV 越高且越接近个人稳定区间，恢复子分越高。")) }
            evidence(context, "restingHeartRate", listOf("静息心率", "rhr", "restingheartrate"), "静息心率")?.let {
                add(feature("静息心率", rhrGood(it.value), it, 15.0, "静息心率越接近基线，恢复子分越高。"))
            }
            sleep?.let { add(feature("睡眠", sleepGood(it.value), it, 20.0, "睡眠时长和连续性直接决定睡眠恢复子分。")) }
            stabilityGood(context)?.let {
                add(WeightedFeature("生理稳定性", it.score, it.confidence, 12.0, it.displayValue, "呼吸、血氧和体温越接近稳定区间，恢复分越高。"))
            }
            activityLoad(context)?.let {
                add(WeightedFeature("前日/今日负荷", 100.0 - it.score, it.confidence, 10.0, it.displayValue, "活动负荷越高，恢复分按负荷权重下调。"))
            }
        }
        val caps = listOfNotNull(if (hrv == null) 55.0 else null, if (sleep == null) 70.0 else null)
        val result = weightedResult(features, 82.0, 5.0, 3.0, caps.minOrNull(), 50.0)
        val value = result.score.roundToInt()
        val ready = result.confidence > 0 && features.size >= 2
        val needsSupport = result.confidence < XAgeScoreStatusPresentation.LOW_CONFIDENCE_THRESHOLD
        val missing = missingSignalLabels(
            features,
            listOf(
                "HRV" to setOf("HRV/PRV"),
                "睡眠" to setOf("睡眠"),
                "静息心率" to setOf("静息心率"),
                "生理稳定性" to setOf("生理稳定性"),
                "活动负荷" to setOf("前日/今日负荷"),
            ),
        )
        return XAgeMetricScore(
            value = value,
            confidence = result.confidence,
            isReady = ready,
            badgeLabel = if (needsSupport) "低置信参考" else recoveryBadge(value),
            stateLabel = if (needsSupport) "恢复低置信参考分" else recoveryState(value),
            summary = if (needsSupport) "当前仍给出恢复参考分，但相关近期数据覆盖或质量不足，结果可能明显变化。" else recoverySummary(value),
            simpleExplanation = "恢复分看的是身体有没有回到稳定状态。HRV 越稳定、睡眠越充分、静息心率和呼吸越平稳，恢复越好；缺少 HRV 或睡眠时会降低置信度并限制分数上限。",
            explanation = "恢复分先把 HRV/PRV、静息心率、昨夜睡眠、呼吸/血氧/体温稳定性和前日/今日负荷换算为 0-100 子分，再按权重加权。",
            nextAction = if (needsSupport) {
                "当前是低置信参考分。先同步 Health Connect 中的近期 HRV、睡眠、静息心率和呼吸/血氧，新增相关信号会提高外环完整度。"
            } else if (value >= 67) {
                "今天可以安排挑战任务；算法依据是 HRV、睡眠和稳定性子分都在较高区间。"
            } else {
                "今天把任务强度降一档，优先补水、低强度活动和提前睡眠；这些动作对应恢复分的主要输入。"
            },
            fields = scoreFields(result.fields, result.confidence, needsSupport, missing),
            drivers = scoreDrivers(result.drivers, needsSupport, "补齐恢复输入", "HRV 和睡眠缺失时会降低置信度并限制分数上限；每新增一类有效信号都会提高外环完整度。"),
            isProxy = false,
            confidenceSignalCount = coveredSignalCount(result.targetSignalCount, missing),
            confidenceSignalTarget = result.targetSignalCount,
            confidenceMissingSignals = missing,
        )
    }

    private fun makeInflammation(context: XAgeAlgorithmContext): XAgeMetricScore {
        val hscrp = evidence(context, null, listOf("hscrp", "crp", "超敏c反应蛋白", "c反应蛋白"), "hsCRP")
        val wbc = evidence(context, null, listOf("白细胞", "wbc"), "WBC")?.takeIf(::credibleBloodWhiteCell)
        val nlr = evidence(context, null, listOf("nlr", "中性粒细胞淋巴细胞比值"), "NLR")
        val cytokine = evidence(context, null, listOf("il6", "白介素6", "tnf"), "炎症因子")
        val hasLab = listOf(hscrp, wbc, nlr, cytokine).any { it != null }
        val features = buildList {
            hscrp?.let { add(feature("hsCRP", hscrpBad(it.value), it, 30.0, if (it.value > 10) "hsCRP 超过 10 时按急性异常上限处理，并降低本次慢性评分权重。" else "hsCRP 作为实验室锚点直接进入炎症主权重。")) }
            if (nlr != null) {
                add(feature("CBC/NLR", nlrBad(nlr.value), nlr, 16.0, "NLR 越高，CBC/NLR 子分越高。"))
            } else if (wbc != null) {
                add(feature("CBC/WBC", wbcBad(wbc.value), wbc, 16.0, "白细胞超出血常规区间时，CBC/WBC 子分上调炎症分。"))
            }
            cytokine?.let { add(feature("炎症因子", cytokineBad(it.value), it, 14.0, "IL-6/TNFα 有值时按炎症因子主权重进入模型。")) }
            evidence(context, "bodyTemperature", listOf("体温", "temperature", "temp"), "体温")?.let {
                add(feature("体温", temperatureBad(it.value), it, if (hasLab) 8.0 else 20.0, "体温偏离按体温子分进入模型；无实验室锚点时权重提高。", confidenceScale = 0.86))
            }
            evidence(context, "restingHeartRate", listOf("静息心率", "rhr", "restingheartrate"), "静息心率")?.let {
                add(feature("静息心率", rhrBad(it.value), it, if (hasLab) 7.0 else 18.0, "静息心率越高，身体小火苗代理子分越高。"))
            }
            evidence(context, "hrv", listOf("心率变异性", "hrv", "sdnn", "rmssd"), "HRV/PRV")?.let {
                add(feature("HRV/PRV", hrvSuppressionBad(it.value), it, if (hasLab) 6.0 else 16.0, "HRV/PRV 越低，慢性负荷代理子分越高。"))
            }
            evidence(context, "respiratoryRate", listOf("呼吸频率", "呼吸率", "respiratory", "respiration"), "呼吸")?.let {
                add(feature("呼吸", respirationBad(it.value), it, if (hasLab) 4.0 else 12.0, "呼吸偏离按偏离幅度提高代理子分。"))
            }
            evidence(context, "bloodOxygen", listOf("血氧", "spo2", "氧饱和"), "血氧")?.let {
                add(feature("血氧", oxygenBad(it.value), it, if (hasLab) 2.0 else 6.0, "血氧低于稳定区间时，提高呼吸/睡眠复核子分。"))
            }
            if (!hasLab) {
                sleepOrOverloadBad(context)?.let {
                    add(WeightedFeature("睡眠/负荷", it.score, it.confidence, 8.0, it.displayValue, "睡眠债和过度负荷直接提高身体小火苗代理分。"))
                }
            }
        }
        val cap = if (hasLab) if ((hscrp?.value ?: 0.0) > 10.0) 70.0 else null else 55.0
        val result = weightedResult(
            features,
            if (hasLab) 87.0 else 80.0,
            if (hasLab) 8.0 else 6.0,
            if (hasLab) 3.0 else 2.0,
            cap,
            50.0,
        )
        val value = result.score.roundToInt()
        val ready = result.confidence > 0 && features.size >= 2
        val needsSupport = result.confidence < XAgeScoreStatusPresentation.LOW_CONFIDENCE_THRESHOLD
        val expected = if (hasLab) {
            listOf(
                "hsCRP" to setOf("hsCRP"),
                "血常规/NLR" to setOf("CBC/NLR", "CBC/WBC"),
                "炎症因子" to setOf("炎症因子"),
                "体温" to setOf("体温"),
                "静息心率" to setOf("静息心率"),
                "HRV" to setOf("HRV/PRV"),
                "呼吸" to setOf("呼吸"),
                "血氧" to setOf("血氧"),
            )
        } else {
            listOf(
                "实验室锚点" to emptySet(),
                "体温" to setOf("体温"),
                "静息心率" to setOf("静息心率"),
                "HRV" to setOf("HRV/PRV"),
                "呼吸/血氧" to setOf("呼吸", "血氧"),
                "睡眠/活动" to setOf("睡眠/负荷"),
            )
        }
        val missing = missingSignalLabels(features, expected)
        return XAgeMetricScore(
            value = value,
            confidence = result.confidence,
            isReady = ready,
            badgeLabel = if (needsSupport) "低置信参考" else inflammationBadge(value),
            stateLabel = if (needsSupport) "炎症低置信参考分" else inflammationState(value, !hasLab),
            summary = if (needsSupport) "当前仍给出低置信参考分；相关近期数据覆盖或质量不足，没有实验室锚点时它只是身体小火苗代理，不代表炎症诊断。" else inflammationSummary(value, !hasLab),
            simpleExplanation = if (hasLab) {
                "炎症分先看报告里的炎症锚点，再用体温、心率、HRV、呼吸和血氧补充判断。实验室指标直接反映炎症相关反应，所以权重最高。"
            } else {
                "当前没有报告里的炎症锚点，小捷只看到体温、心率、睡眠等辅助信号，因此首页显示低置信度的身体小火苗代理分；它只提示身体负荷，不能单独说明炎症。"
            },
            explanation = if (hasLab) {
                "炎症分优先把 hsCRP、CBC/NLR、IL-6/TNFα 换算为实验室子分，并给这些子分最高权重；再加入体温、静息心率、HRV、呼吸和血氧作为补充。"
            } else {
                "当前没有可信实验室锚点，算法启用“身体小火苗”代理信号。该代理信号只表示算法风险负荷，不是炎症诊断。"
            },
            nextAction = if (needsSupport) {
                "继续同步 Health Connect；上传并确认近期血常规、hsCRP 或体检化验报告后，实验室锚点会替代代理项并提高置信度。"
            } else if (value >= 60) {
                "先记录体温、症状、睡眠、饮酒和训练；连续偏高时上传最新报告，实验室锚点会参与重算炎症分。"
            } else {
                "继续同步 Health Connect 和上传报告；新增已确认实验室锚点会提高置信度。"
            },
            fields = scoreFields(
                if (hasLab) result.fields else listOf(XAgeScoreField("类型", "代理信号")) + result.fields,
                result.confidence,
                needsSupport,
                missing,
            ),
            drivers = scoreDrivers(result.drivers, needsSupport, "补齐炎症输入", "每新增一类有效信号都会提高外环完整度；没有实验室锚点时只提供低置信度代理分，不是炎症诊断。"),
            isProxy = !hasLab,
            confidenceSignalCount = coveredSignalCount(result.targetSignalCount, missing),
            confidenceSignalTarget = result.targetSignalCount,
            confidenceMissingSignals = missing,
        )
    }

    private fun feature(
        title: String,
        score: Double,
        evidence: Evidence,
        weight: Double,
        note: String,
        confidenceScale: Double = 1.0,
    ) = WeightedFeature(
        title,
        score,
        evidence.confidence * confidenceScale,
        weight,
        evidence.displayValue,
        note,
    )

    private fun weightedResult(
        features: List<WeightedFeature>,
        expectedWeight: Double?,
        requiredSignals: Double,
        requiredDomains: Double,
        cap: Double?,
        fallback: Double,
    ): WeightedResult {
        val usable = features.filter {
            it.confidence >= XAgeDailyScoreEvidenceContract.MINIMUM_USABLE_CONFIDENCE &&
                it.score.isFinite() &&
                it.weight > 0.0
        }
        val targetSignalCount = max(1, ceil(requiredSignals).toInt())
        if (usable.isEmpty()) {
            return WeightedResult(
                score = fallback,
                confidence = 0,
                drivers = listOf(XAgeScoreDriver("数据不足", "中性先验", "当前参考分使用中性先验；同步 Health Connect 或上传报告后，算法会用真实输入替代。")),
                fields = listOf(XAgeScoreField("数据状态", "建立基线中")),
                availableSignalCount = 0,
                targetSignalCount = targetSignalCount,
            )
        }
        val fixedExpectedWeight = max(
            expectedWeight ?: features.sumOf { it.weight },
            usable.sumOf { it.weight },
        )
        val denominator = usable.sumOf { it.weight * it.confidence }
        val numerator = usable.sumOf { it.weight * it.confidence * it.score }
        val coverage = denominator / fixedExpectedWeight
        val effectiveSignalCount = usable.sumOf { min(1.0, it.confidence / 0.75) }
        val sampleFactor = min(1.0, sqrt(effectiveSignalCount / requiredSignals))
        val domainBalance = min(1.0, effectiveSignalCount / requiredDomains)
        var confidence = 100.0 *
            coverage.pow(0.55) *
            sampleFactor.pow(0.25) *
            domainBalance.pow(0.20) *
            0.94
        cap?.let { confidence = min(confidence, it) }
        val sorted = usable.sortedByDescending { it.driverStrength }
        return WeightedResult(
            score = clamp(numerator / denominator),
            confidence = clamp(confidence).roundToInt(),
            drivers = sorted.take(4).map { it.driver },
            fields = usable.take(8).map { it.field },
            availableSignalCount = min(targetSignalCount, usable.size),
            targetSignalCount = targetSignalCount,
        )
    }

    private fun evidence(
        context: XAgeAlgorithmContext,
        metricId: String?,
        aliases: List<String>,
        title: String,
    ): Evidence? {
        val candidates = mutableListOf<Evidence>()
        if (metricId != null) {
            candidates += context.samples.filter { it.metricId == metricId }.mapNotNull { sample ->
                val unit = sample.displayUnit.ifBlank { sample.unit }
                val value = XAgeDailyScoreEvidenceContract.canonicalValue(sample.value, unit, title)
                    ?: return@mapNotNull null
                Evidence(
                    title = title,
                    value = value,
                    displayValue = listOf(sample.displayValue, unit).filter { it.isNotBlank() }.joinToString(" "),
                    confidence = sampleConfidence(sample, context.referenceDate),
                    measuredAt = sample.measuredAt,
                    rawName = sample.indicatorName,
                    unit = unit,
                    source = "health_connect",
                )
            }
        }
        val normalizedAliases = aliases.map(XAgeAlgorithmTrend::normalizedKey)
        candidates += context.serverTrends.filter { trend ->
            val key = XAgeAlgorithmTrend.normalizedKey(trend.name)
            normalizedAliases.any { alias -> key.contains(alias) || alias.contains(key) }
        }.mapNotNull { trend ->
            if (!XAgeDailyScoreEvidenceContract.admitsServerSource(trend.source)) return@mapNotNull null
            val value = XAgeDailyScoreEvidenceContract.canonicalValue(trend.value, trend.unit, title)
                ?: return@mapNotNull null
            Evidence(
                title = title,
                value = value,
                displayValue = trend.resolvedDisplayValue,
                confidence = serverTrendConfidence(trend, context.referenceDate),
                measuredAt = parseDate(trend.measuredAt),
                rawName = trend.name,
                unit = trend.unit,
                source = trend.source,
            )
        }
        return candidates
            .filter { it.confidence >= XAgeDailyScoreEvidenceContract.MINIMUM_USABLE_CONFIDENCE }
            .maxWithOrNull(
                compareBy<Evidence> { it.confidence }
                    .thenBy { it.measuredAt ?: Instant.MIN }
                    .thenBy { "${it.source.orEmpty()}|${it.displayValue}" },
            )
    }

    private fun sampleConfidence(sample: XAgeDailyScoreSample, referenceDate: Instant): Double {
        val days = max(0.0, Duration.between(sample.measuredAt, referenceDate).seconds / 86_400.0)
        return clamp(0.95 * exp(-days / 21.0), 0.0, 0.95)
    }

    private fun serverTrendConfidence(trend: XAgeAlgorithmTrend, referenceDate: Instant): Double {
        if (!XAgeDailyScoreEvidenceContract.admitsServerSource(trend.source)) return 0.0
        val measuredAt = parseDate(trend.measuredAt) ?: return 0.0
        val days = max(0.0, Duration.between(measuredAt, referenceDate).seconds / 86_400.0)
        val source = trend.source.lowercase()
        val freshnessWindow = when (source) {
            "document" -> 180.0
            "manual" -> 60.0
            else -> 21.0
        }
        val sourceCeiling = when (source) {
            "document" -> 0.86
            "manual" -> 0.72
            else -> 0.90
        }
        return clamp(min(trend.confidence, sourceCeiling) * exp(-days / freshnessWindow), 0.0, sourceCeiling)
    }

    private fun parseDate(raw: String?): Instant? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    private data class Aggregate(val score: Double, val confidence: Double, val displayValue: String)

    private fun activityLoad(context: XAgeAlgorithmContext): Aggregate? {
        val steps = evidence(context, "steps", listOf("步数", "steps"), "步数")
        val exercise = evidence(context, "exerciseMinutes", listOf("运动分钟", "exercise"), "运动分钟")
        val energy = evidence(context, "activeEnergy", listOf("活动能量", "activeenergy", "kcal"), "活动能量")
        val values = listOfNotNull(steps, exercise, energy)
        if (values.isEmpty()) return null
        val stepLoad = steps?.let { linear(it.value, 9_000.0, 16_000.0, 18.0, 86.0) } ?: 0.0
        val exerciseLoad = exercise?.let { linear(it.value, 45.0, 120.0, 18.0, 88.0) } ?: 0.0
        val energyLoad = energy?.let { linear(it.value, 450.0, 900.0, 18.0, 86.0) } ?: 0.0
        return Aggregate(
            score = max(stepLoad, max(exerciseLoad, energyLoad)),
            confidence = values.map { it.confidence }.average(),
            displayValue = values.take(2).joinToString(" · ") { it.displayValue },
        )
    }

    private fun stabilityGood(context: XAgeAlgorithmContext): Aggregate? {
        val parts = buildList<Pair<Double, Evidence>> {
            evidence(context, "respiratoryRate", listOf("呼吸频率", "呼吸率", "respiratory"), "呼吸")?.let {
                add(100.0 - respirationBad(it.value) to it)
            }
            evidence(context, "bloodOxygen", listOf("血氧", "spo2", "氧饱和"), "血氧")?.let {
                add(100.0 - oxygenBad(it.value) to it)
            }
            evidence(context, "bodyTemperature", listOf("体温", "temperature", "temp"), "体温")?.let {
                add(100.0 - temperatureBad(it.value) to it)
            }
        }
        if (parts.isEmpty()) return null
        return Aggregate(
            parts.map { it.first }.average(),
            parts.map { it.second.confidence }.average(),
            parts.take(2).joinToString(" · ") { it.second.displayValue },
        )
    }

    private fun sleepOrOverloadBad(context: XAgeAlgorithmContext): Aggregate? {
        val parts = buildList<Pair<Double, Pair<Double, String>>> {
            evidence(context, "sleep", listOf("睡眠", "sleep"), "睡眠")?.let {
                add(sleepDebtBad(it.value) to (it.confidence to it.displayValue))
            }
            activityLoad(context)?.let { add(it.score to (it.confidence to it.displayValue)) }
        }
        if (parts.isEmpty()) return null
        return Aggregate(
            parts.maxOf { it.first },
            parts.map { it.second.first }.average(),
            parts.take(2).joinToString(" · ") { it.second.second },
        )
    }

    private fun missingSignalLabels(
        features: List<WeightedFeature>,
        expected: List<Pair<String, Set<String>>>,
    ): List<String> {
        val available = features
            .filter { it.confidence >= XAgeDailyScoreEvidenceContract.MINIMUM_USABLE_CONFIDENCE }
            .mapTo(mutableSetOf()) { it.title }
        return expected.mapNotNull { (label, titles) -> label.takeUnless { titles.any(available::contains) } }
    }

    private fun coveredSignalCount(target: Int, missing: List<String>): Int =
        (target - missing.size).coerceIn(0, target)

    private fun scoreFields(
        fields: List<XAgeScoreField>,
        confidence: Int,
        needsSupport: Boolean,
        missing: List<String>,
    ): List<XAgeScoreField> = if (!needsSupport) {
        fields + XAgeScoreField("数据完整度", "$confidence%")
    } else {
        listOf(
            XAgeScoreField("评估状态", "低置信参考分"),
            XAgeScoreField("还需要", missing.ifEmpty { listOf("更新近期数据") }.joinToString("、")),
            XAgeScoreField("数据完整度", "$confidence%"),
        ) + fields.take(3)
    }

    private fun scoreDrivers(
        drivers: List<XAgeScoreDriver>,
        needsSupport: Boolean,
        title: String,
        note: String,
    ): List<XAgeScoreDriver> = if (!needsSupport) {
        drivers
    } else {
        listOf(XAgeScoreDriver(title, "低置信", note)) + drivers.take(2)
    }

    private fun credibleBloodWhiteCell(evidence: Evidence): Boolean {
        val name = (evidence.rawName ?: evidence.title).lowercase()
        val normalizedName = XAgeAlgorithmTrend.normalizedKey(name)
        val unit = evidence.unit.orEmpty().lowercase()
        val display = evidence.displayValue.lowercase()
        if (listOf(display, unit, name).any(::urineSedimentLike)) return false
        if (listOf("尿", "沉渣", "镜检", "上皮", "粪").any(normalizedName::contains)) return false
        val compactUnit = unit.replace(" ", "").replace("×", "x").replace("*", "x")
        val hasBloodUnit = compactUnit.contains("/l") &&
            listOf("10", "e9", "^9").any(compactUnit::contains)
        val hasBloodName = listOf("白细胞计数", "血白细胞", "血常规", "全血", "cbc").any(normalizedName::contains) ||
            normalizedName == "wbc"
        return hasBloodUnit || hasBloodName
    }

    private fun urineSedimentLike(text: String): Boolean =
        listOf("/hp", "/lp", "个/hp", "个/lp").any(text.lowercase()::contains)

    private fun hrvGood(value: Double) = linear(value, 18.0, 65.0, 25.0, 95.0)
    private fun hrvSuppressionBad(value: Double) = 100.0 - hrvGood(value)
    private fun rhrGood(value: Double) = if (value <= 58.0) 92.0 else 100.0 - linear(value, 58.0, 88.0, 18.0, 88.0)
    private fun rhrBad(value: Double) = 100.0 - rhrGood(value)
    private fun respirationBad(value: Double) = linear(abs(value - 16.0), 2.0, 8.0, 12.0, 88.0)
    private fun temperatureBad(value: Double): Double {
        val deviation = if (value > 30.0) abs(value - 36.7) else abs(value)
        return linear(deviation, 0.2, 1.1, 12.0, 86.0)
    }
    private fun oxygenBad(value: Double): Double = when {
        value >= 97.0 -> 10.0
        value >= 95.0 -> linear(97.0 - value, 0.0, 2.0, 16.0, 38.0)
        else -> linear(95.0 - value, 0.0, 6.0, 48.0, 90.0)
    }
    private fun sleepGood(hours: Double): Double = when {
        hours in 7.0..9.0 -> 92.0
        hours < 7.0 -> linear(hours, 4.0, 7.0, 28.0, 88.0)
        else -> clamp(92.0 - (hours - 9.0) * 16.0, 55.0, 92.0)
    }
    private fun sleepDebtBad(hours: Double) = if (hours >= 7.0) 14.0 else linear(7.0 - hours, 0.0, 3.0, 18.0, 88.0)
    private fun hscrpBad(value: Double): Double = when {
        value < 1.0 -> 18.0
        value < 3.0 -> linear(value, 1.0, 3.0, 35.0, 58.0)
        value <= 10.0 -> linear(value, 3.0, 10.0, 62.0, 92.0)
        else -> 95.0
    }
    private fun wbcBad(value: Double): Double = when {
        value in 4.0..10.0 -> 20.0
        value < 4.0 -> linear(4.0 - value, 0.0, 2.0, 32.0, 72.0)
        else -> linear(value, 10.0, 16.0, 42.0, 88.0)
    }
    private fun nlrBad(value: Double) = if (value < 2.5) 22.0 else linear(value, 2.5, 5.5, 38.0, 86.0)
    private fun cytokineBad(value: Double) = linear(value, 2.0, 10.0, 28.0, 88.0)

    private fun pressureBadge(value: Int) = when {
        value >= 70 -> "压力偏高"
        value >= 40 -> "压力中等"
        else -> "压力偏低"
    }
    private fun pressureState(value: Int) = if (value >= 70) "压力偏高" else if (value >= 40) "压力中等" else "压力较低"
    private fun pressureSummary(value: Int) = if (value >= 70) "压力输入处在高负荷区间；先降低刺激并复测。" else "压力负荷处在可管理区间。"
    private fun recoveryBadge(value: Int) = when {
        value >= 67 -> "恢复良好"
        value >= 34 -> "恢复一般"
        else -> "恢复偏低"
    }
    private fun recoveryState(value: Int) = if (value >= 67) "恢复较好" else if (value >= 34) "恢复一般" else "恢复偏低"
    private fun recoverySummary(value: Int) = if (value >= 67) "恢复输入处在高分区间，可以承接适度挑战。" else "恢复输入处在保守区间，今天降低强度并补齐睡眠。"
    private fun inflammationBadge(value: Int) = when {
        value >= 70 -> "小火苗高"
        value >= 40 -> "炎症关注"
        else -> "小火苗低"
    }
    private fun inflammationState(value: Int, proxy: Boolean) = when {
        value >= 70 -> if (proxy) "小火苗偏高" else "炎症负荷偏高"
        value >= 40 -> if (proxy) "小火苗中等" else "炎症负荷中等"
        else -> if (proxy) "小火苗较低" else "炎症负荷较低"
    }
    private fun inflammationSummary(value: Int, proxy: Boolean): String = if (proxy) {
        if (value >= 60) "代理信号处在高位，体温和症状记录会参与下一次重算。" else "代理信号处在低位，实验室数据会替代当前代理项。"
    } else {
        if (value >= 60) "实验室和生理信号处在复核区间。" else "炎症负荷处于较低区间。"
    }

    private fun linear(value: Double, low: Double, high: Double, minScore: Double, maxScore: Double): Double {
        if (high <= low) return minScore
        val ratio = (value - low) / (high - low)
        return clamp(minScore + ratio * (maxScore - minScore))
    }

    private fun clamp(value: Double, lower: Double = 0.0, upper: Double = 100.0) =
        min(max(value, lower), upper)
}
