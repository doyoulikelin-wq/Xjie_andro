package com.xjie.app.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 文件 JSON 离线缓存 — 对应 iOS [OfflineCacheManager.swift] */
@Singleton
class OfflineCache @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val json: Json,
) {
    private val dir: File by lazy {
        File(ctx.cacheDir, "offline_cache").apply { mkdirs() }
    }

    suspend fun <T> save(key: String, value: T, serializer: KSerializer<T>) =
        withContext(Dispatchers.IO) {
            File(dir, "$key.json").writeText(json.encodeToString(serializer, value))
        }

    suspend fun <T> load(key: String, serializer: KSerializer<T>): T? =
        withContext(Dispatchers.IO) {
            val f = File(dir, "$key.json")
            if (!f.exists()) return@withContext null
            runCatching { json.decodeFromString(serializer, f.readText()) }.getOrNull()
        }

    suspend fun clear(key: String) = withContext(Dispatchers.IO) {
        File(dir, "$key.json").delete()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
    }
}
