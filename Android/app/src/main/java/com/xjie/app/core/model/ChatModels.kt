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
    val population: String? = null,
    val study_design: String? = null,
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
    val client_message_id: String? = null,
)

@Serializable
data class ChatInteractionRoute(
    val version: String,
    val route_id: String,
    val strategy: String,
    val primary_intent: String,
    val depth: String,
    val safety_level: String,
    val subject_type: String,
    val needs_literature: Boolean,
    val max_followups: Int,
    val progress_steps: List<String> = emptyList(),
)

@Serializable
data class ChatResponse(
    val summary: String? = null,
    val analysis: String? = null,
    val answer_markdown: String? = null,
    val confidence: Double? = null,
    val followups: List<String>? = null,
    val thread_id: String? = null,
    val message_id: String? = null,
    val response_state: String? = null,
    val interaction_route: ChatInteractionRoute? = null,
    val quality_flags: List<String>? = null,
    val citations: List<Citation>? = null,
)

@Serializable
data class ChatStreamEnvelope(
    val type: String,
    val route: ChatInteractionRoute? = null,
    val step: String? = null,
    val delta: String? = null,
    val result: ChatResponse? = null,
    val message: String? = null,
    val retryable: Boolean? = null,
)

sealed interface ChatStreamEvent {
    data class Route(val route: ChatInteractionRoute) : ChatStreamEvent
    data class Progress(val step: String) : ChatStreamEvent
    data class Token(val delta: String) : ChatStreamEvent
    data class Done(val result: ChatResponse) : ChatStreamEvent
}

@Serializable
data class ConsentUpdate(val allow_ai_chat: Boolean)

@Serializable
data class ConsentResponse(
    val allow_ai_chat: Boolean,
    val allow_data_upload: Boolean? = null,
)
