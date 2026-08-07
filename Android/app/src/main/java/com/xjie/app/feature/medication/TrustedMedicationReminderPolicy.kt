package com.xjie.app.feature.medication

import com.xjie.app.core.model.MedicationPrefillCandidate
import com.xjie.app.core.model.MedicationTodaySummary
import com.xjie.app.core.model.TrustedMedicationPlan
import com.xjie.app.core.auth.AuthManager
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
data class TrustedMedicationReminderSettings(
    val planId: Long,
    val planVersion: Int,
    val subjectUserId: Long,
    val genericName: String,
    val doseText: String? = null,
    val instructions: String? = null,
    val scheduleTimes: List<String> = emptyList(),
    val mealRelation: String = "unspecified",
    val courseStart: String? = null,
    val confirmedCourseEnd: String? = null,
    val enabled: Boolean = false,
    val cadence: String = CADENCE_DAILY,
    val anchorDate: String,
    val advanceMinutes: Int = 0,
    val snoozeMinutes: Int = 15,
    val reminderEndDate: String? = null,
    val soundEnabled: Boolean = true,
    val showMedicationNameOnLockScreen: Boolean = false,
    /** Opaque account owner plus selected subject/generation; old unbound snapshots fail closed. */
    val accountScope: String = "",
    val selectedSubjectId: String = "",
    val authGeneration: Long = -1L,
    val timezoneId: String = "",
) {
    companion object {
        const val CADENCE_DAILY = "daily"
        const val CADENCE_ALTERNATE_DAYS = "alternate_days"
    }
}

internal data class MedicationReminderOwner(
    val accountScope: String,
    val selectedSubjectId: String,
    val authGeneration: Long,
) {
    companion object {
        fun from(snapshot: AuthManager.AccountScopeSnapshot): MedicationReminderOwner =
            MedicationReminderOwner(
                accountScope = snapshot.accountScope,
                selectedSubjectId = snapshot.subjectId,
                authGeneration = snapshot.generation,
            )
    }
}

@Serializable
internal data class TrustedMedicationSnoozeSnapshot(
    val occurrenceKey: String,
    val planId: Long,
    val genericName: String,
    val doseText: String? = null,
    val scheduledTime: String,
    val triggerAtMillis: Long,
    val soundEnabled: Boolean,
    val showMedicationNameOnLockScreen: Boolean,
    val subjectUserId: Long = -1L,
    val planVersion: Int = -1,
    val accountScope: String = "",
    val selectedSubjectId: String = "",
    val authGeneration: Long = -1L,
    val timezoneId: String = "",
)

internal data class TrustedReminderValidation(
    val normalizedTimes: List<LocalTime> = emptyList(),
    val error: String? = null,
) {
    val isValid: Boolean get() = error == null
}

internal data class MedicationConfirmedRate(
    val confirmedCount: Int,
    val plannedCount: Int,
) {
    val percentage: Int? = if (plannedCount == 0) {
        null
    } else {
        (confirmedCount * 100.0 / plannedCount).roundToInt()
    }

    val label: String = percentage?.let { "已确认率 $it%（$confirmedCount/$plannedCount）" }
        ?: "已确认率：暂无计划"
}

internal data class MedicationCoursePresentation(
    val dateRange: String,
    val elapsedDays: Int?,
    val endingSoon: Boolean,
    val refillEligibility: String,
    val confirmedRate: String,
)

internal enum class MedicationAddAction {
    PrescriptionImport,
    RawOcrText,
    HistoryRestart,
    Manual,
}

internal data class MedicationAddCapability(
    val action: MedicationAddAction,
    val title: String,
    val description: String,
    val available: Boolean,
    val candidate: MedicationPrefillCandidate? = null,
)

internal object TrustedMedicationReminderPolicy {
    private val cadenceValues = setOf(
        TrustedMedicationReminderSettings.CADENCE_DAILY,
        TrustedMedicationReminderSettings.CADENCE_ALTERNATE_DAYS,
    )

    fun defaults(
        plan: TrustedMedicationPlan,
        today: LocalDate = LocalDate.now(),
        owner: MedicationReminderOwner? = null,
        timezoneId: String = ZoneId.systemDefault().id,
    ): TrustedMedicationReminderSettings = TrustedMedicationReminderSettings(
        planId = plan.plan_id,
        planVersion = plan.version,
        subjectUserId = plan.subject_user_id,
        genericName = plan.generic_name,
        doseText = plan.dose_text,
        instructions = plan.instructions,
        scheduleTimes = plan.schedule_times,
        mealRelation = plan.meal_relation,
        courseStart = plan.course_start,
        confirmedCourseEnd = plan.course_end,
        enabled = false,
        cadence = TrustedMedicationReminderSettings.CADENCE_DAILY,
        anchorDate = plan.course_start ?: today.toString(),
        reminderEndDate = plan.course_end,
        soundEnabled = true,
        showMedicationNameOnLockScreen = false,
        accountScope = owner?.accountScope.orEmpty(),
        selectedSubjectId = owner?.selectedSubjectId.orEmpty(),
        authGeneration = owner?.authGeneration ?: -1L,
        timezoneId = if (owner == null) "" else timezoneId,
    )

