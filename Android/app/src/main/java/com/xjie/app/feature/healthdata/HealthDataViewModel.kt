package com.xjie.app.feature.healthdata

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.HealthDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthDataUiState(
    val loading: Boolean = false,
    val summary: String = "",
    val summaryUpdatedAt: String = "",
    val generating: Boolean = false,
    val progress: Float = 0f,
    val stage: String = "",
    val recordCount: Int = 0,
    val examCount: Int = 0,
    val uploading: Boolean = false,
    val uploadStage: String = "",
    val uploadDocType: String = "record",
    /**
     * 上传任务已交后台处理，可关闭横幅但任务仍在进行。
     * 用于「AI 正在后台识别…」提示条。
     */
    val backgroundTaskHint: String? = null,
    val toast: String? = null,
    val error: String? = null,
)

@HiltViewModel
class HealthDataViewModel @Inject constructor(
    private val repo: HealthDataRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HealthDataUiState())
    val state: StateFlow<HealthDataUiState> = _state.asStateFlow()

    fun fetchAll() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        val s = repo.summary()
        val records = runCatching { repo.documents("record") }.getOrDefault(emptyList())
        val exams = runCatching { repo.documents("exam") }.getOrDefault(emptyList())
        _state.update {
            it.copy(
                loading = false,
                summary = s?.summary_text ?: "",
                summaryUpdatedAt = s?.updated_at ?: "",
                recordCount = records.size,
                examCount = exams.size,
            )
        }
    }

    fun setUploadDocType(t: String) = _state.update { it.copy(uploadDocType = t) }

    fun uploadFile(uri: Uri, filename: String) = viewModelScope.launch {
        _state.update {
            it.copy(
                uploading = true,
                uploadStage = "正在上传文件…",
                backgroundTaskHint = null,
            )
        }
        runCatching { repo.uploadDocument(uri, filename, _state.value.uploadDocType) }
            .onSuccess { doc ->
                // 上传完成后：立即结束阻塞性 UI，给出「后台识别中」提示，用户可继续操作
                if (doc.extraction_status == "pending") {
                    _state.update {
                        it.copy(
                            uploading = false,
                            uploadStage = "",
                            toast = "上传成功，AI 正在后台识别",
                            backgroundTaskHint = "AI 正在后台识别文件内容，您可以离开此页继续使用。识别完成后会自动出现在「关注指标趋势」中。",
                        )
                    }
                    // 异步轮询：不阻塞 UI，仅用于完成后刷新计数 & 自动消除横幅
                    val status = pollDoc(doc.id)
                    if (status == "failed") {
                        _state.update {
                            it.copy(
                                error = "AI 无法识别该文件，请重新拍照或换张更清晰的图片。",
                                backgroundTaskHint = null,
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                backgroundTaskHint = null,
                                toast = "AI 识别完成",
                            )
                        }
                    }
                    fetchAll()
                } else {
                    _state.update {
                        it.copy(
                            uploading = false,
                            uploadStage = "",
                            toast = "上传成功",
                        )
                    }
                    fetchAll()
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        uploading = false,
                        uploadStage = "",
                        backgroundTaskHint = null,
                        error = e.message ?: "上传失败",
                    )
                }
            }
    }

    /** 用户手动关闭后台任务提示横幅（任务在后端继续）。 */
    fun dismissBackgroundHint() = _state.update { it.copy(backgroundTaskHint = null) }

    private suspend fun pollDoc(id: String): String {
        repeat(45) {
            delay(2000)
            val d = runCatching { repo.document(id) }.getOrNull() ?: return@repeat
            if (d.extraction_status != "pending") return d.extraction_status ?: "done"
        }
        return "failed"
    }

    fun generateSummary() = viewModelScope.launch {
        if (_state.value.generating) return@launch
        _state.update {
            it.copy(
                generating = true,
                progress = 0f,
                stage = "提交任务...",
                toast = "AI 报告生成已开始，您可以继续使用其他功能",
            )
        }
        runCatching {
            val task = repo.startSummaryTask()
            pollTask(task.task_id)
        }.onFailure { e -> _state.update { it.copy(generating = false, error = e.message) } }
    }

    private suspend fun pollTask(taskId: String) {
        while (true) {
            delay(3000)
            val s = runCatching { repo.taskStatus(taskId) }.getOrNull() ?: continue
            val stage = when (s.stage) {
                "l1" -> "分析第 ${s.stage_current ?: 0}/${s.stage_total ?: 0} 次检查..."
                "l2" -> "汇总第 ${s.stage_current ?: 0}/${s.stage_total ?: 0} 年趋势..."
                "l3" -> "生成最终报告..."
                else -> "准备中..."
            }
            _state.update {
                it.copy(progress = (s.progress_pct ?: 0.0).toFloat(), stage = stage)
            }
            if (s.status == "done") {
                val r = repo.summary()
                _state.update {
                    it.copy(
                        generating = false, progress = 1f,
                        summary = r?.summary_text ?: "",
                        summaryUpdatedAt = r?.updated_at ?: "",
                    )
                }
                return
            }
            if (s.status == "failed") {
                _state.update {
                    it.copy(
                        generating = false,
                        error = "生成失败: ${s.error_message ?: "未知错误"}",
                    )
                }
                return
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearToast() = _state.update { it.copy(toast = null) }
}

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val repo: HealthDataRepository,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = false,
        val doc: HealthDocument? = null,
        val error: String? = null,
    )
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun fetch(id: String) = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching { repo.document(id) }
            .onSuccess { d -> _state.update { it.copy(loading = false, doc = d) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }
}

@HiltViewModel
class DocumentListViewModel @Inject constructor(
    private val repo: HealthDataRepository,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = false,
        val items: List<HealthDocument> = emptyList(),
        val uploading: Boolean = false,
        val error: String? = null,
        val toast: String? = null,
    )
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun fetch(docType: String) = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching { repo.documents(docType) }
            .onSuccess { items -> _state.update { it.copy(loading = false, items = items) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }

    fun upload(docType: String, uri: Uri, filename: String) = viewModelScope.launch {
        _state.update { it.copy(uploading = true) }
        runCatching { repo.uploadDocument(uri, filename, docType) }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
        _state.update { it.copy(uploading = false, toast = "上传成功") }
        fetch(docType)
    }

    fun delete(id: String, docType: String) = viewModelScope.launch {
        runCatching { repo.deleteDocument(id) }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
        fetch(docType)
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearToast() = _state.update { it.copy(toast = null) }
}
