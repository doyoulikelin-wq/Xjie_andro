package com.xjie.app.feature.medication

import com.xjie.app.core.model.MedicationDoseActionBody
import com.xjie.app.core.model.MedicationDoseEvent
import com.xjie.app.core.model.MedicationPlanConfirmBody
import com.xjie.app.core.model.MedicationPrefillCandidate
import com.xjie.app.core.model.MedicationTodaySummary
import com.xjie.app.core.model.MedicationTodayTask
import com.xjie.app.core.model.MedicationReaction
import com.xjie.app.core.model.MedicationRecognizePrefillResult
import com.xjie.app.core.model.TrustedMedicationPlan
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal enum class MedicationPrimaryAction {
    ConfirmCurrentDose,
    ReviewPrefill,
    AddFirstMedication,
    ViewTodayRecords,
}

internal data class MedicationPlanDraft(
    val genericName: String = "",
    val brandName: String = "",
    val strength: String = "",
    val doseText: String = "",
    val doseQuantity: String = "",
    val frequency: String = "",
    val scheduleTimes: String = "",
    val mealRelation: String = "unspecified",
    val instructions: String = "",
    val courseStart: String = "",
    val courseEnd: String = "",
    val prescriber: String = "",
    val initialQuantity: String = "",
    val inventoryUnit: String = "",
    val isLongTerm: Boolean = false,
)

internal data class MedicationPlanDraftValidation(
    val normalizedTimes: List<String> = emptyList(),
    val doseQuantity: Double? = null,
    val initialQuantity: Double? = null,
    val error: String? = null,
) {
    val isValid: Boolean get() = error == null
}

internal data class MedicationReactionDraft(
    val planId: Long? = null,
    val symptoms: String = "",
    val severity: String = "mild",
    val durationMinutes: String = "",
    val notes: String = "",
)

internal class StableMedicationEventIds(
    private val factory: (String) -> String = { prefix -> "$prefix-${UUID.randomUUID()}" },
) {
    private val ids = mutableMapOf<String, String>()

    fun getOrCreate(operationKey: String, prefix: String): String =
        ids.getOrPut(operationKey) { factory(prefix) }

    fun complete(operationKey: String) {
        ids.remove(operationKey)
    }

    fun clear() {
        ids.clear()
    }
}

internal object MedicationTrustPolicy {
    private val timePattern = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private val actionableStatuses = setOf(
        "upcoming",
        "awaiting_confirmation",
        "snoozed",
        "possibly_missed",
    )

    fun primaryAction(
        today: MedicationTodaySummary?,
        pendingPrefills: List<MedicationPrefillCandidate>,
        plans: List<TrustedMedicationPlan>,
    ): MedicationPrimaryAction {
        if (today?.next_task?.status?.let(actionableStatuses::contains) == true) {
            return MedicationPrimaryAction.ConfirmCurrentDose
        }
        if (pendingPrefills.any { it.review_status == "pending_review" && !it.plan_created }) {
            return MedicationPrimaryAction.ReviewPrefill
        }
        if (plans.none { it.status in setOf("active", "paused") }) {
            return MedicationPrimaryAction.AddFirstMedication
        }
        return MedicationPrimaryAction.ViewTodayRecords
    }

    fun taskStatusLabel(task: MedicationTodayTask): String = when (task.status) {
        "possibly_missed" -> "可能漏服（待确认）"
        "awaiting_confirmation" -> "等待你的确认"
        "snoozed" -> "已稍后提醒，仍待确认"
        "taken" -> if (task.status_assertion == "user_confirmed") {
            "已由你确认服用"
        } else {
            "状态待核对（未取得用户确认凭据）"
        }
        "skipped" -> if (task.status_assertion == "user_confirmed") {
            "已由你确认跳过"
        } else {
            "状态待核对（未取得用户确认凭据）"
        }
        "upcoming" -> "尚未到计划时间"
        else -> task.status_label
    }