    fun isCurrentForPlan(
        settings: TrustedMedicationReminderSettings,
        plan: TrustedMedicationPlan,
    ): Boolean = settings.planId == plan.plan_id &&
        settings.planVersion == plan.version &&
        settings.subjectUserId == plan.subject_user_id &&
        settings.genericName == plan.generic_name &&
        settings.scheduleTimes == plan.schedule_times &&
        settings.courseStart == plan.course_start &&
        settings.confirmedCourseEnd == plan.course_end &&
        plan.status == "active"

    fun isCurrentForPlan(
        settings: TrustedMedicationReminderSettings,
        plan: TrustedMedicationPlan,
        owner: MedicationReminderOwner,
        timezoneId: String,
    ): Boolean = isCurrentForPlan(settings, plan) &&
        isOwnedBy(settings, owner, timezoneId)

    fun isOwnedBy(
        settings: TrustedMedicationReminderSettings,
        owner: MedicationReminderOwner,
        timezoneId: String,
    ): Boolean = settings.accountScope == owner.accountScope &&
        settings.selectedSubjectId == owner.selectedSubjectId &&
        settings.authGeneration == owner.authGeneration &&
        settings.timezoneId == timezoneId

    fun isOwnedBy(
        snapshot: TrustedMedicationSnoozeSnapshot,
        owner: MedicationReminderOwner,
        timezoneId: String,
    ): Boolean = snapshot.accountScope == owner.accountScope &&
        snapshot.selectedSubjectId == owner.selectedSubjectId &&
        snapshot.authGeneration == owner.authGeneration &&
        snapshot.timezoneId == timezoneId

    fun validate(settings: TrustedMedicationReminderSettings): TrustedReminderValidation {
        if (settings.planId <= 0 || settings.planVersion < 1 || settings.subjectUserId <= 0) {
            return TrustedReminderValidation(error = "提醒缺少可信用药计划身份，请刷新后重试")
        }
        if (settings.genericName.isBlank()) {
            return TrustedReminderValidation(error = "提醒缺少药品名称")
        }
        val times = MedicationReminderPolicy.normalizedTimes(settings.scheduleTimes)
        if (settings.enabled && times.isEmpty()) {
            return TrustedReminderValidation(error = "开启提醒前，请先在用药计划中确认至少一个服药时间")
        }
        if (settings.scheduleTimes.size != times.size) {
            return TrustedReminderValidation(error = "提醒时间必须使用 24 小时 HH:MM，且不能重复")
        }
        if (settings.cadence !in cadenceValues) {
            return TrustedReminderValidation(error = "提醒频次只能选择每日或隔日")
        }
        if (settings.advanceMinutes !in 0..180) {
            return TrustedReminderValidation(error = "提前提醒时间需在 0 到 180 分钟之间")
        }
        if (settings.snoozeMinutes !in 5..120) {
            return TrustedReminderValidation(error = "稍后提醒间隔需在 5 到 120 分钟之间")
        }
        val anchor = parseDate(settings.anchorDate)
            ?: return TrustedReminderValidation(error = "隔日提醒基准日期无效")
        val startResult = parseOptionalDate(settings.courseStart)
        if (!startResult.valid) return TrustedReminderValidation(error = "疗程开始日期无效")
        val confirmedEndResult = parseOptionalDate(settings.confirmedCourseEnd)
        if (!confirmedEndResult.valid) {
            return TrustedReminderValidation(error = "已确认疗程结束日期无效")
        }
        val reminderEndResult = parseOptionalDate(settings.reminderEndDate)
        if (!reminderEndResult.valid) {
            return TrustedReminderValidation(error = "提醒结束日期无效")
        }
        val start = startResult.value
        val confirmedEnd = confirmedEndResult.value
        val reminderEnd = reminderEndResult.value
        if (start != null && reminderEnd != null && reminderEnd.isBefore(start)) {
            return TrustedReminderValidation(error = "提醒结束日期不能早于疗程开始日期")
        }
        if (confirmedEnd != null && reminderEnd != null && reminderEnd.isAfter(confirmedEnd)) {
            return TrustedReminderValidation(error = "提醒结束日期不能晚于已确认疗程结束日期")
        }
        if (confirmedEnd != null && start != null && confirmedEnd.isBefore(start)) {
            return TrustedReminderValidation(error = "已确认疗程日期范围无效")
        }
        if (settings.cadence == TrustedMedicationReminderSettings.CADENCE_ALTERNATE_DAYS &&
            start != null && anchor.isBefore(start)
        ) {
            return TrustedReminderValidation(error = "隔日提醒基准日期不能早于疗程开始日期")
        }
        return TrustedReminderValidation(normalizedTimes = times)
    }

