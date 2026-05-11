package com.xjie.app.core.network.api

import com.xjie.app.core.model.ChatConversation
import com.xjie.app.core.model.ChatMessage
import com.xjie.app.core.model.ChatRequest
import com.xjie.app.core.model.ChatResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {
    @GET("api/chat/conversations")
    suspend fun listConversations(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): List<ChatConversation>

    @GET("api/chat/conversations/{id}")
    suspend fun conversationMessages(@Path("id") id: String): List<ChatMessage>

    @DELETE("api/chat/conversations/{id}")
    suspend fun deleteConversation(@Path("id") id: String): retrofit2.Response<Unit>

    @POST("api/chat")
    suspend fun chat(@Body body: ChatRequest): ChatResponse
}