    fun canRecordDose(task: MedicationTodayTask): Boolean = task.status in actionableStatuses

    fun canCorrectDose(task: MedicationTodayTask): Boolean =
        task.latest_event_id != null &&
            task.occurrence_version >= 1 &&
            task.status_assertion == "user_confirmed" &&
            task.status in setOf("taken", "skipped")

    fun isTrustedTodaySummary(
        today: MedicationTodaySummary,
        subjectUserId: Long,
    ): Boolean {
        if (today.subject_user_id != subjectUserId) return false
        if (today.missed_assertion_policy != "elapsed_time_never_confirms_missed") return false
        if (today.planned_count != today.tasks.size) return false
        if (today.tasks.any { task ->
                task.status == "possibly_missed" && !task.possibly_missed_is_not_confirmation
            }
        ) return false
        if (today.tasks.any { task ->
                task.status in setOf("taken", "skipped") &&
                    task.status_assertion != "user_confirmed"
            }
        ) return false
        return true
    }

    fun isTrustedSnapshot(
        today: MedicationTodaySummary,
        plans: List<TrustedMedicationPlan>,
        prefills: List<MedicationPrefillCandidate>,
        reactions: List<MedicationReaction>,
    ): Boolean {
        if (!isTrustedTodaySummary(today, today.subject_user_id)) return false
        val nextTask = today.next_task
        if (nextTask != null && today.tasks.none {
                it.occurrence_key == nextTask.occurrence_key &&
                    it.occurrence_version == nextTask.occurrence_version
            }
        ) return false
        if (plans.any { !isTrustedPlan(it, today.subject_user_id) }) return false
        if (prefills.any { candidate ->
                candidate.subject_user_id != today.subject_user_id ||
                    candidate.trust_state != "unconfirmed_prefill" ||
                    !candidate.requires_user_confirmation ||
                    candidate.confirmation_endpoint != "/api/medications/trust/plans/confirm"
            }
        ) return false
        if (reactions.any { !isTrustedReaction(it) }) return false
        return true
    }

    fun isTrustedPlan(plan: TrustedMedicationPlan, subjectUserId: Long): Boolean =
        plan.subject_user_id == subjectUserId &&
            plan.trust_state == "user_confirmed" &&
            plan.reminder_management == "client_managed" &&
            !plan.reminder_default_enabled &&
            !plan.server_notification_scheduled &&
            plan.inventory.is_estimate &&
            plan.inventory.label == "预计剩余" &&
            plan.inventory.basis == "user_confirmed_taken_events_only"

    fun isUnconfirmedRecognizeResult(
        result: MedicationRecognizePrefillResult,
        clientEventId: String,
    ): Boolean = result.client_event_id == clientEventId &&
        result.candidate_id > 0 &&
        result.candidate_version >= 1 &&
        result.trust_state == "unconfirmed_prefill" &&
        result.requires_user_confirmation &&
        !result.plan_created &&
        result.confirmation_endpoint == "/api/medications/trust/plans/confirm"

    fun isTrustedDoseEvent(
        event: MedicationDoseEvent,
        request: MedicationDoseActionBody,
    ): Boolean {
        val expectedStatus = when (request.action) {
            "taken" -> "taken"
            "snooze" -> "snoozed"
            "skip" -> "skipped"
            "correct" -> request.corrected_status ?: return false
            else -> return false
        }
        val expectedNotificationStatus = if (expectedStatus == "snoozed") {
            "client_must_schedule"
        } else {
            "not_requested"
        }
        return event.occurrence_key ==
            "dose:v1:${request.plan_id}:${request.scheduled_local_date}:${request.scheduled_time}" &&
            event.occurrence_version > request.expected_occurrence_version &&
            event.action == request.action &&
            event.effective_status == expectedStatus &&
            (request.action != "correct" ||
                event.supersedes_event_id == request.correction_of_event_id) &&
            event.trust_state == "user_confirmed" &&
            event.reminder_management == "client_managed" &&
            event.notification_schedule_status == expectedNotificationStatus
    }

