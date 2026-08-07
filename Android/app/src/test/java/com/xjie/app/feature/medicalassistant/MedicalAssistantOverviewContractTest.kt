package com.xjie.app.feature.medicalassistant

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.MedicalAssistantGenerationResult
import com.xjie.app.core.model.MedicalAssistantOverview
import com.xjie.app.core.model.MedicalAssistantOverviewPolicy
import com.xjie.app.core.network.AuthInterceptor
import com.xjie.app.core.network.api.HealthDataApi
import com.xjie.app.core.storage.TokenStore
import io.mockk.every
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Tag

class MedicalAssistantOverviewContractTest {
    @Test
    fun accountSwitchBeforeDispatchNeverGeneratesMedicalOverviewForNewAccount() {
        val generate = HealthDataApi::class.java.declaredMethods.single {
            it.name == "generateMedicalAssistantOverview"
        }
        assertTrue(generate.parameterAnnotations.flatten().any { it is Tag })

        assertStaleOwnerDoesNotDispatch("/api/health-data/medical-assistant/overview/generate")
    }

    @Test
    fun accountSwitchBeforeDispatchNeverFetchesMedicalOverviewWithNewAccount() {
        val fetch = HealthDataApi::class.java.declaredMethods.single {
            it.name == "medicalAssistantOverview"
        }
        assertTrue(fetch.parameterAnnotations.flatten().any { it is Tag })

        assertStaleOwnerDoesNotDispatch("/api/health-data/medical-assistant/overview")
    }

    private fun assertStaleOwnerDoesNotDispatch(path: String) {

        val auth = authManager()
        auth.establishSession(jwt("account-a", "first"), "refresh-a", "subject-a")
        val staleOwner = requireNotNull(auth.captureAccountScope())
        auth.establishSession(jwt("account-b", "second"), "refresh-b", "subject-b")
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val request = Request.Builder()
                .url(server.url(path))
                .tag(AuthManager.AccountScopeSnapshot::class.java, staleOwner)
                .build()
            val error = runCatching {
                OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor(auth))
                    .build()
                    .newCall(request)
                    .execute()
                    .close()
            }.exceptionOrNull()

            assertTrue(error is java.io.IOException)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun overviewApiPinsExactGetAndBodylessPostRoutes() {
        val get = HealthDataApi::class.java.declaredMethods.single {
            it.name == "medicalAssistantOverview"
        }
        val generate = HealthDataApi::class.java.declaredMethods.single {
            it.name == "generateMedicalAssistantOverview"
        }

        assertEquals(
            "api/health-data/medical-assistant/overview",
            requireNotNull(get.getAnnotation(GET::class.java)).value,
        )
        assertTrue(get.parameterAnnotations.flatten().any { it is Tag })
        assertEquals(
            "api/health-data/medical-assistant/overview/generate",
            requireNotNull(generate.getAnnotation(POST::class.java)).value,
        )
        assertFalse(
            "generation must stay a bodyless POST like the iOS build-22 contract",
            generate.parameterAnnotations.flatten().any { it is Body },
        )
        assertTrue(generate.parameterAnnotations.flatten().any { it is Tag })
    }

