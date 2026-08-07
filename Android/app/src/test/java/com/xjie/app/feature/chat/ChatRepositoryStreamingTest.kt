package com.xjie.app.feature.chat

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.auth.TestAuthManagerFactory
import com.xjie.app.core.model.ChatRequest
import com.xjie.app.core.model.ChatResponse
import com.xjie.app.core.model.ChatStreamEvent
import com.xjie.app.core.network.ApiException
import com.xjie.app.core.network.api.ChatApi
import com.xjie.app.core.network.api.HealthPlanApi
import com.xjie.app.core.network.api.UserApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ChatRepositoryStreamingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun streamEmitsProgressAndRequiresAuthoritativeDoneResult() = runBlocking {
        val chatApi = mockk<ChatApi>()
        val route = """{"type":"route","route":{"version":"2026-07-10","route_id":"fixture","strategy":"llm","primary_intent":"trend","depth":"standard","safety_level":"low","subject_type":"self","needs_literature":false,"max_followups":1,"progress_steps":["核对数据"]}}"""
        val progress = """{"type":"progress","step":"核对数据"}"""
        val done = """{"type":"done","result":{"summary":"完成。","thread_id":"thread-1","message_id":"message-1","citations":[]}}"""
        every { chatApi.chatStream(any(), any()) } returns responseCall(
            Response.success(
                "data: $route\n\ndata: $progress\n\ndata: $done\n\n"
                    .toResponseBody("text/event-stream".toMediaType()),
            ),
        )
        val repository = repository(chatApi)
        val events = mutableListOf<ChatStreamEvent>()

        val result = repository.sendStreaming(
            message = "问题",
            threadId = null,
            clientMessageId = "client-1",
            expectedOwner = owner(),
            onEvent = { events += it },
        )

        assertEquals("message-1", result.message_id)
        assertEquals(
            listOf("Route", "Progress", "Done"),
            events.map {
                when (it) {
                    is ChatStreamEvent.Route -> "Route"
                    is ChatStreamEvent.Progress -> "Progress"
                    is ChatStreamEvent.Token -> "Token"
                    is ChatStreamEvent.Done -> "Done"
                }
            },
        )
    }

    @Test
    fun missingDoneFailsClosedWithoutCallingLegacyEndpoint() = runBlocking {
        val chatApi = mockk<ChatApi>()
        every { chatApi.chatStream(any(), any()) } returns responseCall(
            Response.success(
                "data: {\"type\":\"progress\",\"step\":\"仍在处理\"}\n\n"
                    .toResponseBody("text/event-stream".toMediaType()),
            ),
        )

        val error = runCatching {
            repository(chatApi).sendStreaming("问题", null, "client-1", owner())
        }.exceptionOrNull()

        assertTrue(error is ApiException.InvalidResponse)
        coVerify(exactly = 0) { chatApi.chat(any(), any()) }
    }

    @Test
    fun endpointMissingFallsBackWithTheExactSameIdempotencyKey() = runBlocking {
        val chatApi = mockk<ChatApi>()
        val request = slot<ChatRequest>()
        every { chatApi.chatStream(any(), capture(request)) } returns responseCall(
            Response.error(
                404,
                "{}".toResponseBody("application/json".toMediaType()),
            ),
        )
        coEvery { chatApi.chat(any(), any()) } returns ChatResponse(summary = "旧端点回答。")

        val result = repository(chatApi).sendStreaming(
            "问题",
            "thread-1",
            "stable-client-id",
            owner(),
        )

        assertEquals("旧端点回答。", result.summary)
        assertEquals("stable-client-id", request.captured.client_message_id)
        assertEquals("thread-1", request.captured.thread_id)
        coVerify(exactly = 1) {
            chatApi.chat(any(), match { it.client_message_id == "stable-client-id" })
        }
    }

    @Test
    fun cancellationAfterHeadersClosesTheRealStreamingCallPromptly() = runBlocking {
        val server = MockWebServer()
        val slowStream = buildString {
            append("data: {\"type\":\"progress\",\"step\":\"等待\"}\n\n")
            repeat(2_000) {
                append("data: {\"type\":\"progress\",\"step\":\"仍在等待\"}\n\n")
            }
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setChunkedBody(slowStream, 64)
                .throttleBody(64, 100, TimeUnit.MILLISECONDS),
        )
        server.start()
        try {
            val repository = repository(liveChatApi(server))
            val job = launch {
                repository.sendStreamingWithin(
                    message = "取消测试",
                    threadId = null,
                    clientMessageId = "cancel-client-id",
                    expectedOwner = owner(),
                    totalTimeoutMillis = 10_000,
                )
            }
            yield()
            val recorded = server.takeRequest(2, TimeUnit.SECONDS)
            assertTrue(recorded != null)
            assertEquals("text/event-stream", recorded?.getHeader("Accept"))
            assertTrue(recorded?.body?.readUtf8().orEmpty().contains("cancel-client-id"))
            delay(250)

            val startedNanos = System.nanoTime()
            job.cancel()
            withTimeout(2_000) { job.join() }
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)

            assertTrue(job.isCancelled)
            assertTrue("cancellation took ${elapsedMillis}ms", elapsedMillis < 2_000)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun heartbeatOnlyStreamHitsTotalDeadlineAndNeverFallsBack() = runBlocking {
        val server = MockWebServer()
        val heartbeat = "data: {\"type\":\"progress\",\"step\":\"连接正常\"}\n\n".repeat(2_000)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setChunkedBody(heartbeat, 32)
                .throttleBody(32, 10, TimeUnit.MILLISECONDS),
        )
        server.start()
        try {
            val repository = repository(liveChatApi(server))
            val error = runCatching {
                withTimeout(2_000) {
                    repository.sendStreamingWithin(
                        message = "总期限测试",
                        threadId = "thread-1",
                        clientMessageId = "deadline-client-id",
                        expectedOwner = owner(),
                        totalTimeoutMillis = 150,
                    )
                }
            }.exceptionOrNull()

            assertTrue(error is ApiException.HttpError)
            assertEquals(408, (error as ApiException.HttpError).code)
            assertTrue(error.message.orEmpty().contains("超时"))
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private fun repository(chatApi: ChatApi) = ChatRepository(
        chatApi = chatApi,
        healthPlanApi = mockk<HealthPlanApi>(),
        userApi = mockk<UserApi>(),
        authManager = loggedInAuthManager(),
        json = json,
    )

    private fun responseCall(response: Response<ResponseBody>): Call<ResponseBody> =
        mockk {
            every { execute() } returns response
            every { cancel() } just Runs
        }

    private fun liveChatApi(server: MockWebServer): ChatApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ChatApi::class.java)

    private fun owner(): AuthManager.AccountScopeSnapshot = loggedInAuthManager()
        .captureAccountScope()!!

    private fun loggedInAuthManager(): AuthManager = TestAuthManagerFactory.create().apply {
        establishSession(jwt("account-a"), "refresh", "subject-a")
    }

    private fun jwt(subject: String): String {
        fun segment(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        return "${segment("""{"alg":"none"}""")}.${segment("""{"sub":"$subject"}""")}.signature"
    }
}
