package com.xjie.app.feature.xage

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.auth.TestAuthManagerFactory
import com.xjie.app.core.model.ElderlyCheckinList
import com.xjie.app.core.model.HealthPlanListResponse
import com.xjie.app.core.model.UserInfo
import com.xjie.app.core.network.AuthInterceptor
import com.xjie.app.core.network.api.AgentApi
import com.xjie.app.core.network.api.ChatApi
import com.xjie.app.core.network.api.DashboardApi
import com.xjie.app.core.network.api.ElderlyApi
import com.xjie.app.core.network.api.HealthDataApi
import com.xjie.app.core.network.api.HealthPlanApi
import com.xjie.app.core.network.api.UserApi
import com.xjie.app.feature.chat.ChatRepository
import com.xjie.app.feature.elderly.ElderlyRepository
import com.xjie.app.feature.healthdata.HealthDataRepository
import com.xjie.app.feature.healthplan.HealthPlanRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
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
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Tag

@OptIn(ExperimentalCoroutinesApi::class)
class XAgeServerSyncAccountIsolationTest {
    @Test
    fun allSnapshotReadsUseOneOwnerCapturedBeforeTheFirstSuspension() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = loggedInAuth()
            val expectedOwner = requireNotNull(auth.captureAccountScope())
            val owners = mutableListOf<AuthManager.AccountScopeSnapshot>()
            val dependencies = dependencies()

