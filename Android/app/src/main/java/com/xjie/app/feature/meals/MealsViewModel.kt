package com.xjie.app.feature.meals

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.DietaryAdmissionPolicy
import com.xjie.app.core.model.DietaryBusinessDay
import com.xjie.app.core.model.DietaryDailySummaryStatus
import com.xjie.app.core.model.DietaryDashboardResponse
import com.xjie.app.core.model.DietaryDayCompleteBody
import com.xjie.app.core.model.DietaryDraftConfirmBody
import com.xjie.app.core.model.DietaryDraftCreateBody
import com.xjie.app.core.model.DietaryDraftEditor
import com.xjie.app.core.model.DietaryDraftRetryBody
import com.xjie.app.core.model.DietaryEntrySource
import com.xjie.app.core.model.DietaryFoodItem
import com.xjie.app.core.model.DietaryMealDraft
import com.xjie.app.core.model.DietaryMealRecord
import com.xjie.app.core.model.DietaryMealType
import com.xjie.app.core.model.DietaryMutationBody
import com.xjie.app.core.model.DietaryRecordEditor
import com.xjie.app.core.model.DietaryRecordReuseBody
import com.xjie.app.core.model.DietaryRecordUpdateBody
import com.xjie.app.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DietaryLoadState { Idle, Loading, Loaded, Empty, Error }

data class MealsUiState(
    val loadState: DietaryLoadState = DietaryLoadState.Idle,
    val refreshing: Boolean = false,
    val mutating: Boolean = false,
    val preparingPhoto: Boolean = false,
    val selectedDateKey: String,
    val dashboard: DietaryDashboardResponse? = null,
    val recentRecords: List<DietaryMealRecord> = emptyList(),
    val dailySummaryStatus: DietaryDailySummaryStatus? = null,
    val activeDraftEditor: DietaryDraftEditor? = null,
    val activeRecordEditor: DietaryRecordEditor? = null,
    val error: String? = null,
    val message: String? = null,
) {
    val records: List<DietaryMealRecord> get() = dashboard?.records.orEmpty()
    val pendingDrafts: List<DietaryMealDraft> get() = dashboard?.pending_drafts.orEmpty()
    val loading: Boolean get() = loadState == DietaryLoadState.Loading
    val isToday: Boolean get() = dashboard?.is_today ?: false
    val hasContent: Boolean
        get() = records.isNotEmpty() || pendingDrafts.isNotEmpty() ||
            dashboard?.selected_day_summary != null || dashboard?.displayed_summary != null
}

