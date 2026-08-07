package com.xjie.app.core.network

import com.xjie.app.BuildConfig
import com.xjie.app.core.network.api.DeviceIndicatorSyncApi
import com.xjie.app.core.util.ApiConstants
import com.xjie.app.core.quality.UiAutomationRuntime
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class HealthConnectSessionBound

/** Dedicated no-authenticator client: a shared account change cannot rewrite the captured token. */
@Module
@InstallIn(SingletonComponent::class)
object HealthConnectNetworkModule {
    @Provides
    @Singleton
    @HealthConnectSessionBound
    fun provideHealthConnectClient(): OkHttpClient = UiAutomationRuntime.installOn(OkHttpClient.Builder()
        .connectTimeout(ApiConstants.REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(ApiConstants.REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(ApiConstants.REQUEST_TIMEOUT_S, TimeUnit.SECONDS))
        .build()

    @Provides
    @Singleton
    @HealthConnectSessionBound
    fun provideHealthConnectRetrofit(
        @HealthConnectSessionBound client: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(ApiEndpointPolicy.retrofitBaseUrl(BuildConfig.API_BASE_URL))
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideDeviceIndicatorSyncApi(
        @HealthConnectSessionBound retrofit: Retrofit,
    ): DeviceIndicatorSyncApi = retrofit.create(DeviceIndicatorSyncApi::class.java)
}
