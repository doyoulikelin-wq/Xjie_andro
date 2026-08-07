package com.xjie.app.feature.healthconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HealthConnectSyncPhase {
    Idle,
    PermissionRequired,
    Syncing,
    Empty,
    Success,
    Error,
}

data class HealthConnectPermissionRequest(
    val id: Long,
    val permissions: Set<String>,
)

data class HealthConnectSyncUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.Unavailable,
    val phase: HealthConnectSyncPhase = HealthConnectSyncPhase.Idle,
    val message: String = "正在检查 Health Connect",
    val permissionRequest: HealthConnectPermissionRequest? = null,
    val syncedCount: Int = 0,
    val inserted: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
    val lastSuccessfulSyncAtEpochMillis: Long? = null,
) {
    val hasSuccessfulSync: Boolean get() = lastSuccessfulSyncAtEpochMillis != null
}

object HealthConnectUiPolicy {
    fun blockedMessage(block: HealthConnectSyncBlock, detail: String? = null): String = when (block) {
        HealthConnectSyncBlock.LoggedOut -> "请先登录，再同步 Health Connect。"
        HealthConnectSyncBlock.ProviderUpdateRequired -> "请安装或更新 Health Connect 后重试。"
        HealthConnectSyncBlock.SdkUnavailable -> "当前设备不支持 Health Connect。"
        HealthConnectSyncBlock.AccountChanged -> "账号已切换，本次同步已安全取消，请重新开始。"
        HealthConnectSyncBlock.PermissionMissing -> "读取权限不完整，未读取或上传任何新数据。"
        HealthConnectSyncBlock.MetricUnavailable -> listOfNotNull(
            detail?.takeIf { it.isNotBlank() },
            "本次未上传任何数据。请更新系统与 Health Connect 后重试。",
        ).joinToString("。")
        HealthConnectSyncBlock.NoData -> "最近 30 天没有可同步的 Health Connect 数据。"
        HealthConnectSyncBlock.ServerRejectedSamples -> "服务器未完整接收数据，本次不显示同步成功。"
    }

    fun successMessage(result: HealthConnectSyncResult): String =
        "已同步 ${result.uploadedCount} 条 Health Connect 数据（新增 ${result.inserted}、更新 ${result.updated}、未变化 ${result.unchanged}）。"
}

