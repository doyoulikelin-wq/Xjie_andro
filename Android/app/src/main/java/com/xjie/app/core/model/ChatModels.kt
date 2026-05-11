package com.xjie.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Citation(
    val claim_id: Int,
    val literature_id: Int,
    val claim_text: String,
    val evidence_level: String,
    val short_ref: String,
    val journal: String? = null,
    val year: Int? = null,
    val sample_size: Int? = null,
    val confidence: String,
    val score: Double? = null,
)

@Serializable
data class ChatConversation(
    val id: String,
    val title: String? = null,
    val message_count: Int? = null,
    val updated_at: String? = null,
    val created_at: String? = null,
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val analysis: String? = null,
    val created_at: String? = null,
    val citations: List<Citation> = emptyList(),
)

@Serializable
data class ChatRequest(
    val message: String,
    val thread_id: String? = null,
)

@Serializable
data class ChatResponse(
    val summary: String? = null,
    val analysis: String? = null,
    val answer_markdown: String? = null,
    val confidence: Double? = null,
    val followups: List<String>? = null,
    val thread_id: String? = null,
    val citations: List<Citation>? = null,
)

@Serializable
data class ConsentUpdate(val allow_ai_chat: Boolean)

@Serializable
data class ConsentResponse(
    val allow_ai_chat: Boolean,
    val allow_data_upload: Boolean? = null,
)
