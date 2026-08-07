package com.xjie.app.core.auth

import com.xjie.app.core.model.AuthResponse
import com.xjie.app.core.model.UserInfo
import com.xjie.app.core.network.AuthInterceptor
import com.xjie.app.core.network.api.AuthApi
import com.xjie.app.core.network.api.HealthPlanApi
import com.xjie.app.core.network.api.UserApi
import com.xjie.app.feature.healthconnect.AuthManagerHealthConnectSessionSource
import com.xjie.app.feature.login.LoginRepository
import com.xjie.app.feature.login.SignupPostSessionCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthGenerationIsolationTest {
    @Test
    fun tokenRefreshKeepsGenerationWhileSubjectChangeAndLogoutAdvanceIt() {
        val auth = authManager()
        auth.establishSession(jwt("account-a", "login"), "refresh-1", "subject-a")
        val loginGeneration = auth.generation

        auth.setAuth(jwt("account-a", "refresh"), "refresh-2")
        assertEquals(loginGeneration, auth.generation)
        assertEquals("subject-a", auth.state.value.subjectId)

        auth.setSubject("subject-b")
        assertEquals(loginGeneration + 1L, auth.generation)
        auth.logout()
        assertEquals(loginGeneration + 2L, auth.generation)
        assertFalse(auth.isLoggedIn)
        assertEquals("", auth.state.value.subjectId)
    }

    @Test
    fun accountAtoBtoARejectsTheFirstAccountsLateSnapshot() {
        val auth = authManager()
        auth.establishSession(jwt("account-a", "first"), "refresh-a", "subject-a")
        val firstAccountSnapshot = requireNotNull(auth.captureAccountScope())

        auth.establishSession(jwt("account-b", "middle"), "refresh-b", "subject-b")
        val accountBSnapshot = requireNotNull(auth.captureAccountScope())
        auth.establishSession(jwt("account-a", "second"), "refresh-a2", "subject-a")
        val currentAccountSnapshot = requireNotNull(auth.captureAccountScope())

        assertFalse(auth.isCurrent(firstAccountSnapshot))
        assertFalse(auth.isCurrent(accountBSnapshot))
        assertTrue(auth.isCurrent(currentAccountSnapshot))
        assertEquals(firstAccountSnapshot.accountScope, currentAccountSnapshot.accountScope)
        assertNotEquals(firstAccountSnapshot.generation, currentAccountSnapshot.generation)
    }

    @Test
    fun healthConnectSessionAlsoRejectsAnAccountAtoBtoAReplay() {
        val auth = authManager()
        auth.establishSession(jwt("account-a", "first"), "refresh-a", "subject-a")
        val source = AuthManagerHealthConnectSessionSource(auth)
        val staleSession = requireNotNull(source.capture())

        auth.establishSession(jwt("account-b", "middle"), "refresh-b", "subject-b")
        auth.establishSession(jwt("account-a", "second"), "refresh-a2", "subject-a")

        assertFalse(source.isCurrent(staleSession))
        assertTrue(source.isCurrent(requireNotNull(source.capture())))
    }

    @Test
    fun legacyUserInfoScopeCannotDisappearAndReappearAtTheSameGeneration() {
        val auth = authManager()
        auth.establishSession("legacy-token", "refresh", "subject-a")
        assertTrue(auth.setUserInfo(UserInfo(id = "account-a")))
        val staleSnapshot = requireNotNull(auth.captureAccountScope())

        assertTrue(auth.setUserInfo(null))
        assertTrue(auth.setUserInfo(UserInfo(id = "account-a")))

        assertFalse(auth.isCurrent(staleSnapshot))
        assertTrue(auth.isCurrent(requireNotNull(auth.captureAccountScope())))
    }

    @Test
    fun ownerBoundNetworkRequestNeverDispatchesForAStaleAtoBtoASnapshot() {
        val auth = authManager()
        auth.establishSession(jwt("account-a", "first"), "refresh-a", "subject-a")
        val staleOwner = requireNotNull(auth.captureAccountScope())
        auth.establishSession(jwt("account-b", "middle"), "refresh-b", "subject-b")
        auth.establishSession(jwt("account-a", "second"), "refresh-a2", "subject-a")
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(auth))
                .build()
            val request = Request.Builder()
                .url(server.url("/api/chat"))
                .tag(AuthManager.AccountScopeSnapshot::class.java, staleOwner)
                .build()

            val error = runCatching { client.newCall(request).execute().close() }.exceptionOrNull()

            assertTrue(error is java.io.IOException)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun phoneLoginReplacesThePreviousSubjectAndNeverSilentlyGrantsAiConsent() = runTest {
        val auth = authManager()
        auth.establishSession(jwt("account-a", "old"), "refresh-a", "old-subject")
        val oldGeneration = auth.generation
        val authApi = mockk<AuthApi>()
        val userApi = mockk<UserApi>(relaxed = true)
        coEvery { authApi.login(any()) } returns
            AuthResponse(jwt("account-b", "phone-login"), "refresh-b")
        val repository = LoginRepository(
            authApi = authApi,
            authManager = auth,
            signupPostSessionCoordinator = mockk<SignupPostSessionCoordinator>(relaxed = true),
            json = Json { ignoreUnknownKeys = true },
        )

        repository.loginOrSignupPhone(
            phone = "13800000000",
            username = "ignored",
            password = "password",
            signup = false,
        )

        assertEquals("", auth.state.value.subjectId)
        assertEquals(oldGeneration + 1L, auth.generation)
        assertEquals(
            AuthManager.accountScopeFromJwt(jwt("account-b", "other-refresh")),
            auth.accountScope,
        )
        coVerify(exactly = 0) { userApi.updateConsent(any()) }
    }

    private fun authManager(): AuthManager {
        return TestAuthManagerFactory.create()
    }

    private fun jwt(subject: String, nonce: String): String {
        fun segment(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        return "${segment("""{"alg":"none"}""")}." +
            "${segment("""{"sub":"$subject","nonce":"$nonce"}""")}.signature"
    }
}
