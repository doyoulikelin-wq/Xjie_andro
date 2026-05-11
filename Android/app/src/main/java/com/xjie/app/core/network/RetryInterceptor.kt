package com.xjie.app.core.network

import com.xjie.app.core.util.ApiConstants
import com.xjie.app.core.util.AppLogger
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络重试 — 对应 iOS [APIService.swift] NET-02：
 * - 对 IO 异常（超时/连接断/无网） 与 5xx 做指数退避重试，最多 [ApiConstants.MAX_RETRIES] 次。
 */
@Singleton
class RetryInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        var attempt = 0
        var lastError: IOException? = null

        while (attempt <= ApiConstants.MAX_RETRIES) {
            try {
                val resp = chain.proceed(req)
                if (resp.code in 500..599 && attempt < ApiConstants.MAX_RETRIES) {
                    AppLogger.network.w("Retry %d for %s (HTTP %d)", attempt + 1, req.url.encodedPath, resp.code)
                    resp.close()
                    sleepBackoff(attempt)
                    attempt++
                    continue
                }
                return resp
            } catch (e: IOException) {
                lastError = e
                if (attempt >= ApiConstants.MAX_RETRIES) break
                AppLogger.network.w("Retry %d for %s (%s)", attempt + 1, req.url.encodedPath, e.message)
                sleepBackoff(attempt)
                attempt++
            }
        }
        // 给所有重试都失败一个空 body 兜底；正常分支会在 try 内 return
        throw lastError ?: IOException("Unknown network error")
    }

    private fun sleepBackoff(attempt: Int) {
        val ms = (1L shl attempt) * 1000L
        try { Thread.sleep(ms) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    @Suppress("unused")
    private fun emptyBody(): okhttp3.ResponseBody = "".toResponseBody(null)
}
