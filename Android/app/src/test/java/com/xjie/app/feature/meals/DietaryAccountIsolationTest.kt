package com.xjie.app.feature.meals

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.DietaryDailySummaryStatus
import com.xjie.app.core.model.DietaryDashboardResponse
import com.xjie.app.core.model.DietaryRecentResponse
import com.xjie.app.core.network.AuthInterceptor
import com.xjie.app.core.network.api.MealsApi
import com.xjie.app.core.storage.TokenStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import java.time.Instant
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Tag

class DietaryAccountIsolationTest {
    @Test
    fun accountSwitchBeforeDispatchNeverSendsDietaryMutationWithNewCredential() {
        val mutationNames = setOf(
            "createDietaryDraft",
            "createDietaryPhotoDraft",
            "retryDietaryRecognition",
            "confirmDietaryDraft",
            "updateDietaryRecord",
            "deleteDietaryRecord",
            "reuseDietaryRecord",
            "completeDietaryDay",
        )
        val mutations = MealsApi::class.java.declaredMethods.filter { it.name in mutationNames }
        assertEquals(mutationNames, mutations.map { it.name }.toSet())
        assertTrue(mutations.all { method -> method.parameterAnnotations.flatten().any { it is Tag } })

        assertStaleOwnerDoesNotDispatch("/api/dietary-records/drafts")
    }

    @Test
    fun accountSwitchBeforeDispatchNeverSendsDietaryReadWithNewCredential() {
        val readNames = setOf(
            "dietaryDashboard",
            "recentDietaryRecords",
            "dietaryDailySummary",
        )
        val reads = MealsApi::class.java.declaredMethods.filter { it.name in readNames }
        assertEquals(readNames, reads.map { it.name }.toSet())
        assertTrue(reads.all { method -> method.parameterAnnotations.flatten().any { it is Tag } })

        assertStaleOwnerDoesNotDispatch("/api/dietary-records/dashboard")
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun accountAtoBtoARejectsTheFirstAccountsLateDashboard() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = authManager()
            auth.establishSession(jwt("account-a", "first"), "refresh-a", "subject-a")
            val response = CompletableDeferred<DietaryDashboardResponse>()
            val repository = mockk<MealsDataSource>(relaxed = true)
            coEvery { repository.dashboard(any(), any(), any()) } coAnswers { response.await() }
            val viewModel = MealsViewModel(repository, auth)
            viewModel.setDeterministicProvidersForTesting(
                now = { Instant.parse("2026-07-15T04:00:00Z") },
                eventId = { "deterministic-event" },
            )

            viewModel.fetchData()
            runCurrent()
            auth.establishSession(jwt("account-b", "middle"), "refresh-b", "subject-b")
            auth.establishSession(jwt("account-a", "second"), "refresh-a2", "subject-a")
            runCurrent()

            response.complete(dashboard("2026-07-15"))
            advanceUntilIdle()

            assertNull(viewModel.state.value.dashboard)
            assertEquals(DietaryLoadState.Error, viewModel.state.value.loadState)
            assertTrue(viewModel.state.value.error?.contains("变化") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun dashboardRecentAndDailySummaryUseTheSamePreSuspensionOwner() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = authManager()
            auth.establishSession(jwt("account-a", "first"), "refresh-a", "subject-a")
            val expectedOwner = requireNotNull(auth.captureAccountScope())
            val owners = mutableListOf<AuthManager.AccountScopeSnapshot>()
            val repository = mockk<MealsDataSource>()
            coEvery { repository.dashboard(any(), any(), any()) } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                dashboard("2026-07-15")
            }
            coEvery { repository.recent(any(), any(), any()) } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                DietaryRecentResponse(subject_user_id = 7L)
            }
            coEvery { repository.dailySummary(any()) } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                DietaryDailySummaryStatus(status = "empty", target_date = "2026-07-14")
            }
            val viewModel = MealsViewModel(repository, auth)
            viewModel.setDeterministicProvidersForTesting(
                now = { Instant.parse("2026-07-15T04:00:00Z") },
                eventId = { "deterministic-event" },
            )

            viewModel.fetchData()
            advanceUntilIdle()

            assertEquals(3, owners.size)
            assertTrue(owners.all { it == expectedOwner })
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun dashboard(date: String) = DietaryDashboardResponse(
        subject_user_id = 7L,
        selected_date = date,
        is_today = true,
        recorded_meal_count = 0,
        pending_count = 0,
        streak_days = 0,
        day_state = "open",
        displayed_summary_date = "2026-07-14",
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
