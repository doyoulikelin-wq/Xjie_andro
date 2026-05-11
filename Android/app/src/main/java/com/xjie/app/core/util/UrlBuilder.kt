package com.xjie.app.core.util

import okhttp3.HttpUrl.Companion.toHttpUrl

/** 对应 iOS [Utils.swift] URLBuilder.path */
object UrlBuilder {
    /** 在路径后追加 query，自动 URL-encode。仅用于 path 拼接，不解析完整 URL。 */
    fun pathWithQuery(basePath: String, params: Map<String, String?>): String {
        val pairs = params.filterValues { !it.isNullOrEmpty() }
        if (pairs.isEmpty()) return basePath
        val builder = StringBuilder(basePath).append('?')
        pairs.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) builder.append('&')
            builder.append(java.net.URLEncoder.encode(k, "UTF-8"))
                .append('=')
                .append(java.net.URLEncoder.encode(v.orEmpty(), "UTF-8"))
        }
        return builder.toString()
    }

    fun pathWithQuery(basePath: String, vararg params: Pair<String, String?>): String =
        pathWithQuery(basePath, params.toMap())
}