    @Test
    fun freshnessUsesOnlyParseableServerEvidenceAndUnknownResultFailsClosed() {
        assertTrue(
            MedicalAssistantOverviewPolicy.hasNewerUpload(
                uploadedAt = "2026-08-07T10:30:00Z",
                generatedAt = null,
            ),
        )
        assertTrue(
            MedicalAssistantOverviewPolicy.hasNewerUpload(
                uploadedAt = "2026-08-07T10:30:00+08:00",
                generatedAt = "2026-08-07T02:00:00Z",
            ),
        )
        assertFalse(
            MedicalAssistantOverviewPolicy.hasNewerUpload(
                uploadedAt = "2026-08-07T01:30:00Z",
                generatedAt = "2026-08-07T02:00:00Z",
            ),
        )
        assertFalse(MedicalAssistantOverviewPolicy.hasNewerUpload("not-a-time", null))
        assertFalse(
            MedicalAssistantOverviewPolicy.hasNewerUpload(
                uploadedAt = "2026-08-07T10:30:00Z",
                generatedAt = "not-a-time",
            ),
        )

        val result = MedicalAssistantGenerationResult.fromWire("future_server_state")
        assertEquals(MedicalAssistantGenerationResult.Unknown("future_server_state"), result)
        assertFalse(MedicalAssistantOverviewPolicy.accepts(result))

        val overview = overview(generationResult = "no_information_update")
        assertEquals(
            MedicalAssistantScreenPhase.NoUpdate,
            MedicalAssistantUiState(
                overview = overview,
                lastGenerationResult = MedicalAssistantGenerationResult.NoInformationUpdate,
            ).phase,
        )
        assertEquals(
            MedicalAssistantScreenPhase.Processing,
            MedicalAssistantUiState(
                overview = overview,
                lastGenerationResult = MedicalAssistantGenerationResult.ReportProcessing,
            ).phase,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun accountAtoBtoARejectsTheFirstAccountsLateOverviewResponse() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val auth = authManager()
            auth.establishSession(jwt("account-a", "first"), "refresh-a", "subject-a")
            val expectedOwner = requireNotNull(auth.captureAccountScope())
            val repository = DeferredMedicalAssistantRepository()
            val viewModel = MedicalAssistantViewModel(repository, auth)

            viewModel.load()
            runCurrent()
            assertTrue(repository.fetchStarted.isCompleted)
            assertEquals(expectedOwner, repository.fetchOwner)

            auth.establishSession(jwt("account-b", "middle"), "refresh-b", "subject-b")
            auth.establishSession(jwt("account-a", "second"), "refresh-a2", "subject-a")
            runCurrent()

            repository.fetchResponse.complete(overview(summary = "迟到的 A 账号概况"))
            advanceUntilIdle()

            assertNull(viewModel.state.value.overview)
            assertEquals(MedicalAssistantScreenPhase.Error, viewModel.state.value.phase)
            assertTrue(viewModel.state.value.error?.contains("变化") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun overview(
        summary: String = "已确认资料概况",
        generationResult: String = "loaded",
    ) = MedicalAssistantOverview(
        subject_user_id = 1L,
        summary = summary,
        generated_at = "2026-08-07T02:00:00Z",
        latest_report_uploaded_at = "2026-08-07T02:30:00Z",
        report_count_last_year = 2,
        recent_documents = emptyList(),
        generation_result = generationResult,
    )

    private fun authManager(): AuthManager {
        val tokenStore = mockk<TokenStore>()
        var accessToken = ""
        var refreshToken = ""
        var subjectId = ""
        var generation = 0L
        every { tokenStore.accessToken } answers { accessToken }
        every { tokenStore.accessToken = any() } answers { accessToken = firstArg() }
        every { tokenStore.refreshToken } answers { refreshToken }
        every { tokenStore.refreshToken = any() } answers { refreshToken = firstArg() }
        every { tokenStore.subjectId } answers { subjectId }
        every { tokenStore.subjectId = any() } answers { subjectId = firstArg() }
        every { tokenStore.authGeneration } answers { generation }
        every { tokenStore.authGeneration = any() } answers { generation = firstArg() }
        every { tokenStore.clearAuth() } answers {
            accessToken = ""
            refreshToken = ""
            subjectId = ""
        }
        return AuthManager(tokenStore)
    }

    private fun jwt(subject: String, nonce: String): String {
        fun segment(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        return "${segment("""{"alg":"none"}""")}." +
            "${segment("""{"sub":"$subject","nonce":"$nonce"}""")}.signature"
    }
}

private class DeferredMedicalAssistantRepository : MedicalAssistantRepository {
    val fetchStarted = CompletableDeferred<Unit>()
    val fetchResponse = CompletableDeferred<MedicalAssistantOverview>()
    var fetchOwner: AuthManager.AccountScopeSnapshot? = null

    override suspend fun fetchOverview(owner: AuthManager.AccountScopeSnapshot): MedicalAssistantOverview {
        fetchOwner = owner
        fetchStarted.complete(Unit)
        return fetchResponse.await()
    }

    override suspend fun generateOverview(owner: AuthManager.AccountScopeSnapshot): MedicalAssistantOverview =
        error("not used by this regression")
}