@HiltViewModel
class MealsViewModel @Inject constructor(
    private val repository: MealsDataSource,
    private val authManager: AuthManager,
) : ViewModel() {
    private var nowProvider: () -> Instant = Instant::now
    private var eventIdProvider: () -> String = { UUID.randomUUID().toString().lowercase() }
    private var idempotency = DietaryIdempotencyLedger { eventIdProvider() }
    private val _state = MutableStateFlow(
        MealsUiState(selectedDateKey = DietaryBusinessDay.dateKey(nowProvider()))
    )
    val state: StateFlow<MealsUiState> = _state.asStateFlow()

    private var activeOwner: AuthManager.AccountScopeSnapshot? = null
    private var authoritativeSubjectUserId: Long? = null
    private var requestSequence = 0L
    private var observedAuthGeneration = authManager.generation

    init {
        viewModelScope.launch {
            authManager.state.collect { authState ->
                if (authState.generation == observedAuthGeneration) return@collect
                observedAuthGeneration = authState.generation
                requestSequence += 1L
                activeOwner = null
                authoritativeSubjectUserId = null
                idempotency.clearForAccountChange()
                _state.value = MealsUiState(
                    selectedDateKey = DietaryBusinessDay.dateKey(nowProvider()),
                    loadState = DietaryLoadState.Error,
                    error = if (authState.isLoggedIn) {
                        "登录账号或健康主体已变化，请重新读取饮食记录。"
                    } else {
                        "登录已失效，请重新登录后再记录饮食。"
                    },
                )
            }
        }
    }

    fun fetchData(refresh: Boolean = false) {
        val owner = captureOwner() ?: return
        val requestId = ++requestSequence
        if (activeOwner != owner) authoritativeSubjectUserId = null
        activeOwner = owner
        val selectedDate = _state.value.selectedDateKey
        val expectedSubject = authoritativeSubjectUserId
        _state.update {
            it.copy(
                loadState = if (!refresh && it.dashboard == null) DietaryLoadState.Loading else it.loadState,
                refreshing = refresh,
                error = null,
                message = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                val dashboard = repository.dashboard(owner, selectedDate, expectedSubject)
                validateDashboard(dashboard, selectedDate, expectedSubject)
                if (!canCommit(owner, requestId)) throw DietaryStaleOwnerException
                val recent = runCatching {
                    repository.recent(owner, dashboard.subject_user_id, 12)
                }.getOrNull()
                if (!canCommit(owner, requestId)) throw DietaryStaleOwnerException
                val daily = runCatching { repository.dailySummary(owner) }.getOrNull()
                Triple(dashboard, recent, daily)
            }.onSuccess { (dashboard, recent, daily) ->
                if (!canCommit(owner, requestId)) return@onSuccess
                if (recent != null && (
                        recent.subject_user_id != dashboard.subject_user_id ||
                            recent.items.any { it.subject_user_id != dashboard.subject_user_id }
                        )
                ) {
                    applyLoadFailure("最近餐食主体不匹配，已停止展示。")
                    return@onSuccess
                }
                authoritativeSubjectUserId = dashboard.subject_user_id
                _state.update {
                    it.copy(
                        loadState = if (
                            dashboard.records.isEmpty() &&
                            dashboard.pending_drafts.isEmpty() &&
                            dashboard.selected_day_summary == null &&
                            dashboard.displayed_summary == null
                        ) DietaryLoadState.Empty else DietaryLoadState.Loaded,
                        refreshing = false,
                        dashboard = dashboard,
                        recentRecords = recent?.items.orEmpty(),
                        dailySummaryStatus = daily,
                        error = null,
                    )
                }
            }.onFailure { error ->
                if (error === DietaryStaleOwnerException || !canCommit(owner, requestId)) return@onFailure
                applyLoadFailure(errorMessage(error))
            }
        }
    }

    fun moveDate(days: Long) {
        val current = runCatching { LocalDate.parse(_state.value.selectedDateKey) }.getOrNull() ?: return
        val target = DietaryBusinessDay.clampSelection(current.plusDays(days), nowProvider())
        if (target.toString() == _state.value.selectedDateKey) return
        requestSequence += 1L
        _state.update {
            it.copy(
                selectedDateKey = target.toString(),
                dashboard = null,
                recentRecords = emptyList(),
                activeDraftEditor = null,
                activeRecordEditor = null,
                loadState = DietaryLoadState.Idle,
                error = null,
            )
        }
        fetchData()
    }

    fun createTextDraft(rawText: String, source: DietaryEntrySource) {
        val text = rawText.trim()
        if (source !in setOf(
                DietaryEntrySource.Text,
                DietaryEntrySource.Voice,
                DietaryEntrySource.Chat,
                DietaryEntrySource.Manual,
            ) || text.isEmpty()
        ) {
            _state.update { it.copy(error = "请先描述这餐吃了什么。") }
            return
        }
        if (text.length > DietaryFoodItem.MAX_DESCRIPTION_LENGTH) {
            _state.update { it.copy(error = "一次最多输入 4000 个字符，请删减后再试。") }
            return
        }
        val owner = captureMutationOwner() ?: return
        val timestamp = nowProvider()
        val selectedDate = _state.value.selectedDateKey
        val prepared = idempotency.prepare(
            accountScope = owner.accountScope,
            key = "create-text",
            operation = "POST api/dietary-records/drafts",
        ) { eventId ->
            DietaryDraftCreateBody(
                subject_user_id = authoritativeSubjectUserId,
                client_event_id = eventId,
                source_type = source.wireValue,
                diet_date = selectedDate,
                timezone = DietaryBusinessDay.TIME_ZONE,
                meal_type = DietaryBusinessDay.inferredMealType(timestamp).wireValue,
                eaten_at = DietaryBusinessDay.timestampOnDate(LocalDate.parse(selectedDate), timestamp),
                food_items = emptyList(),
                raw_input = text,
            )
        }
        _state.update { it.copy(mutating = true, error = null, message = null) }
        viewModelScope.launch {
            runCatching { repository.createDraft(owner, prepared.payload) }
                .onSuccess { draft ->
                    if (!isCurrent(owner)) return@onSuccess
                    if (!validateDraft(draft, prepared.payload.subject_user_id) ||
                        draft.source_type != prepared.payload.source_type ||
                        draft.diet_date != prepared.payload.diet_date ||
                        draft.timezone != prepared.payload.timezone
                    ) {
                        retainAmbiguous(prepared, "服务器没有返回可逐项确认的饮食草稿。")
                        return@onSuccess
                    }
                    idempotency.resolve(prepared, DietaryDeliveryOutcome.Success)
                    acceptDraft(draft, "已识别为待确认草稿，请逐项核对。")
                }
                .onFailure { error -> mutationFailed(owner, prepared, error) }
        }
    }

    fun createPhotoDraft(uri: Uri, source: DietaryEntrySource) {
        if (source !in setOf(DietaryEntrySource.Camera, DietaryEntrySource.PhotoLibrary)) return
        val owner = captureMutationOwner() ?: return
        val timestamp = nowProvider()
        val selectedDate = _state.value.selectedDateKey
        _state.update { it.copy(preparingPhoto = true, mutating = true, error = null, message = null) }
        viewModelScope.launch {
            runCatching { repository.readPhoto(uri) }
                .onFailure { error ->
                    if (isCurrent(owner)) {
                        _state.update {
                            it.copy(preparingPhoto = false, mutating = false, error = errorMessage(error))
                        }
                    }
                }
                .onSuccess { photo ->
                    if (!isCurrent(owner)) return@onSuccess
                    val operation = PhotoDraftOperation(
                        payload = photo.copy(bytes = photo.bytes.copyOf()),
                        subjectUserId = authoritativeSubjectUserId,
                        source = source,
                        dietDate = selectedDate,
                        mealType = DietaryBusinessDay.inferredMealType(timestamp),
                        eatenAt = DietaryBusinessDay.timestampOnDate(LocalDate.parse(selectedDate), timestamp),
                    )
                    val prepared = idempotency.prepare(
                        accountScope = owner.accountScope,
                        key = "create-photo",
                        operation = "PUT api/dietary-records/drafts/photo",
                    ) { eventId -> operation.copy(eventId = eventId) }
                    runCatching {
                        repository.createPhotoDraft(
                            owner = owner,
                            payload = prepared.payload.payload,
                            eventId = prepared.payload.eventId,
                            dietDate = prepared.payload.dietDate,
                            mealType = prepared.payload.mealType.wireValue,
                            eatenAt = prepared.payload.eatenAt,
                            source = prepared.payload.source.wireValue,
                            subjectUserId = prepared.payload.subjectUserId,
                        )
                    }.onSuccess { draft ->
                        if (!isCurrent(owner)) return@onSuccess
                        if (!validateDraft(draft, prepared.payload.subjectUserId) ||
                            draft.source != prepared.payload.source ||
                            draft.diet_date != prepared.payload.dietDate
                        ) {
                            retainAmbiguous(prepared, "照片识别结果未形成可确认草稿，未写入正式记录。")
                            return@onSuccess
                        }
                        idempotency.resolve(prepared, DietaryDeliveryOutcome.Success)
                        acceptDraft(draft, "照片已识别为待确认草稿，请核对后保存。")
                    }.onFailure { error -> mutationFailed(owner, prepared, error) }
                }
        }
    }

    fun activateDraft(draft: DietaryMealDraft) {
        if (!validateDraft(draft, authoritativeSubjectUserId)) return
        _state.update { it.copy(activeDraftEditor = DietaryDraftEditor.from(draft), error = null) }
    }

    fun dismissDraftEditor() = _state.update { it.copy(activeDraftEditor = null) }

    fun updateDraftMealType(type: DietaryMealType) = _state.update { state ->
        state.copy(activeDraftEditor = state.activeDraftEditor?.copy(mealType = type))
    }

    fun updateDraftFood(index: Int, name: String? = null, portion: String? = null) =
        _state.update { state ->
            val editor = state.activeDraftEditor ?: return@update state
            if (index !in editor.foodItems.indices) return@update state
            val items = editor.foodItems.toMutableList()
            val current = items[index]
            items[index] = current.copy(
                name = name ?: current.name,
                portion_text = portion ?: current.portion_text,
            )
            state.copy(activeDraftEditor = editor.copy(foodItems = items.toList()))
        }

    fun addDraftFoodItem() = _state.update { state ->
        val editor = state.activeDraftEditor ?: return@update state
        state.copy(
            activeDraftEditor = editor.copy(
                foodItems = editor.foodItems + DietaryFoodItem(name = "", is_estimated = false),
            ),
        )
    }

    fun removeDraftFoodItem(index: Int) = _state.update { state ->
        val editor = state.activeDraftEditor ?: return@update state
        if (editor.foodItems.size <= 1 || index !in editor.foodItems.indices) return@update state
        state.copy(activeDraftEditor = editor.copy(foodItems = editor.foodItems.filterIndexed { i, _ -> i != index }))
    }

    fun confirmActiveDraft() {
        val editor = _state.value.activeDraftEditor ?: return
        if (!editor.isValid) {
            _state.update { it.copy(error = "请逐项确认餐次、食物名称和大致份量。") }
            return
        }
        val owner = captureMutationOwner() ?: return
        val subject = matchingSubject(editor.original.subject_user_id) ?: return
        val items = sanitized(editor.foodItems)
        val operationPath = "api/dietary-records/drafts/${editor.original.draft_id}/confirm"
        val prepared = idempotency.prepare(
            accountScope = owner.accountScope,
            key = "confirm:${editor.original.draft_id}:${editor.original.version}",
            operation = "POST $operationPath",
        ) { eventId ->
            DietaryDraftConfirmBody(
                subject_user_id = subject,
                client_event_id = eventId,
                expected_version = editor.original.version,
                timezone = DietaryBusinessDay.TIME_ZONE,
                diet_date = editor.original.diet_date,
                meal_type = editor.mealType.wireValue,
                eaten_at = editor.eatenAt,
                food_items = items,
                portion_text = editor.portionText.trim().ifEmpty { null },
                structure = editor.original.structure.toMap(),
                estimated_nutrition = editor.original.estimated_nutrition.toMap(),
                field_confidences = editor.original.field_confidences.toMap(),
                recognition_confidence = editor.original.recognition_confidence,
            )
        }
        _state.update { it.copy(mutating = true, error = null, message = null) }
        viewModelScope.launch {
            runCatching { repository.confirmDraft(owner, editor.original.draft_id, prepared.payload) }
                .onSuccess { record ->
                    if (!isCurrent(owner)) return@onSuccess
                    if (!DietaryAdmissionPolicy.acceptsFormalRecord(record, subject)) {
                        retainAmbiguous(prepared, "服务器未返回显式确认的正式记录，草稿仍保持待确认。")
                        return@onSuccess
                    }
                    idempotency.resolve(prepared, DietaryDeliveryOutcome.Success)
                    _state.update { state ->
                        val dashboard = state.dashboard ?: return@update state.copy(mutating = false)
                        state.copy(
                            mutating = false,
                            activeDraftEditor = null,
                            dashboard = dashboard.copy(
                                records = (dashboard.records.filterNot { it.record_id == record.record_id } + record)
                                    .sortedBy { it.eaten_at },
                                pending_drafts = dashboard.pending_drafts.filterNot {
                                    it.draft_id == editor.original.draft_id
                                },
                                recorded_meal_count = dashboard.records
                                    .count { it.record_id != record.record_id } + 1,
                                pending_count = dashboard.pending_drafts
                                    .count { it.draft_id != editor.original.draft_id },
                            ),
                            message = "已显式确认并写入正式饮食记录。",
                        )
                    }
                    fetchData(refresh = true)
                }
                .onFailure { error -> mutationFailed(owner, prepared, error) }
        }
    }

    fun retryRecognition(draft: DietaryMealDraft) {
        if (!draft.canRetryRecognition) {
            _state.update { it.copy(error = "这份草稿不能重新识别，请直接手动补充。") }
            return
        }
        val owner = captureMutationOwner() ?: return
        val subject = matchingSubject(draft.subject_user_id) ?: return
        val prepared = idempotency.prepare(
            owner.accountScope,
            "retry:${draft.draft_id}:${draft.version}",
            "POST api/dietary-records/drafts/${draft.draft_id}/retry-recognition",
        ) { eventId -> DietaryDraftRetryBody(subject, eventId, draft.version) }
        _state.update { it.copy(mutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.retryRecognition(owner, draft.draft_id, prepared.payload) }
                .onSuccess { retried ->
                    if (!isCurrent(owner)) return@onSuccess
                    if (!validateDraft(retried, subject) || retried.draft_id != draft.draft_id ||
                        retried.version <= draft.version
                    ) {
                        retainAmbiguous(prepared, "识别重试结果已过期，草稿仍保持待确认。")
                        return@onSuccess
                    }
                    idempotency.resolve(prepared, DietaryDeliveryOutcome.Success)
                    acceptDraft(retried, "识别已更新，请继续核对。")
                }
                .onFailure { error -> mutationFailed(owner, prepared, error) }
        }
    }

    fun activateRecord(record: DietaryMealRecord) {
        if (!DietaryAdmissionPolicy.acceptsFormalRecord(record, record.subject_user_id)) return
        _state.update { it.copy(activeRecordEditor = DietaryRecordEditor.from(record), error = null) }
    }

    fun dismissRecordEditor() = _state.update { it.copy(activeRecordEditor = null) }

    fun updateRecordMealType(type: DietaryMealType) = _state.update { state ->
        state.copy(activeRecordEditor = state.activeRecordEditor?.copy(mealType = type))
    }

    fun updateRecordFood(index: Int, name: String? = null, portion: String? = null) =
        _state.update { state ->
            val editor = state.activeRecordEditor ?: return@update state
            if (index !in editor.foodItems.indices) return@update state
            val items = editor.foodItems.toMutableList()
            val current = items[index]
            items[index] = current.copy(
                name = name ?: current.name,
                portion_text = portion ?: current.portion_text,
            )
            state.copy(activeRecordEditor = editor.copy(foodItems = items.toList()))
        }

    fun saveActiveRecord() {
        val editor = _state.value.activeRecordEditor ?: return
        if (!editor.isValid) {
            _state.update { it.copy(error = "请保留至少一个有效食物项。") }
            return
        }
        val owner = captureMutationOwner() ?: return
        val subject = matchingSubject(editor.original.subject_user_id) ?: return
        val prepared = idempotency.prepare(
            owner.accountScope,
            "update:${editor.original.record_id}:${editor.original.version}",
            "PATCH api/dietary-records/records/${editor.original.record_id}",
        ) { eventId ->
            DietaryRecordUpdateBody(
                subject_user_id = subject,
                client_event_id = eventId,
                expected_version = editor.original.version,
                timezone = DietaryBusinessDay.TIME_ZONE,
                diet_date = editor.original.diet_date,
                meal_type = editor.mealType.wireValue,
                eaten_at = editor.eatenAt,
                food_items = sanitized(editor.foodItems),
                portion_text = editor.portionText.trim().ifEmpty { null },
                structure = editor.original.structure.toMap(),
                estimated_nutrition = editor.original.estimated_nutrition.toMap(),
                field_confidences = editor.original.field_confidences.toMap(),
                recognition_confidence = editor.original.confidence,
            )
        }
        _state.update { it.copy(mutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.updateRecord(owner, editor.original.record_id, prepared.payload) }
                .onSuccess { record ->
                    if (!isCurrent(owner)) return@onSuccess
                    if (!DietaryAdmissionPolicy.acceptsFormalRecord(
                            record,
                            subject,
                            minimumVersion = editor.original.version + 1,
                        )
                    ) {
                        retainAmbiguous(prepared, "记录版本没有前进，请刷新后重试。")
                        return@onSuccess
                    }
                    idempotency.resolve(prepared, DietaryDeliveryOutcome.Success)
                    _state.update { it.copy(mutating = false, activeRecordEditor = null, message = "饮食记录已更新。") }
                    fetchData(refresh = true)
                }
                .onFailure { error -> mutationFailed(owner, prepared, error) }
        }
    }

    fun deleteRecord(record: DietaryMealRecord) {
        val owner = captureMutationOwner() ?: return
        val subject = matchingSubject(record.subject_user_id) ?: return
        val prepared = idempotency.prepare(
            owner.accountScope,
            "delete:${record.record_id}:${record.version}",
            "DELETE api/dietary-records/records/${record.record_id}",
        ) { eventId -> DietaryMutationBody(subject, eventId, record.version) }
        _state.update { it.copy(mutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.deleteRecord(owner, record.record_id, prepared.payload) }
                .onSuccess { deleted ->
                    if (!isCurrent(owner)) return@onSuccess
                    if (deleted.subject_user_id != subject || deleted.record_id != record.record_id ||
                        deleted.status != "deleted" || deleted.version <= record.version
                    ) {
                        retainAmbiguous(prepared, "删除结果版本不匹配，请刷新确认。")
                        return@onSuccess
                    }
                    idempotency.resolve(prepared, DietaryDeliveryOutcome.Success)
                    _state.update { it.copy(mutating = false, activeRecordEditor = null, message = "已删除正式记录。") }
                    fetchData(refresh = true)
                }
                .onFailure { error -> mutationFailed(owner, prepared, error) }
        }
    }

    fun reuseRecord(record: DietaryMealRecord) {
        val owner = captureMutationOwner() ?: return
        val subject = matchingSubject(record.subject_user_id) ?: return
        val timestamp = nowProvider()
        val selected = LocalDate.parse(_state.value.selectedDateKey)
        val prepared = idempotency.prepare(
            owner.accountScope,
            "reuse:${record.record_id}:${record.version}:${selected}",
            "POST api/dietary-records/records/${record.record_id}/reuse",
        ) { eventId ->
            DietaryRecordReuseBody(
                subject_user_id = subject,
                client_event_id = eventId,
                expected_version = record.version,
                timezone = DietaryBusinessDay.TIME_ZONE,
                diet_date = selected.toString(),
                meal_type = record.mealType.takeUnless { it == DietaryMealType.Unknown }
                    ?.wireValue ?: DietaryBusinessDay.inferredMealType(timestamp).wireValue,
                eaten_at = DietaryBusinessDay.timestampOnDate(selected, timestamp),
            )
        }
        _state.update { it.copy(mutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.reuseRecord(owner, record.record_id, prepared.payload) }
                .onSuccess { draft ->
                    if (!isCurrent(owner)) return@onSuccess
                    if (!DietaryAdmissionPolicy.reuseRemainsPending(draft, subject)) {
                        retainAmbiguous(prepared, "复用结果不是待确认草稿，未写入正式记录。")
                        return@onSuccess
                    }
                    idempotency.resolve(prepared, DietaryDeliveryOutcome.Success)
                    acceptDraft(draft, "已复用为待确认草稿，请核对本餐份量。")
                }
                .onFailure { error -> mutationFailed(owner, prepared, error) }
        }
    }

    fun completeSelectedDay() {
        val owner = captureMutationOwner() ?: return
        val subject = authoritativeSubjectUserId ?: run {
            _state.update { it.copy(error = "请先刷新以确认当前饮食主体。") }
            return
        }
        val selected = _state.value.selectedDateKey
        val prepared = idempotency.prepare(
            owner.accountScope,
            "complete:$selected",
            "POST api/dietary-records/days/$selected/complete",
        ) { eventId ->
            DietaryDayCompleteBody(
                timezone = DietaryBusinessDay.TIME_ZONE,
                subject_user_id = subject,
                client_event_id = eventId,
                complete_with_confirmed_only = true,
            )
        }
        _state.update { it.copy(mutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.completeDay(owner, selected, prepared.payload) }
                .onSuccess { response ->
                    if (!isCurrent(owner)) return@onSuccess
                    if (response.subject_user_id != subject || response.diet_date != selected ||
                        response.summary?.subject_user_id?.let { it != subject } == true
                    ) {
                        retainAmbiguous(prepared, "饮食日完成结果不匹配，请刷新后确认。")
                        return@onSuccess
                    }
                    idempotency.resolve(prepared, DietaryDeliveryOutcome.Success)
                    _state.update { it.copy(mutating = false, message = "已按已确认记录结束当日。") }
                    fetchData(refresh = true)
                }
                .onFailure { error -> mutationFailed(owner, prepared, error) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    internal fun setDeterministicProvidersForTesting(
        now: () -> Instant,
        eventId: () -> String,
    ) {
        nowProvider = now
        eventIdProvider = eventId
        idempotency = DietaryIdempotencyLedger { eventIdProvider() }
        _state.update { it.copy(selectedDateKey = DietaryBusinessDay.dateKey(nowProvider())) }
    }

    private fun captureOwner(): AuthManager.AccountScopeSnapshot? =
        authManager.captureAccountScope().also { owner ->
            if (owner == null) applyLoadFailure("无法确认当前登录账号，请重新登录后再试。")
        }

    private fun captureMutationOwner(): AuthManager.AccountScopeSnapshot? {
        val owner = captureOwner() ?: return null
        if (activeOwner != owner) {
            _state.update { it.copy(error = "账号或健康主体已变化，请先刷新饮食记录。") }
            return null
        }
        return owner
    }

    private fun matchingSubject(expected: Long): Long? {
        val current = authoritativeSubjectUserId
        if (current == null || current != expected) {
            _state.update { it.copy(error = "账号或记录主体不匹配，请刷新后重试。") }
            return null
        }
        return current
    }

    private fun canCommit(owner: AuthManager.AccountScopeSnapshot, requestId: Long): Boolean =
        requestSequence == requestId && activeOwner == owner && authManager.isCurrent(owner)

    private fun isCurrent(owner: AuthManager.AccountScopeSnapshot): Boolean =
        activeOwner == owner && authManager.isCurrent(owner)

    private fun validateDashboard(
        dashboard: DietaryDashboardResponse,
        selectedDate: String,
        expectedSubject: Long?,
    ) {
        check(dashboard.subject_user_id > 0L)
        check(dashboard.selected_date == selectedDate)
        check(expectedSubject == null || dashboard.subject_user_id == expectedSubject)
        check(dashboard.records.all { it.subject_user_id == dashboard.subject_user_id })
        check(dashboard.pending_drafts.all {
            it.subject_user_id == dashboard.subject_user_id && DietaryAdmissionPolicy.acceptsPendingDraft(
                it,
                dashboard.subject_user_id,
            )
        })
        check(dashboard.selected_day_summary?.subject_user_id?.let { it == dashboard.subject_user_id } ?: true)
        check(dashboard.displayed_summary?.subject_user_id?.let { it == dashboard.subject_user_id } ?: true)
    }

    private fun validateDraft(draft: DietaryMealDraft, expectedSubject: Long?): Boolean =
        DietaryAdmissionPolicy.acceptsPendingDraft(draft, expectedSubject)

    private fun acceptDraft(draft: DietaryMealDraft, message: String) {
        authoritativeSubjectUserId = draft.subject_user_id
        _state.update { state ->
            val dashboard = state.dashboard
            state.copy(
                mutating = false,
                preparingPhoto = false,
                activeDraftEditor = DietaryDraftEditor.from(draft),
                dashboard = dashboard?.copy(
                    pending_drafts = listOf(draft) + dashboard.pending_drafts.filterNot {
                        it.draft_id == draft.draft_id
                    },
                    pending_count = dashboard.pending_drafts.count { it.draft_id != draft.draft_id } + 1,
                ),
                message = message,
                error = null,
            )
        }
    }

    private fun sanitized(items: List<DietaryFoodItem>): List<DietaryFoodItem> =
        items.mapNotNull { item ->
            val name = item.name.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            item.copy(
                name = name.take(DietaryFoodItem.MAX_NAME_LENGTH),
                portion_text = item.portion_text?.trim()?.takeIf(String::isNotEmpty),
                categories = item.categories.toList(),
            )
        }.toList()

    private fun applyLoadFailure(message: String) {
        _state.update {
            it.copy(
                loadState = DietaryLoadState.Error,
                refreshing = false,
                mutating = false,
                preparingPhoto = false,
                error = message,
            )
        }
    }

    private fun <T : Any> retainAmbiguous(
        operation: PreparedDietaryOperation<T>,
        message: String,
    ) {
        idempotency.resolve(operation, DietaryDeliveryOutcome.AmbiguousFailure)
        _state.update {
            it.copy(mutating = false, preparingPhoto = false, error = message)
        }
    }

    private fun <T : Any> mutationFailed(
        owner: AuthManager.AccountScopeSnapshot,
        operation: PreparedDietaryOperation<T>,
        error: Throwable,
    ) {
        val outcome = deliveryOutcome(error)
        idempotency.resolve(operation, outcome)
        if (!isCurrent(owner)) return
        _state.update {
            it.copy(
                mutating = false,
                preparingPhoto = false,
                error = errorMessage(error),
            )
        }
    }

    private fun deliveryOutcome(error: Throwable): DietaryDeliveryOutcome = when (error) {
        ApiException.NotLoggedIn, is ApiException.InvalidUrl -> DietaryDeliveryOutcome.DefinitiveFailure
        is ApiException.HttpError -> if (error.code in 400..499 && error.code != 408) {
            DietaryDeliveryOutcome.DefinitiveFailure
        } else {
            DietaryDeliveryOutcome.AmbiguousFailure
        }
        else -> DietaryDeliveryOutcome.AmbiguousFailure
    }

    private fun errorMessage(error: Throwable): String = when (error) {
        is IOException -> "网络暂不可用，已保留原请求；恢复网络后重试会复用同一事件。"
        is ApiException -> error.message ?: "饮食记录暂时无法更新。"
        else -> error.message ?: "饮食记录暂时无法更新，请稍后重试。"
    }

    private data class PhotoDraftOperation(
        val payload: DietaryPhotoPayload,
        val subjectUserId: Long?,
        val source: DietaryEntrySource,
        val dietDate: String,
        val mealType: DietaryMealType,
        val eatenAt: String,
        val eventId: String = "",
    )
}

private object DietaryStaleOwnerException : IllegalStateException("dietary owner changed")
