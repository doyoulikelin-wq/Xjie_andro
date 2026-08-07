package com.xjie.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.Citation
import com.xjie.app.core.model.ChatConversation
import com.xjie.app.core.model.ChatInteractionRoute
import com.xjie.app.core.model.ChatStreamEvent
import com.xjie.app.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class ChatDeliveryStatus(val label: String) {
    Sending("发送中"),
    Sent("已发送"),
    Failed("发送失败，可重试"),
}

data class ChatMessageItem(
    val id: String,
    val role: String,           // user | assistant
    val content: String,
    val analysis: String? = null,
    val confidence: Double? = null,
    val followups: List<String>? = null,
    val citations: List<Citation> = emptyList(),
    val status: ChatDeliveryStatus? = null,
    val retryText: String? = null,
)

data class ChatUiState(
    val messages: List<ChatMessageItem> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val threadId: String? = null,
    val conversations: List<ChatConversation> = emptyList(),
    val hasMoreConversations: Boolean = true,
    val showHistory: Boolean = false,
    val showAiConsentPrompt: Boolean = false,
    val isViewingHistory: Boolean = false,
    val error: String? = null,
    val thinkingHint: String = "",
    val activeRoute: ChatInteractionRoute? = null,
    val planSavingMessageId: String? = null,
    val savedPlanMessageIds: Set<String> = emptySet(),
)

private data class PendingAiConsentRetry(
    val text: String,
    val clientMessageId: String,
    val owner: AuthManager.AccountScopeSnapshot,
)

