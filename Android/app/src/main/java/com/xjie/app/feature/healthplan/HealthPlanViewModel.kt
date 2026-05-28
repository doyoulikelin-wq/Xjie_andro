package com.xjie.app.feature.healthplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.HealthPlan
import com.xjie.app.core.model.HealthPlanDetail
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
    val completingType: String? = null,
    val error: String? = null,
)

@HiltViewModel
class HealthPlanViewModel @Inject constructor(
    private val repo: HealthPlanRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HealthPlanUiState())
    val state: StateFlow<HealthPlanUiState> = _state.asStateFlow()

    fun clearError() = _state.update { it.copy(error = null) }

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
                    cur.copy(week = updatedWeek, completingType = null)
                }
            }
            .onFailure { e -> _state.update { it.copy(completingType = null, error = e.message) } }
    }
}
