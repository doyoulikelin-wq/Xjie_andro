package com.xjie.app.core.network.api

import com.xjie.app.core.model.Citation
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface LiteratureApi {
    @POST("api/literature/retrieve")
    suspend fun retrieve(@Body body: RetrievalRequest): RetrievalResponse
}

@Serializable
data class RetrievalRequest(
    val query: String,
    val topics: List<String>? = null,
    val min_evidence_level: String = "L4",
    val top_k: Int = 5,
)

@Serializable
data class RetrievalResponse(
    val matches: List<Citation> = emptyList(),
    val used_fallback: Boolean = false,
)
