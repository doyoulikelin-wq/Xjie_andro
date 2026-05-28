package com.xjie.app.feature.chat

import com.xjie.app.core.model.ChatConversation
import com.xjie.app.core.model.ChatMessage
import com.xjie.app.core.model.ChatRequest
import com.xjie.app.core.model.ChatResponse
import com.xjie.app.core.model.HealthPlanDetail
import com.xjie.app.core.model.HealthPlanFromChatRequest
import com.xjie.app.core.model.UpdateConsentBody
import com.xjie.app.core.network.api.ChatApi
import com.xjie.app.core.network.api.HealthPlanApi
import com.xjie.app.core.network.api.UserApi
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val healthPlanApi: HealthPlanApi,
    private val userApi: UserApi,
    private val json: Json,
) {
    suspend fun listConversations(limit: Int = 20, offset: Int = 0): List<ChatConversation> =
        safeApiCall(json) { chatApi.listConversations(limit, offset) }

    suspend fun conversationMessages(id: String): List<ChatMessage> =
        safeApiCall(json) { chatApi.conversationMessages(id) }

    suspend fun deleteConversation(id: String) {
        val resp = chatApi.deleteConversation(id)
        if (!resp.isSuccessful) {
            throw com.xjie.app.core.network.ApiException.HttpError(resp.code(), resp.message().ifBlank { "删除失败" })
        }
    }

    suspend fun send(message: String, threadId: String?, clientMessageId: String): ChatResponse =
        safeApiCall(json) {
            chatApi.chat(
                ChatRequest(
                    message = message,
                    thread_id = threadId,
                    client_message_id = clientMessageId,
                )
            )
        }

    suspend fun enableAiChat() =
        safeApiCall(json) { userApi.updateConsent(UpdateConsentBody(allow_ai_chat = true)) }

    suspend fun savePlanFromChat(
        content: String,
        analysis: String?,
        conversationId: String?,
        messageId: String,
    ): HealthPlanDetail =
        safeApiCall(json) {
            healthPlanApi.createFromChat(
                HealthPlanFromChatRequest(
                    content = content,
                    analysis = analysis,
                    conversation_id = conversationId,
                    message_id = messageId,
                    title = null,
                )
            )
        }
}
