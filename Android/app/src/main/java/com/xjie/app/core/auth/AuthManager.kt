package com.xjie.app.core.auth

import com.xjie.app.core.model.UserInfo
import com.xjie.app.core.storage.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    ) {
        val isLoggedIn: Boolean get() = accessToken.isNotEmpty()
    }

    private val _state = MutableStateFlow(
        State(
            accessToken = tokenStore.accessToken,
            refreshToken = tokenStore.refreshToken,
            subjectId = tokenStore.subjectId,
        )
    )
    val state: StateFlow<State> = _state.asStateFlow()

    val accessToken: String get() = _state.value.accessToken
    val refreshToken: String get() = _state.value.refreshToken
    val isLoggedIn: Boolean get() = _state.value.isLoggedIn

    fun setAuth(accessToken: String, refreshToken: String = "") {
        tokenStore.accessToken = accessToken
        tokenStore.refreshToken = refreshToken
        _state.value = _state.value.copy(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    fun setSubject(sid: String) {
        tokenStore.subjectId = sid
        _state.value = _state.value.copy(subjectId = sid)
    }

    fun setUserInfo(info: UserInfo?) {
        _state.value = _state.value.copy(userInfo = info)
    }

    fun logout() {
        tokenStore.clear()
        _state.value = State()
    }
}
