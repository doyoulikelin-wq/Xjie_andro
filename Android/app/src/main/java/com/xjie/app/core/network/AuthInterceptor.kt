package com.xjie.app.core.network

import com.xjie.app.core.auth.AuthManager
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** 注入 `Authorization: Bearer <token>`，登录/刷新接口除外。 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val auth: AuthManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val path = req.url.encodedPath
        val isAuthEndpoint = path.startsWith("/api/auth/")
        val owner = req.tag(AuthManager.AccountScopeSnapshot::class.java)
        val token = if (owner == null) {
            auth.accessToken
        } else {
            auth.accessTokenIfCurrent(owner)
                ?: throw IOException("account or subject changed before request dispatch")
        }
        val newReq = if (token.isNotEmpty() && !isAuthEndpoint) {
            req.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else req
        return chain.proceed(newReq)
    }
}