    fun nextTrigger(
        settings: TrustedMedicationReminderSettings,
        now: ZonedDateTime,
        doseTime: LocalTime,
    ): ZonedDateTime? {
        val validation = validate(settings)
        if (!settings.enabled || !validation.isValid || doseTime !in validation.normalizedTimes) {
            return null
        }
        val start = parseDate(settings.courseStart)
        val end = parseDate(settings.reminderEndDate) ?: parseDate(settings.confirmedCourseEnd)
        val anchor = LocalDate.parse(settings.anchorDate)
        var doseDate = now.toLocalDate()
        if (start != null && doseDate.isBefore(start)) doseDate = start

        repeat(MAX_TRIGGER_SEARCH_DAYS) {
            if (end != null && doseDate.isAfter(end)) return null
            val cadenceMatches = settings.cadence == TrustedMedicationReminderSettings.CADENCE_DAILY ||
                ChronoUnit.DAYS.between(anchor, doseDate).let { it >= 0 && it % 2L == 0L }
            if (cadenceMatches) {
                val reminderAt = doseDate.atTime(doseTime).atZone(now.zone)
                    .minusMinutes(settings.advanceMinutes.toLong())
                if (reminderAt.isAfter(now)) return reminderAt
            }
            doseDate = doseDate.plusDays(1)
        }
        return null
    }

    fun confirmedRate(summaries: List<MedicationTodaySummary>): MedicationConfirmedRate {
        val planned = summaries.sumOf { it.planned_count }
        val confirmed = summaries.sumOf { summary ->
            summary.tasks.count { task ->
                task.status_assertion == "user_confirmed" && task.status in setOf("taken", "skipped")
            }
        }
        return MedicationConfirmedRate(
            confirmedCount = confirmed.coerceAtMost(planned),
            plannedCount = planned,
        )
    }

    fun coursePresentation(
        plan: TrustedMedicationPlan,
        today: LocalDate = LocalDate.now(),
    ): MedicationCoursePresentation {
        val start = parseDate(plan.course_start)
        val end = parseDate(plan.course_end)
        val dateRange = when {
            start != null && end != null -> "$start 至 $end"
            start != null -> "$start 起（未确认结束日期）"
            end != null -> "未确认开始日期，至 $end"
            else -> "未确认疗程起止日期"
        }
        val elapsedDays = start?.let {
            when {
                today.isBefore(it) -> 0
                end != null && today.isAfter(end) -> ChronoUnit.DAYS.between(it, end).toInt() + 1
                else -> ChronoUnit.DAYS.between(it, today).toInt() + 1
            }
        }
        val endingSoon = end != null && !end.isBefore(today) &&
            ChronoUnit.DAYS.between(today, end) in 0..7
        return MedicationCoursePresentation(
            dateRange = dateRange,
            elapsedDays = elapsedDays,
            endingSoon = endingSoon,
            refillEligibility = "续配资格：暂不可用（处方或服务端尚未提供可核验证据）",
            confirmedRate = "疗程已确认率：暂不可用（服务端尚未提供完整疗程聚合）",
        )
    }

    fun addCapabilities(
        prefills: List<MedicationPrefillCandidate>,
    ): List<MedicationAddCapability> {
        val pending = prefills.filter { it.review_status == "pending_review" && !it.plan_created }
        val prescription = pending.firstOrNull { it.source_type == "prescription_import" }
        val history = pending.firstOrNull { it.source_type == "history" }
        return listOf(
            MedicationAddCapability(
                action = MedicationAddAction.PrescriptionImport,
                title = "从已确认处方导入",
                description = if (prescription != null) {
                    "已有一条来自就医记录的待确认处方，请检查后创建计划"
                } else {
                    "当前服务端尚未提供可导入处方；不会创建空白或模拟处方"
                },
                available = prescription != null,
                candidate = prescription,
            ),
            MedicationAddCapability(
                action = MedicationAddAction.RawOcrText,
                title = "处方 / 药盒识别",
                description = "相机尚未接入；可粘贴已经 OCR 得到的原始文字并逐项确认",
                available = true,
            ),
            MedicationAddCapability(
                action = MedicationAddAction.HistoryRestart,
                title = "重新启用历史用药",
                description = if (history != null) {
                    "已有一条历史用药候选，请检查当前剂量、频次和疗程"
                } else {
                    "当前服务端尚未提供历史用药浏览；不会把旧版提醒记录当作可信计划"
                },
                available = history != null,
                candidate = history,
            ),
            MedicationAddCapability(
                action = MedicationAddAction.Manual,
                title = "手动填写",
                description = "逐项填写药名、剂量、频次和疗程，确认后创建计划",
                available = true,
            ),
        )
    }

    private data class OptionalDateResult(val value: LocalDate?, val valid: Boolean)

    private fun parseOptionalDate(value: String?): OptionalDateResult {
        if (value.isNullOrBlank()) return OptionalDateResult(value = null, valid = true)
        val parsed = runCatching { LocalDate.parse(value) }.getOrNull()
        return OptionalDateResult(value = parsed, valid = parsed != null)
    }

    private fun parseDate(value: String?): LocalDate? =
        value?.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private const val MAX_TRIGGER_SEARCH_DAYS = 3_660

}
