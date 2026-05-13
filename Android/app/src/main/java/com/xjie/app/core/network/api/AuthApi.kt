package com.xjie.app.core.network.api

import com.xjie.app.core.model.AuthResponse
import com.xjie.app.core.model.LoginPhoneBody
import com.xjie.app.core.model.LoginSubjectBody
import com.xjie.app.core.model.RefreshTokenBody
import com.xjie.app.core.model.SubjectItem
import com.xjie.app.core.model.WxLoginBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @GET("api/auth/subjects")
    suspend fun listSubjects(): List<SubjectItem>

    @POST("api/auth/login-subject")
    suspend fun loginSubject(@Body body: LoginSubjectBody): AuthResponse

    @POST("api/auth/signup")
    suspend fun signup(@Body body: LoginPhoneBody): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginPhoneBody): AuthResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body body: RefreshTokenBody): AuthResponse

    @POST("api/auth/wx-login")
    suspend fun wxLogin(@Body body: WxLoginBody): AuthResponse

    @POST("api/auth/logout")
    suspend fun logout()

    @POST("api/auth/password/change")
    suspend fun changePassword(@Body body: com.xjie.app.core.model.PasswordChangeBody)

    @POST("api/auth/password/reset/request")
    suspend fun requestPasswordReset(@Body body: com.xjie.app.core.model.PasswordResetRequestBody): com.xjie.app.core.model.SimpleOk

    @POST("api/auth/password/reset/confirm")
    suspend fun confirmPasswordReset(@Body body: com.xjie.app.core.model.PasswordResetConfirmBody): com.xjie.app.core.model.SimpleOk
}
