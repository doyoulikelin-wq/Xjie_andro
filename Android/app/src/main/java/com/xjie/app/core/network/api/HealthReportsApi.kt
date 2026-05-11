package com.xjie.app.core.network.api

import com.xjie.app.core.model.HealthReports
import retrofit2.http.GET

interface HealthReportsApi {
    @GET("api/health-reports")
    suspend fun reports(): HealthReports
}
