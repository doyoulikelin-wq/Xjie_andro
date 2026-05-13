package com.xjie.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val access_token: String,
    val refresh_token: String? = null,
)

@Serializable
data class SubjectItem(
    val subject_id: String,
    val cohort: String? = null,
)

@Serializable
data class LoginSubjectBody(val subject_id: String)

@Serializable
data class LoginPhoneBody(
    val phone: String,
    val username: String,
    val password: String,
    // 仅注册时携带；后端 /login 会忽略多余字段
    val sex: String? = null,
    val age: Int? = null,
    val height_cm: Double? = null,
    val weight_kg: Double? = null,
)

@Serializable
data class WxLoginBody(val code: String)

@Serializable
data class UserInfo(
    val id: String? = null,
    val email: String? = null,
    val is_admin: Boolean? = null,
    val created_at: String? = null,
    val phone: String? = null,
    val username: String? = null,
    val consent: UserConsent? = null,
    val profile: UserProfile? = null,
)

@Serializable
data class UserProfile(
    val sex: String? = null,
    val age: Int? = null,
    val height_cm: Double? = null,
    val weight_kg: Double? = null,
    val display_name: String? = null,
)

@Serializable
data class UpdateProfileBody(
    val sex: String? = null,
    val age: Int? = null,
    val height_cm: Double? = null,
    val weight_kg: Double? = null,
    val display_name: String? = null,
)

@Serializable
data class UserConsent(
    val allow_ai_chat: Boolean? = null,
    val allow_data_upload: Boolean? = null,
)

@Serializable
data class RefreshTokenBody(val refresh_token: String)
