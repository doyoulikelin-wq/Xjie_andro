package com.xjie.app.feature.chat

import com.xjie.app.core.auth.AuthManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestGenerationIsolationTest {
    private val ownerA1 = owner("account-a", "subject-a", generation = 1)

    @Test
    fun newChatOrNewRequestInvalidatesEveryLateEventFromTheOldRequest() {
        val gate = ChatRequestGenerationGate()
        val old = gate.begin(ownerA1, threadId = "thread-old", clientMessageId = "client-old")

        gate.invalidate()
        assertFalse(gate.accepts(old, ownerA1, "thread-old"))

        val current = gate.begin(ownerA1, threadId = null, clientMessageId = "client-new")
        assertFalse(gate.accepts(old, ownerA1, "thread-old"))
        assertTrue(gate.accepts(current, ownerA1, null))
    }

    @Test
    fun accountGenerationAndThreadMustBothMatchExactlyIncludingAtoBtoA() {
        val gate = ChatRequestGenerationGate()
        val request = gate.begin(ownerA1, threadId = "thread-1", clientMessageId = "client-1")

        assertFalse(gate.accepts(request, owner("account-b", "subject-b", 2), "thread-1"))
        assertFalse(gate.accepts(request, owner("account-a", "subject-a", 3), "thread-1"))
        assertFalse(gate.accepts(request, ownerA1, "thread-2"))
        assertTrue(gate.accepts(request, ownerA1, "thread-1"))

        gate.complete(request)
        assertFalse(gate.accepts(request, ownerA1, "thread-1"))
    }

    private fun owner(account: String, subject: String, generation: Long) =
        AuthManager.AccountScopeSnapshot(account, subject, generation)
}
