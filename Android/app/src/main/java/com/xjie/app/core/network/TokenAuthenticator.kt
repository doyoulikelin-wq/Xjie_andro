package com.xjie.app.core.network

import com.xjie.app.BuildConfig
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.AuthResponse
import com.xjie.app.core.model.RefreshTokenBody
import com.xjie.app.core.util.AppLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.Authenticator as OkAuthenticator
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 401 自动刷新 token，对应 iOS [APIService.swift] ERR-02：
 * - 多个并发 401 共享同一次 refresh（Mutex 协调）
 * - refresh 仍失败 → logout
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val auth: AuthManager,
    /** Provider 避免与 ApiClient 形成循环依赖：刷新走独立的简单 client。 */
    @RefreshClient private val refreshClientProvider: Provider<OkHttpClient>,
    private val json: Json,
) : OkAuthenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null  // 防止无限循环

        val triedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()
            .orEmpty()

        return runBlocking {
            refreshMutex.withLock {
                // 期间另一线程可能已刷新成功
                val current = auth.accessToken
                if (current.isNotEmpty() && current != triedToken) {
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $current")
                        .build()
                }

                val rt = auth.refreshToken
                if (rt.isEmpty()) {
                    auth.logout()
                    return@withLock null
                }

                val refreshed = doRefresh(rt)
                if (refreshed == null) {
                    auth.logout()
                    return@withLock null
                }
                auth.setAuth(refreshed.access_token, refreshed.refresh_token ?: rt)
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${refreshed.access_token}")
                    .build()
            }
        }
    }

    private fun doRefresh(refreshToken: String): AuthResponse? {
        return try {
            val client = refreshClientProvider.get()
            val body = json.encodeToString(RefreshTokenBody.serializer(), RefreshTokenBody(refreshToken))
            val req = Request.Builder()
                .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/auth/refresh")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLogger.network.w("Refresh failed: HTTP ${resp.code}")
                    return null
                }
                val raw = resp.body?.string().orEmpty()
                json.decodeFromString(AuthResponse.serializer(), raw)
            }
        } catch (e: Exception) {
            AppLogger.network.e(e, "Token refresh error")
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
