package com.xjie.app.feature.healthdata

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.HealthDocument
import com.xjie.app.core.model.HealthReportInterpretation
import com.xjie.app.core.model.HealthReportReview
import com.xjie.app.core.auth.AuthManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID

data class HealthDataUiState(
    val loading: Boolean = false,
    val summary: String = "",
    val summaryUpdatedAt: String = "",
    val generating: Boolean = false,
    val progress: Float = 0f,
    val stage: String = "",
    val recordCount: Int = 0,
    val examCount: Int = 0,
    val uploading: Boolean = false,
    val uploadStage: String = "",
    val uploadDocType: String = "record",
    /**
     * 上传任务已交后台处理，可关闭横幅但任务仍在进行。
     * 用于「AI 正在后台识别…」提示条。
     */
    val backgroundTaskHint: String? = null,
    val latestReport: HealthDocument? = null,
    val reportDashboard: HealthReportDashboardState = HealthReportDashboardState.initial(),
    val toast: String? = null,
    val error: String? = null,
)

@HiltViewModel
class HealthDataViewModel @Inject constructor(
    private val repo: HealthDataRepository,
    private val reportDashboardController: HealthReportDashboardController,
    private val authManager: AuthManager,
) : ViewModel() {
    private val _state = MutableStateFlow(HealthDataUiState())
    val state: StateFlow<HealthDataUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(reportDashboardController.state, authManager.state) { dashboard, _ ->
                dashboard.takeIf { it.belongsTo(authManager) }
                    ?: HealthReportDashboardState.initial()
            }.collect { dashboard ->
                _state.update { current ->
                    current.copy(
                        reportDashboard = dashboard,
                        uploading = dashboard.upload is HealthReportDashboardUploadState.Submitting,
                        uploadStage = when (dashboard.upload) {
                            HealthReportDashboardUploadState.Submitting ->
                                "正在安全保存并上传报告原件…"
                            else -> current.uploadStage.takeUnless { current.uploading }.orEmpty()
                        },
                    )
                }
            }
        }
    }

    fun fetchAll() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        val dashboardRefresh = launch { reportDashboardController.refresh() }
        val s = repo.summary()
        val records = runCatching { repo.documents("record") }.getOrDefault(emptyList())
        val exams = runCatching { repo.documents("exam") }.getOrDefault(emptyList())
        _state.update {
            it.copy(
                loading = false,
                summary = s?.summary_text ?: "",
                summaryUpdatedAt = s?.updated_at ?: "",
                recordCount = records.size,
                examCount = exams.size,
            )
        }
        dashboardRefresh.join()
    }

    fun setUploadDocType(t: String) = _state.update { it.copy(uploadDocType = t) }

    fun uploadFile(
        uri: Uri,
        filename: String,
        source: HealthReportUploadSource = HealthReportUploadSource.Document,
    ) = viewModelScope.launch {
        if (_state.value.uploadDocType == "exam") {
            uploadReport(uri, filename, source)
            return@launch
        }
        _state.update {
            it.copy(
                uploading = true,
                uploadStage = "正在上传文件…",
                backgroundTaskHint = null,
            )
        }
        runCatching { repo.uploadDocument(uri, filename, _state.value.uploadDocType) }
            .onSuccess { doc ->
                val initialStage = ReportTrustPresentation.stage(doc)
                if (initialStage == ReportTrustPresentation.Stage.Recognizing ||
                    initialStage == ReportTrustPresentation.Stage.Uploading
                ) {
                    _state.update {
                        it.copy(
                            uploading = false,
                            uploadStage = "",
                            latestReport = doc,
                            toast = ReportTrustPresentation.title(doc),
                            backgroundTaskHint = ReportTrustPresentation.nextStep(doc),
                        )
                    }
                    // Polling is only a presentation refresh. A timeout is not a processing failure.
                    val refreshed = pollDoc(doc.id)
                    if (refreshed == null) {
                        _state.update {
                            it.copy(
                                backgroundTaskHint = "报告仍在后台识别，可稍后到报告页继续查看。确认前不会进入趋势、画像、AI 或评分。",
                            )
                        }
                    } else {
                        val refreshedStage = ReportTrustPresentation.stage(refreshed)
                        _state.update {
                            it.copy(
                                latestReport = refreshed,
                                backgroundTaskHint = null,
                                toast = ReportTrustPresentation.title(refreshed),
                                error = if (refreshedStage == ReportTrustPresentation.Stage.Failed) {
                                    "报告识别失败，请确认 PDF/图片清晰完整后重新上传。失败结果不会进入健康数据。"
                                } else {
                                    it.error
                                },
                            )
                        }
                    }
                    fetchAll()
                } else {
                    _state.update {
                        it.copy(
                            uploading = false,
                            uploadStage = "",
                            latestReport = doc,
                            toast = ReportTrustPresentation.title(doc),
                            backgroundTaskHint = ReportTrustPresentation.nextStep(doc),
                        )
                    }
                    fetchAll()
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        uploading = false,
                        uploadStage = "",
                        backgroundTaskHint = null,
                        error = e.message ?: "上传失败",
                    )
                }
            }
    }

    /** Shared future entry for chat/camera/gallery after they have already captured exact bytes. */
    fun uploadReportAsset(
        input: HealthReportUploadAssetInput,
        source: HealthReportUploadSource,
    ) = viewModelScope.launch {
        submitReport(input, source)
    }

    fun recoverReportFile(
        uri: Uri,
        filename: String,
        assetIndex: Int,
    ) = viewModelScope.launch {
        _state.update {
            it.copy(
                uploading = true,
                uploadStage = "正在读取第 $assetIndex 页替换原件…",
                backgroundTaskHint = null,
                error = null,
            )
        }
        val input = runCatching { repo.readReportUploadAsset(uri, filename) }
            .getOrElse { error ->
                _state.update {
                    it.copy(
                        uploading = false,
                        uploadStage = "",
                        error = error.message ?: "无法读取替换原件，请重新选择文件。",
                    )
                }
                return@launch
            }
        runCatching { reportDashboardController.recoverAsset(input, assetIndex) }
            .onSuccess(::publishReportOutcome)
            .onFailure(::publishReportFailure)
    }

    fun abandonReportRecovery() = viewModelScope.launch {
        runCatching { reportDashboardController.abandonRecovery() }
            .onSuccess {
                _state.update {
                    it.copy(
                        uploading = false,
                        uploadStage = "",
                        backgroundTaskHint = null,
                        toast = "已放弃本次报告上传。",
                    )
                }
            }
            .onFailure(::publishReportFailure)
    }

    private suspend fun uploadReport(
        uri: Uri,
        filename: String,
        source: HealthReportUploadSource,
    ) {
        _state.update {
            it.copy(
                uploading = true,
                uploadStage = "正在读取报告原件…",
                backgroundTaskHint = null,
                error = null,
            )
        }
        val input = runCatching { repo.readReportUploadAsset(uri, filename) }
            .getOrElse { error ->
                _state.update {
                    it.copy(
                        uploading = false,
                        uploadStage = "",
                        error = error.message ?: "无法读取报告原件，请重新选择文件。",
                    )
                }
                return
            }
        submitReport(input, source)
    }

    private suspend fun submitReport(
        input: HealthReportUploadAssetInput,
        source: HealthReportUploadSource,
    ) {
        runCatching { reportDashboardController.upload(listOf(input), source) }
            .onSuccess(::publishReportOutcome)
            .onFailure(::publishReportFailure)
    }

    private fun publishReportOutcome(outcome: HealthReportUploadOutcome) {
        when (outcome) {
            is HealthReportUploadOutcome.Completed -> _state.update {
                it.copy(
                    uploading = false,
                    uploadStage = "",
                    toast = if (outcome.duplicate) {
                        "检测到相同报告，已打开原有处理记录。"
                    } else {
                        "报告原件已保存并提交处理。"
                    },
                    backgroundTaskHint = when {
                        outcome.acknowledgement ==
                            HealthReportLocalOriginalAcknowledgementStatus.Deferred ->
                            "本机原件已保存；服务器副本会继续保留，联网后可再次确认。"
                        outcome.runtime.workflowStatus == "completed_score_pending" ->
                            "报告已确认入库，相关评分仍在更新。"
                        else -> "报告正在后台处理，可稍后回到报告页查看。"
                    },
                )
            }
            is HealthReportUploadOutcome.RecoveryRequired -> _state.update {
                it.copy(
                    uploading = false,
                    uploadStage = "",
                    backgroundTaskHint = HealthReportReleasePresentation.failureMessage(
                        outcome.recovery.failureCode,
                    ),
                    toast = "报告需要补充处理",
                )
            }
        }
    }

    private fun publishReportFailure(error: Throwable) {
        val message = when (error) {
            is HealthReportLocalOriginalStoreException -> error.message
            is HealthReportUploadCoordinatorException -> error.message
            else -> null
        } ?: "报告上传未完成，请检查网络后重试。"
        _state.update {
            it.copy(
                uploading = false,
                uploadStage = "",
                backgroundTaskHint = null,
                error = message,
            )
        }
    }

    fun refreshReports() = viewModelScope.launch { reportDashboardController.refresh() }

    /** 用户手动关闭后台任务提示横幅（任务在后端继续）。 */
    fun dismissBackgroundHint() = _state.update { it.copy(backgroundTaskHint = null) }

    private suspend fun pollDoc(id: String): HealthDocument? {
        repeat(45) {
            delay(2000)
            val d = runCatching { repo.document(id) }.getOrNull() ?: return@repeat
            if (d.extraction_status != "pending" ||
                d.report_workflow_status !in setOf(null, "draft", "uploading", "recognizing")
            ) {
                return d
            }
        }
        return null
    }

    fun generateSummary() = viewModelScope.launch {
        if (_state.value.generating) return@launch
        _state.update {
            it.copy(
                generating = true,
                progress = 0f,
                stage = "提交任务...",
                toast = "AI 报告生成已开始，您可以继续使用其他功能",
            )
        }
        runCatching {
            val task = repo.startSummaryTask()
            pollTask(task.task_id)
        }.onFailure { e -> _state.update { it.copy(generating = false, error = e.message) } }
    }

    private suspend fun pollTask(taskId: String) {
        while (true) {
            delay(3000)
            val s = runCatching { repo.taskStatus(taskId) }.getOrNull() ?: continue
            val stage = when (s.stage) {
                "l1" -> "分析第 ${s.stage_current ?: 0}/${s.stage_total ?: 0} 次检查..."
                "l2" -> "汇总第 ${s.stage_current ?: 0}/${s.stage_total ?: 0} 年趋势..."
                "l3" -> "生成最终报告..."
                else -> "准备中..."
            }
            _state.update {
                it.copy(progress = (s.progress_pct ?: 0.0).toFloat(), stage = stage)
            }
            if (s.status == "done") {
                val r = repo.summary()
                _state.update {
                    it.copy(
                        generating = false, progress = 1f,
                        summary = r?.summary_text ?: "",
                        summaryUpdatedAt = r?.updated_at ?: "",
                    )
                }
                return
            }
            if (s.status == "failed") {
                _state.update {
                    it.copy(
                        generating = false,
                        error = "生成失败: ${s.error_message ?: "未知错误"}",
                    )
                }
                return
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearToast() = _state.update { it.copy(toast = null) }
}

@HiltViewModel
class DocumentDetailViewModel internal constructor(
    private val repo: HealthDataRepository,
    private val authManager: AuthManager,
    private val localOriginalStore: HealthReportLocalOriginalStoreContract,
) : ViewModel() {
    @Inject
    constructor(
        repo: HealthDataRepository,
        authManager: AuthManager,
        @ApplicationContext context: Context,
    ) : this(
        repo = repo,
        authManager = authManager,
        localOriginalStore = HealthReportLocalOriginalStore.production(context),
    )

    data class UiState(
        val loading: Boolean = false,
        val owner: AuthManager.AccountScopeSnapshot? = null,
        val authoritativeWorkflowId: Long? = null,
        val doc: HealthDocument? = null,
        val reviewLoading: Boolean = false,
        val review: HealthReportReview? = null,
        val interpretationLoading: Boolean = false,
        val interpretation: HealthReportInterpretation? = null,
        val interpretationError: String? = null,
        val originalLoading: Boolean = false,
        val originalFileUri: Uri? = null,
        val decisions: Map<Int, ReportDecisionDraft> = emptyMap(),
        val confirmationClientEventId: String? = null,
        val manualCandidateClientEventId: String? = null,
        val addingManualCandidate: Boolean = false,
        val confirming: Boolean = false,
        val notice: String? = null,
        val error: String? = null,
    )
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun fetch(id: String) = viewModelScope.launch {
        HealthReportHistoryDestination.decode(id)?.let { workflowId ->
            fetchAuthoritativeWorkflow(workflowId)
            return@launch
        }
        val owner = authManager.captureAccountScope()
        if (owner == null) {
            _state.update { it.copy(loading = false, error = "当前账号无法读取这份报告。") }
            return@launch
        }
        _state.update { it.copy(loading = true) }
        runCatching { repo.document(id) }
            .onSuccess { document ->
                if (!authManager.isCurrent(owner)) return@onSuccess
                _state.update {
                    it.copy(
                        loading = false,
                        owner = owner,
                        authoritativeWorkflowId = document.report_workflow_id?.toLong(),
                        doc = document,
                        interpretation = null,
                        interpretationError = null,
                        originalFileUri = null,
                    )
                }
                loadReview(document, owner)
            }
            .onFailure { e ->
                if (authManager.isCurrent(owner)) {
                    _state.update { it.copy(loading = false, error = e.message) }
                }
            }
    }

    fun reloadReview() = viewModelScope.launch {
        val current = _state.value
        val owner = current.owner?.takeIf(authManager::isCurrent) ?: return@launch
        val workflowId = current.authoritativeWorkflowId
        val subjectUserId = owner.subjectId.trim().toLongOrNull()?.takeIf { it > 0L }
        if (workflowId != null && subjectUserId != null) {
            loadReview(workflowId, subjectUserId, owner)
        } else {
            current.doc?.let { loadReview(it, owner) }
        }
    }

    fun loadInterpretation(force: Boolean = false) = viewModelScope.launch {
        val review = _state.value.review ?: return@launch
        if (review.status !in setOf("completed", "completed_score_pending")) return@launch
        if (_state.value.interpretation != null && !force) return@launch
        _state.update {
            it.copy(interpretationLoading = true, interpretationError = null)
        }
        runCatching {
            repo.reportInterpretation(review.workflow_id, review.subject_user_id).also { response ->
                check(response.workflow_id == review.workflow_id) { "报告任务不匹配" }
                check(response.subject_user_id == review.subject_user_id) { "报告主体不匹配" }
            }
        }.onSuccess { interpretation ->
            _state.update {
                it.copy(
                    interpretationLoading = false,
                    interpretation = interpretation,
                    interpretationError = null,
                )
            }
        }.onFailure { error ->
            _state.update {
                it.copy(
                    interpretationLoading = false,
                    interpretationError = error.message ?: "无法读取本次解读",
                )
            }
        }
    }

    fun prepareOriginalDocument() = viewModelScope.launch {
        val current = _state.value
        val owner = current.owner?.takeIf(authManager::isCurrent)
        val review = current.review
        val workflowId = current.authoritativeWorkflowId ?: review?.workflow_id?.toLong()
        val subjectUserId = review?.subject_user_id
            ?: owner?.subjectId?.trim()?.toLongOrNull()?.takeIf { it > 0L }
        _state.update {
            it.copy(originalLoading = true, interpretationError = null, error = null)
        }
        val localResult = if (owner != null && workflowId != null && subjectUserId != null) {
            runCatching {
                val first = localOriginalStore.listAssets(
                    workflowId = workflowId,
                    accountScope = owner.accountScope,
                    subjectUserId = subjectUserId,
                ).minByOrNull(HealthReportLocalOriginalMetadata::assetIndex)
                    ?: throw HealthReportLocalOriginalStoreException(
                        HealthReportLocalOriginalStoreError.ReportNotFound,
                    )
                val asset = localOriginalStore.loadAsset(
                    workflowId = workflowId,
                    assetIndex = first.assetIndex,
                    accountScope = owner.accountScope,
                    subjectUserId = subjectUserId,
                )
                check(authManager.isCurrent(owner)) { "报告所属账号已变化" }
                repo.cacheLocalReportOriginal(asset)
            }
        } else {
            Result.failure(
                HealthReportLocalOriginalStoreException(
                    HealthReportLocalOriginalStoreError.ReportNotFound,
                ),
            )
        }
        val result = if (localResult.isSuccess) {
            localResult
        } else if (localResult.exceptionOrNull().isMissingLocalOriginal()) {
            val document = current.interpretation?.document ?: current.doc
            if (document?.file_url.isNullOrBlank()) {
                Result.failure(IllegalStateException("这份报告没有可访问的原件备份。"))
            } else {
                runCatching { repo.cacheOriginalDocument(requireNotNull(document)) }
            }
        } else {
            localResult
        }
        result
            .onSuccess { uri ->
                if (owner != null && !authManager.isCurrent(owner)) return@onSuccess
                _state.update {
                    it.copy(originalLoading = false, originalFileUri = uri)
                }
            }
            .onFailure { error ->
                if (owner != null && !authManager.isCurrent(owner)) return@onFailure
                _state.update {
                    it.copy(
                        originalLoading = false,
                        interpretationError = error.message ?: "原始报告读取失败",
                        error = "报告原件暂时无法读取，请稍后重试。",
                    )
                }
            }
    }

    fun consumeOriginalFile() = _state.update { it.copy(originalFileUri = null) }

    fun chooseDecision(candidateId: Int, action: ReportDecisionAction) {
        val candidate = _state.value.review?.candidates?.firstOrNull {
            it.candidate_id == candidateId && it.requires_review
        } ?: return
        val draft = if (action == ReportDecisionAction.Correct) {
            ReportDecisionDraft(
                action = action,
                correctedValue = candidate.normalized_value?.toString()
                    ?: candidate.normalized_text
                    ?: candidate.raw_value.orEmpty(),
                correctedUnit = candidate.normalized_unit ?: candidate.raw_unit.orEmpty(),
            )
        } else {
            ReportDecisionDraft(action)
        }
        _state.update { state -> state.copy(decisions = state.decisions + (candidateId to draft)) }
    }

    fun updateCorrection(candidateId: Int, value: String? = null, unit: String? = null) {
        _state.update { state ->
            val current = state.decisions[candidateId]
                ?.takeIf { it.action == ReportDecisionAction.Correct }
                ?: return@update state
            state.copy(
                decisions = state.decisions + (
                    candidateId to current.copy(
                        correctedValue = value ?: current.correctedValue,
                        correctedUnit = unit ?: current.correctedUnit,
                    )
                ),
            )
        }
    }

    fun addManualCandidate(
        draft: ManualReportCandidateDraft,
        onAdded: () -> Unit,
    ) {
        val state = _state.value
        val review = state.review ?: return
        val owner = captureReportMutationOwner(state, review) ?: return
        val eventId = state.manualCandidateClientEventId ?: "android-manual-${UUID.randomUUID()}"
        val body = runCatching {
            ReportReviewPolicy.buildManualCandidateRequest(review, eventId, draft)
        }.getOrElse { error ->
            _state.update { it.copy(error = error.message ?: "请检查手动补录内容") }
            return
        }
        _state.update {
            it.copy(
                addingManualCandidate = true,
                manualCandidateClientEventId = eventId,
                error = null,
                notice = null,
            )
        }
        viewModelScope.launch {
            if (!reportMutationStillCurrent(owner, review)) {
                publishReportMutationOwnerChanged(addingManualCandidate = false)
                return@launch
            }
            runCatching {
                repo.addManualReportCandidate(owner, review.workflow_id, body)
            }.onSuccess { updated ->
                if (!reportMutationStillCurrent(owner, review)) {
                    publishReportMutationOwnerChanged(addingManualCandidate = false)
                    return@onSuccess
                }
                if (!updated.matchesMutationResponse(review)) {
                    _state.update {
                        it.copy(
                            addingManualCandidate = false,
                            error = "服务器返回的报告身份不一致，请重新打开后再试。",
                        )
                    }
                    return@onSuccess
                }
                _state.update {
                    it.copy(
                        review = updated,
                        decisions = emptyMap(),
                        confirmationClientEventId = "android-${UUID.randomUUID()}",
                        manualCandidateClientEventId = null,
                        addingManualCandidate = false,
                        notice = "已添加待确认字段；仍需逐项复核并确认整份报告。",
                    )
                }
                onAdded()
            }.onFailure { error ->
                if (!reportMutationStillCurrent(owner, review)) {
                    publishReportMutationOwnerChanged(addingManualCandidate = false)
                    return@onFailure
                }
                _state.update {
                    it.copy(
                        addingManualCandidate = false,
                        error = error.message ?: "手动补录失败，请重试",
                    )
                }
            }
        }
    }

    fun confirmReport() {
        val state = _state.value
        val review = state.review ?: return
        val owner = captureReportMutationOwner(state, review) ?: return
        val eventId = state.confirmationClientEventId ?: return
        val body = runCatching {
            ReportReviewPolicy.buildRequest(review, eventId, state.decisions)
        }.getOrElse { error ->
            _state.update { it.copy(error = error.message ?: "请先检查所有待确认指标") }
            return
        }

        _state.update { it.copy(confirming = true, error = null, notice = null) }
        viewModelScope.launch {
            if (!reportMutationStillCurrent(owner, review)) {
                publishReportMutationOwnerChanged(confirming = false)
                return@launch
            }
            runCatching { repo.confirmReport(owner, review.workflow_id, body) }
                .onSuccess { confirmed ->
                    if (!reportMutationStillCurrent(owner, review)) {
                        publishReportMutationOwnerChanged(confirming = false)
                        return@onSuccess
                    }
                    if (!confirmed.matchesMutationResponse(review)) {
                        _state.update {
                            it.copy(
                                confirming = false,
                                error = "服务器返回的报告身份不一致，请重新打开后再试。",
                            )
                        }
                        return@onSuccess
                    }
                    val updatedDocument = _state.value.doc?.copy(
                        report_workflow_status = confirmed.status,
                        report_subject_user_id = confirmed.subject_user_id,
                    )
                    _state.update {
                        it.copy(
                            doc = updatedDocument,
                            review = confirmed,
                            interpretation = null,
                            interpretationError = null,
                            decisions = emptyMap(),
                            manualCandidateClientEventId = null,
                            addingManualCandidate = false,
                            confirmationClientEventId = confirmed.confirmation_client_event_id
                                ?: eventId,
                            confirming = false,
                            notice = when (confirmed.status) {
                                "completed_score_pending" -> "报告已确认入库，相关评分仍在更新。"
                                "completed" -> "报告已确认入库。"
                                "committing" -> "确认结果已保存，服务器正在完成入库。"
                                else -> "报告状态已更新。"
                            },
                        )
                    }
                }
                .onFailure { error ->
                    if (!reportMutationStillCurrent(owner, review)) {
                        publishReportMutationOwnerChanged(confirming = false)
                        return@onFailure
                    }
                    // A lost response may leave the server in committing. Refresh uses the same
                    // immutable owner and is discarded after any account-generation change.
                    val refreshed = runCatching {
                        repo.reportReview(owner, review.workflow_id, review.subject_user_id)
                    }.getOrNull()?.takeIf { response ->
                        reportMutationStillCurrent(owner, review) &&
                            response.matchesMutationResponse(review)
                    }
                    if (!reportMutationStillCurrent(owner, review)) {
                        publishReportMutationOwnerChanged(confirming = false)
                        return@onFailure
                    }
                    _state.update {
                        it.copy(
                            review = refreshed ?: it.review,
                            confirmationClientEventId = refreshed?.confirmation_client_event_id
                                ?: it.confirmationClientEventId,
                            confirming = false,
                            error = error.message ?: "确认失败，请重试",
                        )
                    }
                }
        }
    }

    private fun captureReportMutationOwner(
        state: UiState,
        review: HealthReportReview,
    ): AuthManager.AccountScopeSnapshot? {
        val owner = authManager.captureAccountScope()
        val subjectUserId = owner?.subjectId?.trim()?.toLongOrNull()?.takeIf { it > 0L }
        if (owner == null || state.owner != owner || subjectUserId != review.subject_user_id) {
            publishReportMutationOwnerChanged()
            return null
        }
        return owner
    }

    private fun reportMutationStillCurrent(
        owner: AuthManager.AccountScopeSnapshot,
        review: HealthReportReview,
    ): Boolean {
        val current = _state.value
        return authManager.isCurrent(owner) &&
            current.owner == owner &&
            current.review?.workflow_id == review.workflow_id &&
            current.review?.subject_user_id == review.subject_user_id &&
            current.review?.version == review.version
    }

    private fun publishReportMutationOwnerChanged(
        confirming: Boolean? = null,
        addingManualCandidate: Boolean? = null,
    ) {
        _state.update {
            it.copy(
                confirming = confirming ?: it.confirming,
                addingManualCandidate = addingManualCandidate ?: it.addingManualCandidate,
                error = "账号或报告所属用户已变化，请重新打开报告后再试。",
            )
        }
    }

    private fun HealthReportReview.matchesMutationResponse(previous: HealthReportReview): Boolean =
        workflow_id == previous.workflow_id &&
            subject_user_id == previous.subject_user_id &&
            version >= previous.version

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    private suspend fun fetchAuthoritativeWorkflow(workflowId: Long) {
        val owner = authManager.captureAccountScope()
        val subjectUserId = owner?.subjectId?.trim()?.toLongOrNull()?.takeIf { it > 0L }
        if (owner == null || subjectUserId == null || workflowId > Int.MAX_VALUE) {
            _state.update { it.copy(loading = false, error = "当前账号无法读取这份报告。") }
            return
        }
        _state.value = UiState(
            loading = true,
            owner = owner,
            authoritativeWorkflowId = workflowId,
        )
        loadReview(workflowId, subjectUserId, owner)
    }

    private suspend fun loadReview(
        document: HealthDocument,
        owner: AuthManager.AccountScopeSnapshot,
    ) {
        val workflowId = document.report_workflow_id
        val subjectUserId = document.report_subject_user_id
        if (workflowId == null || subjectUserId == null) {
            _state.update {
                it.copy(
                    reviewLoading = false,
                    review = null,
                    interpretation = null,
                    interpretationError = null,
                    decisions = emptyMap(),
                    confirmationClientEventId = null,
                    manualCandidateClientEventId = null,
                    addingManualCandidate = false,
                )
            }
            return
        }

        loadReview(workflowId.toLong(), subjectUserId, owner)
    }

    private suspend fun loadReview(
        workflowId: Long,
        subjectUserId: Long,
        owner: AuthManager.AccountScopeSnapshot,
    ) {
        if (workflowId <= 0L || workflowId > Int.MAX_VALUE || !authManager.isCurrent(owner)) return
        _state.update { it.copy(reviewLoading = true) }
        runCatching { repo.reportReview(owner, workflowId.toInt(), subjectUserId) }
            .onSuccess { review ->
                if (!authManager.isCurrent(owner)) return@onSuccess
                if (
                    review.workflow_id.toLong() != workflowId ||
                    review.subject_user_id != subjectUserId
                ) {
                    _state.update {
                        it.copy(
                            loading = false,
                            reviewLoading = false,
                            error = "服务器返回的报告身份不一致，请重新打开。",
                        )
                    }
                    return@onSuccess
                }
                val current = _state.value
                val sameRevision = current.review?.let { previous ->
                    ReportReviewPolicy.isSameRevision(previous, review)
                } == true
                val event = review.confirmation_client_event_id
                    ?: current.confirmationClientEventId.takeIf { sameRevision }
                    ?: "android-${UUID.randomUUID()}"
                _state.update {
                    it.copy(
                        loading = false,
                        owner = owner,
                        authoritativeWorkflowId = workflowId,
                        doc = it.doc ?: review.document,
                        reviewLoading = false,
                        review = review,
                        interpretation = current.interpretation?.takeIf { interpretation ->
                            sameRevision && interpretation.status == review.status
                        },
                        decisions = current.decisions.takeIf { sameRevision }.orEmpty(),
                        confirmationClientEventId = event,
                        manualCandidateClientEventId = current.manualCandidateClientEventId
                            .takeIf { sameRevision },
                        addingManualCandidate = false,
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                if (authManager.isCurrent(owner)) {
                    _state.update {
                        it.copy(
                            loading = false,
                            reviewLoading = false,
                            error = error.message ?: "无法加载报告确认内容",
                        )
                    }
                }
            }
    }
}

private fun Throwable?.isMissingLocalOriginal(): Boolean =
    (this as? HealthReportLocalOriginalStoreException)?.error ==
        HealthReportLocalOriginalStoreError.ReportNotFound

@HiltViewModel
class DocumentListViewModel @Inject constructor(
    private val repo: HealthDataRepository,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = false,
        val items: List<HealthDocument> = emptyList(),
        val uploading: Boolean = false,
        val error: String? = null,
        val toast: String? = null,
    )
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun fetch(docType: String) = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching { repo.documents(docType) }
            .onSuccess { items -> _state.update { it.copy(loading = false, items = items) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }

    fun upload(docType: String, uri: Uri, filename: String) = viewModelScope.launch {
        _state.update { it.copy(uploading = true) }
        runCatching { repo.uploadDocument(uri, filename, docType) }
            .onSuccess { doc ->
                _state.update {
                    it.copy(
                        uploading = false,
                        toast = ReportTrustPresentation.title(doc),
                    )
                }
            }
            .onFailure { e -> _state.update { it.copy(uploading = false, error = e.message) } }
        fetch(docType)
    }

    fun delete(id: String, docType: String) = viewModelScope.launch {
        runCatching { repo.deleteDocument(id) }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
        fetch(docType)
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearToast() = _state.update { it.copy(toast = null) }
}
