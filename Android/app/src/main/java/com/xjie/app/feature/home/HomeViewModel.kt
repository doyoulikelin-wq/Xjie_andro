package com.xjie.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.DashboardHealth
import com.xjie.app.core.model.GlucoseUnit
import com.xjie.app.core.model.HealthTreeSummary
import com.xjie.app.core.storage.PreferencesStore
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
    val treeSummary: HealthTreeSummary? = null,
    val contextPrecision: ContextPrecisionSummary = ContextPrecisionSummary(),
    val isOffline: Boolean = false,
    val error: String? = null,
)

data class ContextPrecisionSummary(
    val healthRecordCount: Int = 0,
    val healthExamCount: Int = 0,
    val healthIndicatorCount: Int = 0,
    val hasHealthSummary: Boolean = false,
    val historyFeedbackCount: Int = 0,
    val historyMoodCount: Int = 0,
    val historyBodyCount: Int = 0,
    val omicsCategoryCount: Int = 0,
    val omicsItemCount: Int = 0,
) {
    val score: Int
        get() {
            val healthScore = minOf(
                40,
                healthRecordCount * 8 + healthExamCount * 8 + healthIndicatorCount * 2 + if (hasHealthSummary) 6 else 0,
            )
            val historyScore = minOf(
                30,
                historyFeedbackCount * 4 + historyMoodCount * 2 + historyBodyCount * 2,
            )
            val omicsScore = minOf(30, omicsCategoryCount * 6 + minOf(omicsItemCount, 18))
            return minOf(100, healthScore + historyScore + omicsScore)
        }

    val healthDataDescription: String
        get() = "病例 ${healthRecordCount} 份 · 体检 ${healthExamCount} 份 · 指标 ${healthIndicatorCount} 项"

    val historyDescription: String
        get() = "反馈 ${historyFeedbackCount} 条 · 心情 ${historyMoodCount} 条 · 身体状态 ${historyBodyCount} 条"

    val omicsDescription: String
        get() = if (omicsCategoryCount > 0) "${omicsCategoryCount} 类 · ${omicsItemCount} 项特征" else "暂无真实多组学上传"
}

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
            val treeSummary = repo.loadTreeSummary()
            val contextPrecision = repo.loadContextPrecision()
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    dashboard = dashboard ?: it.dashboard,
                    treeSummary = treeSummary,
                    contextPrecision = contextPrecision,
                    isOffline = fromCache,
                )
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
