package com.xjie.app.feature.patienthistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.HealthProfileCandidateReviewBody
import com.xjie.app.core.model.HealthProfileFactRetractBody
import com.xjie.app.core.model.HealthProfileFactUpsertBody
import com.xjie.app.core.model.HealthProfileGoal
import com.xjie.app.core.model.HealthProfileGoalCreateBody
import com.xjie.app.core.model.HealthProfileGoalStatusBody
import com.xjie.app.core.model.HealthProfileGoalUpdateBody
import com.xjie.app.core.model.HealthProfileLongTermMedicationSummaryItem
import com.xjie.app.core.model.HealthProfileRevisionList
import com.xjie.app.core.model.HealthProfileTrustCandidate
import com.xjie.app.core.model.HealthProfileTrustFact
import com.xjie.app.core.model.HealthProfileTrustProfile
import com.xjie.app.feature.healthdata.HealthDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class HealthProfileScreenPhase { Loading, Empty, Error, Ready }

data class HealthProfileFactEditorDraft(
    val definition: HealthProfileFieldDefinition,
    val originalResponseState: HealthProfileResponseState,
    val originalValue: String,
    val responseState: HealthProfileResponseState,
    val value: String,
) {
    val isDirty: Boolean
        get() = originalResponseState != responseState || originalValue.trim() != value.trim()
}

data class HealthProfileGoalEditorDraft(
    val goalId: Long? = null,
    val expectedVersion: Int? = null,
    val originalName: String = "",
    val originalStartedOn: String = "",
    val originalMetricsText: String = "",
    val name: String = originalName,
    val startedOn: String = originalStartedOn,
    val metricsText: String = originalMetricsText,
) {
    val isCreating: Boolean get() = goalId == null
    val isDirty: Boolean
        get() = if (isCreating) {
            name.isNotBlank() || startedOn.isNotBlank() || metricsText.isNotBlank()
        } else {
            originalName.trim() != name.trim() ||
                originalStartedOn.trim() != startedOn.trim() ||
                originalMetricsText.trim() != metricsText.trim()
        }

    companion object {
        fun from(goal: HealthProfileGoal): HealthProfileGoalEditorDraft {
            val metrics = goal.metrics.joinToString("、") { metric ->
                val label = metric.display_label?.trim().orEmpty()
                label.ifEmpty { metric.metric_key }
            }
            return HealthProfileGoalEditorDraft(
                goalId = goal.goal_id,
                expectedVersion = goal.version,
                originalName = goal.name,
                originalStartedOn = goal.started_on,
                originalMetricsText = metrics,
                name = goal.name,
                startedOn = goal.started_on,
                metricsText = metrics,
            )
        }
    }
}

data class HealthProfileHistoryTarget(
    val kind: String,
    val id: Long,
    val title: String,
) {
    init {
        require(kind == "fact" || kind == "goal")
        require(id > 0L)
    }
}

sealed interface HealthProfileConfirmation {
    data class SaveSafety(val factKey: String) : HealthProfileConfirmation
    data class RetractFact(val factId: Long) : HealthProfileConfirmation
    data class ReviewCandidate(val candidateId: Long, val action: String) : HealthProfileConfirmation
    data class ChangeGoalStatus(
        val goalId: Long,
        val action: HealthProfileGoalAction,
    ) : HealthProfileConfirmation
}

data class PatientHistoryUiState(
    val loading: Boolean = false,
    val operationInProgress: Boolean = false,
    val profile: HealthProfileTrustProfile? = null,
    val longTermMedications: List<HealthProfileLongTermMedicationSummaryItem> = emptyList(),
    val medicationSummaryLoading: Boolean = false,
    val medicationSummaryError: String? = null,
    val factEditor: HealthProfileFactEditorDraft? = null,
    val goalEditor: HealthProfileGoalEditorDraft? = null,
    val confirmation: HealthProfileConfirmation? = null,
    val historyTarget: HealthProfileHistoryTarget? = null,
    val revisionHistory: HealthProfileRevisionList? = null,
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    val pendingMutationLabel: String? = null,
    val loadError: String? = null,
    val error: String? = null,
    val toast: String? = null,
) {
    val phase: HealthProfileScreenPhase
        get() = when {
            loading && profile == null -> HealthProfileScreenPhase.Loading
            profile != null -> HealthProfileScreenPhase.Ready
            loadError != null -> HealthProfileScreenPhase.Error
            else -> HealthProfileScreenPhase.Empty
        }
    val hasUnsavedEditor: Boolean
        get() = factEditor?.isDirty == true || goalEditor?.isDirty == true
    val hasPendingRetry: Boolean get() = pendingMutationLabel != null && !operationInProgress
}

