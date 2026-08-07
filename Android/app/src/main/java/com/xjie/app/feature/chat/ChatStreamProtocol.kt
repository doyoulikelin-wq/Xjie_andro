package com.xjie.app.feature.chat

import com.xjie.app.core.model.ChatStreamEnvelope
import com.xjie.app.core.model.ChatStreamEvent
import com.xjie.app.core.network.ApiException
import kotlinx.serialization.json.Json

internal object ChatStreamProtocol {
    const val MAX_LINE_BYTES: Long = 1024L * 1024L

    fun decodeLine(line: String, json: Json): ChatStreamEvent? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty()) return null
        if (payload.toByteArray(Charsets.UTF_8).size > MAX_LINE_BYTES) {
            throw ApiException.InvalidResponse
        }
        val envelope = runCatching {
            json.decodeFromString(ChatStreamEnvelope.serializer(), payload)
        }.getOrElse { throw ApiException.InvalidResponse }
        return when (envelope.type) {
            "route" -> envelope.route?.let(ChatStreamEvent::Route)
                ?: throw ApiException.InvalidResponse
            "progress" -> envelope.step?.takeIf(String::isNotBlank)?.let(ChatStreamEvent::Progress)
                ?: throw ApiException.InvalidResponse
            "token" -> envelope.delta?.let(ChatStreamEvent::Token)
                ?: throw ApiException.InvalidResponse
            "done" -> envelope.result?.let(ChatStreamEvent::Done)
                ?: throw ApiException.InvalidResponse
            "error" -> throw ApiException.HttpError(
                503,
                envelope.message?.takeIf(String::isNotBlank) ?: "这次回答没有完成，请重试",
            )
            else -> null
        }
    }

    fun isLegacyFallbackStatus(statusCode: Int): Boolean = statusCode == 404 || statusCode == 405
}
