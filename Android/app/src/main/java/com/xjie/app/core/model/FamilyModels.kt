package com.xjie.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FamilyGroup(
    val id: Long,
    val name: String,
    val owner_user_id: Long,
    val created_at: String,
)

@Serializable
data class FamilyMember(
    val id: Long,
    val group_id: Long,
    val user_id: Long,
    val role: String,
    val relation: String? = null,
    val display_name: String? = null,
    val status: String,
    val phone: String? = null,
    val username: String? = null,
    val profile_name: String? = null,
    val created_at: String,
) {
    val bestName: String
        get() = profile_name ?: display_name ?: username ?: phone ?: "家庭成员"
}

@Serializable
data class FamilyPermission(
    val id: Long? = null,
    val subject_user_id: Long,
    val viewer_user_id: Long,
    val can_view_glucose_detail: Boolean = false,
    val can_view_medication: Boolean = false,
    val can_view_health_data: Boolean = false,
    val can_view_documents: Boolean = false,
    val can_view_omics: Boolean = false,
    val can_view_ai_summary: Boolean = false,
) {
    companion object {
        fun empty(subject: Long, viewer: Long) = FamilyPermission(
            subject_user_id = subject,
            viewer_user_id = viewer,
        )
    }
}

@Serializable
data class FamilyInvite(
    val id: Long,
    val group_id: Long,
    val invite_code: String,
    val target_phone: String? = null,
    val relation: String? = null,
    val role: String,
    val status: String,
    val expires_at: String,
    val created_at: String,
)

@Serializable
data class FamilySubject(
    val user_id: Long,
    val display_name: String,
    val relation: String? = null,
    val group_id: Long? = null,
    val member_id: Long? = null,
    val permissions: FamilyPermission,
)

@Serializable
data class FamilyHealthStatus(
    val level: String,
    val reading_count: Int = 0,
    val avg: Double? = null,
    val tir_70_180_pct: Double? = null,
    val min: Int? = null,
    val max: Int? = null,
) {
    val levelLabel: String
        get() = when (level) {
            "stable" -> "稳定"
            "watch" -> "需留意"
            "risk" -> "需关注"
            else -> "待补数据"
        }
}

@Serializable
data class FamilyPlanSummary(
    val date: String,
    val tasks_total: Int = 0,
    val tasks_completed: Int = 0,
    val completion_pct: Int = 0,
)

@Serializable
data class FamilyCareSummary(
    val today_checkins: Int = 0,
    val last_checkin_at: String? = null,
    val pending_care_events: Int = 0,
)

@Serializable
data class FamilySubjectSummary(
    val subject: FamilySubject,
    val health_status: FamilyHealthStatus,
    val plan: FamilyPlanSummary,
    val care: FamilyCareSummary,
    val permissions: FamilyPermission,
    val alerts: List<String> = emptyList(),
    val generated_at: String,
)

@Serializable
data class FamilyCareEvent(
    val id: Long,
    val subject_user_id: Long,
    val actor_user_id: Long,
    val event_type: String,
    val message: String? = null,
    val status: String,
    val created_at: String,
    val handled_at: String? = null,
)

@Serializable
data class FamilyGroupCreateBody(val name: String)

@Serializable
data class FamilyInviteCreateBody(
    val group_id: Long? = null,
    val target_phone: String? = null,
    val relation: String? = null,
    val role: String = "member",
)

@Serializable
data class FamilyInviteAcceptBody(
    val invite_code: String,
    val display_name: String? = null,
)

@Serializable
data class FamilyPermissionPatchBody(
    val can_view_glucose_detail: Boolean? = null,
    val can_view_medication: Boolean? = null,
    val can_view_health_data: Boolean? = null,
    val can_view_documents: Boolean? = null,
    val can_view_omics: Boolean? = null,
    val can_view_ai_summary: Boolean? = null,
)

enum class FamilyPermissionField(val title: String, val subtitle: String) {
    GLUCOSE_DETAIL("血糖明细", "允许查看平均值、TIR、最高/最低值"),
    MEDICATION("用药信息", "允许查看用药提醒和用药信息"),
    HEALTH_DATA("健康数据", "允许查看健康指标统计"),
    DOCUMENTS("病例/体检原始资料", "敏感资料，需单独授权"),
    OMICS("多组学数据", "默认关闭，真实数据上传后才建议授权"),
    AI_SUMMARY("AI 健康总结", "允许查看 AI 整理出的总结"),
}

fun familyPermissionPatch(field: FamilyPermissionField, value: Boolean): FamilyPermissionPatchBody =
    FamilyPermissionPatchBody(
        can_view_glucose_detail = if (field == FamilyPermissionField.GLUCOSE_DETAIL) value else null,
        can_view_medication = if (field == FamilyPermissionField.MEDICATION) value else null,
        can_view_health_data = if (field == FamilyPermissionField.HEALTH_DATA) value else null,
        can_view_documents = if (field == FamilyPermissionField.DOCUMENTS) value else null,
        can_view_omics = if (field == FamilyPermissionField.OMICS) value else null,
        can_view_ai_summary = if (field == FamilyPermissionField.AI_SUMMARY) value else null,
    )

@Serializable
data class FamilyCareEventCreateBody(
    val subject_user_id: Long,
    val event_type: String,
    val message: String? = null,
)
