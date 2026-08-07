package com.xjie.app.core.auth

import com.xjie.app.core.model.UserInfo
import com.xjie.app.core.storage.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** 对应 iOS [AuthManager.swift] —— 全局登录态。 */
@Singleton
class AuthManager @Inject constructor(
    private val tokenStore: TokenStore,
) {
    data class State(
        val accessToken: String = "",
        val refreshToken: String = "",
        val subjectId: String = "",
        val userInfo: UserInfo? = null,
        val generation: Long = 0L,
    ) {
        val isLoggedIn: Boolean get() = accessToken.isNotEmpty()
    }

    /** Immutable owner captured before account-bound asynchronous work starts. */
    data class AccountScopeSnapshot(
        val accountScope: String,
        val subjectId: String,
        val generation: Long,
    )

    private val _state = MutableStateFlow(
        State(
            accessToken = tokenStore.accessToken,
            refreshToken = tokenStore.refreshToken,
            subjectId = tokenStore.subjectId,
            generation = tokenStore.authGeneration,
        )
    )
    val state: StateFlow<State> = _state.asStateFlow()

    val accessToken: String get() = _state.value.accessToken
    val refreshToken: String get() = _state.value.refreshToken
    val isLoggedIn: Boolean get() = _state.value.isLoggedIn
    val generation: Long get() = _state.value.generation
    val accountScope: String? get() = accountScope(_state.value)

    /**
     * Updates credentials for a token refresh. A refresh proven to carry the same JWT `sub`
     * keeps the current generation; every unproven/new account invalidates all old snapshots.
     */
    @Synchronized
    fun setAuth(accessToken: String, refreshToken: String = "") {
        val current = _state.value
        val incomingScope = accountScopeFromJwt(accessToken)
        val sameAccount = current.isLoggedIn &&
            incomingScope != null &&
            incomingScope == accountScope(current)
        val nextGeneration = if (sameAccount) current.generation else nextGeneration(current.generation)
        val nextSubject = if (sameAccount) current.subjectId else ""
        val nextUserInfo = if (sameAccount) current.userInfo else null

        tokenStore.accessToken = accessToken
        tokenStore.refreshToken = refreshToken
        tokenStore.subjectId = nextSubject
        tokenStore.authGeneration = nextGeneration
        _state.value = State(
            accessToken = accessToken,
            refreshToken = refreshToken,
            subjectId = nextSubject,
            userInfo = nextUserInfo,
            generation = nextGeneration,
        )
    }

    /**
     * Atomically installs a login result and its selected health subject. Phone login must pass
     * an empty subject so a previous subject can never leak into the new account.
     */
    @Synchronized
    fun establishSession(
        accessToken: String,
        refreshToken: String = "",
        subjectId: String = "",
    ) {
        require(accessToken.isNotBlank()) { "a login session requires an access token" }
        val normalizedSubject = subjectId.trim()
        val nextGeneration = nextGeneration(_state.value.generation)
        tokenStore.accessToken = accessToken
        tokenStore.refreshToken = refreshToken
        tokenStore.subjectId = normalizedSubject
        tokenStore.authGeneration = nextGeneration
        _state.value = State(
            accessToken = accessToken,
            refreshToken = refreshToken,
            subjectId = normalizedSubject,
            userInfo = null,
            generation = nextGeneration,
        )
    }

    @Synchronized
    fun setSubject(sid: String) {
        val normalized = sid.trim()
        val current = _state.value
        if (current.subjectId == normalized) return
        val nextGeneration = nextGeneration(current.generation)
        tokenStore.subjectId = normalized
        tokenStore.authGeneration = nextGeneration
        _state.value = current.copy(subjectId = normalized, generation = nextGeneration)
    }

    @Synchronized
    fun setUserInfo(info: UserInfo?): Boolean {
        val current = _state.value
        val incomingId = info?.id?.trim().orEmpty()
        val jwtScope = accountScopeFromJwt(current.accessToken)
        if (jwtScope != null && incomingId.isNotEmpty() && opaqueAccountScope(incomingId) != jwtScope) {
            return false
        }

        val currentScope = accountScope(current)
        val candidate = current.copy(userInfo = info)
        val candidateScope = accountScope(candidate)
        if (currentScope != null && currentScope != candidateScope) {
            val nextGeneration = nextGeneration(current.generation)
            tokenStore.subjectId = ""
            tokenStore.authGeneration = nextGeneration
            _state.value = candidate.copy(subjectId = "", generation = nextGeneration)
        } else {
            _state.value = candidate
        }
        return true
    }

    /** Returns null unless the signed account identity is available and the user is logged in. */
    fun captureAccountScope(): AccountScopeSnapshot? {
        val current = _state.value
        val scope = accountScope(current) ?: return null
        return AccountScopeSnapshot(
            accountScope = scope,
            subjectId = current.subjectId,
            generation = current.generation,
        )
    }

    /** Revalidates account, subject and generation; A -> B -> A therefore fails closed. */
    fun isCurrent(snapshot: AccountScopeSnapshot): Boolean {
        val current = _state.value
        return isCurrent(current, snapshot)
    }

    /** Returns the credential only from the exact account/subject generation in the snapshot. */
    fun accessTokenIfCurrent(snapshot: AccountScopeSnapshot): String? {
        val current = _state.value
        return current.accessToken.takeIf { isCurrent(current, snapshot) }
    }

    private fun isCurrent(current: State, snapshot: AccountScopeSnapshot): Boolean =
        current.isLoggedIn &&
            current.generation == snapshot.generation &&
            current.subjectId == snapshot.subjectId &&
            accountScope(current) == snapshot.accountScope

    @Synchronized
    fun logout() {
        val nextGeneration = nextGeneration(_state.value.generation)
        tokenStore.authGeneration = nextGeneration
        tokenStore.clearAuth()
        _state.value = State(generation = nextGeneration)
    }

    /** Atomically clears credentials only when [snapshot] still owns the active session. */
    @Synchronized
    fun logoutIfCurrent(snapshot: AccountScopeSnapshot): Boolean {
        if (!isCurrent(_state.value, snapshot)) return false
        logout()
        return true
    }

    private fun accountScope(state: State): String? {
        if (!state.isLoggedIn) return null
        accountScopeFromJwt(state.accessToken)?.let { return it }
        val userId = state.userInfo?.id?.trim().orEmpty()
        return userId.takeIf(String::isNotEmpty)?.let(::opaqueAccountScope)
    }

    private fun nextGeneration(current: Long): Long {
        check(current < Long.MAX_VALUE) { "auth generation exhausted" }
        return current + 1L
    }

    companion object {
        /** Stable opaque account owner derived only from a well-formed JWT string `sub`. */
        fun accountScopeFromJwt(token: String): String? {
            val parts = token.split('.', limit = 4)
            if (parts.size != 3) return null
            val payload = runCatching {
                val segment = parts[1]
                val padded = segment + "=".repeat((4 - segment.length % 4) % 4)
                Base64.getUrlDecoder().decode(padded).toString(StandardCharsets.UTF_8)
            }.getOrNull() ?: return null
            val subject = runCatching {
                Json.parseToJsonElement(payload)
                    .jsonObject["sub"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.trim()
            }.getOrNull().orEmpty()
            return subject.takeIf(String::isNotEmpty)?.let(::opaqueAccountScope)
        }

        private fun opaqueAccountScope(subject: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(subject.toByteArray(StandardCharsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
            return "account-$digest"
        }
    }
}
