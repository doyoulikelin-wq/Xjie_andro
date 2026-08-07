package com.xjie.app.feature.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.Medication
import com.xjie.app.core.model.MedicationBody
import com.xjie.app.core.model.MedicationDoseActionBody
import com.xjie.app.core.model.MedicationPlanConfirmBody
import com.xjie.app.core.model.MedicationPrefillCandidate
import com.xjie.app.core.model.MedicationReaction
import com.xjie.app.core.model.MedicationReactionCreateBody
import com.xjie.app.core.model.MedicationRecognizePrefillBody
import com.xjie.app.core.model.MedicationTodaySummary
import com.xjie.app.core.model.MedicationTodayTask
import com.xjie.app.core.model.TrustedMedicationPlan
import com.xjie.app.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MedicationUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val operationInProgress: Boolean = false,
    val activeOperation: String? = null,
    val items: List<Medication> = emptyList(),
    val today: MedicationTodaySummary? = null,
    val plans: List<TrustedMedicationPlan> = emptyList(),
    val prefills: List<MedicationPrefillCandidate> = emptyList(),
    val reactions: List<MedicationReaction> = emptyList(),
    val reminderSettings: Map<Long, TrustedMedicationReminderSettings> = emptyMap(),
    val scheduledReminderCountByPlan: Map<Long, Int> = emptyMap(),
    val notificationPermission: MedicationNotificationPermissionState =
        MedicationNotificationPermissionState.Unknown,
    val exactAlarmAccess: MedicationExactAlarmAccessState =
        MedicationExactAlarmAccessState.Unavailable,
    val weeklyRecordsLoading: Boolean = false,
    val weeklyRecords: List<MedicationTodaySummary> = emptyList(),
    val weeklyRecordsError: String? = null,
    val error: String? = null,
    val message: String? = null,
)

private data class TrustedMedicationBundle(
    val today: MedicationTodaySummary,
    val plans: List<TrustedMedicationPlan>,
    val prefills: List<MedicationPrefillCandidate>,
    val reactions: List<MedicationReaction>,
)

