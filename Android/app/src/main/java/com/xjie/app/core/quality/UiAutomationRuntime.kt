package com.xjie.app.core.quality

import com.xjie.app.BuildConfig
import com.xjie.app.core.auth.AuthManager
import okhttp3.OkHttpClient

/**
 * Release-safe bridge to a Debug-source-set-only deterministic UI transport.
 *
 * The production artifact contains no fixture or interceptor implementation. Reflection is used
 * only after an exact Debug launch token is accepted; a missing Debug implementation fails closed.
 */
object UiAutomationRuntime {
    const val INTENT_EXTRA = "xjie.ui.mode"
    const val INTENT_AUTHENTICATED_EXTRA = "xjie.ui.authenticated"
    const val EXACT_DEBUG_TOKEN = "xjie-ui-deterministic-v1"
    private const val DEBUG_BRIDGE =
        "com.xjie.app.core.quality.DebugUiAutomationTransport"

    @Volatile
    private var active = false

    fun activateIfExplicit(rawValue: String?) {
        if (!BuildConfig.DEBUG || rawValue != EXACT_DEBUG_TOKEN) return
        invokeDebug("activate")
        active = true
    }

    fun bootstrapAuth(authManager: AuthManager, authenticated: Boolean = true) {
        if (!isActive) return
        invokeDebug(
            "bootstrapAuth",
            arrayOf<Class<*>>(AuthManager::class.java, java.lang.Boolean.TYPE),
            arrayOf(authManager, authenticated),
        )
    }

    val isActive: Boolean
        get() = BuildConfig.DEBUG && active

    fun installOn(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (!isActive) return builder
        return invokeDebug(
            name = "installOn",
            parameterTypes = arrayOf(OkHttpClient.Builder::class.java),
            arguments = arrayOf(builder),
        ) as? OkHttpClient.Builder
            ?: error("Debug UI transport returned an invalid client builder")
    }

    private fun invokeDebug(
        name: String,
        parameterTypes: Array<Class<*>> = emptyArray(),
        arguments: Array<Any> = emptyArray(),
    ): Any? {
        check(BuildConfig.DEBUG) { "UI automation transport is unavailable in Release" }
        return runCatching {
            Class.forName(DEBUG_BRIDGE)
                .getMethod(name, *parameterTypes)
                .invoke(null, *arguments)
        }.getOrElse { error ->
            throw IllegalStateException("Debug UI automation transport failed closed", error)
        }
    }
}
