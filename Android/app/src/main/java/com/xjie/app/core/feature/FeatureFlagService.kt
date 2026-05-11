package com.xjie.app.core.feature

import com.xjie.app.core.network.api.FeatureFlagsApi
import com.xjie.app.core.network.safeApiCall
import com.xjie.app.core.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对应 iOS [FeatureFlagService.swift] —— 缓存 /api/feature-flags 结果，
 * 暴露 flags / skills 两个 StateFlow，并支持手动 refresh。
 */
@Singleton
class FeatureFlagService @Inject constructor(
    private val api: FeatureFlagsApi,
    private val json: Json,
) {
    private val _flags = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val flags: StateFlow<Map<String, Boolean>> = _flags.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var loaded = false

    fun isEnabled(key: String): Boolean = _flags.value[key] == true

    suspend fun ensureLoaded() {
        if (loaded) return
        refresh()
    }

    suspend fun refresh() {
        mutex.withLock {
            try {
                val resp = safeApiCall(json) { api.list() }
                _flags.value = resp.flags
                loaded = true
            } catch (t: Throwable) {
                AppLogger.network.w(t, "feature flags refresh failed")
            }
        }
    }
}
