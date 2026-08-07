package com.xjie.app.feature.healthdata

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.network.api.HealthReportUploadApi
import com.xjie.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

interface HealthReportUploadRemote {
    suspend fun startUploadSession(
        owner: AuthManager.AccountScopeSnapshot,
        request: HealthReportUploadSessionRequest,
    ): HealthReportUploadSession

    suspend fun uploadAsset(
        owner: AuthManager.AccountScopeSnapshot,
        assetSetId: Long,
        assetIndex: Int,
        subjectUserId: Long,
        input: HealthReportUploadAssetInput,
        clientAssetId: String,
    ): HealthReportUploadedAsset

    suspend fun recoverAsset(
        owner: AuthManager.AccountScopeSnapshot,
        assetSetId: Long,
        assetIndex: Int,
        subjectUserId: Long,
        input: HealthReportUploadAssetInput,
        clientAssetId: String,
    ): HealthReportRecoveredAsset

    suspend fun sealUploadSession(
        owner: AuthManager.AccountScopeSnapshot,
        assetSetId: Long,
        request: HealthReportSealRequest,
    ): HealthReportSealResult

    suspend fun acknowledgeLocalOriginal(
        owner: AuthManager.AccountScopeSnapshot,
        workflowId: Long,
        request: HealthReportLocalOriginalAcknowledgementRequest,
    ): HealthReportLocalOriginalAcknowledgementResult

    suspend fun abandonUploadSession(
        owner: AuthManager.AccountScopeSnapshot,
        assetSetId: Long,
        subjectUserId: Long,
    ): HealthReportUploadSessionAbandonResult

    suspend fun fetchRuntime(
        owner: AuthManager.AccountScopeSnapshot,
        workflowId: Long,
        subjectUserId: Long,
    ): HealthReportRuntime
}

interface HealthReportDashboardRemote {
    suspend fun fetchHistory(
        owner: AuthManager.AccountScopeSnapshot,
        subjectUserId: Long,
        dateFrom: String,
        dateTo: String,
    ): HealthReportHistoryResponse

    suspend fun fetchRuntime(
        owner: AuthManager.AccountScopeSnapshot,
        workflowId: Long,
        subjectUserId: Long,
    ): HealthReportRuntime
}

@Singleton
class HealthReportUploadNetworkRepository @Inject constructor(
    private val api: HealthReportUploadApi,
    private val json: Json,
) : HealthReportUploadRemote, HealthReportDashboardRemote {
    override suspend fun startUploadSession(
        owner: AuthManager.AccountScopeSnapshot,
        request: HealthReportUploadSessionRequest,
    ): HealthReportUploadSession = safeApiCall(json) {
        api.startUploadSession(owner, request)
    }

    override suspend fun uploadAsset(
        owner: AuthManager.AccountScopeSnapshot,
        assetSetId: Long,
        assetIndex: Int,
        subjectUserId: Long,
        input: HealthReportUploadAssetInput,
        clientAssetId: String,
    ): HealthReportUploadedAsset = safeApiCall(json) {
        api.uploadAsset(
            owner = owner,
            assetSetId = assetSetId,
            assetIndex = assetIndex,
            file = filePart(input),
            subjectUserId = textPart(subjectUserId.toString()),
            clientAssetId = textPart(clientAssetId),
        )
    }

    override suspend fun recoverAsset(
        owner: AuthManager.AccountScopeSnapshot,
        assetSetId: Long,
        assetIndex: Int,
        subjectUserId: Long,
        input: HealthReportUploadAssetInput,
        clientAssetId: String,
    ): HealthReportRecoveredAsset = safeApiCall(json) {
        api.recoverAsset(
            owner = owner,
            assetSetId = assetSetId,
            assetIndex = assetIndex,
            file = filePart(input),
            subjectUserId = textPart(subjectUserId.toString()),
            clientAssetId = textPart(clientAssetId),
        )
    }

    override suspend fun sealUploadSession(
        owner: AuthManager.AccountScopeSnapshot,
        assetSetId: Long,
        request: HealthReportSealRequest,
    ): HealthReportSealResult = safeApiCall(json) {
        api.sealUploadSession(owner, assetSetId, request)
    }

    override suspend fun acknowledgeLocalOriginal(
        owner: AuthManager.AccountScopeSnapshot,
        workflowId: Long,
        request: HealthReportLocalOriginalAcknowledgementRequest,
    ): HealthReportLocalOriginalAcknowledgementResult = safeApiCall(json) {
        api.acknowledgeLocalOriginal(owner, workflowId, request)
    }

    override suspend fun abandonUploadSession(
        owner: AuthManager.AccountScopeSnapshot,
        assetSetId: Long,
        subjectUserId: Long,
    ): HealthReportUploadSessionAbandonResult = safeApiCall(json) {
        api.abandonUploadSession(owner, assetSetId, subjectUserId)
    }

    override suspend fun fetchRuntime(
        owner: AuthManager.AccountScopeSnapshot,
        workflowId: Long,
        subjectUserId: Long,
    ): HealthReportRuntime = safeApiCall(json) {
        api.fetchRuntime(owner, workflowId, subjectUserId)
    }

    override suspend fun fetchHistory(
        owner: AuthManager.AccountScopeSnapshot,
        subjectUserId: Long,
        dateFrom: String,
        dateTo: String,
    ): HealthReportHistoryResponse = safeApiCall(json) {
        api.fetchHistory(
            owner = owner,
            subjectUserId = subjectUserId,
            dateFrom = dateFrom,
            dateTo = dateTo,
        )
    }

    private fun filePart(input: HealthReportUploadAssetInput): MultipartBody.Part {
        val mimeType = healthReportUploadMimeType(input.fileName).toMediaType()
        val body = input.data.toRequestBody(mimeType)
        return MultipartBody.Part.createFormData("file", input.fileName, body)
    }

    private fun textPart(value: String) = value.toRequestBody(TEXT_MEDIA_TYPE)

    private companion object {
        val TEXT_MEDIA_TYPE = "text/plain; charset=utf-8".toMediaType()
    }
}
