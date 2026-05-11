package com.xjie.app.core.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 厂商 push 提供者抽象。具体厂商 SDK 通过依赖隔离，便于按设备制造商动态选择。 */
interface PushProvider {
    /** 唯一标识，匹配后端 `device_tokens.provider` 字段。 */
    val name: String

    /** 是否在当前设备/进程可用（厂商 SDK 缺失或机型不匹配时返回 false）。 */
    fun isAvailable(): Boolean

    /** 发起 token 注册，结果通过 [tokenFlow] 异步推送。 */
    fun register()

    /** 注销 token（如调用厂商 SDK 的 unregister）。 */
    fun unregister()

    /** 最近一次的 token，可能为 null。 */
    val tokenFlow: StateFlow<String?>
}

/** 默认未实现的 stub provider —— 真正接入厂商 SDK 时替换。 */
abstract class StubPushProvider(override val name: String) : PushProvider {
    protected val _token = MutableStateFlow<String?>(null)
    override val tokenFlow: StateFlow<String?> = _token
    override fun isAvailable(): Boolean = false
    override fun register() { /* no-op until SDK wired */ }
    override fun unregister() { /* no-op */ }
}

class HmsPushProvider : StubPushProvider("hms")
class MiPushProvider : StubPushProvider("mipush")
class OppoPushProvider : StubPushProvider("oppo")
class VivoPushProvider : StubPushProvider("vivo")
class MeizuPushProvider : StubPushProvider("meizu")
class HonorPushProvider : StubPushProvider("honor")
