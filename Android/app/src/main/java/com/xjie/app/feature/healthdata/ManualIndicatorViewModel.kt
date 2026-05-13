package com.xjie.app.feature.healthdata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.IndicatorSearchItem
import com.xjie.app.core.model.ManualIndicatorBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ManualIndicatorUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<IndicatorSearchItem> = emptyList(),
    val selected: IndicatorSearchItem? = null,
    val saving: Boolean = false,
    val savedOk: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ManualIndicatorViewModel @Inject constructor(
    private val repo: HealthDataRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ManualIndicatorUiState())
    val state: StateFlow<ManualIndicatorUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun updateQuery(q: String) {
        _state.update { it.copy(query = q, savedOk = false, error = null) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(280)
            _state.update { it.copy(searching = true) }
            runCatching { repo.searchIndicators(q.trim(), limit = 20) }
                .onSuccess { list -> _state.update { it.copy(searching = false, results = list) } }
                .onFailure { e -> _state.update { it.copy(searching = false, error = e.message) } }
        }
    }

    fun select(item: IndicatorSearchItem) {
        _state.update { it.copy(selected = item, query = item.name, results = emptyList()) }
    }

    fun clearSelection() = _state.update { it.copy(selected = null) }

    fun reset() = _state.update { ManualIndicatorUiState() }

    fun submit(
        indicatorName: String,
        value: Double,
        unit: String?,
        measuredAt: OffsetDateTime,
        notes: String?,
    ) = viewModelScope.launch {
        _state.update { it.copy(saving = true, error = null) }
        runCatching {
            repo.createManualIndicator(
                ManualIndicatorBody(
                    indicator_name = indicatorName,
                    value = value,
                    unit = unit?.takeIf { it.isNotBlank() },
                    measured_at = measuredAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    notes = notes?.takeIf { it.isNotBlank() },
                )
            )
        }.onSuccess {
            _state.update { it.copy(saving = false, savedOk = true) }
        }.onFailure { e ->
            _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
