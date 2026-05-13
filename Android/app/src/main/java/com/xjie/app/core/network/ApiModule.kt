package com.xjie.app.core.network

import com.xjie.app.core.network.api.AdminApi
import com.xjie.app.core.network.api.AgentApi
import com.xjie.app.core.network.api.AuthApi
import com.xjie.app.core.network.api.ChatApi
import com.xjie.app.core.network.api.DashboardApi
import com.xjie.app.core.network.api.ExerciseApi
import com.xjie.app.core.network.api.FeatureFlagsApi
import com.xjie.app.core.network.api.GlucoseApi
import com.xjie.app.core.network.api.HealthDataApi
import com.xjie.app.core.network.api.IndicatorExtraApi
import com.xjie.app.core.network.api.HealthReportsApi
import com.xjie.app.core.network.api.LiteratureApi
import com.xjie.app.core.network.api.MealsApi
import com.xjie.app.core.network.api.MoodApi
import com.xjie.app.core.network.api.OmicsApi
import com.xjie.app.core.network.api.PushApi
import com.xjie.app.core.network.api.UserApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    @Provides @Singleton fun authApi(r: Retrofit): AuthApi = r.create(AuthApi::class.java)
    @Provides @Singleton fun userApi(r: Retrofit): UserApi = r.create(UserApi::class.java)
    @Provides @Singleton fun dashboardApi(r: Retrofit): DashboardApi = r.create(DashboardApi::class.java)
    @Provides @Singleton fun agentApi(r: Retrofit): AgentApi = r.create(AgentApi::class.java)
    @Provides @Singleton fun glucoseApi(r: Retrofit): GlucoseApi = r.create(GlucoseApi::class.java)
    @Provides @Singleton fun mealsApi(r: Retrofit): MealsApi = r.create(MealsApi::class.java)
    @Provides @Singleton fun chatApi(@LlmClient r: Retrofit): ChatApi = r.create(ChatApi::class.java)
    @Provides @Singleton fun moodApi(r: Retrofit): MoodApi = r.create(MoodApi::class.java)
    @Provides @Singleton fun healthDataApi(r: Retrofit): HealthDataApi = r.create(HealthDataApi::class.java)
    @Provides @Singleton fun healthReportsApi(r: Retrofit): HealthReportsApi = r.create(HealthReportsApi::class.java)
    @Provides @Singleton fun omicsApi(r: Retrofit): OmicsApi = r.create(OmicsApi::class.java)
    @Provides @Singleton fun literatureApi(r: Retrofit): LiteratureApi = r.create(LiteratureApi::class.java)
    @Provides @Singleton fun pushApi(r: Retrofit): PushApi = r.create(PushApi::class.java)
    @Provides @Singleton fun featureFlagsApi(r: Retrofit): FeatureFlagsApi = r.create(FeatureFlagsApi::class.java)
    @Provides @Singleton fun adminApi(r: Retrofit): AdminApi = r.create(AdminApi::class.java)
    @Provides @Singleton fun indicatorExtraApi(r: Retrofit): IndicatorExtraApi = r.create(IndicatorExtraApi::class.java)
    @Provides @Singleton fun exerciseApi(r: Retrofit): ExerciseApi = r.create(ExerciseApi::class.java)
}
