package com.xjie.app.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 后端错误体：`{ "detail": "msg" }` 或 `{ "detail": { "message": "..." } }` */
@Serializable
data class ErrorBody(
    @SerialName("detail") val detail: ErrorDetail? = null,
)

@Serializable(with = ErrorDetailSerializer::class)
sealed class ErrorDetail {
    data class StringDetail(val value: String) : ErrorDetail()
    data class ObjectDetail(val msg: String?) : ErrorDetail()

    val message: String?
        get() = when (this) {
            is StringDetail -> value
            is ObjectDetail -> msg
        }
}

private object ErrorDetailSerializer : KSerializer<ErrorDetail> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ErrorDetail")

    override fun deserialize(decoder: Decoder): ErrorDetail {
        val input = decoder as? JsonDecoder
            ?: error("ErrorDetail requires Json decoder")
        val element = input.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> ErrorDetail.StringDetail(
                if (element.isString) element.content else element.toString()
            )
            is JsonObject -> ErrorDetail.ObjectDetail(
                (element["message"] as? JsonPrimitive)?.contentOrNull
            )
            else -> ErrorDetail.StringDetail(element.toString())
        }
    }

    override fun serialize(encoder: Encoder, value: ErrorDetail) {
        encoder.encodeString(value.message.orEmpty())
    }
}

/** 自定义网络异常 — 对应 iOS APIError */
sealed class ApiException(message: String) : RuntimeException(message) {
    data object NotLoggedIn : ApiException("未登录")
    class HttpError(val code: Int, msg: String) : ApiException(msg)
    class InvalidUrl(val path: String) : ApiException("无效的请求地址: $path")
    data object InvalidResponse : ApiException("服务器响应异常")
}
