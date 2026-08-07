package com.xjie.app.feature.healthconnect

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.DeviceIndicatorSyncBody
import com.xjie.app.core.model.DeviceIndicatorSyncResponse
import com.xjie.app.core.network.api.DeviceIndicatorSyncApi
import com.xjie.app.core.network.safeApiCall
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class AuthManagerHealthConnectSessionSource @Inject constructor(
    private val authManager: AuthManager,
) : HealthConnectSessionSource {
    override fun capture(): HealthConnectAccountSession? {
        val owner = authManager.captureAccountScope() ?: return null
        val state = authManager.state.value
        if (!state.isLoggedIn || state.accessToken.isBlank()) return null
        if (!authManager.isCurrent(owner)) return null
        return HealthConnectAccountSession(
            bearerToken = state.accessToken,
            subjectId = state.subjectId,
            accountOwner = owner,
        )
    }

    override fun isCurrent(session: HealthConnectAccountSession): Boolean {
        session.accountOwner?.let { return authManager.isCurrent(it) }
        val state = authManager.state.value
        return state.isLoggedIn &&
            state.accessToken == session.bearerToken &&
            state.subjectId == session.subjectId
    }
}

@Singleton
class RetrofitDeviceIndicatorSyncSink @Inject constructor(
    private val api: DeviceIndicatorSyncApi,
    private val json: Json,
) : DeviceIndicatorSyncSink {
    override suspend fun upload(
        session: HealthConnectAccountSession,
        body: DeviceIndicatorSyncBody,
    ): DeviceIndicatorSyncResponse = safeApiCall(json) {
        api.sync("Bearer ${session.bearerToken}", body)
    }
}

@Singleton
class HealthConnectSyncRepository @Inject constructor(
    private val gateway: AndroidHealthConnectGateway,
    private val sink: RetrofitDeviceIndicatorSyncSink,
    private val sessionSource: AuthManagerHealthConnectSessionSource,
) {
    private val engine = HealthConnectSyncEngine(
        dataSource = gateway,
        sink = sink,
        sessionSource = sessionSource,
        clock = Clock.systemUTC(),
    )

    val requiredReadPermissions: Set<String> get() = gateway.requiredReadPermissions

    fun availability(): HealthConnectAvailability = engine.availability()
    suspend fun prepare(): HealthConnectPreparation = engine.prepare()
    suspend fun sync(days: Long = HealthConnectReadWindow.MAX_DAYS): HealthConnectSyncResult =
        engine.sync(days)
}
