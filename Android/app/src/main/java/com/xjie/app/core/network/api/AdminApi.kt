package com.xjie.app.core.network.api

import com.xjie.app.core.model.AdminFeatureFlag
import com.xjie.app.core.model.AdminFeatureFlagCreate
import com.xjie.app.core.model.AdminFeatureFlagList
import com.xjie.app.core.model.AdminFeatureFlagUpdate
import com.xjie.app.core.model.AdminSkill
import com.xjie.app.core.model.AdminSkillCreate
import com.xjie.app.core.model.AdminSkillList
import com.xjie.app.core.model.AdminSkillUpdate
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AdminApi {
    @GET("api/admin/stats")
    suspend fun stats(): AdminStats

    @GET("api/admin/users")
    suspend fun users(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50,
    ): List<AdminUserItem>

    @GET("api/admin/conversations")
    suspend fun conversations(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50,
    ): List<AdminConversationItem>

    @GET("api/admin/omics")
    suspend fun omics(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50,
    ): List<AdminOmicsItem>

    @GET("api/admin/token-stats")
    suspend fun tokenStats(): AdminTokenStats

    @GET("api/admin/token-stats/details")
    suspend fun tokenStatsDetails(): AdminTokenDetails

    // Feature flags CRUD
    @GET("api/admin/feature-flags")
    suspend fun listFeatureFlags(): AdminFeatureFlagList

    @POST("api/admin/feature-flags")
    suspend fun createFeatureFlag(@Body body: AdminFeatureFlagCreate): AdminFeatureFlag

    @PATCH("api/admin/feature-flags/{id}")
    suspend fun updateFeatureFlag(
        @Path("id") id: Int,
        @Body body: AdminFeatureFlagUpdate,
    ): AdminFeatureFlag

    @DELETE("api/admin/feature-flags/{id}")
    suspend fun deleteFeatureFlag(@Path("id") id: Int)

    // Skills CRUD
    @GET("api/admin/skills")
    suspend fun listSkills(): AdminSkillList

    @POST("api/admin/skills")
    suspend fun createSkill(@Body body: AdminSkillCreate): AdminSkill

    @PATCH("api/admin/skills/{id}")
    suspend fun updateSkill(
        @Path("id") id: Int,
        @Body body: AdminSkillUpdate,
    ): AdminSkill

    @DELETE("api/admin/skills/{id}")
    suspend fun deleteSkill(@Path("id") id: Int)
}

@Serializable
data class AdminStats(
    val total_users: Int,
    val active_users_7d: Int,
    val total_conversations: Int,
    val total_messages: Int,
    val total_omics_uploads: Int,
    val total_meals: Int,
)

@Serializable
data class FeatureTokenDetail(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0,
    val call_count: Int = 0,
)

@Serializable
data class AdminTokenStats(
    val total_prompt_tokens: Int = 0,
    val total_completion_tokens: Int = 0,
    val total_tokens: Int = 0,
    val total_calls: Int = 0,
    val summary_task_tokens: Int = 0,
    val summary_task_count: Int = 0,
    val by_feature: Map<String, FeatureTokenDetail> = emptyMap(),
)

@Serializable
data class SummaryTaskItem(
    val task_id: String,
    val user_id: Int,
    val username: String? = null,
    val status: String,
    val stage: String? = null,
    val token_used: Int = 0,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class UserTokenItem(
    val user_id: Int,
    val username: String? = null,
    val phone: String,
    val audit_tokens: Int = 0,
    val audit_calls: Int = 0,
    val summary_tokens: Int = 0,
    val summary_calls: Int = 0,
    val total_tokens: Int = 0,
)

@Serializable
data class AdminTokenDetails(
    val by_user: List<UserTokenItem> = emptyList(),
    val recent_tasks: List<SummaryTaskItem> = emptyList(),
)

@Serializable
data class AdminUserItem(
    val id: Int,
    val phone: String,
    val username: String? = null,
    val is_admin: Boolean = false,
    val created_at: String? = null,
    val conversation_count: Int = 0,
    val message_count: Int = 0,
    val last_active: String? = null,
)

@Serializable
data class AdminConversationItem(
    val id: Int,
    val user_id: Int,
    val username: String? = null,
    val title: String? = null,
    val message_count: Int = 0,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class AdminOmicsItem(
    val id: Int,
    val user_id: Int,
    val username: String? = null,
    val omics_type: String,
    val file_name: String? = null,
    val file_size: Long? = null,
    val risk_level: String? = null,
    val llm_summary: String? = null,
    val created_at: String? = null,
)
