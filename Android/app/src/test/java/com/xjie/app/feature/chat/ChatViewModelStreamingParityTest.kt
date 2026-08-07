package com.xjie.app.feature.chat

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.auth.TestAuthManagerFactory
import com.xjie.app.core.model.ChatInteractionRoute
import com.xjie.app.core.model.ChatMessage
import com.xjie.app.core.model.ChatResponse
import com.xjie.app.core.model.ChatStreamEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelStreamingParityTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendSynchronouslyConsumesDraftAndAppliesServerRouteAndStableMessageId() = runTest(dispatcher) {
        val repository = mockk<ChatRepository>()
        val route = route(depth = "deep")
        coEvery { repository.sendStreaming(any(), any(), any(), any(), any()) } coAnswers {
            arg<(ChatStreamEvent) -> Unit>(4)(ChatStreamEvent.Route(route))
            arg<(ChatStreamEvent) -> Unit>(4)(ChatStreamEvent.Progress("正在核对证据"))
            ChatResponse(
                summary = "可能有关，但",
                answer_markdown = "完整的深度回答。",
                analysis = "完整的深度回答。",
                thread_id = "thread-9",
                message_id = "18",
                interaction_route = route,
            )
        }
        val viewModel = ChatViewModel(repository, loggedInAuthManager())
        viewModel.setInput("  帮我分析 HRV  ")

        viewModel.send()

        assertEquals("", viewModel.state.value.input)
        assertTrue(viewModel.state.value.sending)
        assertEquals("帮我分析 HRV", viewModel.state.value.messages.single().content)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.sending)
        assertEquals("server-18", viewModel.state.value.messages.last().id)
        assertEquals("完整的深度回答。", viewModel.state.value.messages.last().content)
        assertNull(viewModel.state.value.messages.last().analysis)
        assertEquals("llm.health.deep", viewModel.state.value.activeRoute?.route_id)
    }

    @Test
    fun processingReplayMarksUserSentWithoutCreatingAssistantBubble() = runTest(dispatcher) {
        val repository = mockk<ChatRepository>()
        coEvery { repository.sendStreaming(any(), any(), any(), any(), any()) } returns
            ChatResponse(
                summary = "这条消息仍在处理中。",
                thread_id = "thread-processing",
                response_state = "processing",
            )
        val viewModel = ChatViewModel(repository, loggedInAuthManager())

        viewModel.sendText("重试上一条问题")
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.messages.size)
        assertEquals("user", viewModel.state.value.messages.single().role)
        assertEquals(ChatDeliveryStatus.Sent, viewModel.state.value.messages.single().status)
        assertEquals("thread-processing", viewModel.state.value.threadId)
        assertEquals("这条消息仍在处理中。", viewModel.state.value.error)
    }

    @Test
    fun retryReusesClientMessageIdAndDoesNotAccumulateSyntheticErrorBubbles() = runTest(dispatcher) {
        val repository = mockk<ChatRepository>()
        coEvery { repository.sendStreaming(any(), any(), any(), any(), any()) } throws
            IOException("offline")
        val viewModel = ChatViewModel(repository, loggedInAuthManager())

        viewModel.sendText("同一条问题")
        advanceUntilIdle()
        val clientMessageId = viewModel.state.value.messages.single().id
        assertEquals(ChatDeliveryStatus.Failed, viewModel.state.value.messages.single().status)

        coEvery { repository.sendStreaming(any(), any(), any(), any(), any()) } returns
            ChatResponse(summary = "重放成功。", message_id = "server-replay")
        viewModel.retry(clientMessageId)
        advanceUntilIdle()

        assertEquals(listOf("user", "assistant"), viewModel.state.value.messages.map { it.role })
        assertEquals("server-server-replay", viewModel.state.value.messages.last().id)
        coVerify(exactly = 2) {
            repository.sendStreaming("同一条问题", any(), clientMessageId, any(), any())
        }
    }

    @Test
    fun newChatRejectsLateCompletionFromTheClearedConversation() = runTest(dispatcher) {
        val repository = mockk<ChatRepository>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { repository.sendStreaming(any(), any(), any(), any(), any()) } coAnswers {
            started.complete(Unit)
            release.await()
            ChatResponse(summary = "旧会话回答", thread_id = "old-thread", message_id = "old-message")
        }
        val viewModel = ChatViewModel(repository, loggedInAuthManager())

        viewModel.sendText("旧会话问题")
        runCurrent()
        started.await()
        viewModel.newChat()
        release.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.messages.isEmpty())
        assertNull(viewModel.state.value.threadId)
        assertFalse(viewModel.state.value.sending)
    }

    @Test
    fun deletingCurrentConversationDuringInFlightSendCancelsGenerationAndNeverLeavesSendingStuck() =
        runTest(dispatcher) {
            val repository = mockk<ChatRepository>()
            val streamStarted = CompletableDeferred<Unit>()
            val releaseStream = CompletableDeferred<Unit>()
            var sendCount = 0
            coEvery { repository.sendStreaming(any(), any(), any(), any(), any()) } coAnswers {
                sendCount += 1
                if (sendCount == 1) {
                    ChatResponse(summary = "初始回答", thread_id = "thread-1", message_id = "seed")
                } else {
                    streamStarted.complete(Unit)
                    withContext(NonCancellable) { releaseStream.await() }
                    ChatResponse(summary = "已删除会话的晚到回答", thread_id = "thread-1", message_id = "late")
                }
            }
            coEvery { repository.deleteConversation("thread-1", any()) } returns Unit
            val viewModel = ChatViewModel(repository, loggedInAuthManager())
            viewModel.sendText("建立会话")
            advanceUntilIdle()
            assertEquals("thread-1", viewModel.state.value.threadId)

            viewModel.sendText("仍在生成的消息")
            runCurrent()
            streamStarted.await()
            viewModel.deleteConversation("thread-1")
            runCurrent()

            assertNull(viewModel.state.value.threadId)
            assertTrue(viewModel.state.value.messages.isEmpty())
            assertFalse(viewModel.state.value.sending)

            releaseStream.complete(Unit)
            advanceUntilIdle()
            assertNull(viewModel.state.value.threadId)
            assertTrue(viewModel.state.value.messages.isEmpty())
            assertFalse(viewModel.state.value.sending)
        }

    @Test
    fun lateHistoryLoadCannotReplaceConversationOrStrandInFlightSend() = runTest(dispatcher) {
        val repository = mockk<ChatRepository>()
        val historyStarted = CompletableDeferred<Unit>()
        val releaseHistory = CompletableDeferred<Unit>()
        val streamStarted = CompletableDeferred<Unit>()
        val releaseStream = CompletableDeferred<Unit>()
        coEvery { repository.conversationMessages("history-1", any()) } coAnswers {
            historyStarted.complete(Unit)
            releaseHistory.await()
            listOf(ChatMessage(id = "history-message", role = "assistant", content = "旧历史回答"))
        }
        coEvery { repository.sendStreaming(any(), any(), any(), any(), any()) } coAnswers {
            streamStarted.complete(Unit)
            releaseStream.await()
            ChatResponse(summary = "当前回答", thread_id = "current-thread", message_id = "current-message")
        }
        val viewModel = ChatViewModel(repository, loggedInAuthManager())

        viewModel.loadConversation("history-1")
        runCurrent()
        historyStarted.await()
        viewModel.sendText("当前消息")
        runCurrent()
        streamStarted.await()

        releaseHistory.complete(Unit)
        runCurrent()
        assertNull(viewModel.state.value.threadId)
        assertFalse(viewModel.state.value.isViewingHistory)
        assertEquals(listOf("当前消息"), viewModel.state.value.messages.map { it.content })
        assertTrue(viewModel.state.value.sending)

        releaseStream.complete(Unit)
        advanceUntilIdle()
        assertEquals("current-thread", viewModel.state.value.threadId)
        assertEquals(listOf("user", "assistant"), viewModel.state.value.messages.map { it.role })
        assertFalse(viewModel.state.value.sending)
    }

    private fun loggedInAuthManager(): AuthManager = TestAuthManagerFactory.create().apply {
        establishSession(jwt("account-a"), "refresh", "subject-a")
    }

    private fun jwt(subject: String): String {
        fun segment(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        return "${segment("""{"alg":"none"}""")}.${segment("""{"sub":"$subject"}""")}.signature"
    }

    private fun route(depth: String) = ChatInteractionRoute(
        version = "2026-07-10",
        route_id = "llm.health.$depth",
        strategy = "llm",
        primary_intent = "trend_analysis",
        depth = depth,
        safety_level = "low",
        subject_type = "self",
        needs_literature = true,
        max_followups = 1,
        progress_steps = listOf("正在核对证据"),
    )
}
