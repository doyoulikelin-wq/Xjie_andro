package com.xjie.app.feature.healthplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.HealthPlan
import com.xjie.app.core.model.HealthPlanDetail
import com.xjie.app.core.model.HealthPlanQuestionnaireRequest
import com.xjie.app.core.model.PlanRevisionApplyRequest
import com.xjie.app.core.model.PlanRevisionProposal
import com.xjie.app.core.model.PlanTask
import com.xjie.app.core.model.PlanTaskUpdateRequest
import com.xjie.app.core.model.TubeWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

data class HealthPlanUiState(
    val plans: List<HealthPlan> = emptyList(),
    val selectedPlan: HealthPlanDetail? = null,
    val week: TubeWeek? = null,
    val weekStart: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY),
    val loading: Boolean = false,
    val creatingPlan: Boolean = false,
    val completingType: String? = null,
    val lastCompletedType: String? = null,
    val revisionProposal: PlanRevisionProposal? = null,
    val revisionLoading: Boolean = false,
    val revisionApplying: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HealthPlanViewModel @Inject constructor(
    private val repo: HealthPlanRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HealthPlanUiState())
    val state: StateFlow<HealthPlanUiState> = _state.asStateFlow()

    fun clearError() = _state.update { it.copy(error = null) }

    fun clearCompletionEffect() = _state.update { it.copy(lastCompletedType = null) }

    fun refresh() = viewModelScope.launch {
        val start = _state.value.weekStart
        _state.update { it.copy(loading = true) }
        runCatching {
            val plansDeferred = async { repo.plans() }
            val weekDeferred = async { repo.week(start.toString()) }
            val plans = plansDeferred.await().items
            val week = weekDeferred.await()
            val detail = _state.value.selectedPlan?.let { selected ->
                plans.firstOrNull { it.id == selected.id }?.let { repo.detail(it.id) }
            } ?: plans.firstOrNull()?.let { repo.detail(it.id) }
            Triple(plans, week, detail)
        }.onSuccess { (plans, week, detail) ->
            _state.update {
                it.copy(
                    plans = plans,
                    week = week,
                    selectedPlan = detail,
                    loading = false,
                )
            }
        }.onFailure { e ->
            _state.update { it.copy(loading = false, error = e.message) }
        }
    }

    fun selectPlan(plan: HealthPlan) = viewModelScope.launch {
        runCatching { repo.detail(plan.id) }
            .onSuccess { detail -> _state.update { it.copy(selectedPlan = detail) } }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }

    fun previousWeek() {
        _state.update { it.copy(weekStart = it.weekStart.minusWeeks(1)) }
        refresh()
    }

    fun nextWeek() {
        _state.update { it.copy(weekStart = it.weekStart.plusWeeks(1)) }
        refresh()
    }

    fun backToThisWeek() {
        _state.update { it.copy(weekStart = LocalDate.now().with(DayOfWeek.MONDAY)) }
        refresh()
    }

    fun completeToday(taskType: String) = viewModelScope.launch {
        val today = _state.value.week?.today ?: return@launch
        _state.update { it.copy(completingType = taskType) }
        runCatching { repo.complete(today, taskType) }
            .onSuccess { res ->
                _state.update { cur ->
                    val updatedWeek = cur.week?.let { week ->
                        week.copy(days = week.days.map { if (it.date == res.day.date) res.day else it })
                    }
                    cur.copy(
                        week = updatedWeek,
                        completingType = null,
                        lastCompletedType = taskType,
                    )
                }
            }
            .onFailure { e -> _state.update { it.copy(completingType = null, error = e.message) } }
    }

    fun updateTask(task: PlanTask, request: PlanTaskUpdateRequest) = viewModelScope.launch {
        runCatching { repo.updateTask(task.id, request) }
            .onSuccess { refresh() }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }

    fun generateAIRevision() = viewModelScope.launch {
        if (_state.value.revisionLoading) return@launch
        _state.update { it.copy(revisionLoading = true) }
        runCatching { repo.generateRevision(_state.value.week?.today) }
            .onSuccess { proposal ->
                _state.update { it.copy(revisionProposal = proposal, revisionLoading = false) }
            }
            .onFailure { e ->
                _state.update { it.copy(revisionLoading = false, error = e.message) }
            }
    }

    fun dismissRevision() {
        _state.update { it.copy(revisionProposal = null) }
    }

    fun applyRevision(keys: List<String>, acceptAll: Boolean = false, rejectAll: Boolean = false) = viewModelScope.launch {
        val proposal = _state.value.revisionProposal ?: return@launch
        _state.update { it.copy(revisionApplying = true) }
        runCatching {
            repo.applyRevision(
                proposal.id,
                PlanRevisionApplyRequest(
                    accepted_task_keys = keys,
                    accept_all = acceptAll,
                    reject_all = rejectAll,
                ),
            )
        }.onSuccess {
            _state.update { state -> state.copy(revisionProposal = null, revisionApplying = false) }
            refresh()
        }.onFailure { e ->
            _state.update { it.copy(revisionApplying = false, error = e.message) }
        }
    }

    fun createFromQuestionnaire(request: HealthPlanQuestionnaireRequest) = viewModelScope.launch {
        _state.update { it.copy(creatingPlan = true) }
        runCatching { repo.createFromQuestionnaire(request) }
            .onSuccess { detail ->
                refresh()
                _state.update { it.copy(selectedPlan = detail, creatingPlan = false) }
            }
            .onFailure { e ->
                _state.update { it.copy(creatingPlan = false, error = e.message) }
            }
    }
}
