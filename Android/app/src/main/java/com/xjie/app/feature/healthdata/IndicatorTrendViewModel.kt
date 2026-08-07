package com.xjie.app.feature.healthdata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.auth.AuthManager
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
    private val authManager: AuthManager,
) : ViewModel() {
    private val _state = MutableStateFlow(IndicatorTrendUiState())
    val state: StateFlow<IndicatorTrendUiState> = _state.asStateFlow()

    fun fetchIndicators() {
        val owner = authManager.captureAccountScope()
        if (owner == null) {
            publishOwnerChanged()
            return
        }
        viewModelScope.launch { fetchIndicators(owner) }
    }

    private suspend fun fetchIndicators(owner: AuthManager.AccountScopeSnapshot) {
        if (!authManager.isCurrent(owner)) {
            publishOwnerChanged()
            return
        }
        _state.update { it.copy(trendLoading = true) }
        runCatching {
            val all = repo.listIndicators()
            val watched = repo.watchedIndicators()
            val names = watched.map { it.indicator_name }
            val trends = if (names.isNotEmpty()) repo.trends(names) else emptyList()
            Triple(all, names, trends)
        }.onSuccess { (all, names, trends) ->
            if (!authManager.isCurrent(owner)) {
                publishOwnerChanged()
                return@onSuccess
            }
            _state.update {
                it.copy(
                    trendLoading = false,
                    allIndicators = all,
                    watchedNames = names,
                    trends = trends,
                )
            }
        }.onFailure { e ->
            if (!authManager.isCurrent(owner)) {
                publishOwnerChanged()
                return@onFailure
            }
            _state.update {
                it.copy(
                    trendLoading = false,
                    error = e.message ?: "关注指标暂时无法读取，请稍后重试。",
                )
            }
        }
    }

    fun applySelection(selected: Set<String>) {
        val owner = authManager.captureAccountScope()
        if (owner == null) {
            publishOwnerChanged()
            return
        }
        val current = _state.value.watchedNames.toSet()
        val toAdd = selected - current
        val toRemove = current - selected
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            if (!authManager.isCurrent(owner)) {
                publishOwnerChanged()
                return@launch
            }
            runCatching {
                toAdd.sorted().forEach { name ->
                    ensureCurrent(owner)
                    repo.watch(owner, name)
                    ensureCurrent(owner)
                }
                toRemove.sorted().forEach { name ->
                    ensureCurrent(owner)
                    repo.unwatch(owner, name)
                    ensureCurrent(owner)
                }
            }.onSuccess {
                if (!authManager.isCurrent(owner)) {
                    publishOwnerChanged()
                    return@onSuccess
                }
                fetchIndicators(owner)
            }.onFailure { error ->
                if (!authManager.isCurrent(owner) || error is IndicatorOwnerChangedException) {
                    publishOwnerChanged()
                    return@onFailure
                }
                _state.update {
                    it.copy(error = error.message ?: "关注指标更新失败，请稍后重试。")
                }
            }
        }
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

    private fun ensureCurrent(owner: AuthManager.AccountScopeSnapshot) {
        if (!authManager.isCurrent(owner)) throw IndicatorOwnerChangedException
    }

    private fun publishOwnerChanged() {
        _state.update {
            it.copy(
                trendLoading = false,
                error = "账号或健康数据所属用户已变化，请重新打开后再试。",
            )
        }
    }

    private data object IndicatorOwnerChangedException : IllegalStateException()
}
