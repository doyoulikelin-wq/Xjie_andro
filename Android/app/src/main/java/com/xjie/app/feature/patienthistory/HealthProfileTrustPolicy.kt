package com.xjie.app.feature.patienthistory

import com.xjie.app.core.model.HealthProfileTrustCandidate
import com.xjie.app.core.model.HealthProfileTrustFact
import com.xjie.app.core.model.HealthProfileGoalMetricBody
import com.xjie.app.core.model.HealthProfileLongTermMedicationSummaryItem
import com.xjie.app.core.model.HealthProfilePrimaryAction
import com.xjie.app.core.model.HealthProfileTrustSource
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

enum class HealthProfileResponseState(val wireValue: String, val label: String) {
    Value("value", "填写内容"),
    None("none", "明确没有"),
    NotApplicable("not_applicable", "不适用"),
    PreferNotToAnswer("prefer_not_to_answer", "暂不回答"),
}

enum class HealthProfileGoalStatus(val wireValue: String, val label: String) {
    Active("active", "进行中"),
    Paused("paused", "已暂停"),
    Completed("completed", "已完成"),
    Archived("archived", "已归档"),
}

enum class HealthProfileGoalAction(val wireValue: String, val label: String) {
    Pause("pause", "暂停"),
    Resume("resume", "继续"),
    Complete("complete", "标记完成"),
    Archive("archive", "归档删除"),
}

enum class HealthProfileAnswerState { Unanswered, Answered }

data class HealthProfileMedicationDisplayField(val title: String, val value: String)

data class HealthProfileDerivedBmi(
    val value: Double?,
    val sourceDescription: String,
    val updatedAt: String?,
)

data class HealthProfileFieldDefinition(
    val factKey: String,
    val category: String,
    val title: String,
    val hint: String,
    val safetyCritical: Boolean = false,
)

internal object HealthProfileTrustPolicy {
    val fields = listOf(
        HealthProfileFieldDefinition("basic.birth_date", "basic", "出生日期", "用于年龄相关判断；可只填写到月份。"),
        HealthProfileFieldDefinition("basic.sex", "basic", "生理性别", "用于参考范围和特定健康风险判断。"),
        HealthProfileFieldDefinition("basic.height", "basic", "身高", "请同时写明单位，例如 168 cm。"),
        HealthProfileFieldDefinition("basic.blood_type", "basic", "血型", "不知道也可选择“不愿回答”或暂不处理。"),
        HealthProfileFieldDefinition("basic.region", "basic", "常住地区", "城市或地区即可，不需要详细地址。"),
        HealthProfileFieldDefinition("basic.lifestyle", "basic", "生活方式", "吸烟、饮酒、睡眠、运动等长期情况。"),
        HealthProfileFieldDefinition("long_term_health.diagnoses", "long_term_health", "明确诊断", "仅填写医生已明确诊断的长期问题。"),
        HealthProfileFieldDefinition("long_term_health.family_history", "long_term_health", "家族史", "直系亲属的重要疾病史。"),
        HealthProfileFieldDefinition("long_term_health.recent_findings", "long_term_health", "长期异常指标", "只记录已经确认、需要长期关注的异常。"),
        HealthProfileFieldDefinition("long_term_health.risk_factor", "long_term_health", "长期风险因素", "例如吸烟史、长期久坐或夜班。"),
        HealthProfileFieldDefinition("long_term_health.active_concern", "long_term_health", "主动关注问题", "例如希望持续关注血脂变化。"),
        HealthProfileFieldDefinition("safety.medication_allergy", "safety", "药物过敏", "修改或删除都需要再次确认。", true),
        HealthProfileFieldDefinition("safety.other_allergy", "safety", "其他过敏", "包括食物、材料和明确不耐受。", true),
        HealthProfileFieldDefinition("safety.contraindication", "safety", "禁忌事项", "医生明确告知应避免的药物或行为。", true),
        HealthProfileFieldDefinition("safety.pregnancy_or_breastfeeding", "safety", "妊娠或哺乳", "影响用药和检查建议。", true),
        HealthProfileFieldDefinition("safety.major_surgery", "safety", "重大手术", "填写时间和手术名称；没有可选择“明确无”。", true),
        HealthProfileFieldDefinition("safety.important_condition", "safety", "重要健康状况", "可能影响建议安全性的疾病或状态。", true),
        HealthProfileFieldDefinition("safety.clinician_restriction", "safety", "医生限制", "例如运动、饮食或用药限制。", true),
    )

