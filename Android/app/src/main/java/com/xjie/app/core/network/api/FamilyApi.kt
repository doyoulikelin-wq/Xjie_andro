package com.xjie.app.core.network.api

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
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface FamilyApi {
    @GET("api/family/groups")
    suspend fun groups(): List<FamilyGroup>

    @POST("api/family/groups")
    suspend fun createGroup(@Body body: FamilyGroupCreateBody): FamilyGroup

    @GET("api/family/members")
    suspend fun members(): List<FamilyMember>

    @POST("api/family/invites")
    suspend fun createInvite(@Body body: FamilyInviteCreateBody): FamilyInvite

    @POST("api/family/invites/accept")
    suspend fun acceptInvite(@Body body: FamilyInviteAcceptBody): FamilyMember

    @GET("api/family/subjects")
    suspend fun subjects(): List<FamilySubject>

    @GET("api/family/subjects/{subjectId}/summary")
    suspend fun summary(@Path("subjectId") subjectId: Long): FamilySubjectSummary

    @GET("api/family/permissions/{viewerUserId}")
    suspend fun permission(@Path("viewerUserId") viewerUserId: Long): FamilyPermission

    @PATCH("api/family/permissions/{viewerUserId}")
    suspend fun updatePermission(
        @Path("viewerUserId") viewerUserId: Long,
        @Body body: FamilyPermissionPatchBody,
    ): FamilyPermission

    @POST("api/family/care-events")
    suspend fun createCareEvent(@Body body: FamilyCareEventCreateBody): FamilyCareEvent
}
