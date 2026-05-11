package com.xjie.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.DashboardHealth
import com.xjie.app.core.model.GlucoseUnit
import com.xjie.app.core.model.ProactiveMessage
import com.xjie.app.core.storage.PreferencesStore
import com.xjie.app.core.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val dashboard: DashboardHealth? = null,
    val proactive: ProactiveMessage? = null,
    val isOffline: Boolean = false,
    val interventionIndex: Int = 1,   // 0=L1, 1=L2, 2=L3
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: HomeRepository,
    authManager: AuthManager,
    prefs: PreferencesStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val subjectId: StateFlow<String> =
        kotlinx.coroutines.flow.MutableStateFlow("").also { target ->
            viewModelScope.launch { authManager.state.collect { target.value = it.subjectId } }
        }

    val glucoseUnit: StateFlow<GlucoseUnit> = prefs.glucoseUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, GlucoseUnit.MMOL)

    fun refresh() { fetch(isPullToRefresh = true) }

    fun load() { fetch(isPullToRefresh = false) }

    private fun fetch(isPullToRefresh: Boolean) {
        viewModelScope.launch {
            _state.update {
                if (isPullToRefresh) it.copy(refreshing = true) else it.copy(loading = true)
            }
            val (dashboard, fromCache) = repo.loadDashboard()
            val proactive = repo.loadProactive()
            val settings = repo.loadSettings()
            val idx = when (settings?.intervention_level) {
                "L1" -> 0
                "L3" -> 2
                else -> 1
            }
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    dashboard = dashboard ?: it.dashboard,
                    proactive = proactive,
                    isOffline = fromCache,
                    interventionIndex = idx,
                )
            }
        }
    }

    fun setInterventionIndex(i: Int) {
        _state.update { it.copy(interventionIndex = i) }
        viewModelScope.launch {
            val level = when (i) { 0 -> "L1"; 2 -> "L3"; else -> "L2" }
            runCatching { repo.updateInterventionLevel(level) }
                .onFailure { AppLogger.ui.w(it, "update intervention failed") }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
