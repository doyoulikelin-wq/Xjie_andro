package com.xjie.app.feature.healthdata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.IndicatorExplanation
import com.xjie.app.core.model.IndicatorInfo
import com.xjie.app.core.model.IndicatorTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IndicatorTrendUiState(
    val trendLoading: Boolean = false,
    val trends: List<IndicatorTrend> = emptyList(),
    val watchedNames: List<String> = emptyList(),
    val allIndicators: List<IndicatorInfo> = emptyList(),
    val explanations: Map<String, IndicatorExplanation> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel
class IndicatorTrendViewModel @Inject constructor(
    private val repo: HealthDataRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(IndicatorTrendUiState())
    val state: StateFlow<IndicatorTrendUiState> = _state.asStateFlow()

    fun fetchIndicators() = viewModelScope.launch {
        _state.update { it.copy(trendLoading = true) }
        runCatching {
            val all = repo.listIndicators()
            val watched = repo.watchedIndicators()
            val names = watched.map { it.indicator_name }
            val trends = if (names.isNotEmpty()) repo.trends(names) else emptyList()
            Triple(all, names, trends)
        }.onSuccess { (all, names, trends) ->
            _state.update {
                it.copy(
                    trendLoading = false,
                    allIndicators = all,
                    watchedNames = names,
                    trends = trends,
                )
            }
        }.onFailure { e ->
            _state.update { it.copy(trendLoading = false, error = e.message) }
        }
    }

    fun applySelection(selected: Set<String>) = viewModelScope.launch {
        val current = _state.value.watchedNames.toSet()
        val toAdd = selected - current
        val toRemove = current - selected
        runCatching {
            toAdd.forEach { repo.watch(it) }
            toRemove.forEach { repo.unwatch(it) }
        }
        fetchIndicators()
    }

    fun fetchExplanation(name: String) = viewModelScope.launch {
        if (_state.value.explanations.containsKey(name)) return@launch
        runCatching { repo.explain(name) }
            .onSuccess { exp ->
                _state.update {
                    it.copy(explanations = it.explanations + (name to exp))
                }
            }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