    val goalRequirement = HealthProfileFieldDefinition(
        "goal.primary",
        "goal",
        "健康目标",
        "通过多目标列表主动添加。",
    )

    fun field(factKey: String): HealthProfileFieldDefinition? =
        fields.firstOrNull { it.factKey == factKey } ?: goalRequirement.takeIf { it.factKey == factKey }

    fun responseStateOrNull(data: JsonObject): HealthProfileResponseState? =
        HealthProfileResponseState.entries.firstOrNull {
            it.wireValue == (data["response_state"] as? JsonPrimitive)?.contentOrNull
        }

    fun responseState(data: JsonObject): HealthProfileResponseState =
        responseStateOrNull(data) ?: HealthProfileResponseState.Value

    fun answerState(fact: HealthProfileTrustFact?): HealthProfileAnswerState =
        if (fact != null && responseStateOrNull(fact.value_data) != null) {
            HealthProfileAnswerState.Answered
        } else {
            HealthProfileAnswerState.Unanswered
        }

    fun displayValue(data: JsonObject): String = when (responseState(data)) {
        HealthProfileResponseState.Value -> data["value"]?.let(::renderValue)
            ?: repeatedAbnormalSummary(data)
            ?: "已确认"
        HealthProfileResponseState.None -> "明确没有"
        HealthProfileResponseState.NotApplicable -> "不适用"
        HealthProfileResponseState.PreferNotToAnswer -> "暂不回答"
    }

    fun candidateLabel(candidate: HealthProfileTrustCandidate): String =
        if (candidate.review_status == "conflict") "与现有画像冲突" else "待确认更新"

    fun independentReportCount(candidate: HealthProfileTrustCandidate): Int =
        (candidate.proposed_value["occurrence_count"] as? JsonPrimitive)?.intOrNull
            ?: candidate.sources.map { it.source_ref }.distinct().size

    fun canAutoConfirm(candidate: HealthProfileTrustCandidate): Boolean = false

    fun canReviewCandidate(candidate: HealthProfileTrustCandidate, action: String): Boolean =
        action in setOf("accept", "reject") &&
            candidate.review_status in setOf("pending_review", "conflict") &&
            (candidate.category != "goal" || action == "reject") &&
            !((candidate.category == "safety" || candidate.is_safety_critical) && action == "accept")

    fun isSupportedPrimaryAction(action: HealthProfilePrimaryAction?): Boolean = action != null &&
        action.item_count >= 0 &&
        action.kind in setOf("review_updates", "complete_profile", "edit_profile") &&
        action.route in setOf("profile_updates", "profile_safety_editor", "profile_editor")

    fun primaryActionTitle(action: HealthProfilePrimaryAction?): String = when (action?.kind) {
        "review_updates" -> "检查 ${action.item_count} 项更新"
        "complete_profile" -> "完善 ${action.item_count} 项资料"
        "edit_profile" -> "编辑健康画像"
        else -> "画像状态暂不可用"
    }

    fun primaryActionStatus(action: HealthProfilePrimaryAction?): String = when (action?.kind) {
        "review_updates" -> "有 ${action.item_count} 项更新等待你决定"
        "complete_profile" -> "还有 ${action.item_count} 项资料可完善"
        "edit_profile" -> "画像已更新"
        else -> "服务端暂未返回可执行的画像状态"
    }

    fun isServerConfirmed(fact: HealthProfileTrustFact): Boolean =
        fact.confirmation_method in setOf("user", "clinician", "verified_source")

    fun hasUnsavedEditor(
        editingFactKey: String?,
        initialResponseState: HealthProfileResponseState,
        initialValue: String,
        responseState: HealthProfileResponseState,
        value: String,
    ): Boolean = editingFactKey != null &&
        (initialResponseState != responseState || initialValue != value)

