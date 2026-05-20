package com.xjie.app.feature.elderly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.ElderlyCheckin
import com.xjie.app.core.model.ElderlyCheckinBody
import com.xjie.app.core.model.ElderlyTodayStatus
import com.xjie.app.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ElderlyUiState(
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val status: ElderlyTodayStatus? = null,
    val history: List<ElderlyCheckin> = emptyList(),
    val showSheet: Boolean = false,
    val sheetSource: String = "auto_prompt",
    val autoPromptShown: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ElderlyViewModel @Inject constructor(
    private val repo: ElderlyRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ElderlyUiState())
    val state: StateFlow<ElderlyUiState> = _state.asStateFlow()

    val isEnabled: Boolean get() = _state.value.status?.enabled == true

    fun loadStatus(autoPrompt: Boolean = true) = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching { repo.today() }
            .onSuccess { s ->
                _state.update {
                    val shouldShow = autoPrompt && s.enabled && s.should_prompt && !it.autoPromptShown
                    it.copy(
                        loading = false,
                        status = s,
                        showSheet = shouldShow || it.showSheet,
                        sheetSource = if (shouldShow) "auto_prompt" else it.sheetSource,
                        autoPromptShown = it.autoPromptShown || shouldShow,
                    )
                }
            }
            .onFailure { e ->
                _state.update { it.copy(loading = false, error = (e as? ApiException)?.message ?: e.message) }
            }
    }

    fun loadHistory(days: Int = 30, limit: Int = 100) = viewModelScope.launch {
        runCatching { repo.list(days, limit) }
            .onSuccess { r -> _state.update { it.copy(history = r.items) } }
            .onFailure { e -> _state.update { it.copy(error = (e as? ApiException)?.message ?: e.message) } }
    }

    fun openSheet(source: String = "manual") =
        _state.update { it.copy(showSheet = true, sheetSource = source) }

    fun closeSheet() = _state.update { it.copy(showSheet = false) }

    fun submit(
        activity: String?,
        bodyFeeling: String?,
        mood: String?,
        note: String?,
        source: String = "manual",
        onDone: (() -> Unit)? = null,
    ) = viewModelScope.launch {
        _state.update { it.copy(submitting = true) }
        runCatching {
            repo.create(
                ElderlyCheckinBody(
                    activity = activity?.takeIf { it.isNotBlank() },
                    body_feeling = bodyFeeling,
                    mood = mood,
                    note = note?.takeIf { it.isNotBlank() },
                    source = source,
                )
            )
        }.onSuccess {
            _state.update { it.copy(submitting = false, showSheet = false) }
            loadStatus(autoPrompt = false)
            loadHistory()
            onDone?.invoke()
        }.onFailure { e ->
            _state.update {
                it.copy(submitting = false, error = (e as? ApiException)?.message ?: e.message)
            }
        }
    }

    fun delete(id: Long) = viewModelScope.launch {
        runCatching { repo.delete(id) }
            .onSuccess { loadHistory(); loadStatus(autoPrompt = false) }
            .onFailure { e -> _state.update { it.copy(error = (e as? ApiException)?.message ?: e.message) } }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
