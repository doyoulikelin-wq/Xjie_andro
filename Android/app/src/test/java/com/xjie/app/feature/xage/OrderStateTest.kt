package com.xjie.app.feature.xage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderStateTest {
    @Test
    fun unknownAndDuplicateValuesAreRemovedAndNewActionsAppendStably() {
        val stored = XAgeQuickActionOrderState(
            orderedIds = listOf("reports", "unknown", "reports", "meals"),
        )

        assertEquals(
            listOf("reports", "meals", "weight", "medications", "medical"),
            stored.resolvedIds(),
        )
    }

    @Test
    fun movingActionsPreservesExactlyOneOfEveryRegisteredItem() {
        val moved = XAgeQuickActionOrderState().moving("medical", 0)

        assertEquals(
            listOf("medical", "meals", "weight", "reports", "medications"),
            moved.resolvedIds(),
        )
        assertEquals(5, moved.resolvedIds().distinct().size)
        assertEquals(moved, moved.moving("missing", 2))
    }

    @Test
    fun preferenceKeysArePartitionedByOpaqueAccountScopeAndRejectUnscopedWrites() {
        val accountA = "account-" + "a".repeat(64)
        val accountB = "account-" + "b".repeat(64)

        assertNotEquals(
            XAgeQuickActionOrderStore.preferenceKey(accountA),
            XAgeQuickActionOrderStore.preferenceKey(accountB),
        )
        assertNull(XAgeQuickActionOrderStore.preferenceKey(null))
        assertNull(XAgeQuickActionOrderStore.preferenceKey("user@example.com"))
    }
}
