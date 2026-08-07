package com.xjie.app.feature.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WeightScreenPhase {
    Loading,
    Empty,
    Error,
    Ready,
}

data class WeightUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val dashboard: WeightDashboardPresentation? = null,
    val loadError: String? = null,
    val operationError: String? = null,
    val ownerGeneration: Long? = null,
    val saveRevision: Long = 0L,
) {
    val phase: WeightScreenPhase
        get() = when {
            dashboard == null && isLoading -> WeightScreenPhase.Loading
            dashboard == null && loadError != null -> WeightScreenPhase.Error
            dashboard?.hasWeight == true -> WeightScreenPhase.Ready
            else -> WeightScreenPhase.Empty
        }
}

/** Account-generation-bound owner for the dedicated Weight route. */
@HiltViewModel
class WeightViewModel @Inject constructor(
    private val repository: WeightRepository,
    private val authManager: AuthManager,
) : ViewModel() {
    private val clock: Clock = Clock.systemUTC()
    private val _state = MutableStateFlow(WeightUiState())
    val state: StateFlow<WeightUiState> = _state.asStateFlow()

    private var activeOwner: AuthManager.AccountScopeSnapshot? = null
    private var activeRequest: WeightRequestToken? = null
    private var requestSequence = 0L
    private var observedAuthGeneration = authManager.generation

    init {
        viewModelScope.launch {
            authManager.state.collect { authState ->
                if (authState.generation == observedAuthGeneration) return@collect
                observedAuthGeneration = authState.generation
                activeOwner = null
                activeRequest = null
                requestSequence += 1L
                _state.value = WeightUiState(
                    isLoading = false,
                    loadError = if (authState.isLoggedIn) {
                        "登录账号或健康主体已变化，请重新读取体重记录。"
                    } else {
                        "登录已失效，请重新登录后再读取体重记录。"
                    },
                    ownerGeneration = authState.generation,
                )
            }
        }
    }

    fun load() {
        if (_state.value.isSaving) return
        val owner = captureOwner() ?: return
        val preserveDashboard = activeOwner == owner
        activeOwner = owner
        val token = nextToken(owner)
        _state.update { current ->
            current.copy(
                isLoading = true,
                dashboard = current.dashboard.takeIf { preserveDashboard },
                loadError = null,
                operationError = null,
                ownerGeneration = owner.generation,
            )
        }

        viewModelScope.launch {
            runCatching { repository.load(owner) }
                .onSuccess { snapshot ->
                    if (!canCommit(token)) return@onSuccess
                    _state.update {
                        it.copy(
                            isLoading = false,
                            dashboard = WeightDashboardPolicy.presentation(
                                snapshot,
                                LocalDate.now(clock),
                            ),
                            loadError = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (!canCommit(token)) return@onFailure
                    _state.update {
                        it.copy(
                            isLoading = false,
                            loadError = error.message ?: "无法读取体重记录，请稍后重试。",
                        )
                    }
                }
        }
    }

    fun recordWeight(valueKg: Double) {
        if (!WeightDashboardPolicy.validWeight(valueKg)) {
            _state.update { it.copy(operationError = WeightDashboardPolicy.WEIGHT_ERROR) }
            return
        }
        performSave { owner -> repository.recordWeight(owner, valueKg, clock.instant()) }
    }

    fun recordHeight(valueCm: Int) {
        if (valueCm !in WeightDashboardPolicy.validHeightRange) {
            _state.update { it.copy(operationError = WeightDashboardPolicy.HEIGHT_ERROR) }
            return
        }
        performSave { owner -> repository.recordHeight(owner, valueCm, clock.instant()) }
    }

    fun clearOperationError() = _state.update { it.copy(operationError = null) }

    private fun performSave(
        operation: suspend (AuthManager.AccountScopeSnapshot) -> WeightNetworkSnapshot,
    ) {
        if (_state.value.isSaving || _state.value.isLoading) return
        val owner = captureOwner() ?: return
        if (owner != activeOwner) {
            _state.update {
                it.copy(operationError = "账号或健康主体已变化，请先重新读取体重记录。")
            }
            return
        }
        val token = nextToken(owner)
        _state.update { it.copy(isSaving = true, operationError = null) }

        viewModelScope.launch {
            runCatching { operation(owner) }
                .onSuccess { snapshot ->
                    if (!canCommit(token)) return@onSuccess
                    _state.update {
                        it.copy(
                            isSaving = false,
                            dashboard = WeightDashboardPolicy.presentation(
                                snapshot,
                                LocalDate.now(clock),
                            ),
                            loadError = null,
                            operationError = null,
                            saveRevision = it.saveRevision + 1L,
                        )
                    }
                }
                .onFailure { error ->
                    if (!canCommit(token)) return@onFailure
                    _state.update {
                        it.copy(
                            isSaving = false,
                            operationError = error.message ?: "保存失败，请检查网络后重试。",
                        )
                    }
                }
        }
    }

    private fun captureOwner(): AuthManager.AccountScopeSnapshot? =
        authManager.captureAccountScope().also { owner ->
            if (owner == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isSaving = false,
                        dashboard = null,
                        loadError = "无法确认当前登录账号，请重新登录后再试。",
                    )
                }
            }
        }

    private fun nextToken(owner: AuthManager.AccountScopeSnapshot): WeightRequestToken {
        requestSequence += 1L
        return WeightRequestToken(owner, requestSequence).also { activeRequest = it }
    }

    private fun canCommit(token: WeightRequestToken): Boolean {
        val currentOwner = authManager.captureAccountScope()
        return token.accepts(activeRequest, currentOwner) && authManager.isCurrent(token.owner)
    }
}
