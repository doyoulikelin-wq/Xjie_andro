package com.xjie.app.core.network.api

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.feature.healthdata.HealthReportLocalOriginalAcknowledgementRequest
import com.xjie.app.feature.healthdata.HealthReportLocalOriginalAcknowledgementResult
import com.xjie.app.feature.healthdata.HealthReportHistoryResponse
import com.xjie.app.feature.healthdata.HealthReportRecoveredAsset
import com.xjie.app.feature.healthdata.HealthReportRuntime
import com.xjie.app.feature.healthdata.HealthReportSealRequest
import com.xjie.app.feature.healthdata.HealthReportSealResult
import com.xjie.app.feature.healthdata.HealthReportUploadSession
import com.xjie.app.feature.healthdata.HealthReportUploadSessionAbandonResult
import com.xjie.app.feature.healthdata.HealthReportUploadSessionRequest
import com.xjie.app.feature.healthdata.HealthReportUploadedAsset
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

interface HealthReportUploadApi {
    @POST("api/health-data/report-upload-sessions")
    suspend fun startUploadSession(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body request: HealthReportUploadSessionRequest,
    ): HealthReportUploadSession

    @Multipart
    @PUT("api/health-data/report-upload-sessions/{assetSetId}/assets/{assetIndex}")
    suspend fun uploadAsset(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("assetSetId") assetSetId: Long,
        @Path("assetIndex") assetIndex: Int,
        @Part file: MultipartBody.Part,
        @Part("subject_user_id") subjectUserId: RequestBody,
        @Part("client_asset_id") clientAssetId: RequestBody,
    ): HealthReportUploadedAsset

    @Multipart
    @PUT("api/health-data/report-upload-sessions/{assetSetId}/assets/{assetIndex}/replacement")
    suspend fun recoverAsset(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("assetSetId") assetSetId: Long,
        @Path("assetIndex") assetIndex: Int,
        @Part file: MultipartBody.Part,
        @Part("subject_user_id") subjectUserId: RequestBody,
        @Part("client_asset_id") clientAssetId: RequestBody,
    ): HealthReportRecoveredAsset

    @POST("api/health-data/report-upload-sessions/{assetSetId}/seal")
    suspend fun sealUploadSession(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("assetSetId") assetSetId: Long,
        @Body request: HealthReportSealRequest,
    ): HealthReportSealResult

    @POST("api/health-data/report-workflows/{workflowId}/local-original-ack")
    suspend fun acknowledgeLocalOriginal(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("workflowId") workflowId: Long,
        @Body request: HealthReportLocalOriginalAcknowledgementRequest,
    ): HealthReportLocalOriginalAcknowledgementResult

    @DELETE("api/health-data/report-upload-sessions/{assetSetId}")
    suspend fun abandonUploadSession(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("assetSetId") assetSetId: Long,
        @Query("subject_user_id") subjectUserId: Long,
    ): HealthReportUploadSessionAbandonResult

    @GET("api/health-data/report-workflows/{workflowId}/runtime")
    suspend fun fetchRuntime(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Path("workflowId") workflowId: Long,
        @Query("subject_user_id") subjectUserId: Long,
    ): HealthReportRuntime

    @GET("api/health-data/report-workflows")
    suspend fun fetchHistory(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("subject_user_id") subjectUserId: Long,
        @Query("date_from") dateFrom: String,
        @Query("date_to") dateTo: String,
        @Query("hospital") hospital: String? = null,
        @Query("report_type") reportType: String? = null,
    ): HealthReportHistoryResponse
}
