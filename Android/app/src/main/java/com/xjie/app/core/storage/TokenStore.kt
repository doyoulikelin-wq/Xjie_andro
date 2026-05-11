package com.xjie.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 对应 iOS [KeychainHelper.swift] —— 安全保存 token / subject_id 等敏感字段。 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            "xjie_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var accessToken: String
        get() = prefs.getString(K_ACCESS, "").orEmpty()
        set(v) { prefs.edit().putString(K_ACCESS, v).apply() }

    var refreshToken: String
        get() = prefs.getString(K_REFRESH, "").orEmpty()
        set(v) { prefs.edit().putString(K_REFRESH, v).apply() }

    var subjectId: String
        get() = prefs.getString(K_SUBJECT, "").orEmpty()
        set(v) { prefs.edit().putString(K_SUBJECT, v).apply() }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val K_ACCESS = "auth_token"
        const val K_REFRESH = "auth_refresh_token"
        const val K_SUBJECT = "auth_subject_id"
    }
}
