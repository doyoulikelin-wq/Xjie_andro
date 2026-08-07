package com.xjie.app.feature.chat

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.ChatStreamEvent
import com.xjie.app.core.network.ApiException
import com.xjie.app.core.network.api.ChatApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Tag

class ChatStreamingParityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun streamingEndpointIsAccountTaggedAndRequestsEventStream() {
        val method = ChatApi::class.java.declaredMethods.single { it.name == "chatStream" }

        assertEquals("api/chat/stream", method.getAnnotation(POST::class.java).value)
        assertTrue(method.isAnnotationPresent(Streaming::class.java))
        assertTrue(
            method.getAnnotation(Headers::class.java).value.contains("Accept: text/event-stream"),
        )
        assertTrue(
            method.parameterAnnotations.indices.any { index ->
                method.parameterAnnotations[index].any { it is Tag } &&
                    method.parameterTypes.getOrNull(index) == AuthManager.AccountScopeSnapshot::class.java
            },
        )
    }

    @Test
    fun routeProgressAndDoneDecodeWithStructuredAuditFieldsIgnored() {
        val route = ChatStreamProtocol.decodeLine(
            """data: {"type":"route","route":{"version":"2026-07-10","route_id":"llm.health.deep","strategy":"llm","primary_intent":"trend_analysis","depth":"deep","safety_level":"low","subject_type":"self","needs_literature":true,"max_followups":1,"progress_steps":["核对来源","检索证据"]}}""",
            json,
        ) as ChatStreamEvent.Route
        val progress = ChatStreamProtocol.decodeLine(
            """data: {"type":"progress","step":"检索证据"}""",
            json,
        ) as ChatStreamEvent.Progress
        val done = ChatStreamProtocol.decodeLine(
            """data: {"type":"done","result":{"summary":"已完成","answer_markdown":"完整正文","thread_id":"9","message_id":"18","response_state":"completed","quality_flags":[],"safety_flags":[],"used_context":{"message_structure_version":"2026-07-10"},"citations":[]}}""",
            json,
        ) as ChatStreamEvent.Done

        assertEquals("llm.health.deep", route.route.route_id)
        assertEquals(listOf("核对来源", "检索证据"), route.route.progress_steps)
        assertEquals("检索证据", progress.step)
        assertEquals("18", done.result.message_id)
        assertEquals("completed", done.result.response_state)
    }

    @Test
    fun legacyFallbackIsAllowedOnlyWhenStreamEndpointIsMissing() {
        assertTrue(ChatStreamProtocol.isLegacyFallbackStatus(404))
        assertTrue(ChatStreamProtocol.isLegacyFallbackStatus(405))
        assertFalse(ChatStreamProtocol.isLegacyFallbackStatus(400))
        assertFalse(ChatStreamProtocol.isLegacyFallbackStatus(401))
        assertFalse(ChatStreamProtocol.isLegacyFallbackStatus(403))
        assertFalse(ChatStreamProtocol.isLegacyFallbackStatus(500))
    }

    @Test
    fun malformedTerminalAndServerErrorFailClosed() {
        val missingResult = runCatching {
            ChatStreamProtocol.decodeLine("""data: {"type":"done"}""", json)
        }.exceptionOrNull()
        val serverError = runCatching {
            ChatStreamProtocol.decodeLine(
                """data: {"type":"error","message":"fixture failed","retryable":true}""",
                json,
            )
        }.exceptionOrNull()

        assertTrue(missingResult is ApiException.InvalidResponse)
        assertTrue(serverError is ApiException.HttpError)
        assertEquals(503, (serverError as ApiException.HttpError).code)
        assertEquals("fixture failed", serverError.message)
    }
}
