package com.xjie.app.core.network.api

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.ChatConversation
import com.xjie.app.core.model.ChatMessage
import com.xjie.app.core.model.ChatRequest
import com.xjie.app.core.model.ChatResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Headers
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag
import retrofit2.http.Streaming

interface ChatApi {
    @GET("api/chat/conversations")
    suspend fun listConversations(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): List<ChatConversation>

    @GET("api/chat/conversations/{id}")
    suspend fun conversationMessages(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("id") id: String,
    ): List<ChatMessage>

    @DELETE("api/chat/conversations/{id}")
    suspend fun deleteConversation(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("id") id: String,
    ): retrofit2.Response<Unit>

    @POST("api/chat")
    suspend fun chat(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: ChatRequest,
    ): ChatResponse

    @Streaming
    @Headers("Accept: text/event-stream")
    @POST("api/chat/stream")
    fun chatStream(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: ChatRequest,
    ): Call<ResponseBody>
}
