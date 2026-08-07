package com.xjie.app.feature.healthconnect

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.auth.TestAuthManagerFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthConnectSyncPresentationTest {
    @Test
    fun successfulSyncHidesHomeCardAcrossRecreationForSameAccount() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val auth = TestAuthManagerFactory.create().apply {
                establishSession(jwt("account-a"), subjectId = "subject-a")
            }
            val owner = requireNotNull(auth.captureAccountScope())
            val backend = MemoryBackend()
            val store = HealthConnectSuccessfulSyncStore.forTesting(backend)
            val repository = mockk<HealthConnectSyncRepository>()
            every { repository.availability() } returns HealthConnectAvailability.Available
            coEvery { repository.prepare() } returns HealthConnectPreparation.Ready
            coEvery { repository.sync(any()) } returns HealthConnectSyncResult(
                readCount = 2,
                uploadedCount = 2,
                inserted = 1,
                updated = 0,
                unchanged = 1,
            )

            val first = HealthConnectSyncViewModel(repository, auth, store)
            first.requestSync()
            advanceUntilIdle()

            assertTrue(first.state.value.hasSuccessfulSync)
            assertTrue(requireNotNull(store.restore(owner)) > 0L)

            val recreated = HealthConnectSyncViewModel(
                repository,
                auth,
                HealthConnectSuccessfulSyncStore.forTesting(backend),
            )
            val home = HealthConnectCardPresentationPolicy.presentation(
                HealthConnectCardSurface.CompactHome,
                recreated.state.value,
            )
            assertTrue(recreated.state.value.hasSuccessfulSync)
            assertFalse(home.isVisible)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun accountSwitchDoesNotReuseHealthConnectSuccess() {
        val backend = MemoryBackend()
        val firstProcess = HealthConnectSuccessfulSyncStore.forTesting(backend)
        val firstA = owner("account-a", "subject-a", generation = 4L)
        val accountB = owner("account-b", "subject-b", generation = 5L)
        val secondA = owner("account-a", "subject-a", generation = 6L)

        assertTrue(firstProcess.record(firstA, 1_786_032_000_000L))

        val recreated = HealthConnectSuccessfulSyncStore.forTesting(backend)
        assertEquals(1_786_032_000_000L, recreated.restore(firstA))
        assertNull(recreated.restore(accountB))
        assertNull(recreated.restore(secondA))
    }

    @Test
    fun managerRetainsFullHealthConnectStatus() {
        val state = HealthConnectSyncUiState(
            availability = HealthConnectAvailability.Available,
            phase = HealthConnectSyncPhase.Success,
            message = "已同步 3 条 Health Connect 数据",
            syncedCount = 3,
            lastSuccessfulSyncAtEpochMillis = 1_786_032_000_000L,
        )

        val home = HealthConnectCardPresentationPolicy.presentation(
            HealthConnectCardSurface.CompactHome,
            state,
        )
        val manager = HealthConnectCardPresentationPolicy.presentation(
            HealthConnectCardSurface.FullManager,
            state,
        )

        assertFalse(home.isVisible)
        assertFalse(home.showsDetailedStatus)
        assertTrue(home.detailBadges.isEmpty())
        assertTrue(manager.isVisible)
        assertTrue(manager.showsDetailedStatus)
        assertEquals("Health Connect 同步", manager.title)
        assertEquals("已同步 3 条 Health Connect 数据", manager.subtitle)
        assertEquals(listOf("可用", "只读授权", "3 项已同步"), manager.detailBadges)
        assertEquals("同步", manager.buttonTitle)
    }

    private fun owner(
        accountScope: String,
        subjectId: String,
        generation: Long,
    ) = AuthManager.AccountScopeSnapshot(accountScope, subjectId, generation)

    private fun jwt(subject: String): String {
        fun segment(raw: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        return "${segment("""{"alg":"none"}""")}.${segment("""{"sub":"$subject"}""")}.test"
    }

    private class MemoryBackend : HealthConnectSuccessfulSyncBackend {
        private var receipt: HealthConnectSuccessfulSyncReceipt? = null

        override fun read(): HealthConnectSuccessfulSyncReceipt? = receipt

        override fun write(receipt: HealthConnectSuccessfulSyncReceipt): Boolean {
            this.receipt = receipt
            return true
        }
    }
}
