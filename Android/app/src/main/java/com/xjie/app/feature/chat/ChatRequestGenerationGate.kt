package com.xjie.app.feature.chat

import com.xjie.app.core.auth.AuthManager

/** Exact request identity used to reject late stream events and A -> B -> A account replays. */
data class ChatRequestIdentity internal constructor(
    val sequence: Long,
    val owner: AuthManager.AccountScopeSnapshot,
    val threadId: String?,
    val clientMessageId: String,
)

class ChatRequestGenerationGate {
    private var nextSequence = 0L
    private var active: ChatRequestIdentity? = null

    @Synchronized
    fun begin(
        owner: AuthManager.AccountScopeSnapshot,
        threadId: String?,
        clientMessageId: String,
    ): ChatRequestIdentity {
        check(nextSequence < Long.MAX_VALUE) { "chat request generation exhausted" }
        val token = ChatRequestIdentity(
            sequence = ++nextSequence,
            owner = owner,
            threadId = threadId,
            clientMessageId = clientMessageId,
        )
        active = token
        return token
    }

    @Synchronized
    fun accepts(
        token: ChatRequestIdentity,
        currentOwner: AuthManager.AccountScopeSnapshot?,
        currentThreadId: String?,
    ): Boolean = active == token && currentOwner == token.owner && currentThreadId == token.threadId

    @Synchronized
    fun complete(token: ChatRequestIdentity) {
        if (active == token) active = null
    }

    @Synchronized
    fun invalidate() {
        active = null
    }
}
