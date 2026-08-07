package com.xjie.app.core.network.api

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.IndicatorTrendResponse
import com.xjie.app.core.model.ManualIndicatorBody
import com.xjie.app.core.model.ManualIndicatorItem
import com.xjie.app.core.model.UserInfo
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Tag

/** Dedicated account-tagged wire surface for the XAGE weight flow. */
interface WeightApi {
    @GET("api/health-data/indicators/trend")
    suspend fun trends(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Query("names") names: String,
    ): IndicatorTrendResponse

    @GET("api/users/me")
    suspend fun currentUser(
        @Tag owner: AuthManager.AccountScopeSnapshot,
    ): UserInfo

    @POST("api/health-data/indicators/manual")
    suspend fun createManualIndicator(
        @Tag owner: AuthManager.AccountScopeSnapshot,
        @Body body: ManualIndicatorBody,
    ): ManualIndicatorItem
}
