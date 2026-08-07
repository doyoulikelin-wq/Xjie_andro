package com.xjie.app.core.auth

import com.xjie.app.core.storage.TokenStore
import io.mockk.every
import io.mockk.mockk

object TestAuthManagerFactory {
    fun create(): AuthManager {
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
        every { tokenStore.authGeneration = any() } answers {
            val next = firstArg<Long>()
            require(next >= generation) { "test store generation cannot move backwards" }
            generation = next
        }
        every { tokenStore.clearAuth() } answers {
            accessToken = ""
            refreshToken = ""
            subjectId = ""
        }
        return AuthManager(tokenStore)
    }
}