            coEvery { dependencies.userApi.meForOwner(any()) } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                throw IOException("optional fixture")
            }
            coEvery { dependencies.dashboardApi.healthForOwner(any()) } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                throw IOException("optional fixture")
            }
            coEvery { dependencies.agentApi.todayForOwner(any()) } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                throw IOException("optional fixture")
            }
            coEvery {
                dependencies.healthDataRepository.summary(any<AuthManager.AccountScopeSnapshot>())
            } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                null
            }
            coEvery {
                dependencies.healthDataRepository.documents(
                    any<AuthManager.AccountScopeSnapshot>(),
                    any(),
                )
            } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                emptyList()
            }
            coEvery {
                dependencies.healthDataRepository.listIndicators(any<AuthManager.AccountScopeSnapshot>())
            } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                emptyList()
            }
            coEvery {
                dependencies.healthDataRepository.watchedIndicators(any<AuthManager.AccountScopeSnapshot>())
            } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                emptyList()
            }
            coEvery {
                dependencies.chatRepository.listConversations(20, 0, any())
            } coAnswers {
                owners += thirdArg<AuthManager.AccountScopeSnapshot>()
                emptyList()
            }
            coEvery {
                dependencies.healthPlanRepository.plans(any<AuthManager.AccountScopeSnapshot>())
            } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                HealthPlanListResponse()
            }
            coEvery {
                dependencies.elderlyRepository.list(
                    any<AuthManager.AccountScopeSnapshot>(),
                    30,
                    100,
                )
            } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                ElderlyCheckinList()
            }
            coEvery {
                dependencies.healthDataRepository.trends(
                    any<AuthManager.AccountScopeSnapshot>(),
                    any(),
                )
            } coAnswers {
                owners += firstArg<AuthManager.AccountScopeSnapshot>()
                emptyList()
            }

            dependencies.viewModel(auth)
            advanceUntilIdle()

            assertEquals(12, owners.size)
            assertTrue(owners.all { it == expectedOwner })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun staleAtoBtoAOwnerIsRejectedBeforeAnyRetrofitRequestDispatch() = runTest {
        val requiredTaggedReads = mapOf(
            UserApi::class.java to setOf("meForOwner"),
            DashboardApi::class.java to setOf("healthForOwner"),
            AgentApi::class.java to setOf("todayForOwner"),
            HealthDataApi::class.java to setOf(
                "summaryForOwner",
                "documentsForOwner",
                "indicatorsForOwner",
                "watchedForOwner",
                "trendForOwner",
            ),
            ChatApi::class.java to setOf("listConversations"),
            HealthPlanApi::class.java to setOf("listPlansForOwner"),
            ElderlyApi::class.java to setOf("listForOwner"),
        )
        requiredTaggedReads.forEach { (api, names) ->
            val methods = api.declaredMethods.filter { it.name in names }
            assertEquals(names, methods.map { it.name }.toSet())
            assertTrue(
                "$api snapshot reads must carry AccountScopeSnapshot @Tag",
                methods.all { method ->
                    method.parameterAnnotations.flatten().any { it is Tag } &&
                        method.parameterTypes.any {
                            it == AuthManager.AccountScopeSnapshot::class.java
                        }
                },
            )
        }

        val auth = loggedInAuth()
        val staleOwner = requireNotNull(auth.captureAccountScope())
        auth.establishSession(jwt("account-b", "middle"), "refresh-b", "subject-b")
        auth.establishSession(jwt("account-a", "second"), "refresh-a2", "subject-a")
        val server = MockWebServer()
        server.start()
        try {
            val api = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(
                    OkHttpClient.Builder()
                        .addInterceptor(AuthInterceptor(auth))
                        .build(),
                )
                .addConverterFactory(
                    Json { ignoreUnknownKeys = true }
                        .asConverterFactory("application/json".toMediaType()),
                )
                .build()
                .create(UserApi::class.java)

            val error = runCatching { api.meForOwner(staleOwner) }.exceptionOrNull()

            assertTrue(error is IOException)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun lateAtoBtoASnapshotNeverCommitsIntoTheReplacementSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = loggedInAuth()
            val staleOwner = requireNotNull(auth.captureAccountScope())
            val dependencies = dependenciesWithEmptyResults()
            val lateUser = CompletableDeferred<UserInfo>()
            coEvery { dependencies.userApi.meForOwner(any()) } coAnswers { lateUser.await() }
            val viewModel = dependencies.viewModel(auth)
            runCurrent()

            auth.establishSession(jwt("account-b", "middle"), "refresh-b", "subject-b")
            auth.establishSession(jwt("account-a", "second"), "refresh-a2", "subject-a")
            lateUser.complete(mockk(relaxed = true))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.snapshot.isLoaded)
            assertFalse(auth.isCurrent(staleOwner))
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun loggedInAuth(): AuthManager = TestAuthManagerFactory.create().also { auth ->
        auth.establishSession(jwt("account-a", "first"), "refresh-a", "subject-a")
    }

    private fun dependenciesWithEmptyResults(): Dependencies = dependencies().also { dependencies ->
        coEvery { dependencies.dashboardApi.healthForOwner(any()) } throws IOException("optional fixture")
        coEvery { dependencies.agentApi.todayForOwner(any()) } throws IOException("optional fixture")
        coEvery { dependencies.healthDataRepository.summary(any()) } returns null
        coEvery {
            dependencies.healthDataRepository.documents(
                any<AuthManager.AccountScopeSnapshot>(),
                any(),
            )
        } returns emptyList()
        coEvery {
            dependencies.healthDataRepository.listIndicators(any<AuthManager.AccountScopeSnapshot>())
        } returns emptyList()
        coEvery {
            dependencies.healthDataRepository.watchedIndicators(any<AuthManager.AccountScopeSnapshot>())
        } returns emptyList()
        coEvery { dependencies.chatRepository.listConversations(20, 0, any()) } returns emptyList()
        coEvery { dependencies.healthPlanRepository.plans(any()) } returns HealthPlanListResponse()
        coEvery {
            dependencies.elderlyRepository.list(
                any<AuthManager.AccountScopeSnapshot>(),
                30,
                100,
            )
        } returns ElderlyCheckinList()
        coEvery {
            dependencies.healthDataRepository.trends(
                any<AuthManager.AccountScopeSnapshot>(),
                any(),
            )
        } returns emptyList()
    }

    private fun dependencies() = Dependencies(
        userApi = mockk(),
        dashboardApi = mockk(),
        agentApi = mockk(),
        healthDataRepository = mockk(),
        chatRepository = mockk(),
        healthPlanRepository = mockk(),
        elderlyRepository = mockk(),
    )

    private data class Dependencies(
        val userApi: UserApi,
        val dashboardApi: DashboardApi,
        val agentApi: AgentApi,
        val healthDataRepository: HealthDataRepository,
        val chatRepository: ChatRepository,
        val healthPlanRepository: HealthPlanRepository,
        val elderlyRepository: ElderlyRepository,
    ) {
        fun viewModel(auth: AuthManager) = XAgeServerSyncViewModel(
            authManager = auth,
            userApi = userApi,
            dashboardApi = dashboardApi,
            agentApi = agentApi,
            healthDataRepository = healthDataRepository,
            chatRepository = chatRepository,
            healthPlanRepository = healthPlanRepository,
            elderlyRepository = elderlyRepository,
        )
    }

    private fun jwt(subject: String, nonce: String): String {
        fun segment(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        return "${segment("""{"alg":"none"}""")}." +
            "${segment("""{"sub":"$subject","nonce":"$nonce"}""")}.signature"
    }
}
