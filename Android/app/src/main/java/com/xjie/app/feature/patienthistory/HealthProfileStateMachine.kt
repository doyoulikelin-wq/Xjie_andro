package com.xjie.app.feature.patienthistory

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.HealthProfileRevisionList

/** Immutable owner identity for every Profile coroutine and response. */
internal data class HealthProfileOwner(
    val accountScope: String,
    val selectedSubject: String,
    val authGeneration: Long,
) {
    companion object {
        fun from(snapshot: AuthManager.AccountScopeSnapshot): HealthProfileOwner =
            HealthProfileOwner(
                accountScope = snapshot.accountScope,
                selectedSubject = snapshot.subjectId,
                authGeneration = snapshot.generation,
            )
    }
}

internal data class HealthProfileRequestToken(
    val owner: HealthProfileOwner,
    val sequence: Long,
)

/**
 * Pure admission state machine shared by load, medication, mutation and history paths.
 * A response can commit only to the exact account + selected subject + generation + request that
 * started it. The subject returned by the server is checked separately and never inferred locally.
 */
internal object HealthProfileStateMachine {
    fun acceptsProfile(
        token: HealthProfileRequestToken,
        activeToken: HealthProfileRequestToken?,
        currentOwner: HealthProfileOwner?,
        expectedServerSubject: Long?,
        responseSubject: Long,
    ): Boolean = token == activeToken &&
        token.owner == currentOwner &&
        responseSubject > 0L &&
        (expectedServerSubject == null || expectedServerSubject == responseSubject)

    fun acceptsRevision(
        token: HealthProfileRequestToken,
        activeToken: HealthProfileRequestToken?,
        currentOwner: HealthProfileOwner?,
        expectedSubject: Long,
        expectedKind: String,
        expectedTargetId: Long,
        response: HealthProfileRevisionList,
    ): Boolean = acceptsProfile(
        token = token,
        activeToken = activeToken,
        currentOwner = currentOwner,
        expectedServerSubject = expectedSubject,
        responseSubject = response.subject_user_id,
    ) && response.target_kind == expectedKind && response.target_id == expectedTargetId

    fun mergeRevisionPages(
        current: HealthProfileRevisionList,
        next: HealthProfileRevisionList,
    ): HealthProfileRevisionList {
        require(current.subject_user_id == next.subject_user_id)
        require(current.target_kind == next.target_kind)
        require(current.target_id == next.target_id)
        val known = current.items.mapTo(mutableSetOf()) { it.revision_id }
        return next.copy(items = current.items + next.items.filter { known.add(it.revision_id) })
    }
}
