package com.xjie.app.feature.healthdata

import com.xjie.app.core.auth.AuthManager
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** App-lifetime report dashboard and upload state shared by Health Data and attachment entry points. */
@Singleton
class HealthReportDashboardController internal constructor(
    private val remote: HealthReportDashboardRemote,
    private val uploadCoordinator: HealthReportUploadCoordinator,
    private val authManager: AuthManager,
    private val today: () -> LocalDate,
    private val uploadStateGate: Mutex,
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    @Inject
    constructor(
        remote: HealthReportUploadNetworkRepository,
        uploadCoordinator: HealthReportUploadCoordinator,
        authManager: AuthManager,
    ) : this(
        remote = remote,
        uploadCoordinator = uploadCoordinator,
        authManager = authManager,
        today = LocalDate::now,
        uploadStateGate = Mutex(),
    )

    private val refreshGeneration = AtomicLong(0)
    private val acknowledgementRetries = ConcurrentHashMap.newKeySet<AcknowledgementRetryKey>()
    private val _state = MutableStateFlow(HealthReportDashboardState.initial())
    val state: StateFlow<HealthReportDashboardState> = _state.asStateFlow()

    fun visibleState(): HealthReportDashboardState = _state.value.takeIf {
        it.belongsTo(authManager)
    } ?: HealthReportDashboardState.initial()

    suspend fun refresh() {
        val owner = captureOwnerOrPublishError() ?: return
        val subjectUserId = owner.numericSubject()
            ?: return publishInvalidOwner(owner)
        val generation = refreshGeneration.incrementAndGet()
        val base = _state.value.takeIf { it.owner == owner }
            ?: HealthReportDashboardState(owner = owner)
        _state.value = base.copy(loading = true, readError = null, detailWarning = null)

        try {
            val end = today()
            val history = remote.fetchHistory(
                owner = owner,
                subjectUserId = subjectUserId,
                dateFrom = end.minusYears(1).toString(),
                dateTo = end.toString(),
            )
            ensureCurrent(owner, subjectUserId)
            validateHistory(history)
            if (generation != refreshGeneration.get()) return

            var warning: String? = null
            val runtime = history.items.firstOrNull()?.let { latest ->
                try {
                    remote.fetchRuntime(owner, latest.workflowId, subjectUserId).also { result ->
                        ensureCurrent(owner, subjectUserId)
                        if (
                            result.workflowId != latest.workflowId ||
                            result.subjectUserId != subjectUserId ||
                            result.workflowVersion <= 0
                        ) {
                            fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: HealthReportUploadCoordinatorException) {
                    throw error
                } catch (_: Exception) {
                    warning = "报告详情暂时无法读取，下拉即可重试。"
                    null
                }
            }
            ensureCurrent(owner, subjectUserId)
            if (generation != refreshGeneration.get()) return
            val current = _state.value.takeIf { it.owner == owner } ?: base
            _state.value = current.copy(
                loading = false,
                items = history.items,
                runtime = runtime,
                readError = null,
                detailWarning = warning,
            )
            scheduleLocalOriginalAcknowledgements(
                owner = owner,
                subjectUserId = subjectUserId,
                items = history.items,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (!authManager.isCurrent(owner) || generation != refreshGeneration.get()) return
            val current = _state.value.takeIf { it.owner == owner } ?: base
            _state.value = current.copy(
                loading = false,
                readError = "健康报告暂时无法读取，请稍后重试。",
            )
        }
    }

    suspend fun upload(
        inputs: List<HealthReportUploadAssetInput>,
        source: HealthReportUploadSource,
        title: String? = null,
    ): HealthReportUploadOutcome = withUploadStateLease {
        val owner = authManager.captureAccountScope()
            ?: fail(HealthReportUploadCoordinatorError.InvalidOwner)
        val subjectUserId = owner.numericSubject()
            ?: fail(HealthReportUploadCoordinatorError.InvalidOwner)
        val before = _state.value
        val base = before.takeIf { it.owner == owner } ?: HealthReportDashboardState(owner = owner)
        _state.value = base.copy(
            upload = HealthReportDashboardUploadState.Submitting,
            readError = null,
        )
        try {
            val outcome = uploadCoordinator.upload(
                inputs = inputs,
                source = source,
                subjectUserId = subjectUserId,
                title = title,
            )
            ensureCurrent(owner, subjectUserId)
            val current = _state.value.takeIf { it.owner == owner } ?: base
            when (outcome) {
                is HealthReportUploadOutcome.RecoveryRequired -> {
                    _state.value = current.copy(
                        upload = HealthReportDashboardUploadState.RecoveryRequired(outcome.recovery),
                    )
                }
                is HealthReportUploadOutcome.Completed -> {
                    _state.value = current.copy(
                        upload = HealthReportDashboardUploadState.Completed(
                            runtime = outcome.runtime,
                            duplicate = outcome.duplicate,
                            acknowledgementDeferred =
                                outcome.acknowledgement == HealthReportLocalOriginalAcknowledgementStatus.Deferred,
                        ),
                    )
                    refresh()
                }
            }
            outcome
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error is HealthReportUploadCoordinatorException &&
                error.error == HealthReportUploadCoordinatorError.Busy
            ) {
                _state.value = before
                throw error
            }
            publishUploadFailure(owner, base, error)
            throw error
        }
    }

    suspend fun recoverAsset(
        input: HealthReportUploadAssetInput,
        assetIndex: Int,
    ): HealthReportUploadOutcome = withUploadStateLease {
        val owner = _state.value.owner
            ?.takeIf(authManager::isCurrent)
            ?: fail(HealthReportUploadCoordinatorError.InvalidOwner)
        val base = _state.value
        _state.value = base.copy(upload = HealthReportDashboardUploadState.Submitting)
        try {
            val outcome = uploadCoordinator.recoverAsset(input, assetIndex)
            val subjectUserId = owner.numericSubject()
                ?: fail(HealthReportUploadCoordinatorError.InvalidOwner)
            ensureCurrent(owner, subjectUserId)
            val current = _state.value.takeIf { it.owner == owner } ?: base
            when (outcome) {
                is HealthReportUploadOutcome.RecoveryRequired -> {
                    _state.value = current.copy(
                        upload = HealthReportDashboardUploadState.RecoveryRequired(outcome.recovery),
                    )
                }
                is HealthReportUploadOutcome.Completed -> {
                    _state.value = current.copy(
                        upload = HealthReportDashboardUploadState.Completed(
                            runtime = outcome.runtime,
                            duplicate = outcome.duplicate,
                            acknowledgementDeferred =
                                outcome.acknowledgement == HealthReportLocalOriginalAcknowledgementStatus.Deferred,
                        ),
                    )
                    refresh()
                }
            }
            outcome
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            publishUploadFailure(owner, base, error)
            throw error
        }
    }

    suspend fun abandonRecovery() = withUploadStateLease {
        val owner = _state.value.owner
            ?.takeIf(authManager::isCurrent)
            ?: fail(HealthReportUploadCoordinatorError.InvalidOwner)
        uploadCoordinator.abandonRecovery()
        if (authManager.isCurrent(owner)) {
            _state.value = _state.value.copy(upload = HealthReportDashboardUploadState.Idle)
        }
    }

    private fun captureOwnerOrPublishError(): AuthManager.AccountScopeSnapshot? {
        val owner = authManager.captureAccountScope()
        if (owner == null) {
            _state.value = HealthReportDashboardState(
                readError = "当前账号无法读取健康报告，请重新登录后重试。",
            )
        }
        return owner
    }

    private fun publishInvalidOwner(owner: AuthManager.AccountScopeSnapshot) {
        if (authManager.isCurrent(owner)) {
            _state.value = HealthReportDashboardState(
                owner = owner,
                readError = "当前报告所属用户信息不完整，请重新登录后重试。",
            )
        }
    }

    private fun publishUploadFailure(
        owner: AuthManager.AccountScopeSnapshot,
        fallback: HealthReportDashboardState,
        error: Exception,
    ) {
        if (!authManager.isCurrent(owner)) return
        val message = when (error) {
            is HealthReportLocalOriginalStoreException -> error.message
            is HealthReportUploadCoordinatorException -> error.message
            else -> null
        } ?: "报告上传未完成，请检查网络后重试。"
        val current = _state.value.takeIf { it.owner == owner } ?: fallback
        _state.value = current.copy(upload = HealthReportDashboardUploadState.Failed(message))
    }

    private fun validateHistory(history: HealthReportHistoryResponse) {
        val ids = mutableSetOf<Long>()
        if (history.items.any { item ->
                item.workflowId <= 0L ||
                    !ids.add(item.workflowId) ||
                    item.status.isBlank() ||
                    item.reportType.isBlank() ||
                    item.title.isBlank() ||
                    item.createdAt.isBlank()
            }
        ) {
            fail(HealthReportUploadCoordinatorError.InvalidServerResponse)
        }
    }

    private fun ensureCurrent(owner: AuthManager.AccountScopeSnapshot, subjectUserId: Long) {
        if (owner.numericSubject() != subjectUserId || !authManager.isCurrent(owner)) {
            fail(HealthReportUploadCoordinatorError.OwnerChanged)
        }
    }

    private fun scheduleLocalOriginalAcknowledgements(
        owner: AuthManager.AccountScopeSnapshot,
        subjectUserId: Long,
        items: List<HealthReportHistoryItem>,
    ) {
        val upload = _state.value.upload as? HealthReportDashboardUploadState.Completed
        items.forEach { item ->
            if (
                upload?.runtime?.workflowId == item.workflowId &&
                !upload.acknowledgementDeferred
            ) {
                return@forEach
            }
            val key = AcknowledgementRetryKey(
                accountScope = owner.accountScope,
                subjectUserId = subjectUserId,
                generation = owner.generation,
                workflowId = item.workflowId,
            )
            if (!acknowledgementRetries.add(key)) return@forEach
            backgroundScope.launch {
                try {
                    if (!authManager.isCurrent(owner)) return@launch
                    uploadCoordinator.retryLocalOriginalAcknowledgement(
                        workflowId = item.workflowId,
                        subjectUserId = subjectUserId,
                        expectedOwner = owner,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Missing local proof and transient ACK failures never affect dashboard content.
                } finally {
                    acknowledgementRetries.remove(key)
                }
            }
        }
    }

    private suspend fun <T> withUploadStateLease(block: suspend () -> T): T {
        val token = Any()
        if (!uploadStateGate.tryLock(token)) fail(HealthReportUploadCoordinatorError.Busy)
        return try {
            block()
        } finally {
            uploadStateGate.unlock(token)
        }
    }

    private fun AuthManager.AccountScopeSnapshot.numericSubject(): Long? =
        subjectId.trim().toLongOrNull()?.takeIf { it > 0L }

    private fun fail(error: HealthReportUploadCoordinatorError): Nothing {
        throw HealthReportUploadCoordinatorException(error)
    }

    private data class AcknowledgementRetryKey(
        val accountScope: String,
        val subjectUserId: Long,
        val generation: Long,
        val workflowId: Long,
    )
}
