package com.xjie.app.feature.chat

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.auth.TestAuthManagerFactory
import com.xjie.app.core.model.ChatResponse
import com.xjie.app.core.model.ConsentResponse
import com.xjie.app.core.network.ApiException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExplicitAiConsentTest {
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
    fun http403NeverGrantsConsentOrRetriesUntilTheUserExplicitlyConfirms() = runTest(dispatcher) {
        val repository = mockk<ChatRepository>()
        val auth = loggedInAuthManager()
        coEvery { repository.sendStreaming(any(), any(), any(), any(), any()) } throws
            ApiException.HttpError(403, "consent required")
        coEvery { repository.enableAiChat(any()) } returns ConsentResponse(allow_ai_chat = true)
        val viewModel = ChatViewModel(repository, auth)

        viewModel.setInput("请分析我的健康趋势")
        viewModel.send()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showAiConsentPrompt)
        assertEquals(ChatDeliveryStatus.Failed, viewModel.state.value.messages.single().status)
        coVerify(exactly = 1) { repository.sendStreaming(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { repository.enableAiChat(any()) }

        coEvery { repository.sendStreaming(any(), any(), any(), any(), any()) } returns
            ChatResponse(summary = "已在明确授权后回答")
        viewModel.grantAiConsentAndRetry()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showAiConsentPrompt)
        assertEquals(ChatDeliveryStatus.Sent, viewModel.state.value.messages.first().status)
        assertEquals("已在明确授权后回答", viewModel.state.value.messages.last().content)
        coVerify(exactly = 1) { repository.enableAiChat(any()) }
        coVerify(exactly = 2) { repository.sendStreaming(any(), any(), any(), any(), any()) }
    }

    @Test
    fun consentResponseThatDoesNotConfirmAuthorizationNeverRetriesTheChat() = runTest(dispatcher) {
        val repository = mockk<ChatRepository>()
        val auth = loggedInAuthManager()
        coEvery { repository.sendStreaming(any(), any(), any(), any(), any()) } throws
            ApiException.HttpError(403, "consent required")
        coEvery { repository.enableAiChat(any()) } returns ConsentResponse(allow_ai_chat = false)
        val viewModel = ChatViewModel(repository, auth)

        viewModel.setInput("需要显式授权")
        viewModel.send()
        advanceUntilIdle()
        viewModel.grantAiConsentAndRetry()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showAiConsentPrompt)
        assertTrue(viewModel.state.value.error.orEmpty().contains("授权没有保存"))
        coVerify(exactly = 1) { repository.enableAiChat(any()) }
        coVerify(exactly = 1) { repository.sendStreaming(any(), any(), any(), any(), any()) }
    }

    @Test
    fun consentSaveReservesSendSlotAndNewChatCannotResurrectPendingRetry() = runTest(dispatcher) {
        val repository = mockk<ChatRepository>()
        val consentStarted = CompletableDeferred<Unit>()
        val releaseConsent = CompletableDeferred<Unit>()
        var sendCount = 0
        coEvery { repository.sendStreaming(any(), any(), any(), any(), any()) } coAnswers {
            sendCount += 1
            if (sendCount == 1) {
                throw ApiException.HttpError(403, "consent required")
            }
            ChatResponse(summary = "不应出现的旧重试")
        }
        coEvery { repository.enableAiChat(any()) } coAnswers {
            consentStarted.complete(Unit)
            releaseConsent.await()
            ConsentResponse(allow_ai_chat = true)
        }
        val viewModel = ChatViewModel(repository, loggedInAuthManager())
        viewModel.sendText("原始授权消息")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.showAiConsentPrompt)

        viewModel.grantAiConsentAndRetry()
        runCurrent()
        consentStarted.await()
        assertTrue(viewModel.state.value.sending)

        viewModel.sendText("授权保存期间的新消息")
        assertEquals(listOf("原始授权消息"), viewModel.state.value.messages.map { it.content })

        viewModel.newChat()
        releaseConsent.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.messages.isEmpty())
        assertFalse(viewModel.state.value.sending)
        assertFalse(viewModel.state.value.showAiConsentPrompt)
        coVerify(exactly = 1) { repository.sendStreaming(any(), any(), any(), any(), any()) }
    }

    private fun loggedInAuthManager(): AuthManager {
        return TestAuthManagerFactory.create().apply {
            establishSession(jwt("account-a"), "refresh", "subject-a")
        }
    }

    private fun jwt(subject: String): String {
        fun segment(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        return "${segment("""{"alg":"none"}""")}." +
            "${segment("""{"sub":"$subject"}""")}.signature"
    }
}
