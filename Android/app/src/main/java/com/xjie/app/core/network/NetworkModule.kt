package com.xjie.app.core.network

import com.xjie.app.BuildConfig
import com.xjie.app.core.util.ApiConstants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 三档 OkHttpClient：
 *   • Default — 普通 API（含 Authenticator + 重试 + 鉴权头）
 *   • Upload  — multipart 上传，超时 60s
 *   • Llm     — LLM 长请求，超时 90s
 *   • Refresh — 仅供 [TokenAuthenticator] 内部刷新使用，不挂任何拦截器避免循环
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultClient
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class UploadClient
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class LlmClient
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class RefreshClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }

    @Provides @Singleton @RefreshClient
    fun provideRefreshClient(
        logging: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConstants.REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(ApiConstants.REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(ApiConstants.REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    @Provides @Singleton @DefaultClient
    fun provideDefaultClient(
        authInterceptor: AuthInterceptor,
        retryInterceptor: RetryInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        logging: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConstants.REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(ApiConstants.REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(ApiConstants.REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(retryInterceptor)
        .addInterceptor(logging)
        .authenticator(tokenAuthenticator)
        .build()

    @Provides @Singleton @UploadClient
    fun provideUploadClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        logging: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConstants.REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(ApiConstants.UPLOAD_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(ApiConstants.UPLOAD_TIMEOUT_S, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .authenticator(tokenAuthenticator)
        .build()

    @Provides @Singleton @LlmClient
    fun provideLlmClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        logging: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConstants.REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(ApiConstants.LLM_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(ApiConstants.LLM_TIMEOUT_S, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .authenticator(tokenAuthenticator)
        .build()

    @Provides @Singleton
    fun provideRetrofit(
        @DefaultClient client: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
            .callFactory(client as Call.Factory)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides @Singleton @LlmClient
    fun provideLlmRetrofit(
        @LlmClient client: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
            .callFactory(client as Call.Factory)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}