private val DEFAULT_THINKING_HINTS = listOf(
    "正在确认问题与健康主体…",
    "正在核对本次可用的数据范围…",
    "仍在等待回答完成…",
    "这次分析需要更长时间，请稍候…",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: ChatRepository,
    private val authManager: AuthManager,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var savedMessages: List<ChatMessageItem> = emptyList()
    private var savedThreadId: String? = null
    private val pageSize = 20
    private var thinkingJob: Job? = null
    private var sendJob: Job? = null
    private val requestGate = ChatRequestGenerationGate()
    private var activeThinkingHints = DEFAULT_THINKING_HINTS
    private var pendingAiConsentRetry: PendingAiConsentRetry? = null
    private var observedAuthGeneration = authManager.generation
    private var nextConversationLoadGeneration = 0L
    private var activeConversationLoadGeneration: Long? = null

    init {
        viewModelScope.launch {
            authManager.state.collect { authState ->
                if (authState.generation == observedAuthGeneration) return@collect
                observedAuthGeneration = authState.generation
                invalidateConversationLoads()
                requestGate.invalidate()
                sendJob?.cancel()
                sendJob = null
                stopThinkingTicker()
                savedMessages = emptyList()
                savedThreadId = null
                pendingAiConsentRetry = null
                _state.value = ChatUiState()
            }
        }
    }

    fun setInput(v: String) = _state.update { it.copy(input = v) }
    fun toggleHistory() = _state.update { it.copy(showHistory = !it.showHistory) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun loadConversations() = viewModelScope.launch {
        val owner = captureOwner() ?: return@launch
        runCatching { repo.listConversations(pageSize, 0, owner) }
            .onSuccess { list ->
                if (!authManager.isCurrent(owner)) return@onSuccess
                _state.update {
                    it.copy(conversations = list, hasMoreConversations = list.size >= pageSize)
                }
            }
            .onFailure { e ->
                if (authManager.isCurrent(owner)) _state.update { it.copy(error = e.message) }
            }
    }

    fun loadMoreConversations() = viewModelScope.launch {
        val cur = _state.value
        if (!cur.hasMoreConversations) return@launch
        val owner = captureOwner() ?: return@launch
        val more = runCatching { repo.listConversations(pageSize, cur.conversations.size, owner) }
            .getOrDefault(emptyList())
        if (!authManager.isCurrent(owner)) return@launch
        _state.update {
            it.copy(
                conversations = it.conversations + more,
                hasMoreConversations = more.size >= pageSize,
            )
        }
    }

    fun deleteConversation(id: String) = viewModelScope.launch {
        val owner = captureOwner() ?: return@launch
        runCatching { repo.deleteConversation(id, owner) }
            .onSuccess {
                if (!authManager.isCurrent(owner)) return@onSuccess
                val cur = _state.value
                val newList = cur.conversations.filterNot { it.id == id }
                val isCurrent = cur.threadId == id
                invalidateConversationLoads()
                if (isCurrent) invalidateActiveConversationOperation()
                _state.update {
                    it.copy(
                        conversations = newList,
                        messages = if (isCurrent) emptyList() else it.messages,
                        threadId = if (isCurrent) null else it.threadId,
                        isViewingHistory = if (isCurrent) false else it.isViewingHistory,
                        sending = if (isCurrent) false else it.sending,
                        thinkingHint = if (isCurrent) "" else it.thinkingHint,
                        activeRoute = if (isCurrent) null else it.activeRoute,
                        showAiConsentPrompt = if (isCurrent) false else it.showAiConsentPrompt,
                    )
                }
                if (isCurrent) {
                    savedMessages = emptyList()
                    savedThreadId = null
                }
            }
            .onFailure { e ->
                if (authManager.isCurrent(owner)) {
                    _state.update { it.copy(error = e.message ?: "删除失败") }
                }
            }
    }

    fun loadConversation(id: String) = viewModelScope.launch {
        if (_state.value.sending) {
            _state.update { it.copy(error = "请等待当前回答完成后再切换历史对话。") }
            return@launch
        }
        val owner = captureOwner() ?: return@launch
        val loadGeneration = beginConversationLoad()
        runCatching { repo.conversationMessages(id, owner) }
            .onSuccess { msgs ->
                if (!authManager.isCurrent(owner)) return@onSuccess
                if (!acceptsConversationLoad(loadGeneration) || _state.value.sending) {
                    return@onSuccess
                }
                if (!_state.value.isViewingHistory) {
                    savedMessages = _state.value.messages
                    savedThreadId = _state.value.threadId
                }
                _state.update {
                    it.copy(
                        messages = dedupeMessages(msgs.map { m ->
                            val content = ChatPresentationPolicy.cleanContent(m.content)
                            val analysis = ChatPresentationPolicy.cleanAnalysis(m.analysis.orEmpty())
                                .takeIf { it.isNotBlank() && it.replace(Regex("\\s+"), "") != content.replace(Regex("\\s+"), "") }
                            ChatMessageItem(
                                id = "server-${m.id}",
                                role = m.role,
                                content = content,
                                analysis = analysis,
                                citations = m.citations,
                            )
                        }),
                        threadId = id,
                        isViewingHistory = true,
                        showHistory = false,
                    )
                }
                completeConversationLoad(loadGeneration)
            }
            .onFailure { e ->
                if (authManager.isCurrent(owner) && acceptsConversationLoad(loadGeneration)) {
                    completeConversationLoad(loadGeneration)
                    _state.update { it.copy(error = e.message) }
                }
            }
    }

    fun backToCurrentChat() {
        if (_state.value.sending) {
            _state.update { it.copy(error = "请等待当前回答完成后再切换对话。") }
            return
        }
        invalidateConversationLoads()
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
        invalidateActiveConversationOperation()
        savedMessages = emptyList()
        savedThreadId = null
        stopThinkingTicker()
        _state.update {
            it.copy(
                messages = emptyList(),
                threadId = null,
                isViewingHistory = false,
                sending = false,
                thinkingHint = "",
                activeRoute = null,
                showAiConsentPrompt = false,
            )
        }
        pendingAiConsentRetry = null
    }

    fun send() {
        val cur = _state.value
        val msg = cur.input.trim()
        if (msg.isEmpty() || cur.sending) return
        beginMessage(msg, UUID.randomUUID().toString(), existingUserMessageId = null)
    }

    fun startPlanConversation(prompt: String) {
        if (prompt.isBlank() || _state.value.sending) return
        newChat()
        beginMessage(prompt.trim(), UUID.randomUUID().toString(), existingUserMessageId = null)
    }

    fun sendText(prompt: String) {
        val msg = prompt.trim()
        if (msg.isEmpty() || _state.value.sending) return
        beginMessage(msg, UUID.randomUUID().toString(), existingUserMessageId = null)
    }

    fun retry(id: String) {
        val cur = _state.value
        val item = cur.messages.firstOrNull { it.id == id && it.status == ChatDeliveryStatus.Failed }
            ?: return
        if (cur.sending) return
        beginMessage(item.retryText ?: item.content, id, existingUserMessageId = id)
    }

    private fun beginMessage(
        msg: String,
        clientMessageId: String,
        existingUserMessageId: String?,
        reservedSendSlot: Boolean = false,
    ) {
        val owner = captureOwner() ?: return
        val cur = _state.value
        if ((cur.sending || cur.showAiConsentPrompt) && !reservedSendSlot) return
        invalidateConversationLoads()
        if (cur.isViewingHistory) {
            savedMessages = emptyList()
            savedThreadId = null
        }

        val userItem = ChatMessageItem(
            id = clientMessageId,
            role = "user",
            content = msg,
            status = ChatDeliveryStatus.Sending,
            retryText = msg,
        )
        val request = requestGate.begin(owner, cur.threadId, clientMessageId)
        _state.update {
            val updatedMessages = if (existingUserMessageId != null) {
                it.messages.map { item ->
                    if (item.id == existingUserMessageId) {
                        item.copy(status = ChatDeliveryStatus.Sending, retryText = item.retryText ?: item.content)
                    } else item
                }
            } else {
                it.messages + userItem
            }
            it.copy(
                messages = updatedMessages,
                input = "",
                sending = true,
                isViewingHistory = false,
                error = null,
                activeRoute = null,
                thinkingHint = DEFAULT_THINKING_HINTS.first(),
            )
        }
        activeThinkingHints = DEFAULT_THINKING_HINTS
        startThinkingTicker()
        sendJob = viewModelScope.launch {
            performMessage(msg, request)
        }
    }

    private suspend fun performMessage(
        msg: String,
        request: ChatRequestIdentity,
    ) {
        var streamedRoute: ChatInteractionRoute? = null
        try {
            val res = repo.sendStreaming(
                message = msg,
                threadId = request.threadId,
                clientMessageId = request.clientMessageId,
                expectedOwner = request.owner,
            ) { event ->
                if (!accepts(request)) return@sendStreaming
                when (event) {
                    is ChatStreamEvent.Route -> {
                        streamedRoute = event.route
                        activeThinkingHints = event.route.progress_steps
                            .filter(String::isNotBlank)
                            .ifEmpty { DEFAULT_THINKING_HINTS }
                        _state.update {
                            it.copy(
                                activeRoute = event.route,
                                thinkingHint = activeThinkingHints.first(),
                            )
                        }
                    }
                    is ChatStreamEvent.Progress -> _state.update {
                        it.copy(thinkingHint = event.step)
                    }
                    is ChatStreamEvent.Token -> Unit // Final structured body is authoritative.
                    is ChatStreamEvent.Done -> Unit
                }
            }
            if (!accepts(request)) {
                return
            }

            val route = res.interaction_route ?: streamedRoute
            if (res.response_state.equals("processing", ignoreCase = true)) {
                stopThinkingTicker()
                requestGate.complete(request)
                sendJob = null
                _state.update {
                    it.copy(
                        messages = markUserMessage(
                            it.messages,
                            request.clientMessageId,
                            ChatDeliveryStatus.Sent,
                        ),
                        threadId = res.thread_id ?: it.threadId,
                        sending = false,
                        thinkingHint = "",
                        activeRoute = route,
                        error = res.summary ?: "这条消息仍在处理中，请稍后从历史对话查看。",
                    )
                }
                return
            }

            val content = ChatPresentationPolicy.selectContent(res, route)
            val assistantItem = ChatMessageItem(
                id = res.message_id?.trim()?.takeIf(String::isNotEmpty)?.let { "server-$it" }
                    ?: "assistant-${request.clientMessageId}",
                role = "assistant",
                content = content,
                analysis = ChatPresentationPolicy.distinctAnalysis(res, content),
                confidence = res.confidence,
                followups = res.followups,
                citations = res.citations.orEmpty(),
            )
            stopThinkingTicker()
            requestGate.complete(request)
            sendJob = null
            _state.update {
                it.copy(
                    messages = dedupeMessages(
                        markUserMessage(
                            it.messages,
                            request.clientMessageId,
                            ChatDeliveryStatus.Sent,
                        ) + assistantItem
                    ),
                    threadId = res.thread_id ?: it.threadId,
                    sending = false,
                    thinkingHint = "",
                    activeRoute = route,
                )
            }
        } catch (e: Exception) {
            if (!accepts(request)) {
                return
            }
            if (e is ApiException.HttpError && e.code == 403) {
                pendingAiConsentRetry = PendingAiConsentRetry(
                    msg,
                    request.clientMessageId,
                    request.owner,
                )
                stopThinkingTicker()
                requestGate.complete(request)
                sendJob = null
                _state.update {
                    it.copy(
                        messages = markUserMessage(
                            it.messages,
                            request.clientMessageId,
                            ChatDeliveryStatus.Failed,
                        ),
                        sending = false,
                        showAiConsentPrompt = true,
                        error = null,
                        thinkingHint = "",
                    )
                }
                return
            }
            stopThinkingTicker()
            requestGate.complete(request)
            sendJob = null
            _state.update {
                it.copy(
                    messages = markUserMessage(
                        it.messages,
                        request.clientMessageId,
                        ChatDeliveryStatus.Failed,
                    ),
                    sending = false,
                    error = e.message?.takeIf(String::isNotBlank)
                        ?: "这次回答没有完成，请检查网络后重试。",
                    thinkingHint = "",
                )
            }
        }
    }

    /** The only chat-local path that may grant consent; it is called by an explicit UI action. */
    fun grantAiConsentAndRetry() {
        val pending = pendingAiConsentRetry ?: return
        if (_state.value.sending) return
        if (!authManager.isCurrent(pending.owner)) {
            pendingAiConsentRetry = null
            _state.update { it.copy(showAiConsentPrompt = false) }
            return
        }
        _state.update {
            it.copy(
                showAiConsentPrompt = false,
                error = null,
                sending = true,
                thinkingHint = "正在保存 AI 健康问答授权…",
            )
        }
        viewModelScope.launch {
            try {
                val consent = repo.enableAiChat(pending.owner)
                check(consent.allow_ai_chat) { "服务器未确认 AI 健康问答授权" }
                if (
                    !authManager.isCurrent(pending.owner) ||
                    pendingAiConsentRetry != pending
                ) {
                    return@launch
                }
                pendingAiConsentRetry = null
                beginMessage(
                    msg = pending.text,
                    clientMessageId = pending.clientMessageId,
                    existingUserMessageId = pending.clientMessageId,
                    reservedSendSlot = true,
                )
            } catch (_: Exception) {
                if (
                    authManager.isCurrent(pending.owner) &&
                    pendingAiConsentRetry == pending
                ) {
                    pendingAiConsentRetry = null
                    _state.update {
                        it.copy(
                            error = "AI 健康问答授权没有保存，请稍后重试或在设置中开启。",
                            sending = false,
                            thinkingHint = "",
                        )
                    }
                }
            }
        }
    }

    fun declineAiConsent() {
        pendingAiConsentRetry = null
        _state.update {
            it.copy(
                showAiConsentPrompt = false,
                sending = false,
                thinkingHint = "",
            )
        }
    }

    private fun invalidateActiveConversationOperation() {
        invalidateConversationLoads()
        requestGate.invalidate()
        sendJob?.cancel()
        sendJob = null
        stopThinkingTicker()
        pendingAiConsentRetry = null
    }

    @Synchronized
    private fun beginConversationLoad(): Long {
        check(nextConversationLoadGeneration < Long.MAX_VALUE) {
            "chat conversation load generation exhausted"
        }
        return (++nextConversationLoadGeneration).also {
            activeConversationLoadGeneration = it
        }
    }

    @Synchronized
    private fun acceptsConversationLoad(generation: Long): Boolean =
        activeConversationLoadGeneration == generation

    @Synchronized
    private fun completeConversationLoad(generation: Long) {
        if (activeConversationLoadGeneration == generation) {
            activeConversationLoadGeneration = null
        }
    }

    @Synchronized
    private fun invalidateConversationLoads() {
        activeConversationLoadGeneration = null
    }

    private fun markUserMessage(
        messages: List<ChatMessageItem>,
        id: String,
        status: ChatDeliveryStatus,
    ): List<ChatMessageItem> =
        messages.map { item ->
            if (item.id == id) item.copy(status = status, retryText = item.retryText ?: item.content)
            else item
        }

    private fun startThinkingTicker() {
        thinkingJob?.cancel()
        thinkingJob = viewModelScope.launch {
            var idx = 0
            while (true) {
                delay(3500)
                val hints = activeThinkingHints.ifEmpty { DEFAULT_THINKING_HINTS }
                idx = (idx + 1) % hints.size
                _state.update {
                    if (!it.sending) it
                    else it.copy(thinkingHint = hints[idx])
                }
            }
        }
    }

    private fun stopThinkingTicker() {
        thinkingJob?.cancel()
        thinkingJob = null
        activeThinkingHints = DEFAULT_THINKING_HINTS
    }

    fun useFollowup(q: String) {
        setInput(q)
    }

    fun shouldOfferSavePlan(msg: ChatMessageItem): Boolean {
        if (msg.role != "assistant") return false
        if (_state.value.savedPlanMessageIds.contains(msg.id)) return false
        return looksLikeHealthPlan("${msg.content}\n${msg.analysis.orEmpty()}")
    }

    fun saveAsHealthPlan(msg: ChatMessageItem) = viewModelScope.launch {
        if (!shouldOfferSavePlan(msg) || _state.value.planSavingMessageId != null) return@launch
        val owner = captureOwner() ?: return@launch
        _state.update { it.copy(planSavingMessageId = msg.id) }
        runCatching {
            repo.savePlanFromChat(
                content = msg.content,
                analysis = msg.analysis,
                conversationId = _state.value.threadId,
                messageId = msg.id,
                expectedOwner = owner,
            )
        }.onSuccess {
            if (!authManager.isCurrent(owner)) return@onSuccess
            _state.update {
                it.copy(
                    planSavingMessageId = null,
                    savedPlanMessageIds = it.savedPlanMessageIds + msg.id,
                    error = "已保存为健康计划，可在「计划」页查看。",
                )
            }
        }.onFailure { e ->
            if (authManager.isCurrent(owner)) {
                _state.update { it.copy(planSavingMessageId = null, error = e.message) }
            }
        }
    }

    private fun captureOwner(): AuthManager.AccountScopeSnapshot? =
        authManager.captureAccountScope().also { snapshot ->
            if (snapshot == null) {
                _state.update { it.copy(error = "登录信息不完整，请重新登录后再试。") }
            }
        }

    private fun accepts(request: ChatRequestIdentity): Boolean =
        authManager.isCurrent(request.owner) && requestGate.accepts(
            token = request,
            currentOwner = authManager.captureAccountScope(),
            currentThreadId = _state.value.threadId,
        )

    private fun dedupeMessages(items: List<ChatMessageItem>): List<ChatMessageItem> {
        val seenIds = mutableSetOf<String>()
        val result = mutableListOf<ChatMessageItem>()
        for (item in items) {
            if (!seenIds.add(item.id)) continue
            val last = result.lastOrNull()
            if (
                last != null &&
                last.status == null &&
                item.status == null &&
                last.role == item.role &&
                last.content.trim() == item.content.trim()
            ) {
                continue
            }
            result += item
        }
        return result
    }
}

private fun looksLikeHealthPlan(text: String): Boolean {
    val planWords = listOf("计划", "方案", "安排", "周期", "一周", "7天", "每日", "每天")
    val healthWords = listOf("饮食", "运动", "康复", "用药", "服药", "控糖", "血糖", "热量", "恢复")
    return planWords.any { text.contains(it, ignoreCase = true) } &&
        healthWords.any { text.contains(it, ignoreCase = true) }
}
