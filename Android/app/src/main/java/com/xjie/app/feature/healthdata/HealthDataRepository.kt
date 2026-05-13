package com.xjie.app.feature.healthdata

import android.content.Context
import android.net.Uri
import com.xjie.app.core.model.HealthDataSummary
import com.xjie.app.core.model.HealthDocument
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
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun documents(docType: String): List<HealthDocument> =
        safeApiCall(json) { api.documents(docType) }.items ?: emptyList()

    suspend fun document(id: String): HealthDocument =
        safeApiCall(json) { api.document(id) }

    suspend fun deleteDocument(id: String) =
        safeApiCall(json) { api.deleteDocument(id) }

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

    private fun guessMime(name: String): String = when {
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".csv", true) -> "text/csv"
        else -> "image/jpeg"
    }

    suspend fun listIndicators(): List<IndicatorInfo> =
        safeApiCall(json) { api.indicators() }.indicators

    suspend fun watchedIndicators(): List<WatchedIndicatorItem> =
        safeApiCall(json) { api.watched() }.items

    suspend fun trends(names: List<String>): List<IndicatorTrend> {
        if (names.isEmpty()) return emptyList()
        return safeApiCall(json) { api.trend(names.joinToString(",")) }.indicators
    }

    suspend fun watch(name: String) =
        safeApiCall(json) { api.watch(WatchBody(indicator_name = name)) }

    suspend fun unwatch(name: String) =
        safeApiCall(json) { api.unwatch(name) }

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
