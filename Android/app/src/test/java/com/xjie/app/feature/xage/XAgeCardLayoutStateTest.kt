package com.xjie.app.feature.xage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XAgeCardLayoutStateTest {
    @Test
    fun uncustomizedLayoutStartsWithTheFixedFourIosCards() {
        val state = XAgeCardLayoutState()

        assertEquals(
            listOf("hrv", "sleep", "glucose", "temp", "server-weight"),
            state.visibleIds(
                serverIds = listOf("server-weight"),
                candidateIds = listOf("steps", "temp", "glucose", "sleep", "hrv"),
            ),
        )
        assertEquals(
            listOf("hrv", "sleep", "glucose", "temp"),
            XAgeDefaultCardContract.orderedIds,
        )
    }

    @Test
    fun customizedEmptyLayoutStaysEmptyWhenServerAndCandidatesRefresh() {
        val customizedEmpty = XAgeCardLayoutState().withVisibleOrder(emptyList())

        assertTrue(customizedEmpty.isCustomized)
        assertEquals(
            emptyList<String>(),
            customizedEmpty.visibleIds(
                serverIds = listOf("server-weight"),
                candidateIds = XAgeDefaultCardContract.orderedIds,
            ),
        )
    }

    @Test
    fun customizedCardsKeepUserOrderAndRequireExplicitAddAfterRemoval() {
        val selected = XAgeCardLayoutState()
            .withVisibleOrder(listOf("sleep", "server-weight"))
            .removing("server-weight", isServer = true, visibleIds = listOf("sleep", "server-weight"))

        assertEquals(
            listOf("sleep"),
            selected.visibleIds(
                serverIds = listOf("server-weight", "server-hrv"),
                candidateIds = listOf("sleep", "steps"),
            ),
        )

        val restored = selected.adding(
            "server-weight",
            isServer = true,
            visibleIds = listOf("sleep"),
        )
        assertEquals(
            listOf("sleep", "server-weight"),
            restored.visibleIds(listOf("server-weight"), listOf("sleep")),
        )
    }

    @Test
    fun cardLayoutStorageIsStableAndAccountScoped() {
        val accountA = XAgeCardLayoutStore.preferencesName("account-a")
        val accountAAgain = XAgeCardLayoutStore.preferencesName("account-a")
        val accountB = XAgeCardLayoutStore.preferencesName("account-b")

        assertEquals(accountA, accountAAgain)
        assertNotEquals(accountA, accountB)
        assertTrue(accountA.startsWith("xage_card_layout_v2_"))
        assertTrue(!accountA.contains("account-a"))
    }
}
