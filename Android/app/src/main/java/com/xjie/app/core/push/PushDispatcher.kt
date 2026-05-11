package com.xjie.app.core.push

import android.os.Build
import com.xjie.app.core.network.api.PushApi
import com.xjie.app.core.network.api.RegisterDeviceTokenBody
import com.xjie.app.core.network.safeApiCall
import com.xjie.app.core.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 根据 [Build.MANUFACTURER] 选择厂商 PushProvider，并把 token 推送到后端。
 * 若没有可用厂商，则不进行注册（CN 市场无 GCM/FCM 通道）。
 */
@Singleton
class PushDispatcher @Inject constructor(
    private val api: PushApi,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val providers: List<PushProvider> = listOf(
        HmsPushProvider(),
        HonorPushProvider(),
        MiPushProvider(),
        OppoPushProvider(),
        VivoPushProvider(),
        MeizuPushProvider(),
    )

    private val active: PushProvider? by lazy {
        val mf = Build.MANUFACTURER.lowercase()
        when {
            mf.contains("huawei") -> providers.first { it.name == "hms" }
            mf.contains("honor") -> providers.first { it.name == "honor" }
            mf.contains("xiaomi") || mf.contains("redmi") -> providers.first { it.name == "mipush" }
            mf.contains("oppo") || mf.contains("realme") || mf.contains("oneplus") -> providers.first { it.name == "oppo" }
            mf.contains("vivo") || mf.contains("iqoo") -> providers.first { it.name == "vivo" }
            mf.contains("meizu") -> providers.first { it.name == "meizu" }
            else -> null
        }?.takeIf { it.isAvailable() }
    }

    fun start() {
        val provider = active
        if (provider == null) {
            AppLogger.network.i("no push provider available for manufacturer=%s", Build.MANUFACTURER)
            return
        }
        AppLogger.network.i("starting push provider %s", provider.name)
        scope.launch {
            provider.tokenFlow
                .filterNotNull()
                .collect { token -> postToken(provider.name, token) }
        }
        provider.register()
    }

    fun stop() {
        active?.tokenFlow?.value?.let { token ->
            scope.launch { runCatching { safeApiCall(json) { api.unregister(token) } } }
        }
        active?.unregister()
    }

    private suspend fun postToken(provider: String, token: String) {
        runCatching {
            safeApiCall(json) {
                api.register(
                    RegisterDeviceTokenBody(
                        token = token,
                        platform = "android",
                        provider = provider,
                        extras = "${Build.MANUFACTURER}/${Build.MODEL}",
                    )
                )
            }
        }.onFailure { AppLogger.network.w(it, "push token register failed") }
    }
}
