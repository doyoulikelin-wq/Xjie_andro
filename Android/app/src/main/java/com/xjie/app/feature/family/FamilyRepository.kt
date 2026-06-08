package com.xjie.app.feature.family

import com.xjie.app.core.model.FamilyCareEvent
import com.xjie.app.core.model.FamilyCareEventCreateBody
import com.xjie.app.core.model.FamilyGroup
import com.xjie.app.core.model.FamilyGroupCreateBody
import com.xjie.app.core.model.FamilyInvite
import com.xjie.app.core.model.FamilyInviteAcceptBody
import com.xjie.app.core.model.FamilyInviteCreateBody
import com.xjie.app.core.model.FamilyMember
import com.xjie.app.core.model.FamilyPermission
import com.xjie.app.core.model.FamilyPermissionPatchBody
import com.xjie.app.core.model.FamilySubject
import com.xjie.app.core.model.FamilySubjectSummary
import com.xjie.app.core.model.UserInfo
import com.xjie.app.core.network.api.FamilyApi
import com.xjie.app.core.network.api.UserApi
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyRepository @Inject constructor(
    private val familyApi: FamilyApi,
    private val userApi: UserApi,
    private val json: Json,
) {
    suspend fun me(): UserInfo = safeApiCall(json) { userApi.me() }
    suspend fun groups(): List<FamilyGroup> = safeApiCall(json) { familyApi.groups() }
    suspend fun createGroup(): FamilyGroup = safeApiCall(json) {
        familyApi.createGroup(FamilyGroupCreateBody("我的家庭"))
    }
    suspend fun members(): List<FamilyMember> = safeApiCall(json) { familyApi.members() }
    suspend fun createInvite(groupId: Long?, phone: String?, relation: String?): FamilyInvite = safeApiCall(json) {
        familyApi.createInvite(
            FamilyInviteCreateBody(
                group_id = groupId,
                target_phone = phone?.takeIf { it.isNotBlank() },
                relation = relation?.takeIf { it.isNotBlank() },
            )
        )
    }
    suspend fun acceptInvite(code: String, displayName: String?): FamilyMember = safeApiCall(json) {
        familyApi.acceptInvite(
            FamilyInviteAcceptBody(
                invite_code = code.trim().uppercase(),
                display_name = displayName?.takeIf { it.isNotBlank() },
            )
        )
    }
    suspend fun subjects(): List<FamilySubject> = safeApiCall(json) { familyApi.subjects() }
    suspend fun summary(subjectId: Long): FamilySubjectSummary = safeApiCall(json) { familyApi.summary(subjectId) }
    suspend fun permission(viewerUserId: Long): FamilyPermission = safeApiCall(json) { familyApi.permission(viewerUserId) }
    suspend fun updatePermission(viewerUserId: Long, body: FamilyPermissionPatchBody): FamilyPermission = safeApiCall(json) {
        familyApi.updatePermission(viewerUserId, body)
    }
    suspend fun createCareEvent(subjectId: Long, type: String, message: String?): FamilyCareEvent = safeApiCall(json) {
        familyApi.createCareEvent(FamilyCareEventCreateBody(subjectId, type, message))
    }
}
