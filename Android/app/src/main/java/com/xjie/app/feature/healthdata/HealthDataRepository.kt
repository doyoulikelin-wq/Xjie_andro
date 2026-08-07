package com.xjie.app.feature.healthdata

import android.content.Context
import android.net.Uri
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.HealthDataSummary
import com.xjie.app.core.model.HealthDocument
import com.xjie.app.core.model.HealthReportConfirmBody
import com.xjie.app.core.model.HealthReportInterpretation
import com.xjie.app.core.model.HealthReportManualCandidateBody
import com.xjie.app.core.model.HealthReportReview
import com.xjie.app.core.model.HealthProfileCandidateReviewBody
import com.xjie.app.core.model.HealthProfileFactRetractBody
import com.xjie.app.core.model.HealthProfileFactUpsertBody
import com.xjie.app.core.model.HealthProfileGoalCreateBody
import com.xjie.app.core.model.HealthProfileGoalStatusBody
import com.xjie.app.core.model.HealthProfileGoalUpdateBody
import com.xjie.app.core.model.HealthProfileLongTermMedicationSummary
import com.xjie.app.core.model.HealthProfileRevisionList
import com.xjie.app.core.model.HealthProfileTrustProfile
import com.xjie.app.core.model.IndicatorExplanation
import com.xjie.app.core.model.IndicatorInfo
import com.xjie.app.core.model.IndicatorSearchItem
import com.xjie.app.core.model.IndicatorTrend
import com.xjie.app.core.model.ManualIndicatorBody
import com.xjie.app.core.model.ManualIndicatorItem
import com.xjie.app.core.model.PatientHistoryProfile
import com.xjie.app.core.model.PatientHistoryUpdateBody
import com.xjie.app.core.model.SummaryTaskResponse
import com.xjie.app.core.model.WatchedIndicatorItem
import com.xjie.app.core.network.api.HealthDataApi
import com.xjie.app.core.network.api.IndicatorExtraApi
import com.xjie.app.core.network.api.WatchBody
import com.xjie.app.core.network.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.core.content.FileProvider
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class HealthDataRepository @Inject constructor(
    private val api: HealthDataApi,
    private val extraApi: IndicatorExtraApi,
    private val json: Json,
    @ApplicationContext private val context: Context,
) {
    suspend fun summary(): HealthDataSummary? = runCatching {
        safeApiCall(json) { api.summary() }
    }.getOrNull()

    suspend fun summary(owner: AuthManager.AccountScopeSnapshot): HealthDataSummary? = runCatching {
        safeApiCall(json) { api.summaryForOwner(owner) }
    }.getOrNull()

    suspend fun documents(docType: String): List<HealthDocument> =
        safeApiCall(json) { api.documents(docType) }.items ?: emptyList()

    suspend fun documents(
        owner: AuthManager.AccountScopeSnapshot,
        docType: String,
    ): List<HealthDocument> =
        safeApiCall(json) { api.documentsForOwner(owner, docType) }.items ?: emptyList()

    suspend fun document(id: String): HealthDocument =
        safeApiCall(json) { api.document(id) }

    suspend fun deleteDocument(id: String) =
        safeApiCall(json) { api.deleteDocument(id) }

    suspend fun reportReview(
        owner: AuthManager.AccountScopeSnapshot,
        workflowId: Int,
        subjectUserId: Long,
    ): HealthReportReview = safeApiCall(json) {
        api.reportReview(owner, workflowId, subjectUserId)
    }

    suspend fun reportInterpretation(
        workflowId: Int,
        subjectUserId: Long,
    ): HealthReportInterpretation = safeApiCall(json) {
        api.reportInterpretation(workflowId, subjectUserId)
    }

    suspend fun cacheOriginalDocument(document: HealthDocument): Uri {
        val body = safeApiCall(json) { api.documentFile(document.id) }
        val extension = document.name
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it in setOf("pdf", "png", "jpg", "jpeg") }
            ?: "bin"
        val safeID = document.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(context.cacheDir, "health_report_originals").apply { mkdirs() }
        val target = File(directory, "report_${safeID}.$extension")
        body.use { response ->
            response.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
        )
    }

    /** Caches one already integrity-verified local original without reaching any network API. */
    suspend fun cacheLocalReportOriginal(asset: HealthReportLocalOriginalAsset): Uri =
        withContext(Dispatchers.IO) {
            val extension = asset.fileName
                .substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
                .takeIf { it in setOf("pdf", "png", "jpg", "jpeg", "heic", "heif", "webp") }
                ?: "bin"
            val directory = File(context.cacheDir, "health_report_originals").apply { mkdirs() }
            val target = File(directory, "local_${asset.sha256.take(20)}.$extension")
            target.outputStream().use { output -> output.write(asset.data) }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target,
            )
        }

    suspend fun confirmReport(
        owner: AuthManager.AccountScopeSnapshot,
        workflowId: Int,
        body: HealthReportConfirmBody,
    ): HealthReportReview = safeApiCall(json) { api.confirmReport(owner, workflowId, body) }

    suspend fun addManualReportCandidate(
        owner: AuthManager.AccountScopeSnapshot,
        workflowId: Int,
        body: HealthReportManualCandidateBody,
    ): HealthReportReview = safeApiCall(json) {
        api.addManualReportCandidate(owner, workflowId, body)
    }

    suspend fun healthProfileTrust(
        owner: AuthManager.AccountScopeSnapshot,
    ): HealthProfileTrustProfile = safeApiCall(json) { api.healthProfileTrust(owner) }

    suspend fun healthProfileLongTermMedicationSummary(
        owner: AuthManager.AccountScopeSnapshot,
        subjectUserId: Long,
    ): HealthProfileLongTermMedicationSummary = safeApiCall(json) {
        api.healthProfileLongTermMedicationSummary(owner, subjectUserId)
    }

    suspend fun healthProfileFactRevisions(
        owner: AuthManager.AccountScopeSnapshot,
        factId: Long,
        subjectUserId: Long,
        afterRevisionId: Long? = null,
    ): HealthProfileRevisionList = safeApiCall(json) {
        api.healthProfileFactRevisions(
            owner = owner,
            factId = factId,
            subjectUserId = subjectUserId,
            afterRevisionId = afterRevisionId,
        )
    }

    suspend fun healthProfileGoalRevisions(
        owner: AuthManager.AccountScopeSnapshot,
        goalId: Long,
        subjectUserId: Long,
        afterRevisionId: Long? = null,
    ): HealthProfileRevisionList = safeApiCall(json) {
        api.healthProfileGoalRevisions(
            owner = owner,
            goalId = goalId,
            subjectUserId = subjectUserId,
            afterRevisionId = afterRevisionId,
        )
    }

    suspend fun reviewHealthProfileCandidate(
        owner: AuthManager.AccountScopeSnapshot,
        candidateId: Long,
        body: HealthProfileCandidateReviewBody,
    ): HealthProfileTrustProfile = safeApiCall(json) {
        api.reviewHealthProfileCandidate(owner, candidateId, body)
    }

    suspend fun upsertHealthProfileFact(
        owner: AuthManager.AccountScopeSnapshot,
        body: HealthProfileFactUpsertBody,
    ): HealthProfileTrustProfile = safeApiCall(json) { api.upsertHealthProfileFact(owner, body) }

    suspend fun retractHealthProfileFact(
        owner: AuthManager.AccountScopeSnapshot,
        factId: Long,
        body: HealthProfileFactRetractBody,
    ): HealthProfileTrustProfile = safeApiCall(json) {
        api.retractHealthProfileFact(owner, factId, body)
    }

    suspend fun createHealthProfileGoal(
        owner: AuthManager.AccountScopeSnapshot,
        body: HealthProfileGoalCreateBody,
    ): HealthProfileTrustProfile = safeApiCall(json) {
        api.createHealthProfileGoal(owner, body)
    }

    suspend fun updateHealthProfileGoal(
        owner: AuthManager.AccountScopeSnapshot,
        goalId: Long,
        body: HealthProfileGoalUpdateBody,
    ): HealthProfileTrustProfile = safeApiCall(json) {
        api.updateHealthProfileGoal(owner, goalId, body)
    }

    suspend fun updateHealthProfileGoalStatus(
        owner: AuthManager.AccountScopeSnapshot,
        goalId: Long,
        body: HealthProfileGoalStatusBody,
    ): HealthProfileTrustProfile = safeApiCall(json) {
        api.updateHealthProfileGoalStatus(owner, goalId, body)
    }

    suspend fun startSummaryTask(): SummaryTaskResponse =
        safeApiCall(json) { api.generateSummaryAsync() }

    suspend fun taskStatus(taskId: String): SummaryTaskResponse =
        safeApiCall(json) { api.summaryTaskStatus(taskId) }

    suspend fun uploadDocument(uri: Uri, filename: String, docType: String): HealthDocument {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取文件")
        val mime = guessMime(filename)
        val filePart = MultipartBody.Part.createFormData(
            "file", filename, bytes.toRequestBody(mime.toMediaTypeOrNull()),
        )
        val docTypePart = MultipartBody.Part.createFormData("doc_type", docType)
        val namePart = MultipartBody.Part.createFormData("name", filename)
        return safeApiCall(json) { api.upload(filePart, docTypePart, namePart) }
    }

    /** Reads one exact report original locally. No network API is reachable from this method. */
    suspend fun readReportUploadAsset(uri: Uri, filename: String): HealthReportUploadAssetInput =
        withContext(Dispatchers.IO) {
            val safeName = filename.replace('\\', '/')
                .substringAfterLast('/')
                .filterNot(Char::isISOControl)
                .trim()
                .take(180)
                .takeUnless { it.isNullOrBlank() || it == "." || it == ".." }
                ?: "健康报告原件"
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_REPORT_ASSET_BYTES) {
                        throw IllegalArgumentException("单页文件超过 25MB，请压缩或重新导出后上传。")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: throw IllegalStateException("无法读取报告原件，请重新选择文件。")
            require(bytes.isNotEmpty()) { "报告原件为空，请重新选择文件。" }
            HealthReportUploadAssetInput(bytes, safeName)
        }

    private fun guessMime(name: String): String = when {
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".csv", true) -> "text/csv"
        else -> "image/jpeg"
    }

    private companion object {
        const val MAX_REPORT_ASSET_BYTES = 25 * 1024 * 1024
    }

    suspend fun listIndicators(): List<IndicatorInfo> =
        safeApiCall(json) { api.indicators() }.indicators

    suspend fun listIndicators(owner: AuthManager.AccountScopeSnapshot): List<IndicatorInfo> =
        safeApiCall(json) { api.indicatorsForOwner(owner) }.indicators

    suspend fun watchedIndicators(): List<WatchedIndicatorItem> =
        safeApiCall(json) { api.watched() }.items

    suspend fun watchedIndicators(
        owner: AuthManager.AccountScopeSnapshot,
    ): List<WatchedIndicatorItem> =
        safeApiCall(json) { api.watchedForOwner(owner) }.items

    suspend fun trends(names: List<String>): List<IndicatorTrend> {
        if (names.isEmpty()) return emptyList()
        return safeApiCall(json) { api.trend(names.joinToString(",")) }.indicators
    }

    suspend fun trends(
        owner: AuthManager.AccountScopeSnapshot,
        names: List<String>,
    ): List<IndicatorTrend> {
        if (names.isEmpty()) return emptyList()
        return safeApiCall(json) {
            api.trendForOwner(owner, names.joinToString(","))
        }.indicators
    }

    suspend fun watch(owner: AuthManager.AccountScopeSnapshot, name: String) =
        safeApiCall(json) { api.watch(owner, WatchBody(indicator_name = name)) }

    suspend fun unwatch(owner: AuthManager.AccountScopeSnapshot, name: String) =
        safeApiCall(json) { api.unwatch(owner, name) }

    suspend fun explain(name: String): IndicatorExplanation =
        safeApiCall(json) { api.explain(name) }

    suspend fun patientHistory(): PatientHistoryProfile =
        safeApiCall(json) { api.patientHistory() }

    suspend fun savePatientHistory(body: PatientHistoryUpdateBody): PatientHistoryProfile =
        safeApiCall(json) { api.savePatientHistory(body) }

    // ── Indicator extras ──
    suspend fun searchIndicators(q: String, limit: Int = 20): List<IndicatorSearchItem> =
        safeApiCall(json) { extraApi.search(q, limit) }.items

    suspend fun listManualIndicators(name: String? = null): List<ManualIndicatorItem> =
        safeApiCall(json) { extraApi.listManual(name) }.items

    suspend fun createManualIndicator(body: ManualIndicatorBody): ManualIndicatorItem =
        safeApiCall(json) { extraApi.createManual(body) }

    suspend fun deleteManualIndicator(id: Long) =
        safeApiCall(json) { extraApi.deleteManual(id) }
}
