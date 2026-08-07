package com.xjie.app.feature.chat

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.ChatConversation
import com.xjie.app.core.model.ChatMessage
import com.xjie.app.core.model.ChatRequest
import com.xjie.app.core.model.ChatResponse
import com.xjie.app.core.model.ChatStreamEvent
import com.xjie.app.core.model.HealthPlanDetail
import com.xjie.app.core.model.HealthPlanFromChatRequest
import com.xjie.app.core.model.UpdateConsentBody
import com.xjie.app.core.network.api.ChatApi
import com.xjie.app.core.network.api.HealthPlanApi
import com.xjie.app.core.network.api.UserApi
import com.xjie.app.core.network.safeApiCall
import com.xjie.app.core.network.ApiException
import com.xjie.app.core.network.ErrorBody
import com.xjie.app.core.util.ApiConstants
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val healthPlanApi: HealthPlanApi,
    private val userApi: UserApi,
    private val authManager: AuthManager,
    private val json: Json,
) {
    suspend fun listConversations(
        limit: Int = 20,
        offset: Int = 0,
        expectedOwner: AuthManager.AccountScopeSnapshot? = null,
    ): List<ChatConversation> {
        val owner = owner(expectedOwner)
        return safeApiCall(json) { chatApi.listConversations(owner, limit, offset) }
    }

    suspend fun conversationMessages(
        id: String,
        expectedOwner: AuthManager.AccountScopeSnapshot? = null,
    ): List<ChatMessage> {
        val owner = owner(expectedOwner)
        return safeApiCall(json) { chatApi.conversationMessages(owner, id) }
    }

    suspend fun deleteConversation(
        id: String,
        expectedOwner: AuthManager.AccountScopeSnapshot? = null,
    ) {
        val resp = chatApi.deleteConversation(owner(expectedOwner), id)
        if (!resp.isSuccessful) {
            throw com.xjie.app.core.network.ApiException.HttpError(resp.code(), resp.message().ifBlank { "删除失败" })
        }
    }

    suspend fun send(
        message: String,
        threadId: String?,
        clientMessageId: String,
        expectedOwner: AuthManager.AccountScopeSnapshot? = null,
    ): ChatResponse =
        safeApiCall(json) {
            chatApi.chat(
                owner(expectedOwner),
                ChatRequest(
                    message = message,
                    thread_id = threadId,
                    client_message_id = clientMessageId,
                )
            )
        }

    /**
     * Preferred chat transport. The legacy JSON endpoint is used only when a deployed backend
     * explicitly reports that the SSE endpoint is unavailable (404/405).
     */
    suspend fun sendStreaming(
        message: String,
        threadId: String?,
        clientMessageId: String,
        expectedOwner: AuthManager.AccountScopeSnapshot? = null,
        onEvent: (ChatStreamEvent) -> Unit = {},
    ): ChatResponse = sendStreamingWithin(
        message = message,
        threadId = threadId,
        clientMessageId = clientMessageId,
        expectedOwner = expectedOwner,
        totalTimeoutMillis = STREAM_TOTAL_TIMEOUT_MILLIS,
        onEvent = onEvent,
    )

    internal suspend fun sendStreamingWithin(
        message: String,
        threadId: String?,
        clientMessageId: String,
        expectedOwner: AuthManager.AccountScopeSnapshot? = null,
        totalTimeoutMillis: Long,
        onEvent: (ChatStreamEvent) -> Unit = {},
    ): ChatResponse {
        require(totalTimeoutMillis > 0L) { "chat stream timeout must be positive" }
        val accountOwner = owner(expectedOwner)
        val request = ChatRequest(
            message = message,
            thread_id = threadId,
            client_message_id = clientMessageId,
        )
        val callbackContext = currentCoroutineContext().minusKey(Job)
        return withTimeoutOrNull(totalTimeoutMillis) {
            try {
                executeStreamingCall(
                    call = chatApi.chatStream(accountOwner, request),
                    callbackContext = callbackContext,
                    onEvent = onEvent,
                )
            } catch (_: LegacyChatEndpointUnavailable) {
                safeApiCall(json) { chatApi.chat(accountOwner, request) }
            }
        } ?: throw ApiException.HttpError(
            408,
            "回答等待超时，原消息已保留，请重试。",
        )
    }

    private suspend fun executeStreamingCall(
        call: Call<ResponseBody>,
        callbackContext: kotlin.coroutines.CoroutineContext,
        onEvent: (ChatStreamEvent) -> Unit,
    ): ChatResponse = suspendCancellableCoroutine { continuation ->
        val activeBody = AtomicReference<ResponseBody?>()
        continuation.invokeOnCancellation {
            runCatching { call.cancel() }
        }
        CoroutineScope(continuation.context).launch(Dispatchers.IO) {
            try {
                val response = call.execute()
                val body = response.body() ?: response.errorBody()
                activeBody.set(body)
                val result = parseStreamingResponse(response, callbackContext, onEvent)
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(result))
                }
            } catch (error: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.failure(error))
                }
            } finally {
                runCatching { activeBody.getAndSet(null)?.close() }
            }
        }
    }

    private suspend fun parseStreamingResponse(
        response: Response<ResponseBody>,
        callbackContext: kotlin.coroutines.CoroutineContext,
        onEvent: (ChatStreamEvent) -> Unit,
    ): ChatResponse {
        if (ChatStreamProtocol.isLegacyFallbackStatus(response.code())) {
            throw LegacyChatEndpointUnavailable()
        }
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.source()?.run {
                request(MAX_ERROR_BODY_BYTES)
                buffer.readUtf8(buffer.size.coerceAtMost(MAX_ERROR_BODY_BYTES))
            }.orEmpty()
            val detail = runCatching {
                json.decodeFromString(ErrorBody.serializer(), errorBody).detail?.message
            }.getOrNull()
            throw ApiException.HttpError(
                response.code(),
                detail ?: errorBody.take(200).ifBlank { response.message().ifBlank { "问答请求失败" } },
            )
        }

        val body = response.body() ?: throw ApiException.InvalidResponse
        val source = body.source()
        try {
            while (!source.exhausted()) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8LineStrict(ChatStreamProtocol.MAX_LINE_BYTES)
                val event = ChatStreamProtocol.decodeLine(line, json) ?: continue
                withContext(callbackContext) { onEvent(event) }
                if (event is ChatStreamEvent.Done) return event.result
            }
        } catch (error: IOException) {
            throw ApiException.InvalidResponse
        }
        throw ApiException.InvalidResponse
    }

    suspend fun enableAiChat(expectedOwner: AuthManager.AccountScopeSnapshot? = null) =
        safeApiCall(json) {
            userApi.updateConsentForOwner(
                owner(expectedOwner),
                UpdateConsentBody(allow_ai_chat = true),
            )
        }

    suspend fun savePlanFromChat(
        content: String,
        analysis: String?,
        conversationId: String?,
        messageId: String,
        expectedOwner: AuthManager.AccountScopeSnapshot? = null,
    ): HealthPlanDetail =
        safeApiCall(json) {
            healthPlanApi.createFromChatForOwner(
                owner(expectedOwner),
                HealthPlanFromChatRequest(
                    content = content,
                    analysis = analysis,
                    conversation_id = conversationId,
                    message_id = messageId,
                    title = null,
                )
            )
        }

    private fun owner(
        expectedOwner: AuthManager.AccountScopeSnapshot?,
    ): AuthManager.AccountScopeSnapshot = expectedOwner
        ?: authManager.captureAccountScope()
        ?: throw com.xjie.app.core.network.ApiException.NotLoggedIn

    private companion object {
        const val MAX_ERROR_BODY_BYTES = 64L * 1024L
        const val STREAM_TOTAL_TIMEOUT_MILLIS = ApiConstants.LLM_TIMEOUT_S * 1000L
    }

    private class LegacyChatEndpointUnavailable : RuntimeException()
}