    fun isTrustedReaction(reaction: MedicationReaction): Boolean =
        reaction.causal_attribution == "temporal_association_only" &&
            reaction.user_facing_causality == "该症状发生在服药后，不能据此认定由药物导致" &&
            (reaction.severity != "severe" || reaction.safety_guidance.isNotBlank())

    fun inventoryLine(plan: TrustedMedicationPlan): String {
        val inventory = plan.inventory
        if (!inventory.is_estimate || inventory.label != "预计剩余" ||
            inventory.basis != "user_confirmed_taken_events_only"
        ) {
            return "预计剩余：暂不可用（仅能按已确认服药记录估算）"
        }
        val value = inventory.estimated_remaining?.let { number ->
            val display = if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()
            listOfNotNull(display, inventory.inventory_unit).joinToString(" ")
        } ?: inventory.unavailable_reason ?: "暂不可用"
        return "预计剩余：$value（仅按已确认服药记录估算）"
    }

    fun validatePlanDraft(draft: MedicationPlanDraft): MedicationPlanDraftValidation {
        if (draft.genericName.isBlank()) {
            return MedicationPlanDraftValidation(error = "请填写药品通用名")
        }
        val times = draft.scheduleTimes
            .split(',', '，', '、', ';', '；', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        if (times.any { !timePattern.matches(it) }) {
            return MedicationPlanDraftValidation(error = "服药时间请使用 24 小时 HH:MM")
        }
        val start = parseDate(draft.courseStart)
        val end = parseDate(draft.courseEnd)
        if (draft.courseStart.isNotBlank() && start == null) {
            return MedicationPlanDraftValidation(error = "开始日期请使用 YYYY-MM-DD")
        }
        if (draft.courseEnd.isNotBlank() && end == null) {
            return MedicationPlanDraftValidation(error = "结束日期请使用 YYYY-MM-DD")
        }
        if (start != null && end != null && end.isBefore(start)) {
            return MedicationPlanDraftValidation(error = "结束日期不能早于开始日期")
        }
        val dose = positiveNumber(draft.doseQuantity)
        if (draft.doseQuantity.isNotBlank() && dose == null) {
            return MedicationPlanDraftValidation(error = "单次数量必须是大于 0 的数字")
        }
        val inventory = nonNegativeNumber(draft.initialQuantity)
        if (draft.initialQuantity.isNotBlank() && inventory == null) {
            return MedicationPlanDraftValidation(error = "初始药量不能是负数")
        }
        if (draft.initialQuantity.isBlank() != draft.inventoryUnit.isBlank()) {
            return MedicationPlanDraftValidation(error = "初始药量和单位需要同时填写")
        }
        return MedicationPlanDraftValidation(
            normalizedTimes = times.sorted(),
            doseQuantity = dose,
            initialQuantity = inventory,
        )
    }

    fun buildDoseAction(
        subjectUserId: Long,
        task: MedicationTodayTask,
        clientEventId: String,
        action: String,
        snoozedUntil: String? = null,
        reason: String? = null,
    ): MedicationDoseActionBody {
        require(canRecordDose(task)) { "completed occurrences cannot be recorded again" }
        require(action in setOf("taken", "snooze", "skip")) { "unsupported dose action" }
        require((action == "snooze") == (snoozedUntil != null)) {
            "snooze must be the only action with snoozed_until"
        }
        return MedicationDoseActionBody(
            subject_user_id = subjectUserId,
            plan_id = task.plan_id,
            expected_plan_version = task.plan_version,
            client_event_id = clientEventId,
            scheduled_local_date = task.scheduled_local_date,
            scheduled_time = task.scheduled_time,
            expected_occurrence_version = task.occurrence_version,
            action = action,
            snoozed_until = snoozedUntil,
            reason = reason?.trim()?.ifBlank { null },
        )
    }

    fun buildDoseCorrection(
        subjectUserId: Long,
        task: MedicationTodayTask,
        clientEventId: String,
        correctedStatus: String,
        reason: String,
    ): MedicationDoseActionBody {
        require(canCorrectDose(task)) { "only a latest user-confirmed occurrence may be corrected" }
        require(correctedStatus in setOf("taken", "skipped", "pending")) {
            "unsupported corrected status"
        }
        return MedicationDoseActionBody(
            subject_user_id = subjectUserId,
            plan_id = task.plan_id,
            expected_plan_version = task.plan_version,
            client_event_id = clientEventId,
            scheduled_local_date = task.scheduled_local_date,
            scheduled_time = task.scheduled_time,
            expected_occurrence_version = task.occurrence_version,
            action = "correct",
            corrected_status = correctedStatus,
            correction_of_event_id = requireNotNull(task.latest_event_id),
            reason = reason.trim().ifBlank { "用户修正当天误操作" },
        )
    }

    fun buildPlanConfirmation(
        subjectUserId: Long,
        draft: MedicationPlanDraft,
        clientEventId: String,
        candidate: MedicationPrefillCandidate? = null,
    ): MedicationPlanConfirmBody {
        val validation = validatePlanDraft(draft)
        require(validation.isValid) { validation.error ?: "invalid medication plan" }
        val sourceType = candidate?.source_type ?: "manual"
        require(candidate == null || candidate.review_status == "pending_review") {
            "only pending prefills may be confirmed"
        }
        return MedicationPlanConfirmBody(
            subject_user_id = subjectUserId,
            client_request_id = "request-$clientEventId",
            client_event_id = clientEventId,
            candidate_id = candidate?.candidate_id,
            candidate_version = candidate?.version,
            generic_name = draft.genericName.trim(),
            brand_name = draft.brandName.trim().ifBlank { null },
            strength = draft.strength.trim().ifBlank { null },
            dose_text = draft.doseText.trim().ifBlank { null },
            dose_quantity = validation.doseQuantity,
            frequency = draft.frequency.trim().ifBlank { null },
            schedule_times = validation.normalizedTimes,
            meal_relation = draft.mealRelation,
            instructions = draft.instructions.trim().ifBlank { null },
            course_start = draft.courseStart.trim().ifBlank { null },
            course_end = draft.courseEnd.trim().ifBlank { null },
            prescriber = draft.prescriber.trim().ifBlank { null },
            initial_quantity = validation.initialQuantity,
            inventory_unit = draft.inventoryUnit.trim().ifBlank { null },
            is_long_term = draft.isLongTerm,
            source_type = sourceType,
            source_ref = candidate?.source_ref,
        )
    }

    fun draftFrom(candidate: MedicationPrefillCandidate): MedicationPlanDraft {
        val extracted = candidate.extracted_data
        val schedule = (extracted["schedule_times"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
            .joinToString("、")
        return MedicationPlanDraft(
            genericName = extracted.string("generic_name"),
            brandName = extracted.string("brand_name"),
            strength = extracted.string("strength"),
            doseText = extracted.string("dose_text"),
            frequency = extracted.string("frequency"),
            scheduleTimes = schedule,
            mealRelation = extracted.string("meal_relation").ifBlank { "unspecified" },
            instructions = extracted.string("instructions"),
            courseStart = extracted.string("course_start"),
            courseEnd = extracted.string("course_end"),
            prescriber = extracted.string("prescriber"),
        )
    }

    private fun parseDate(value: String): LocalDate? = value.trim().takeIf(String::isNotEmpty)?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }

    private fun positiveNumber(value: String): Double? = value.trim().takeIf(String::isNotEmpty)?.let {
        it.toDoubleOrNull()?.takeIf { number -> number.isFinite() && number > 0 }
    }

    private fun nonNegativeNumber(value: String): Double? =
        value.trim().takeIf(String::isNotEmpty)?.let {
            it.toDoubleOrNull()?.takeIf { number -> number.isFinite() && number >= 0 }
        }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}