    fun splitMetrics(raw: String): List<String> = raw
        .split(Regex("[，,、\\n]"))
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    fun isValidGoalDate(raw: String): Boolean {
        val normalized = raw.trim()
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(normalized)) return false
        return runCatching { LocalDate.parse(normalized) }.isSuccess
    }

    fun goalStatus(raw: String): HealthProfileGoalStatus? =
        HealthProfileGoalStatus.entries.firstOrNull { it.wireValue == raw }

    fun goalMetricRequests(raw: String): List<HealthProfileGoalMetricBody>? {
        val known = mapOf(
            "睡眠时长" to "sleep_duration",
            "hrv" to "hrv",
            "体重" to "weight",
            "步数" to "steps",
            "血压" to "blood_pressure",
            "血糖" to "glucose",
        )
        val seen = mutableSetOf<String>()
        return splitMetrics(raw).map { display ->
            val normalized = display.lowercase()
            val key = known[display] ?: known[normalized] ?: normalized
            if (!Regex("^[a-z0-9_.:-]+$").matches(key) || !seen.add(key)) return null
            HealthProfileGoalMetricBody(metric_key = key, display_label = display)
        }
    }

    fun allowsGoalAction(action: HealthProfileGoalAction, status: HealthProfileGoalStatus): Boolean =
        when (status to action) {
            HealthProfileGoalStatus.Active to HealthProfileGoalAction.Pause,
            HealthProfileGoalStatus.Paused to HealthProfileGoalAction.Resume,
            HealthProfileGoalStatus.Active to HealthProfileGoalAction.Complete,
            HealthProfileGoalStatus.Paused to HealthProfileGoalAction.Complete,
            HealthProfileGoalStatus.Active to HealthProfileGoalAction.Archive,
            HealthProfileGoalStatus.Paused to HealthProfileGoalAction.Archive,
            HealthProfileGoalStatus.Completed to HealthProfileGoalAction.Archive -> true
            else -> false
        }

    fun sourceLabel(sources: List<HealthProfileTrustSource>): String {
        if (sources.isEmpty()) return "来源明细待服务端补充"
        return sources.map { source ->
            when (source.source_type) {
                "report_observation", "report", "health_report", "confirmed_observation" -> "已确认报告"
                "apple_health" -> "Apple Health"
                "device" -> if (source.source_ref.contains("apple", ignoreCase = true)) {
                    "Apple Health"
                } else {
                    "Health Connect"
                }
                "medication" -> "用药记录"
                "health_plan" -> "健康计划"
                "medical_record" -> "就医记录"
                "ai_suggestion" -> "AI 建议补充"
                "manual", "user" -> "用户填写"
                else -> "其他来源"
            }
        }.distinct().joinToString("、")
    }

    fun medicationDisplayFields(
        item: HealthProfileLongTermMedicationSummaryItem,
    ): List<HealthProfileMedicationDisplayField> = listOf(
        HealthProfileMedicationDisplayField("药名", item.medication_name),
        HealthProfileMedicationDisplayField("用途", item.purpose?.trim().takeUnless { it.isNullOrEmpty() } ?: "未填写"),
        HealthProfileMedicationDisplayField("开始时间", item.started_on?.trim().takeUnless { it.isNullOrEmpty() } ?: "未填写"),
        HealthProfileMedicationDisplayField("是否仍在服用", if (item.is_still_taking) "是" else "否"),
        HealthProfileMedicationDisplayField("来源", medicationSourceLabel(item.source)),
        HealthProfileMedicationDisplayField("最近确认", timestamp(item.last_confirmed_at)),
    )

    fun medicationSourceLabel(source: String): String = when (source) {
        "prescription" -> "已确认处方"
        "user_added" -> "用户添加"
        "ocr_confirmed" -> "识别后确认"
        "history_confirmed" -> "历史用药确认"
        else -> "服务端来源"
    }

    fun timestamp(raw: String?): String = raw?.takeIf(String::isNotBlank)
        ?.replace('T', ' ')
        ?.take(16)
        ?: "时间未知"

    fun derivedBmi(facts: List<HealthProfileTrustFact>): HealthProfileDerivedBmi {
        val height = facts.firstOrNull { it.fact_key == "basic.height" }
        val weight = facts.firstOrNull { it.fact_key == "basic.weight" }
        if (height == null || weight == null ||
            !isServerConfirmed(height) ||
            !isServerConfirmed(weight) ||
            responseState(height.value_data) != HealthProfileResponseState.Value ||
            responseState(weight.value_data) != HealthProfileResponseState.Value
        ) {
            return HealthProfileDerivedBmi(
                null,
                "需要已确认且带单位的身高与体重；无法安全解析时不会猜测。",
                null,
            )
        }
        val heightCm = parseMeasurement(height.value_data["value"], height = true)
        val weightKg = parseMeasurement(weight.value_data["value"], height = false)
        if (heightCm == null || weightKg == null) {
            return HealthProfileDerivedBmi(
                null,
                "需要已确认且带单位的身高与体重；无法安全解析时不会猜测。",
                maxOf(height.updated_at, weight.updated_at),
            )
        }
        val bmi = weightKg / ((heightCm / 100) * (heightCm / 100))
        if (!bmi.isFinite() || bmi !in 8.0..80.0) {
            return HealthProfileDerivedBmi(
                null,
                "身高或体重超出可安全派生范围，请先核对原始事实。",
                maxOf(height.updated_at, weight.updated_at),
            )
        }
        return HealthProfileDerivedBmi(
            bmi,
            "由已确认身高（${sourceLabel(height.sources)}）与体重（${sourceLabel(weight.sources)}）透明计算，不是用户单独填写项。",
            maxOf(height.updated_at, weight.updated_at),
        )
    }

    private fun parseMeasurement(value: JsonElement?, height: Boolean): Double? = when (value) {
        is JsonPrimitive -> {
            if (!value.isString) {
                null // A bare number has no trustworthy unit.
            } else {
                parseMeasurementString(value.content, height)
            }
        }
        is JsonObject -> {
            val directKey = if (height) "height_cm" else "weight_kg"
            val direct = (value[directKey] as? JsonPrimitive)?.doubleOrNull
            direct?.let { validateMeasurement(it, height) } ?: run {
                val nested = value["value"]
                val unit = (value["unit"] as? JsonPrimitive)?.contentOrNull
                if (nested is JsonPrimitive && !nested.isString && unit != null) {
                    parseMeasurementString("${nested.content} $unit", height)
                } else {
                    parseMeasurement(nested, height)
                }
            }
        }
        else -> null
    }

    private fun parseMeasurementString(raw: String, height: Boolean): Double? {
        val normalized = raw.trim().lowercase()
        val number = Regex("\\d+(?:\\.\\d+)?").find(normalized)?.value?.toDoubleOrNull() ?: return null
        val converted = if (height) {
            when {
                listOf("cm", "厘米", "公分").any(normalized::contains) -> number
                normalized.contains("米") || normalized.endsWith("m") -> number * 100
                else -> return null
            }
        } else {
            when {
                listOf("kg", "千克", "公斤").any(normalized::contains) -> number
                normalized.contains("克") || normalized.endsWith("g") -> number / 1000
                else -> return null
            }
        }
        return validateMeasurement(converted, height)
    }

    private fun validateMeasurement(value: Double, height: Boolean): Double? =
        if (value in if (height) 80.0..250.0 else 20.0..400.0) value else null

    private fun renderValue(value: JsonElement): String = when (value) {
        is JsonPrimitive -> value.contentOrNull ?: value.toString()
        is JsonArray -> value.map(::renderValue).joinToString("、")
        is JsonObject -> {
            value.entries
                .filterNot { it.key in setOf("dose", "dosage", "reminder", "taken_events") }
                .joinToString(" · ") { (key, nested) -> "${fieldLabel(key)}：${renderValue(nested)}" }
                .ifBlank { "详情暂不可用" }
        }
        else -> value.toString()
    }

    private fun fieldLabel(key: String): String = when (key) {
        "count" -> "数量"
        "name", "names" -> "药品"
        "purpose" -> "用途"
        "start_date", "started_at" -> "开始时间"
        "active" -> "仍在服用"
        "source" -> "来源"
        "last_confirmed_at" -> "最近确认"
        "related_metrics" -> "关联指标"
        else -> key.replace('_', ' ')
    }

    private fun repeatedAbnormalSummary(data: JsonObject): String? {
        val name = (data["canonical_name"] as? JsonPrimitive)?.contentOrNull ?: return null
        val count = (data["occurrence_count"] as? JsonPrimitive)?.contentOrNull
        val value = (data["latest_value_numeric"] as? JsonPrimitive)?.contentOrNull
            ?: (data["latest_value_text"] as? JsonPrimitive)?.contentOrNull
        val unit = (data["latest_unit"] as? JsonPrimitive)?.contentOrNull
        return buildString {
            append(name)
            count?.let { append("，已确认报告中重复异常 ").append(it).append(" 次") }
            value?.let { append("；最近值 ").append(it) }
            unit?.takeIf(String::isNotBlank)?.let { append(" ").append(it) }
        }
    }
}
