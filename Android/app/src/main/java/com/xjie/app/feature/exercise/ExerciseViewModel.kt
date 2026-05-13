package com.xjie.app.feature.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.ExerciseBody
import com.xjie.app.core.model.ExerciseItem
import com.xjie.app.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseUiState(
    val loading: Boolean = false,
    val items: List<ExerciseItem> = emptyList(),
    val totalMinutes: Int = 0,
    val totalKcal: Double = 0.0,
    val showAdd: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val repo: ExerciseRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ExerciseUiState())
    val state: StateFlow<ExerciseUiState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching { repo.listToday() }
            .onSuccess { resp ->
                _state.update {
                    it.copy(
                        loading = false,
                        items = resp.items,
                        totalMinutes = resp.total_minutes,
                        totalKcal = resp.total_kcal,
                    )
                }
            }
            .onFailure { e ->
                _state.update { it.copy(loading = false, error = (e as? ApiException)?.message ?: e.message) }
            }
    }

    fun showAdd(v: Boolean) = _state.update { it.copy(showAdd = v) }

    fun add(body: ExerciseBody) = viewModelScope.launch {
        runCatching { repo.create(body) }
            .onSuccess { _state.update { it.copy(showAdd = false) }; load() }
            .onFailure { e -> _state.update { it.copy(error = (e as? ApiException)?.message ?: e.message) } }
    }

    fun delete(id: Long) = viewModelScope.launch {
        runCatching { repo.delete(id) }.onSuccess { load() }
            .onFailure { e -> _state.update { it.copy(error = (e as? ApiException)?.message ?: e.message) } }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
