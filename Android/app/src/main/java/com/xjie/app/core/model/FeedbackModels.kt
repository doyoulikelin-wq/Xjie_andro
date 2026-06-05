package com.xjie.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackCreate(
    val category: String = "general",
    val content: String,
    val contact: String? = null,
    val app_platform: String = "android",
    val app_version: String? = null,
    val device_info: String? = null,
)

@Serializable
data class FeedbackOut(
    val id: Long,
    val user_id: Long? = null,
    val category: String? = null,
    val status: String? = null,
    val created_at: String? = null,
)
