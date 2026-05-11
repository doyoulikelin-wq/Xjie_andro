package com.xjie.app.core.network.api

import com.xjie.app.core.model.DashboardHealth
import com.xjie.app.core.model.MealPhoto
import com.xjie.app.core.model.ProactiveMessage
import com.xjie.app.core.model.TodayBriefing
import retrofit2.http.GET

interface DashboardApi {
    @GET("api/dashboard/health")
    suspend fun health(): DashboardHealth

    @GET("api/dashboard/meals")
    suspend fun meals(): List<MealPhoto>
}

interface AgentApi {
    @GET("api/agent/today")
    suspend fun today(): TodayBriefing

    @GET("api/agent/proactive")
    suspend fun proactive(): ProactiveMessage
}
