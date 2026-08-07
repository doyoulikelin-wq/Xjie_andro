package com.xjie.app.feature.medicalassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.MedicalAssistantGenerationResult
import com.xjie.app.core.model.MedicalAssistantOverview
import com.xjie.app.core.model.MedicalAssistantOverviewPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MedicalAssistantScreenPhase {
    Loading,
    Empty,
    Error,
    Generating,
    Ready,
    NoUpdate,
    Processing,
}

data class MedicalAssistantUiState(
    val loading: Boolean = false,
    val generating: Boolean = false,
    val overview: MedicalAssistantOverview? = null,
    val lastGenerationResult: MedicalAssistantGenerationResult? = null,
    val error: String? = null,
    val notice: String? = null,
) {
    val phase: MedicalAssistantScreenPhase
        get() = when {
            loading && overview == null -> MedicalAssistantScreenPhase.Loading
            error != null -> MedicalAssistantScreenPhase.Error
            generating -> MedicalAssistantScreenPhase.Generating
            lastGenerationResult is MedicalAssistantGenerationResult.NoInformationUpdate ->
                MedicalAssistantScreenPhase.NoUpdate
            lastGenerationResult is MedicalAssistantGenerationResult.ReportProcessing ->
                MedicalAssistantScreenPhase.Processing
            overview?.hasSummary == true -> MedicalAssistantScreenPhase.Ready
            else -> MedicalAssistantScreenPhase.Empty
        }
}

/**
 * Account-bound dashboard state machine. Every network operation owns the exact account, selected
 * subject and monotonic auth generation captured before its first suspension.
 */
@HiltViewModel
class MedicalAssistantViewModel @Inject constructor(
    private val repository: MedicalAssistantRepository,
    private val authManager: AuthManager,
) : ViewModel() {
    private val _state = MutableStateFlow(MedicalAssistantUiState())
    val state: StateFlow<MedicalAssistantUiState> = _state.asStateFlow()

    private var activeOwner: AuthManager.AccountScopeSnapshot? = null
    private var requestSequence = 0L
    private var observedAuthGeneration = authManager.generation

    init {
        viewModelScope.launch {
            authManager.state.collect { authState ->
                if (authState.generation == observedAuthGeneration) return@collect
                observedAuthGeneration = authState.generation
                activeOwner = null
                requestSequence += 1L
                _state.value = MedicalAssistantUiState(
                    error = if (authState.isLoggedIn) {
                        "登录账号或健康主体已变化，请重新读取病人概况。"
                    } else {
                        "登录已失效，请重新登录后再使用就医助手。"
                    },
                )
            }
        }
    }

    fun load() {
        val owner = captureOwner() ?: return
        val requestId = ++requestSequence
        activeOwner = owner
        _state.update {
            it.copy(
                loading = true,
                generating = false,
                error = null,
                notice = null,
                lastGenerationResult = null,
            )
        }

        viewModelScope.launch {
            runCatching { repository.fetchOverview(owner) }
                .onSuccess { response ->
                    if (!canCommit(owner, requestId)) return@onSuccess
                    validationError(response)?.let { message ->
                        _state.update { it.copy(loading = false, error = message) }
                        return@onSuccess
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            overview = response,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (!canCommit(owner, requestId)) return@onFailure
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "无法读取最新病人概况",
                        )
                    }
                }
        }
    }

    fun generate() {
        if (_state.value.generating) return
        val owner = captureOwner() ?: return
        if (activeOwner != owner) {
            _state.update {
                it.copy(error = "登录账号或健康主体已变化，请先重新读取病人概况。")
            }
            return
        }

        val requestId = ++requestSequence
        _state.update {
            it.copy(
                generating = true,
                error = null,
                notice = null,
                lastGenerationResult = null,
            )
        }

        viewModelScope.launch {
            runCatching { repository.generateOverview(owner) }
                .onSuccess { response ->
                    if (!canCommit(owner, requestId)) return@onSuccess
                    validationError(response)?.let { message ->
                        _state.update {
                            it.copy(
                                generating = false,
                                error = message,
                                lastGenerationResult = response.generationResult,
                            )
                        }
                        return@onSuccess
                    }

                    val result = response.generationResult
                    _state.update {
                        it.copy(
                            generating = false,
                            overview = response,
                            lastGenerationResult = result,
                            notice = noticeFor(result),
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (!canCommit(owner, requestId)) return@onFailure
                    _state.update {
                        it.copy(
                            generating = false,
                            error = error.message ?: "病人概况生成失败",
                        )
                    }
                }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    private fun captureOwner(): AuthManager.AccountScopeSnapshot? =
        authManager.captureAccountScope().also { owner ->
            if (owner == null) {
                _state.update {
                    it.copy(error = "无法确认当前登录账号，请重新登录后再试。")
                }
            }
        }

    private fun canCommit(
        owner: AuthManager.AccountScopeSnapshot,
        requestId: Long,
    ): Boolean =
        requestSequence == requestId &&
            activeOwner == owner &&
            authManager.isCurrent(owner)

    private fun validationError(response: MedicalAssistantOverview): String? {
        if (response.subject_user_id <= 0L) return "服务端未确认当前健康主体，已停止展示概况。"
        if (response.report_count_last_year < 0) return "服务端返回的资料数量无效，请稍后重试。"
        if (!MedicalAssistantOverviewPolicy.accepts(response.generationResult)) {
            return "服务端返回了无法识别的生成状态，请稍后重试。"
        }
        return null
    }

    private fun noticeFor(result: MedicalAssistantGenerationResult): String? = when (result) {
        MedicalAssistantGenerationResult.Generated -> "病人概况已更新"
        MedicalAssistantGenerationResult.NoInformationUpdate -> "无信息更新"
        MedicalAssistantGenerationResult.NoReports -> "还没有可用于生成概况的报告"
        MedicalAssistantGenerationResult.ReportProcessing -> "最新报告尚未完成确认或入库"
        MedicalAssistantGenerationResult.Loaded -> null
        is MedicalAssistantGenerationResult.Unknown -> null
    }
}