@HiltViewModel
class MedicationViewModel @Inject constructor(
    private val repo: MedicationRepository,
    private val scheduler: MedicationScheduler,
    private val authManager: AuthManager,
) : ViewModel() {

    private val _state = MutableStateFlow(MedicationUiState())
    val state: StateFlow<MedicationUiState> = _state.asStateFlow()

    private val eventIds = StableMedicationEventIds()
    private val pendingDoseBodies = mutableMapOf<String, MedicationDoseActionBody>()
    private val pendingPlanBodies = mutableMapOf<String, MedicationPlanConfirmBody>()
    private val pendingRecognizeBodies = mutableMapOf<String, MedicationRecognizePrefillBody>()
    private val pendingReactionBodies = mutableMapOf<String, MedicationReactionCreateBody>()
    private var activeOwner: AuthManager.AccountScopeSnapshot? = null
    private var authoritativeSubjectUserId: Long? = null
    private var requestSequence = 0L
    private var observedAuthGeneration = authManager.generation

    init {
        viewModelScope.launch {
            authManager.state.collect { authState ->
                if (authState.generation == observedAuthGeneration) return@collect
                val obsoleteOwner = activeOwner
                observedAuthGeneration = authState.generation
                requestSequence += 1L
                activeOwner = null
                authoritativeSubjectUserId = null
                obsoleteOwner?.let { scheduler.invalidateTrustedOwner(MedicationReminderOwner.from(it)) }
                clearPendingOperations()
                _state.value = MedicationUiState(
                    error = if (authState.isLoggedIn) {
                        "登录账号或健康主体已变化，请重新读取用药记录。"
                    } else {
                        "登录已失效，请重新登录后再管理用药。"
                    },
                )
            }
        }
    }

    fun load() {
        val owner = captureOwner() ?: return
        val requestId = ++requestSequence
        activeOwner = owner
        val firstLoad = _state.value.today == null
        _state.update {
            it.copy(
                loading = firstLoad,
                refreshing = !firstLoad,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { fetchTrustedBundle(owner) }
                .onSuccess { bundle ->
                    if (canCommit(owner, requestId)) applyBundle(bundle, owner)
                }
                .onFailure { error ->
                    if (!canCommit(owner, requestId)) return@onFailure
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = error.userMessage("可信用药加载失败，请重试"),
                        )
                    }
                }
        }
    }

    fun recordDose(
        task: MedicationTodayTask,
        action: String,
        reason: String? = null,
        snoozeMinutes: Int? = null,
    ) {
        val owner = captureMutationOwner() ?: return
        val today = _state.value.today ?: return
        if (_state.value.operationInProgress || !MedicationTrustPolicy.canRecordDose(task)) return
        val operationKey = "${owner.accountScope}:${owner.generation}:dose:${task.occurrence_key}:" +
            "${task.occurrence_version}:$action:${reason.orEmpty()}:${snoozeMinutes ?: "default"}"
        val body = pendingDoseBodies.getOrPut(operationKey) {
            val eventId = eventIds.getOrCreate(operationKey, "android-med-dose")
            MedicationTrustPolicy.buildDoseAction(
                subjectUserId = today.subject_user_id,
                task = task,
                clientEventId = eventId,
                action = action,
                snoozedUntil = if (action == "snooze") {
                    OffsetDateTime.now()
                        .plusMinutes(
                            (snoozeMinutes ?: scheduler.snoozeMinutesForPlan(task.plan_id)).toLong(),
                        )
                        .toString()
                } else {
                    null
                },
                reason = reason,
            )
        }
        runMutation(
            owner = owner,
            operationKey = operationKey,
            operationLabel = "dose",
            request = {
                repo.recordDoseAction(owner, body).also { response ->
                    check(MedicationTrustPolicy.isTrustedDoseEvent(response, body)) {
                        "服药确认响应缺少可信证据"
                    }
                }
            },
            successMessage = when (action) {
                "taken" -> "本次服药已由你确认"
                "snooze" -> null
                else -> "已记录本次跳过，不代表系统判断是否正确"
            },
            onSuccess = {
                pendingDoseBodies.remove(operationKey)
                eventIds.complete(operationKey)
                if (action != "snooze") scheduler.cancelTrustedSnooze(task.occurrence_key)
            },
            onSuccessWithValue = {
                if (action == "snooze") {
                    val triggerAtMillis = body.snoozed_until
                        ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
                    val result = if (triggerAtMillis != null && isCurrent(owner)) {
                        scheduler.scheduleTrustedSnooze(
                            task,
                            triggerAtMillis,
                            MedicationReminderOwner.from(owner),
                        )
                    } else {
                        null
                    }
                        ?: TrustedSnoozeScheduleResult.ScheduleFailed
                    _state.update {
                        it.copy(
                            message = when (result) {
                                TrustedSnoozeScheduleResult.Scheduled ->
                                    "已稍后 ${snoozeMinutes ?: scheduler.snoozeMinutesForPlan(task.plan_id)} " +
                                        "分钟提醒，本次仍待确认"
                                TrustedSnoozeScheduleResult.NotificationPermissionRequired ->
                                    "已记录稍后处理，但通知权限未开启；请到系统设置为小捷开启通知"
                                TrustedSnoozeScheduleResult.ScheduleFailed ->
                                    "已记录稍后处理，但本机提醒设置失败；请刷新后重试或自行设置闹钟"
                            },
                        )
                    }
                }
            },
        )
    }

    fun correctDose(
        task: MedicationTodayTask,
        correctedStatus: String,
    ) {
        val owner = captureMutationOwner() ?: return
        val today = _state.value.today ?: return
        if (_state.value.operationInProgress || !MedicationTrustPolicy.canCorrectDose(task)) return
        val operationKey = "${owner.accountScope}:${owner.generation}:dose-correction:" +
            "${task.occurrence_key}:${task.occurrence_version}:" +
            "${task.latest_event_id}:$correctedStatus"
        val body = pendingDoseBodies.getOrPut(operationKey) {
            MedicationTrustPolicy.buildDoseCorrection(
                subjectUserId = today.subject_user_id,
                task = task,
                clientEventId = eventIds.getOrCreate(operationKey, "android-med-dose-correction"),
                correctedStatus = correctedStatus,
                reason = "用户修正当天误操作",
            )
        }
        runMutation(
            owner = owner,
            operationKey = operationKey,
            operationLabel = "dose-correction",
            request = {
                repo.recordDoseAction(owner, body).also { response ->
                    check(MedicationTrustPolicy.isTrustedDoseEvent(response, body)) {
                        "服药修正响应缺少可信证据"
                    }
                }
            },
            successMessage = when (correctedStatus) {
                "taken" -> "已把当天记录修正为已服用"
                "skipped" -> "已把当天记录修正为本次跳过"
                else -> "已把当天误操作修正为待确认"
            },
            onSuccess = {
                pendingDoseBodies.remove(operationKey)
                eventIds.complete(operationKey)
                scheduler.cancelTrustedSnooze(task.occurrence_key)
                _state.update { it.copy(weeklyRecords = emptyList()) }
            },
        )
    }

    fun saveReminder(
        settings: TrustedMedicationReminderSettings,
        onDone: () -> Unit,
    ) {
        val accountOwner = captureMutationOwner() ?: return
        val owner = MedicationReminderOwner.from(accountOwner)
        val plan = _state.value.plans.firstOrNull { it.plan_id == settings.planId }
        if (plan == null || !TrustedMedicationReminderPolicy.isCurrentForPlan(
                settings = settings,
                plan = plan,
                owner = owner,
                timezoneId = ZoneId.systemDefault().id,
            )
        ) {
            _state.update { it.copy(error = "用药计划已更新，请刷新后重新核对提醒设置") }
            return
        }
        val validation = TrustedMedicationReminderPolicy.validate(settings)
        if (!validation.isValid) {
            _state.update { it.copy(error = validation.error) }
            return
        }
        val evidence = scheduler.saveTrustedReminder(settings, owner)
        val result = evidence.result
        if (result == TrustedReminderSaveResult.ScheduleFailed) {
            _state.update { it.copy(error = "本机提醒保存失败，请重试或暂时使用系统闹钟") }
            return
        }
        val current = _state.value.reminderSettings.toMutableMap().apply {
            put(settings.planId, evidence.persistedSettings)
        }
        val scheduledCounts = _state.value.scheduledReminderCountByPlan.toMutableMap().apply {
            put(settings.planId, evidence.scheduledCount)
        }
        _state.update {
            it.copy(
                reminderSettings = current,
                scheduledReminderCountByPlan = scheduledCounts,
                notificationPermission = scheduler.notificationPermissionState(),
                exactAlarmAccess = scheduler.exactAlarmAccessState(),
                message = when (result) {
                    TrustedReminderSaveResult.Disabled -> "已关闭这项用药的本机提醒"
                    TrustedReminderSaveResult.Scheduled ->
                        "已实际安排 ${evidence.scheduledCount} 个本机提醒"
                    TrustedReminderSaveResult.NotificationPermissionRequired ->
                        "提醒未开启；请先允许通知权限后再保存"
                    TrustedReminderSaveResult.ExactAlarmPermissionRequired ->
                        "提醒未开启；请先允许系统精确闹钟权限后再保存"
                    TrustedReminderSaveResult.NoUpcomingTrigger ->
                        "提醒未开启；已确认疗程内没有下一次可排期提醒"
                    TrustedReminderSaveResult.ScheduleFailed -> error("handled above")
                },
            )
        }
        onDone()
    }

    fun loadWeeklyRecords() {
        val owner = captureMutationOwner() ?: return
        val today = _state.value.today ?: return
        if (_state.value.weeklyRecordsLoading) return
        _state.update { it.copy(weeklyRecordsLoading = true, weeklyRecordsError = null) }
        viewModelScope.launch {
            runCatching { fetchWeeklyRecords(owner, today) }
                .onSuccess { summaries ->
                    if (!isCurrent(owner)) return@onSuccess
                    _state.update {
                        it.copy(
                            weeklyRecordsLoading = false,
                            weeklyRecords = summaries,
                            weeklyRecordsError = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (!isCurrent(owner)) return@onFailure
                    _state.update {
                        it.copy(
                            weeklyRecordsLoading = false,
                            weeklyRecordsError = error.userMessage("每周记录加载失败，请重试"),
                        )
                    }
                }
        }
    }

    fun recognizeRawText(rawText: String, onSuccess: () -> Unit) {
        val owner = captureMutationOwner() ?: return
        val subjectUserId = currentSubjectUserId() ?: return
        val normalized = rawText.trim()
        if (_state.value.operationInProgress || normalized.isBlank()) return
        val operationKey = "${owner.accountScope}:${owner.generation}:recognize:$normalized"
        val body = pendingRecognizeBodies.getOrPut(operationKey) {
            MedicationRecognizePrefillBody(
                raw_text = normalized,
                subject_user_id = subjectUserId,
                client_event_id = eventIds.getOrCreate(operationKey, "android-med-ocr"),
            )
        }
        runMutation(
            owner = owner,
            operationKey = operationKey,
            operationLabel = "recognize",
            request = {
                repo.recognizePrefill(owner, body).also { response ->
                    check(
                        MedicationTrustPolicy.isUnconfirmedRecognizeResult(
                            response,
                            body.client_event_id,
                        ),
                    ) { "识别响应错误地声称已确认" }
                }
            },
            successMessage = "识别结果已保存为待复核信息，尚未创建用药计划",
            onSuccess = {
                pendingRecognizeBodies.remove(operationKey)
                eventIds.complete(operationKey)
                onSuccess()
            },
        )
    }

    internal fun confirmPlan(
        draft: MedicationPlanDraft,
        candidate: MedicationPrefillCandidate?,
        onSuccess: () -> Unit,
    ) {
        val owner = captureMutationOwner() ?: return
        val subjectUserId = currentSubjectUserId() ?: return
        if (_state.value.operationInProgress) return
        val validation = MedicationTrustPolicy.validatePlanDraft(draft)
        if (!validation.isValid) {
            _state.update { it.copy(error = validation.error) }
            return
        }
        val sourceIdentity = candidate?.let { "candidate:${it.candidate_id}:${it.version}" } ?: "manual"
        val operationKey = "${owner.accountScope}:${owner.generation}:confirm:$sourceIdentity:$draft"
        val body = pendingPlanBodies.getOrPut(operationKey) {
            MedicationTrustPolicy.buildPlanConfirmation(
                subjectUserId = subjectUserId,
                draft = draft,
                clientEventId = eventIds.getOrCreate(operationKey, "android-med-plan"),
                candidate = candidate,
            )
        }
        runMutation(
            owner = owner,
            operationKey = operationKey,
            operationLabel = "confirm-plan",
            request = {
                repo.confirmTrustedPlan(owner, body).also { response ->
                    check(MedicationTrustPolicy.isTrustedPlan(response, subjectUserId)) {
                        "计划确认响应缺少可信证据"
                    }
                }
            },
            successMessage = "用药信息已由你确认，提醒仍默认关闭",
            onSuccess = {
                pendingPlanBodies.remove(operationKey)
                eventIds.complete(operationKey)
                onSuccess()
            },
        )
    }

    internal fun createReaction(draft: MedicationReactionDraft, onSuccess: () -> Unit) {
        val owner = captureMutationOwner() ?: return
        val subjectUserId = currentSubjectUserId() ?: return
        val planId = draft.planId
        if (_state.value.operationInProgress || planId == null || draft.symptoms.isBlank()) return
        val duration = draft.durationMinutes.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
        if (draft.durationMinutes.isNotBlank() && (duration == null || duration < 0)) {
            _state.update { it.copy(error = "持续时间请填写不小于 0 的分钟数") }
            return
        }
        val operationKey = "${owner.accountScope}:${owner.generation}:reaction:$draft"
        val body = pendingReactionBodies.getOrPut(operationKey) {
            val eventId = eventIds.getOrCreate(operationKey, "android-med-reaction")
            MedicationReactionCreateBody(
                subject_user_id = subjectUserId,
                client_event_id = eventId,
                reaction_key = "reaction-${UUID.randomUUID()}",
                plan_id = planId,
                symptoms = draft.symptoms.trim(),
                onset_at = OffsetDateTime.now().toString(),
                severity = draft.severity,
                duration_minutes = duration,
                related_occurrence_key = _state.value.today?.next_task?.occurrence_key,
                notes = draft.notes.trim().ifBlank { null },
            )
        }
        runMutation(
            owner = owner,
            operationKey = operationKey,
            operationLabel = "reaction",
            request = {
                repo.createReaction(owner, body).also { response ->
                    check(MedicationTrustPolicy.isTrustedReaction(response)) {
                        "不适记录响应越过了时间关联边界"
                    }
                }
            },
            successMessage = null,
            onSuccessWithValue = { reaction ->
                pendingReactionBodies.remove(operationKey)
                eventIds.complete(operationKey)
                _state.update {
                    it.copy(
                        message = if (reaction.severity == "severe") {
                            reaction.safety_guidance
                        } else {
                            "不适已记录；这只表示症状与服药时间接近，不能认定由药物导致"
                        },
                    )
                }
                onSuccess()
            },
        )
    }

    /** Legacy CRUD remains available for old callers, but is not read by the trusted screen. */
    fun save(body: MedicationBody, editing: Medication?, onDone: () -> Unit) {
        val owner = captureMutationOwner() ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            runCatching {
                if (editing != null) {
                    repo.update(owner, editing.id, body)
                } else {
                    repo.create(owner, body)
                }
            }.onSuccess {
                if (!isCurrent(owner)) return@onSuccess
                _state.update { it.copy(saving = false, message = "旧版提醒记录已保存，尚未成为可信用药计划") }
                onDone()
            }.onFailure { error ->
                if (!isCurrent(owner)) return@onFailure
                _state.update {
                    it.copy(saving = false, error = error.userMessage("保存失败，请重试"))
                }
            }
        }
    }

    fun delete(med: Medication) {
        val owner = captureMutationOwner() ?: return
        viewModelScope.launch {
            runCatching { repo.delete(owner, med.id) }
            .onSuccess {
                if (isCurrent(owner)) _state.update { it.copy(message = "旧版提醒记录已删除") }
            }
            .onFailure { error ->
                if (isCurrent(owner)) {
                    _state.update { it.copy(error = error.userMessage("删除失败，请重试")) }
                }
            }
        }
    }

    fun recognize(rawText: String) = recognizeRawText(rawText) {}
    fun clearRecognized() = Unit
    fun clearError() = _state.update { it.copy(error = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun markNotificationPermissionRequested() {
        scheduler.markNotificationPermissionRequested()
        refreshReminderPermissionState()
    }

    fun refreshReminderPermissionState() {
        _state.update {
            it.copy(
                notificationPermission = scheduler.notificationPermissionState(),
                exactAlarmAccess = scheduler.exactAlarmAccessState(),
            )
        }
    }

    fun fireTestNotification() {
        scheduler.fireTestNotification()
        _state.update { it.copy(message = "已发送测试通知；这不代表任何服药状态已确认") }
    }

    fun scheduleTestAlarm() {
        scheduler.scheduleTestAlarm(10)
        _state.update { it.copy(message = "已安排 10 秒后的本地测试闹钟") }
    }

    fun scheduleCustomAlarm(triggerAtMillis: Long, label: String) {
        scheduler.scheduleCustomAlarm(triggerAtMillis)
        _state.update { it.copy(message = "已设定 $label 的本地提醒闹钟") }
    }

    private fun <T> runMutation(
        owner: AuthManager.AccountScopeSnapshot,
        operationKey: String,
        operationLabel: String,
        request: suspend () -> T,
        successMessage: String?,
        onSuccess: () -> Unit = {},
        onSuccessWithValue: (T) -> Unit = {},
    ) = viewModelScope.launch {
        _state.update {
            it.copy(
                operationInProgress = true,
                activeOperation = operationLabel,
                error = null,
            )
        }
        runCatching { request() }
            .onSuccess { value ->
                if (!isCurrent(owner)) return@onSuccess
                onSuccess()
                onSuccessWithValue(value)
                _state.update {
                    it.copy(
                        operationInProgress = false,
                        activeOperation = null,
                        message = successMessage ?: it.message,
                    )
                }
                refreshAfterMutation(owner)
            }
            .onFailure { error ->
                if (!isCurrent(owner)) return@onFailure
                // Keep event id and exact body so the next tap is an idempotent retry.
                _state.update {
                    it.copy(
                        operationInProgress = false,
                        activeOperation = null,
                        error = error.userMessage("操作失败，重试会沿用同一次事件"),
                    )
                }
            }
    }

    private suspend fun refreshAfterMutation(owner: AuthManager.AccountScopeSnapshot) {
        runCatching { fetchTrustedBundle(owner) }
            .onSuccess { bundle ->
                if (isCurrent(owner)) applyBundle(bundle, owner)
            }
            .onFailure { error ->
                if (!isCurrent(owner)) return@onFailure
                _state.update {
                    it.copy(error = error.userMessage("操作已完成，但最新状态刷新失败，请重试"))
                }
            }
    }

    private suspend fun fetchTrustedBundle(
        owner: AuthManager.AccountScopeSnapshot,
    ): TrustedMedicationBundle = coroutineScope {
        val localDate = LocalDate.now().toString()
        val timezoneOffsetMinutes = currentTimezoneOffsetMinutes()
        val expectedSubject = authoritativeSubjectUserId
        val today = async {
            repo.trustedToday(owner, localDate, timezoneOffsetMinutes, expectedSubject)
        }
        val plans = async { repo.trustedPlans(owner, expectedSubject) }
        val prefills = async { repo.trustedPrefills(owner, expectedSubject) }
        val reactions = async { repo.trustedReactions(owner, expectedSubject) }
        val todayValue = today.await()
        val plansValue = plans.await()
        val prefillsValue = prefills.await()
        val reactionsValue = reactions.await()
        val subjects = setOf(
            todayValue.subject_user_id,
            plansValue.subject_user_id,
            prefillsValue.subject_user_id,
            reactionsValue.subject_user_id,
        )
        check(subjects.size == 1) { "可信用药返回的主体不一致，已停止展示" }
        val subject = subjects.single()
        check(expectedSubject == null || subject == expectedSubject) {
            "可信用药主体在请求期间发生变化，已停止展示"
        }
        check(owner.subjectId.isBlank() || owner.subjectId == subject.toString()) {
            "可信用药主体与当前健康主体不一致，已停止展示"
        }
        check(
            MedicationTrustPolicy.isTrustedSnapshot(
                todayValue,
                plansValue.items,
                prefillsValue.items,
                reactionsValue.items,
            ),
        ) { "可信用药证据契约不完整，已停止展示" }
        TrustedMedicationBundle(
            today = todayValue,
            plans = plansValue.items,
            prefills = prefillsValue.items,
            reactions = reactionsValue.items,
        )
    }

    private suspend fun fetchWeeklyRecords(
        owner: AuthManager.AccountScopeSnapshot,
        current: MedicationTodaySummary,
    ): List<MedicationTodaySummary> = coroutineScope {
        val localDate = LocalDate.parse(current.local_date)
        val timezoneOffsetMinutes = currentTimezoneOffsetMinutes()
        val summaries = (6L downTo 0L).map { daysAgo ->
            async {
                repo.trustedToday(
                    owner = owner,
                    localDate = localDate.minusDays(daysAgo).toString(),
                    timezoneOffsetMinutes = timezoneOffsetMinutes,
                    subjectUserId = current.subject_user_id,
                )
            }
        }.awaitAll()
        check(summaries.all {
            MedicationTrustPolicy.isTrustedTodaySummary(it, current.subject_user_id)
        }) { "每周记录的主体或确认凭据不一致，已停止展示" }
        summaries
    }

    private fun applyBundle(
        bundle: TrustedMedicationBundle,
        owner: AuthManager.AccountScopeSnapshot,
    ) {
        if (!isCurrent(owner)) return
        authoritativeSubjectUserId = bundle.today.subject_user_id
        val reminderEvidence = scheduler.reconcileTrustedPlans(
            bundle.plans,
            MedicationReminderOwner.from(owner),
        )
        if (!isCurrent(owner)) return
        _state.update {
            it.copy(
                loading = false,
                refreshing = false,
                today = bundle.today,
                plans = bundle.plans,
                prefills = bundle.prefills,
                reactions = bundle.reactions,
                reminderSettings = reminderEvidence.settings,
                scheduledReminderCountByPlan = reminderEvidence.scheduledCountByPlan,
                notificationPermission = reminderEvidence.notificationPermission,
                exactAlarmAccess = reminderEvidence.exactAlarmAccess,
                error = null,
            )
        }
    }

    private fun currentTimezoneOffsetMinutes(): Int = ZoneId.systemDefault()
        .rules
        .getOffset(Instant.now())
        .totalSeconds / 60

    private fun currentSubjectUserId(): Long? = _state.value.today?.subject_user_id ?: run {
        _state.update { it.copy(error = "尚未从今日可信状态取得主体，请先刷新页面") }
        null
    }

    private fun Throwable.userMessage(fallback: String): String = when (this) {
        is ApiException -> message ?: fallback
        else -> message ?: fallback
    }

    private fun captureOwner(): AuthManager.AccountScopeSnapshot? =
        authManager.captureAccountScope().also { owner ->
            if (owner == null) {
                _state.update { it.copy(error = "登录状态不可用，请重新登录后再读取用药记录") }
            }
        }

    private fun captureMutationOwner(): AuthManager.AccountScopeSnapshot? {
        val owner = activeOwner
        if (owner == null || !authManager.isCurrent(owner)) {
            _state.update { it.copy(error = "登录账号或健康主体已变化，请重新读取用药记录") }
            return null
        }
        return owner
    }

    private fun canCommit(owner: AuthManager.AccountScopeSnapshot, requestId: Long): Boolean =
        requestSequence == requestId && activeOwner == owner && authManager.isCurrent(owner)

    private fun isCurrent(owner: AuthManager.AccountScopeSnapshot): Boolean =
        activeOwner == owner && authManager.isCurrent(owner)

    private fun clearPendingOperations() {
        pendingDoseBodies.clear()
        pendingPlanBodies.clear()
        pendingRecognizeBodies.clear()
        pendingReactionBodies.clear()
        eventIds.clear()
    }
}
