package com.xjie.app.feature.glucose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.GlucosePoint
import com.xjie.app.core.model.GlucoseRange
import com.xjie.app.core.model.GlucoseSummary
import com.xjie.app.core.model.GlucoseUnit
import com.xjie.app.core.storage.PreferencesStore
import com.xjie.app.core.util.AppLogger
import com.xjie.app.core.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChartPoint(val epochMs: Long, val value: Double)

data class GlucoseUiState(
    val window: GlucoseWindow = GlucoseWindow.H24,
    val loading: Boolean = false,
    val points: List<GlucosePoint> = emptyList(),
    val chart: List<ChartPoint> = emptyList(),
    val summary: GlucoseSummary? = null,
    val range: GlucoseRange? = null,
    val error: String? = null,
)

@HiltViewModel
class GlucoseViewModel @Inject constructor(
    private val repo: GlucoseRepository,
    prefs: PreferencesStore,
) : ViewModel() {

    private val _state = MutableStateFlow(GlucoseUiState())
    val state: StateFlow<GlucoseUiState> = _state.asStateFlow()

    val unit: StateFlow<GlucoseUnit> = prefs.glucoseUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, GlucoseUnit.MMOL)

    private var pointsJob: Job? = null

    fun loadInitial() {
        viewModelScope.launch {
            runCatching { repo.range() }
                .onSuccess { r -> _state.update { it.copy(range = r) } }
                .onFailure { AppLogger.data.w(it, "load range failed") }
            fetchPoints()
        }
    }

    fun setWindow(w: GlucoseWindow) {
        _state.update { it.copy(window = w) }
        fetchPoints()
    }

    fun fetchPoints() {
        pointsJob?.cancel()
        pointsJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val w = _state.value.window
            runCatching { repo.points(w, _state.value.range) }
                .onSuccess { pts ->
                    val chart = pts.mapNotNull {
                        val ms = DateUtils.parseISO(it.ts)?.time ?: return@mapNotNull null
                        ChartPoint(ms, it.glucose_mgdl)
                    }
                    _state.update { it.copy(points = pts, chart = chart) }
                }
                .onFailure {
                    AppLogger.data.w(it, "load points failed")
                    _state.update { st -> st.copy(error = it.localizedMessage) }
                }

            val dashboard = repo.dashboard()
            val summary = if (w == GlucoseWindow.H24) dashboard?.glucose?.last_24h
            else dashboard?.glucose?.last_7d
            _state.update { it.copy(summary = summary, loading = false) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
