package com.xjie.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.Citation
import com.xjie.app.core.model.ChatConversation
import com.xjie.app.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessageItem(
    val id: Long,
    val role: String,           // user | assistant
    val content: String,
    val analysis: String? = null,
    val confidence: Double? = null,
    val followups: List<String>? = null,
    val citations: List<Citation> = emptyList(),
)

data class ChatUiState(
    val messages: List<ChatMessageItem> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val threadId: String? = null,
    val conversations: List<ChatConversation> = emptyList(),
    val hasMoreConversations: Boolean = true,
    val showHistory: Boolean = false,
    val isViewingHistory: Boolean = false,
    val error: String? = null,
    val thinkingHint: String = "",
)

private val THINKING_HINTS = listOf(
    "正在读取您的个人健康画像…",
    "正在检索临床文献证据库…",
    "正在结合多组学指标精准分析…",
    "正在进行倒推与证据质量评级…",
    "正在生成个性化健康建议…",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: ChatRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var savedMessages: List<ChatMessageItem> = emptyList()
    private var savedThreadId: String? = null
    private var nextId = 1L
    private val pageSize = 20
    private var thinkingJob: Job? = null

    fun setInput(v: String) = _state.update { it.copy(input = v) }
    fun toggleHistory() = _state.update { it.copy(showHistory = !it.showHistory) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun loadConversations() = viewModelScope.launch {
        runCatching { repo.listConversations(pageSize, 0) }
            .onSuccess { list ->
                _state.update {
                    it.copy(conversations = list, hasMoreConversations = list.size >= pageSize)
                }
            }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }

    fun loadMoreConversations() = viewModelScope.launch {
        val cur = _state.value
        if (!cur.hasMoreConversations) return@launch
        val more = runCatching { repo.listConversations(pageSize, cur.conversations.size) }
            .getOrDefault(emptyList())
        _state.update {
            it.copy(
                conversations = it.conversations + more,
                hasMoreConversations = more.size >= pageSize,
            )
        }
    }

    fun deleteConversation(id: String) = viewModelScope.launch {
        runCatching { repo.deleteConversation(id) }
            .onSuccess {
                val cur = _state.value
                val newList = cur.conversations.filterNot { it.id == id }
                val isCurrent = cur.threadId == id
                _state.update {
                    it.copy(
                        conversations = newList,
                        messages = if (isCurrent) emptyList() else it.messages,
                        threadId = if (isCurrent) null else it.threadId,
                        isViewingHistory = if (isCurrent) false else it.isViewingHistory,
                    )
                }
                if (isCurrent) {
                    savedMessages = emptyList()
                    savedThreadId = null
                }
            }
            .onFailure { e -> _state.update { it.copy(error = e.message ?: "删除失败") } }
    }

    fun loadConversation(id: String) = viewModelScope.launch {
        runCatching { repo.conversationMessages(id) }
            .onSuccess { msgs ->
                if (!_state.value.isViewingHistory) {
                    savedMessages = _state.value.messages
                    savedThreadId = _state.value.threadId
                }
                _state.update {
                    it.copy(
                        messages = msgs.map { m ->
                            ChatMessageItem(
                                id = nextId++,
                                role = m.role,
                                content = m.content,
                                analysis = m.analysis,
                                citations = m.citations,
                            )
                        },
                        threadId = id,
                        isViewingHistory = true,
                        showHistory = false,
                    )
                }
            }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }

    fun backToCurrentChat() {
        _state.update {
            it.copy(
                messages = savedMessages,
                threadId = savedThreadId,
                isViewingHistory = false,
            )
        }
        savedMessages = emptyList()
        savedThreadId = null
    }

    fun newChat() {
        savedMessages = emptyList()
        savedThreadId = null
        _state.update {
            it.copy(messages = emptyList(), threadId = null, isViewingHistory = false)
        }
    }

    fun send() = viewModelScope.launch {
        val cur = _state.value
        val msg = cur.input.trim()
        if (msg.isEmpty() || cur.sending) return@launch

        if (cur.isViewingHistory) {
            savedMessages = emptyList()
            savedThreadId = null
        }

        val userItem = ChatMessageItem(id = nextId++, role = "user", content = msg)
        _state.update {
            it.copy(
                messages = it.messages + userItem,
                input = "",
                sending = true,
                isViewingHistory = false,
                thinkingHint = THINKING_HINTS.first(),
            )
        }
        startThinkingTicker()

        try {
            val res = try {
                repo.send(msg, _state.value.threadId)
            } catch (e: ApiException.HttpError) {
                if (e.code == 403) {
                    runCatching { repo.enableAiChat() }
                    repo.send(msg, _state.value.threadId)
                } else throw e
            }

            val rawContent = res.summary ?: res.answer_markdown ?: "..."
            val content = cleanContent(rawContent)
            val assistantItem = ChatMessageItem(
                id = nextId++,
                role = "assistant",
                content = content,
                analysis = res.analysis,
                confidence = res.confidence,
                followups = res.followups,
                citations = res.citations.orEmpty(),
            )
            stopThinkingTicker()
            _state.update {
                it.copy(
                    messages = it.messages + assistantItem,
                    threadId = res.thread_id ?: it.threadId,
                    sending = false,
                    thinkingHint = "",
                )
            }
        } catch (e: Exception) {
            val errItem = ChatMessageItem(
                id = nextId++,
                role = "assistant",
                content = "请求失败: ${e.message ?: "未知错误"}",
            )
            stopThinkingTicker()
            _state.update {
                it.copy(
                    messages = it.messages + errItem,
                    sending = false,
                    error = e.message,
                    thinkingHint = "",
                )
            }
        }
    }

    private fun startThinkingTicker() {
        thinkingJob?.cancel()
        thinkingJob = viewModelScope.launch {
            var idx = 0
            while (true) {
                delay(3500)
                idx = (idx + 1) % THINKING_HINTS.size
                _state.update {
                    if (!it.sending) it
                    else it.copy(thinkingHint = THINKING_HINTS[idx])
                }
            }
        }
    }

    private fun stopThinkingTicker() {
        thinkingJob?.cancel()
        thinkingJob = null
    }

    fun useFollowup(q: String) {
        setInput(q)
    }

    private fun cleanContent(text: String): String {
        var s = text.trim()
        // Extract summary from raw JSON
        if (s.startsWith("{") && s.contains("\"summary\"")) {
            runCatching { extractSummary(s) }.getOrNull()?.let { return it }
        }
        if (s.startsWith("```")) {
            s = s.replace("```json", "").replace("```", "").trim()
            if (s.startsWith("{")) {
                runCatching { extractSummary(s) }.getOrNull()?.let { return it }
            }
        }
        return s
    }

    private fun extractSummary(jsonStr: String): String? {
        val el = kotlinx.serialization.json.Json.parseToJsonElement(jsonStr)
        val obj = (el as? kotlinx.serialization.json.JsonObject) ?: return null
        val v = obj["summary"] as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return v.contentOrNull
    }
}

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = runCatching { content }.getOrNull()
