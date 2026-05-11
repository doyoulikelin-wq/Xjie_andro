package com.xjie.app.core.network

import com.xjie.app.core.util.AppLogger
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * 统一包装 Retrofit 调用：
 *   • [HttpException] → 解析后端 detail，抛 [ApiException.HttpError]
 *   • 其他异常原样抛出（含网络 IOException）
 */
suspend inline fun <T> safeApiCall(json: Json, block: () -> T): T {
    try {
        return block()
    } catch (e: HttpException) {
        val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val msg = runCatching {
            json.decodeFromString(ErrorBody.serializer(), raw).detail?.message
        }.getOrNull() ?: raw.take(200).ifBlank { e.message() ?: "请求失败" }
        AppLogger.network.w("HTTP %d %s", e.code(), msg)
        throw ApiException.HttpError(e.code(), msg)
    }
}
