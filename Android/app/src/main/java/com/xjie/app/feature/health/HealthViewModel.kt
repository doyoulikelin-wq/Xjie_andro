package com.xjie.app.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.HealthReports
import com.xjie.app.core.model.SummaryTaskResponse
import com.xjie.app.core.model.TodayBriefing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthUiState(
    val loading: Boolean = false,
    val briefing: TodayBriefing? = null,
    val reports: HealthReports? = null,
    val aiSummary: String = "",
    val summaryLoading: Boolean = false,
    val summaryProgress: Float = 0f,
    val summaryStage: String = "",
    val error: String? = null,
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val repo: HealthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HealthUiState())
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    fun fetchData() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        val b = repo.todayBriefing()
        val r = repo.healthReports()
        val s = repo.summary()
        _state.update {
            it.copy(
                loading = false,
                briefing = b,
                reports = r,
                aiSummary = s?.summary_text ?: it.aiSummary,
            )
        }
    }

    fun generateSummary() = viewModelScope.launch {
        _state.update { it.copy(summaryLoading = true, summaryProgress = 0f, summaryStage = "提交任务...") }
        runCatching {
            val task: SummaryTaskResponse = repo.startSummaryTask()
            pollTask(task.task_id)
        }.onFailure { e ->
            _state.update {
                it.copy(summaryLoading = false, error = e.message, aiSummary = "获取失败，请重试")
            }
        }
    }

    private suspend fun pollTask(taskId: String) {
        while (true) {
            delay(3000)
            val status = runCatching { repo.taskStatus(taskId) }.getOrNull() ?: continue
            val stage = when (status.stage) {
                "l1" -> "分析第 ${status.stage_current ?: 0}/${status.stage_total ?: 0} 次检查..."
                "l2" -> "汇总第 ${status.stage_current ?: 0}/${status.stage_total ?: 0} 年趋势..."
                "l3" -> "生成最终报告..."
                else -> "准备中..."
            }
            _state.update {
                it.copy(
                    summaryProgress = (status.progress_pct ?: 0.0).toFloat(),
                    summaryStage = stage,
                )
            }
            when (status.status) {
                "done" -> {
                    val result = repo.summary()
                    _state.update {
                        it.copy(
                            summaryLoading = false,
                            summaryProgress = 1f,
                            aiSummary = result?.summary_text ?: "暂无摘要",
                        )
                    }
                    return
                }
                "failed" -> {
                    _state.update {
                        it.copy(
                            summaryLoading = false,
                            aiSummary = "生成失败: ${status.error_message ?: "未知错误"}",
                        )
                    }
                    return
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