@HiltViewModel
class HealthConnectSyncViewModel @Inject constructor(
    private val repository: HealthConnectSyncRepository,
    private val authManager: AuthManager,
    private val successfulSyncStore: HealthConnectSuccessfulSyncStore,
) : ViewModel() {
    private var observedAuthGeneration = authManager.generation
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<HealthConnectSyncUiState> = _state.asStateFlow()

    private var requestSequence = 0L
    private var lastConsumedRequest = 0L
    private var syncJob: Job? = null

    init {
        viewModelScope.launch {
            authManager.state.collect { authState ->
                if (authState.generation == observedAuthGeneration) return@collect
                observedAuthGeneration = authState.generation
                syncJob?.cancel()
                syncJob = null
                _state.value = initialState()
            }
        }
    }

    /** Call only from an explicit user action. It never fabricates samples or delayed success. */
    fun requestSync() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            when (val preparation = repository.prepare()) {
                HealthConnectPreparation.Ready -> runSync()
                is HealthConnectPreparation.PermissionRequired -> {
                    requestSequence += 1
                    _state.update {
                        it.copy(
                            availability = HealthConnectAvailability.Available,
                            phase = HealthConnectSyncPhase.PermissionRequired,
                            message = "允许只读访问后才能同步这 6 类健康数据。",
                            permissionRequest = HealthConnectPermissionRequest(
                                id = requestSequence,
                                permissions = preparation.permissions,
                            ),
                        )
                    }
                }
                is HealthConnectPreparation.Blocked -> showBlocked(preparation.reason)
            }
        }
    }

    /** The returned grant set is not trusted; the SDK permission controller is queried again. */
    fun onPermissionResult(@Suppress("UNUSED_PARAMETER") returnedGrants: Set<String>) {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            when (val preparation = repository.prepare()) {
                HealthConnectPreparation.Ready -> runSync()
                is HealthConnectPreparation.PermissionRequired -> showBlocked(
                    HealthConnectSyncBlock.PermissionMissing,
                )
                is HealthConnectPreparation.Blocked -> showBlocked(preparation.reason)
            }
        }
    }

    fun consumePermissionRequest(requestId: Long): Set<String>? {
        val request = _state.value.permissionRequest ?: return null
        if (request.id != requestId || requestId <= lastConsumedRequest) return null
        lastConsumedRequest = requestId
        return request.permissions
    }

    fun refreshAvailability() {
        val availability = repository.availability()
        _state.update {
            it.copy(
                availability = availability,
                phase = if (availability == HealthConnectAvailability.Available) {
                    HealthConnectSyncPhase.Idle
                } else {
                    HealthConnectSyncPhase.Error
                },
                message = availabilityMessage(availability),
                permissionRequest = null,
            )
        }
    }

    private suspend fun runSync() {
        val owner = authManager.captureAccountScope()
        if (owner == null) {
            showBlocked(HealthConnectSyncBlock.LoggedOut)
            return
        }
        _state.update {
            it.copy(
                availability = HealthConnectAvailability.Available,
                phase = HealthConnectSyncPhase.Syncing,
                message = "正在从 Health Connect 读取并同步…",
                permissionRequest = null,
                syncedCount = 0,
            )
        }
        runCatching { repository.sync() }
            .onSuccess { result ->
                if (!authManager.isCurrent(owner)) {
                    showBlocked(HealthConnectSyncBlock.AccountChanged)
                    return@onSuccess
                }
                val syncedAt = System.currentTimeMillis()
                val persisted = successfulSyncStore.record(owner, syncedAt)
                _state.update {
                    it.copy(
                        phase = HealthConnectSyncPhase.Success,
                        message = HealthConnectUiPolicy.successMessage(result),
                        syncedCount = result.uploadedCount,
                        inserted = result.inserted,
                        updated = result.updated,
                        unchanged = result.unchanged,
                        lastSuccessfulSyncAtEpochMillis = syncedAt.takeIf { persisted },
                    )
                }
            }
            .onFailure { error ->
                val blocked = error as? HealthConnectSyncBlockedException
                if (blocked != null) {
                    showBlocked(blocked.reason, blocked.message)
                } else {
                    _state.update {
                        it.copy(
                            phase = HealthConnectSyncPhase.Error,
                            message = error.localizedMessage ?: "Health Connect 同步失败，请重试。",
                            syncedCount = 0,
                        )
                    }
                }
            }
    }

    private fun showBlocked(reason: HealthConnectSyncBlock, detail: String? = null) {
        _state.update {
            it.copy(
                phase = if (reason == HealthConnectSyncBlock.NoData) {
                    HealthConnectSyncPhase.Empty
                } else {
                    HealthConnectSyncPhase.Error
                },
                message = HealthConnectUiPolicy.blockedMessage(reason, detail),
                permissionRequest = null,
                syncedCount = 0,
            )
        }
    }

    private companion object {
        fun availabilityMessage(availability: HealthConnectAvailability): String = when (availability) {
            HealthConnectAvailability.Available -> "可读取 Health Connect；同步前会再次检查权限。"
            HealthConnectAvailability.ProviderUpdateRequired -> "请安装或更新 Health Connect 后使用。"
            HealthConnectAvailability.Unavailable -> "当前设备不支持 Health Connect。"
        }
    }

    private fun initialState(): HealthConnectSyncUiState {
        val availability = repository.availability()
        val owner = authManager.captureAccountScope()
        return HealthConnectSyncUiState(
            availability = availability,
            message = availabilityMessage(availability),
            lastSuccessfulSyncAtEpochMillis = owner?.let(successfulSyncStore::restore),
        )
    }
}
