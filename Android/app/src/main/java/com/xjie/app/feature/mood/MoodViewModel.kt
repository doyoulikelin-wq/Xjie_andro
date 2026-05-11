package com.xjie.app.feature.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.MoodDay
import com.xjie.app.core.model.MoodGlucoseCorrelation
import com.xjie.app.core.model.MoodLevel
import com.xjie.app.core.model.MoodSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class MoodUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val days: List<MoodDay> = emptyList(),
    val correlation: MoodGlucoseCorrelation? = null,
    val lookbackDays: Int = 7,
    val error: String? = null,
) {
    val today: MoodDay?
        get() {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val key = fmt.format(Date())
            return days.firstOrNull { it.date == key }
        }
}

@HiltViewModel
class MoodViewModel @Inject constructor(
    private val repo: MoodRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MoodUiState())
    val state: StateFlow<MoodUiState> = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        val d = _state.value.lookbackDays
        runCatching { repo.days(d) }
            .onSuccess { days -> _state.update { it.copy(days = days) } }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
        val corr = repo.correlation(maxOf(d, 7))
        _state.update { it.copy(loading = false, correlation = corr) }
    }

    fun setLookback(days: Int) {
        _state.update { it.copy(lookbackDays = days) }
        refresh()
    }

    fun checkIn(segment: MoodSegment, level: MoodLevel) = viewModelScope.launch {
        _state.update { it.copy(saving = true) }
        runCatching { repo.checkIn(segment.key, level.value) }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
        _state.update { it.copy(saving = false) }
        refresh()
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
