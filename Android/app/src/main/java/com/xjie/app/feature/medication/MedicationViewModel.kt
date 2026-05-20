package com.xjie.app.feature.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.Medication
import com.xjie.app.core.model.MedicationBody
import com.xjie.app.core.model.MedicationRecognizeResult
import com.xjie.app.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MedicationUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val recognizing: Boolean = false,
    val items: List<Medication> = emptyList(),
    val recognized: MedicationRecognizeResult? = null,
    val recognizeError: String? = null,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class MedicationViewModel @Inject constructor(
    private val repo: MedicationRepository,
    private val scheduler: MedicationScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(MedicationUiState())
    val state: StateFlow<MedicationUiState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching { repo.list() }
            .onSuccess { list ->
                _state.update { it.copy(loading = false, items = list) }
                scheduler.rescheduleAll(list)
            }
            .onFailure { e ->
                _state.update {
                    it.copy(loading = false, error = (e as? ApiException)?.message ?: e.message)
                }
            }
    }

    fun save(body: MedicationBody, editing: Medication?, onDone: () -> Unit) =
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            runCatching {
                if (editing != null) repo.update(editing.id, body) else repo.create(body)
            }.onSuccess {
                _state.update { it.copy(saving = false, message = "已保存") }
                load()
                onDone()
            }.onFailure { e ->
                _state.update {
                    it.copy(saving = false, error = (e as? ApiException)?.message ?: e.message)
                }
            }
        }

    fun delete(med: Medication) = viewModelScope.launch {
        runCatching { repo.delete(med.id) }
            .onSuccess { load() }
            .onFailure { e ->
                _state.update { it.copy(error = (e as? ApiException)?.message ?: e.message) }
            }
    }

    fun recognize(rawText: String) = viewModelScope.launch {
        _state.update { it.copy(recognizing = true, recognized = null, recognizeError = null) }
        runCatching { repo.recognize(rawText) }
            .onSuccess { r -> _state.update { it.copy(recognizing = false, recognized = r) } }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        recognizing = false,
                        recognizeError = (e as? ApiException)?.message ?: e.message,
                    )
                }
            }
    }

    fun clearRecognized() = _state.update {
        it.copy(recognized = null, recognizeError = null)
    }
    fun clearError() = _state.update { it.copy(error = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun fireTestNotification() {
        scheduler.fireTestNotification()
        _state.update { it.copy(message = "已发送测试通知，请下拉通知中心查看。若没看到，请到系统设置打开 Xjie 的通知权限。") }
    }

    fun scheduleTestAlarm() {
        scheduler.scheduleTestAlarm(10)
        _state.update { it.copy(message = "已安排 10 秒后的测试闹钟，请保持手机点亮等待。") }
    }
}