@HiltViewModel
class PatientHistoryViewModel @Inject constructor(
    private val repo: HealthDataRepository,
    private val authManager: AuthManager,
) : ViewModel() {
    private val _state = MutableStateFlow(PatientHistoryUiState())
    val state: StateFlow<PatientHistoryUiState> = _state.asStateFlow()

    private var activeOwner: AuthManager.AccountScopeSnapshot? = null
    private var activeLoadToken: HealthProfileRequestToken? = null
    private var activeMutationToken: HealthProfileRequestToken? = null
    private var activeHistoryToken: HealthProfileRequestToken? = null
    private var requestSequence = 0L
    private var observedAuthGeneration = authManager.generation
    private var pendingMutation: PendingMutation? = null

    init {
        viewModelScope.launch {
            authManager.state.collect { authState ->
                if (authState.generation == observedAuthGeneration) return@collect
                observedAuthGeneration = authState.generation
                invalidateRequests()
                _state.value = PatientHistoryUiState(
                    loadError = if (authState.isLoggedIn) {
                        "登录账号或健康主体已变化，请重新读取健康画像。"
                    } else {
                        "登录已失效，请重新登录后再读取健康画像。"
                    },
                )
            }
        }
    }

    fun load() {
        val owner = captureOwner() ?: return
        val token = newToken(owner).also { activeLoadToken = it }
        activeOwner = owner
        activeMutationToken = null
        activeHistoryToken = null
        pendingMutation = null
        _state.value = PatientHistoryUiState(loading = true)

        viewModelScope.launch {
            runCatching { repo.healthProfileTrust(owner) }
                .onSuccess { profile ->
                    if (!acceptsProfile(token, activeLoadToken, null, profile.subject_user_id)) {
                        rejectMismatchedProfileIfCurrent(token, profile.subject_user_id)
                        return@onSuccess
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            profile = profile,
                            medicationSummaryLoading = true,
                            loadError = null,
                        )
                    }
                    loadMedicationSummary(owner, token, profile.subject_user_id)
                }
                .onFailure { error ->
                    if (!isCurrent(token, activeLoadToken)) return@onFailure
                    _state.update {
                        it.copy(
                            loading = false,
                            loadError = error.message ?: "健康画像加载失败",
                        )
                    }
                }
        }
    }

    fun startEditing(definition: HealthProfileFieldDefinition) {
        val current = _state.value
        if (current.operationInProgress || current.hasPendingRetry || definition.category == "goal") return
        val fact = current.profile?.facts?.firstOrNull { it.fact_key == definition.factKey }
        val response = fact?.let { HealthProfileTrustPolicy.responseStateOrNull(it.value_data) }
            ?: HealthProfileResponseState.Value
        val value = editableFactValue(fact)
        _state.update {
            it.copy(
                factEditor = HealthProfileFactEditorDraft(
                    definition = definition,
                    originalResponseState = response,
                    originalValue = value,
                    responseState = response,
                    value = value,
                ),
                goalEditor = null,
                confirmation = null,
            )
        }
    }

    fun updateDraftResponseState(value: HealthProfileResponseState) {
        _state.update { state ->
            val draft = state.factEditor ?: return@update state
            if (state.operationInProgress) state else state.copy(
                factEditor = draft.copy(
                    responseState = value,
                    value = if (value == HealthProfileResponseState.Value) draft.value else "",
                ),
            )
        }
    }

    fun updateDraftValue(value: String) {
        _state.update { state ->
            val draft = state.factEditor ?: return@update state
            if (state.operationInProgress) state else state.copy(factEditor = draft.copy(value = value))
        }
    }

    fun cancelFactEditing() {
        if (_state.value.operationInProgress) return
        _state.update { it.copy(factEditor = null, confirmation = null) }
    }

    fun requestSaveFact() {
        val state = _state.value
        val draft = state.factEditor ?: return
        if (state.operationInProgress || state.hasPendingRetry || !draft.isDirty) return
        if (draft.responseState == HealthProfileResponseState.Value && draft.value.isBlank()) {
            _state.update { it.copy(error = "请填写内容，或明确选择“没有 / 不适用 / 暂不回答”。") }
            return
        }
        if (draft.definition.safetyCritical) {
            _state.update {
                it.copy(confirmation = HealthProfileConfirmation.SaveSafety(draft.definition.factKey))
            }
        } else {
            startFactMutation(draft)
        }
    }

    fun requestRetractFact(fact: HealthProfileTrustFact) {
        val state = _state.value
        if (state.operationInProgress || state.hasPendingRetry) return
        if (state.profile?.facts?.none { it.fact_id == fact.fact_id && it.version == fact.version } != false) return
        _state.update { it.copy(confirmation = HealthProfileConfirmation.RetractFact(fact.fact_id)) }
    }

    fun beginCreatingGoal() {
        val state = _state.value
        if (state.operationInProgress || state.hasPendingRetry) return
        _state.update {
            it.copy(goalEditor = HealthProfileGoalEditorDraft(), factEditor = null, confirmation = null)
        }
    }

    fun beginEditingGoal(goal: HealthProfileGoal) {
        val state = _state.value
        if (state.operationInProgress || state.hasPendingRetry || goal.status == "archived") return
        if (state.profile?.goals?.none { it.goal_id == goal.goal_id && it.version == goal.version } != false) return
        _state.update {
            it.copy(goalEditor = HealthProfileGoalEditorDraft.from(goal), factEditor = null, confirmation = null)
        }
    }

    fun updateGoalName(value: String) = updateGoalDraft { it.copy(name = value) }
    fun updateGoalStartedOn(value: String) = updateGoalDraft { it.copy(startedOn = value) }
    fun updateGoalMetrics(value: String) = updateGoalDraft { it.copy(metricsText = value) }

    fun cancelGoalEditing() {
        if (_state.value.operationInProgress) return
        _state.update { it.copy(goalEditor = null, confirmation = null) }
    }

    fun saveGoal() {
        val state = _state.value
        val draft = state.goalEditor ?: return
        if (state.operationInProgress || state.hasPendingRetry || !draft.isDirty) return
        val name = draft.name.trim()
        val startedOn = draft.startedOn.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(error = "请填写目标名称。") }
            return
        }
        if (!HealthProfileTrustPolicy.isValidGoalDate(startedOn)) {
            _state.update { it.copy(error = "开始时间必须是有效日期（YYYY-MM-DD）。") }
            return
        }
        val metrics = HealthProfileTrustPolicy.goalMetricRequests(draft.metricsText)
        if (metrics.isNullOrEmpty()) {
            _state.update {
                it.copy(error = "请至少关联一个有效指标，例如睡眠时长、HRV、体重、步数、血压或血糖。")
            }
            return
        }
        val context = mutationContext() ?: return
        val (owner, subject) = context
        val eventId = newEventId()
        val mutation = if (draft.goalId != null && draft.expectedVersion != null) {
            PendingMutation.UpdateGoal(
                owner = owner,
                subject = subject,
                goalId = draft.goalId,
                body = HealthProfileGoalUpdateBody(
                    subject_user_id = subject,
                    client_event_id = eventId,
                    expected_version = draft.expectedVersion,
                    name = name,
                    started_on = startedOn,
                    metrics = metrics,
                ),
            )
        } else {
            PendingMutation.CreateGoal(
                owner = owner,
                subject = subject,
                body = HealthProfileGoalCreateBody(
                    subject_user_id = subject,
                    client_event_id = eventId,
                    name = name,
                    started_on = startedOn,
                    metrics = metrics,
                ),
            )
        }
        startMutation(mutation)
    }

    fun requestGoalStatus(goal: HealthProfileGoal, action: HealthProfileGoalAction) {
        val status = HealthProfileTrustPolicy.goalStatus(goal.status)
        val current = _state.value
        if (current.operationInProgress || current.hasPendingRetry || status == null ||
            !HealthProfileTrustPolicy.allowsGoalAction(action, status) ||
            current.profile?.goals?.none { it.goal_id == goal.goal_id && it.version == goal.version } != false
        ) {
            _state.update { it.copy(error = "目标状态已变化或当前操作不受支持，请刷新后重试。") }
            return
        }
        _state.update {
            it.copy(confirmation = HealthProfileConfirmation.ChangeGoalStatus(goal.goal_id, action))
        }
    }

    fun requestCandidateReview(candidateId: Long, action: String) {
        val state = _state.value
        val candidate = state.profile?.candidates?.firstOrNull { it.candidate_id == candidateId } ?: return
        if (state.operationInProgress || state.hasPendingRetry) return
        if (!HealthProfileTrustPolicy.canReviewCandidate(candidate, action)) {
            _state.update {
                it.copy(
                    error = if (action == "accept") {
                        "目标或安全候选不能直接接受；请由你主动创建或手动确认。"
                    } else {
                        "这项候选状态已失效，请刷新后重试。"
                    },
                )
            }
            return
        }
        _state.update {
            it.copy(confirmation = HealthProfileConfirmation.ReviewCandidate(candidateId, action))
        }
    }

    fun confirmAction() {
        val confirmation = _state.value.confirmation ?: return
        when (confirmation) {
            is HealthProfileConfirmation.SaveSafety -> {
                val draft = _state.value.factEditor
                if (draft?.definition?.factKey == confirmation.factKey) startFactMutation(draft)
            }
            is HealthProfileConfirmation.RetractFact -> startRetractMutation(confirmation.factId)
            is HealthProfileConfirmation.ReviewCandidate -> startCandidateMutation(
                confirmation.candidateId,
                confirmation.action,
            )
            is HealthProfileConfirmation.ChangeGoalStatus -> startGoalStatusMutation(
                confirmation.goalId,
                confirmation.action,
            )
        }
    }

    fun dismissConfirmation() {
        if (!_state.value.operationInProgress) _state.update { it.copy(confirmation = null) }
    }

    fun retryPendingMutation() {
        val mutation = pendingMutation ?: return
        if (_state.value.operationInProgress) return
        performMutation(mutation)
    }

    fun openFactHistory(fact: HealthProfileTrustFact) = openHistory(
        HealthProfileHistoryTarget("fact", fact.fact_id, HealthProfileTrustPolicy.field(fact.fact_key)?.title ?: fact.fact_key),
    )

    fun openGoalHistory(goal: HealthProfileGoal) = openHistory(
        HealthProfileHistoryTarget("goal", goal.goal_id, goal.name),
    )

    fun loadMoreHistory() {
        val next = _state.value.revisionHistory?.next_after_revision_id ?: return
        loadHistoryPage(next, append = true)
    }

    fun closeHistory() {
        activeHistoryToken = null
        requestSequence += 1L
        _state.update {
            it.copy(historyTarget = null, revisionHistory = null, historyLoading = false, historyError = null)
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearToast() = _state.update { it.copy(toast = null) }

    private fun loadMedicationSummary(
        owner: AuthManager.AccountScopeSnapshot,
        token: HealthProfileRequestToken,
        subject: Long,
    ) = viewModelScope.launch {
        runCatching { repo.healthProfileLongTermMedicationSummary(owner, subject) }
            .onSuccess { summary ->
                if (!acceptsProfile(token, activeLoadToken, subject, summary.subject_user_id)) return@onSuccess
                _state.update {
                    it.copy(
                        longTermMedications = summary.items,
                        medicationSummaryLoading = false,
                        medicationSummaryError = null,
                    )
                }
            }
            .onFailure { error ->
                if (!isCurrent(token, activeLoadToken)) return@onFailure
                _state.update {
                    it.copy(
                        medicationSummaryLoading = false,
                        medicationSummaryError = "长期用药摘要暂时无法读取：${error.message ?: "未知错误"}",
                    )
                }
            }
    }

    private fun startFactMutation(draft: HealthProfileFactEditorDraft) {
        val context = mutationContext() ?: return
        val (owner, subject) = context
        val existing = _state.value.profile?.facts?.firstOrNull {
            it.fact_key == draft.definition.factKey
        }
        val body = HealthProfileFactUpsertBody(
            subject_user_id = subject,
            client_event_id = newEventId(),
            fact_key = draft.definition.factKey,
            category = draft.definition.category,
            response_state = draft.responseState.wireValue,
            value = if (draft.responseState == HealthProfileResponseState.Value) {
                JsonPrimitive(draft.value.trim())
            } else {
                null
            },
            is_safety_critical = draft.definition.safetyCritical,
            expected_version = existing?.version,
        )
        startMutation(PendingMutation.UpsertFact(owner, subject, body))
    }

    private fun startRetractMutation(factId: Long) {
        val fact = _state.value.profile?.facts?.firstOrNull { it.fact_id == factId } ?: return
        val context = mutationContext() ?: return
        val (owner, subject) = context
        startMutation(
            PendingMutation.RetractFact(
                owner,
                subject,
                fact.fact_id,
                HealthProfileFactRetractBody(
                    subject_user_id = subject,
                    client_event_id = newEventId(),
                    expected_version = fact.version,
                ),
            ),
        )
    }

    private fun startCandidateMutation(candidateId: Long, action: String) {
        val candidate: HealthProfileTrustCandidate = _state.value.profile?.candidates
            ?.firstOrNull { it.candidate_id == candidateId } ?: return
        if (!HealthProfileTrustPolicy.canReviewCandidate(candidate, action)) return
        val context = mutationContext() ?: return
        val (owner, subject) = context
        startMutation(
            PendingMutation.ReviewCandidate(
                owner,
                subject,
                candidateId,
                HealthProfileCandidateReviewBody(
                    subject_user_id = subject,
                    client_event_id = newEventId(),
                    candidate_version = candidate.version,
                    action = action,
                ),
            ),
        )
    }

    private fun startGoalStatusMutation(goalId: Long, action: HealthProfileGoalAction) {
        val goal = _state.value.profile?.goals?.firstOrNull { it.goal_id == goalId } ?: return
        val status = HealthProfileTrustPolicy.goalStatus(goal.status) ?: return
        if (!HealthProfileTrustPolicy.allowsGoalAction(action, status)) return
        val context = mutationContext() ?: return
        val (owner, subject) = context
        startMutation(
            PendingMutation.GoalStatus(
                owner,
                subject,
                goalId,
                HealthProfileGoalStatusBody(
                    subject_user_id = subject,
                    client_event_id = newEventId(),
                    expected_version = goal.version,
                    action = action.wireValue,
                ),
            ),
        )
    }

    private fun startMutation(mutation: PendingMutation) {
        if (pendingMutation != null || _state.value.operationInProgress) {
            _state.update { it.copy(error = "上一项修改结果尚未确认，请使用同一请求重试。") }
            return
        }
        pendingMutation = mutation
        _state.update { it.copy(pendingMutationLabel = mutation.label) }
        performMutation(mutation)
    }

    private fun performMutation(mutation: PendingMutation) {
        if (!validateMutationOwner(mutation.owner, mutation.subject)) return
        val token = newToken(mutation.owner).also { activeMutationToken = it }
        _state.update {
            it.copy(operationInProgress = true, confirmation = null, error = null)
        }
        viewModelScope.launch {
            runCatching {
                when (mutation) {
                    is PendingMutation.ReviewCandidate -> repo.reviewHealthProfileCandidate(
                        mutation.owner,
                        mutation.candidateId,
                        mutation.body,
                    )
                    is PendingMutation.UpsertFact -> repo.upsertHealthProfileFact(mutation.owner, mutation.body)
                    is PendingMutation.RetractFact -> repo.retractHealthProfileFact(
                        mutation.owner,
                        mutation.factId,
                        mutation.body,
                    )
                    is PendingMutation.CreateGoal -> repo.createHealthProfileGoal(mutation.owner, mutation.body)
                    is PendingMutation.UpdateGoal -> repo.updateHealthProfileGoal(
                        mutation.owner,
                        mutation.goalId,
                        mutation.body,
                    )
                    is PendingMutation.GoalStatus -> repo.updateHealthProfileGoalStatus(
                        mutation.owner,
                        mutation.goalId,
                        mutation.body,
                    )
                }
            }.onSuccess { response ->
                if (!acceptsProfile(token, activeMutationToken, mutation.subject, response.subject_user_id)) {
                    if (isCurrent(token, activeMutationToken)) {
                        _state.update {
                            it.copy(
                                operationInProgress = false,
                                error = "画像主体或登录账号已变化，已拒绝显示这次响应。",
                            )
                        }
                    }
                    return@onSuccess
                }
                pendingMutation = null
                activeMutationToken = null
                _state.update {
                    it.copy(
                        operationInProgress = false,
                        profile = response,
                        factEditor = null,
                        goalEditor = null,
                        pendingMutationLabel = null,
                        toast = "健康画像已更新",
                    )
                }
            }.onFailure { error ->
                if (!isCurrent(token, activeMutationToken)) return@onFailure
                _state.update {
                    it.copy(
                        operationInProgress = false,
                        error = "${error.message ?: "画像更新失败"}；可使用同一请求重试，避免重复写入。",
                    )
                }
            }
        }
    }

    private fun openHistory(target: HealthProfileHistoryTarget) {
        val profile = _state.value.profile ?: return
        val exists = if (target.kind == "fact") {
            profile.facts.any { it.fact_id == target.id }
        } else {
            profile.goals.any { it.goal_id == target.id }
        }
        val owner = activeOwner
        if (!exists || owner == null || !authManager.isCurrent(owner)) {
            _state.update { it.copy(error = "无法确认这项历史记录的主体，已停止读取。") }
            return
        }
        _state.update {
            it.copy(
                historyTarget = target,
                revisionHistory = null,
                historyLoading = false,
                historyError = null,
            )
        }
        loadHistoryPage(null, append = false)
    }

    private fun loadHistoryPage(afterRevisionId: Long?, append: Boolean) {
        val state = _state.value
        val target = state.historyTarget ?: return
        val subject = state.profile?.subject_user_id ?: return
        val owner = activeOwner ?: return
        if (state.historyLoading || !authManager.isCurrent(owner)) return
        val token = newToken(owner).also { activeHistoryToken = it }
        _state.update { it.copy(historyLoading = true, historyError = null) }
        viewModelScope.launch {
            runCatching {
                if (target.kind == "fact") {
                    repo.healthProfileFactRevisions(owner, target.id, subject, afterRevisionId)
                } else {
                    repo.healthProfileGoalRevisions(owner, target.id, subject, afterRevisionId)
                }
            }.onSuccess { response ->
                if (!HealthProfileStateMachine.acceptsRevision(
                        token = token,
                        activeToken = activeHistoryToken,
                        currentOwner = currentOwner(),
                        expectedSubject = subject,
                        expectedKind = target.kind,
                        expectedTargetId = target.id,
                        response = response,
                    )
                ) {
                    if (isCurrent(token, activeHistoryToken)) {
                        _state.update {
                            it.copy(
                                historyLoading = false,
                                historyError = "历史记录主体或目标不匹配，已拒绝显示。",
                            )
                        }
                    }
                    return@onSuccess
                }
                val merged = if (append) {
                    state.revisionHistory?.let {
                        HealthProfileStateMachine.mergeRevisionPages(it, response)
                    } ?: response
                } else {
                    response
                }
                _state.update { it.copy(revisionHistory = merged, historyLoading = false) }
            }.onFailure { error ->
                if (!isCurrent(token, activeHistoryToken)) return@onFailure
                _state.update {
                    it.copy(
                        historyLoading = false,
                        historyError = "历史记录读取失败：${error.message ?: "未知错误"}",
                    )
                }
            }
        }
    }

    private fun updateGoalDraft(transform: (HealthProfileGoalEditorDraft) -> HealthProfileGoalEditorDraft) {
        _state.update { state ->
            val draft = state.goalEditor ?: return@update state
            if (state.operationInProgress) state else state.copy(goalEditor = transform(draft))
        }
    }

    private fun mutationContext(): Pair<AuthManager.AccountScopeSnapshot, Long>? {
        val owner = activeOwner
        val subject = _state.value.profile?.subject_user_id
        if (pendingMutation != null) {
            _state.update { it.copy(error = "上一项修改结果尚未确认，请先重试。") }
            return null
        }
        if (owner == null || subject == null || subject <= 0L || !authManager.isCurrent(owner)) {
            _state.update { it.copy(error = "账号或健康主体已变化，请重新读取健康画像。") }
            return null
        }
        return owner to subject
    }

    private fun validateMutationOwner(owner: AuthManager.AccountScopeSnapshot, subject: Long): Boolean {
        val valid = activeOwner == owner &&
            authManager.isCurrent(owner) &&
            _state.value.profile?.subject_user_id == subject
        if (!valid) {
            _state.update { it.copy(error = "画像主体或登录账号已变化，已停止本次修改。") }
        }
        return valid
    }

    private fun captureOwner(): AuthManager.AccountScopeSnapshot? =
        authManager.captureAccountScope().also { owner ->
            if (owner == null) {
                _state.update { it.copy(loadError = "无法确认当前登录账号，请重新登录后再试。") }
            }
        }

    private fun newToken(owner: AuthManager.AccountScopeSnapshot): HealthProfileRequestToken =
        HealthProfileRequestToken(HealthProfileOwner.from(owner), ++requestSequence)

    private fun currentOwner(): HealthProfileOwner? =
        authManager.captureAccountScope()?.let(HealthProfileOwner::from)

    private fun acceptsProfile(
        token: HealthProfileRequestToken,
        activeToken: HealthProfileRequestToken?,
        expectedSubject: Long?,
        responseSubject: Long,
    ): Boolean = HealthProfileStateMachine.acceptsProfile(
        token = token,
        activeToken = activeToken,
        currentOwner = currentOwner(),
        expectedServerSubject = expectedSubject,
        responseSubject = responseSubject,
    )

    private fun isCurrent(
        token: HealthProfileRequestToken,
        activeToken: HealthProfileRequestToken?,
    ): Boolean = token == activeToken && token.owner == currentOwner()

    private fun rejectMismatchedProfileIfCurrent(token: HealthProfileRequestToken, responseSubject: Long) {
        if (!isCurrent(token, activeLoadToken)) return
        _state.update {
            it.copy(
                loading = false,
                loadError = if (responseSubject <= 0L) {
                    "服务端未确认当前健康主体，已停止展示画像。"
                } else {
                    "账号或健康主体已变化，已拒绝显示晚到的画像响应。"
                },
            )
        }
    }

    private fun editableFactValue(fact: HealthProfileTrustFact?): String {
        val value = fact?.value_data?.get("value") ?: return ""
        return (value as? JsonPrimitive)?.contentOrNull
            ?: HealthProfileTrustPolicy.displayValue(fact.value_data)
    }

    private fun newEventId(): String = "android-profile-${UUID.randomUUID()}".take(80)

    private fun invalidateRequests() {
        requestSequence += 1L
        activeOwner = null
        activeLoadToken = null
        activeMutationToken = null
        activeHistoryToken = null
        pendingMutation = null
    }

    private sealed interface PendingMutation {
        val owner: AuthManager.AccountScopeSnapshot
        val subject: Long
        val label: String

        data class ReviewCandidate(
            override val owner: AuthManager.AccountScopeSnapshot,
            override val subject: Long,
            val candidateId: Long,
            val body: HealthProfileCandidateReviewBody,
        ) : PendingMutation { override val label = "候选更新" }

        data class UpsertFact(
            override val owner: AuthManager.AccountScopeSnapshot,
            override val subject: Long,
            val body: HealthProfileFactUpsertBody,
        ) : PendingMutation { override val label = "画像事实" }

        data class RetractFact(
            override val owner: AuthManager.AccountScopeSnapshot,
            override val subject: Long,
            val factId: Long,
            val body: HealthProfileFactRetractBody,
        ) : PendingMutation { override val label = "删除画像事实" }

        data class CreateGoal(
            override val owner: AuthManager.AccountScopeSnapshot,
            override val subject: Long,
            val body: HealthProfileGoalCreateBody,
        ) : PendingMutation { override val label = "创建健康目标" }

        data class UpdateGoal(
            override val owner: AuthManager.AccountScopeSnapshot,
            override val subject: Long,
            val goalId: Long,
            val body: HealthProfileGoalUpdateBody,
        ) : PendingMutation { override val label = "更新健康目标" }

        data class GoalStatus(
            override val owner: AuthManager.AccountScopeSnapshot,
            override val subject: Long,
            val goalId: Long,
            val body: HealthProfileGoalStatusBody,
        ) : PendingMutation { override val label = "目标状态" }
    }
}
