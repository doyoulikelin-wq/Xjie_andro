package com.xjie.app.core.network.api

import com.xjie.app.core.model.AppUpdateCheck
import retrofit2.http.GET
import retrofit2.http.Query

interface AppUpdateApi {
    @GET("api/app-version")
    suspend fun check(
        @Query("platform") platform: String,
        @Query("version") version: String,
        @Query("build") build: Int,
    ): AppUpdateCheck
}
