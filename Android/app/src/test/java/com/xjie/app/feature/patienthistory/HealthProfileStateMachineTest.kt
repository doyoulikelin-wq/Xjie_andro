package com.xjie.app.feature.patienthistory

import com.xjie.app.core.model.HealthProfileRevisionItem
import com.xjie.app.core.model.HealthProfileRevisionList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthProfileStateMachineTest {
    @Test
    fun accountSubjectGenerationAndRequestSequenceMustAllMatchBeforeProfileCommit() {
        val firstA = HealthProfileOwner("account-a", "subject-1", 4)
        val accountB = HealthProfileOwner("account-b", "subject-1", 5)
        val returnedA = HealthProfileOwner("account-a", "subject-1", 6)
        val changedSubject = HealthProfileOwner("account-a", "subject-2", 5)
        val token = HealthProfileRequestToken(firstA, 10)

        assertTrue(HealthProfileStateMachine.acceptsProfile(token, token, firstA, 9, 9))
        assertFalse(HealthProfileStateMachine.acceptsProfile(token, token, accountB, 9, 9))
        assertFalse(HealthProfileStateMachine.acceptsProfile(token, token, returnedA, 9, 9))
        assertFalse(HealthProfileStateMachine.acceptsProfile(token, token, changedSubject, 9, 9))
        assertFalse(HealthProfileStateMachine.acceptsProfile(token, token.copy(sequence = 11), firstA, 9, 9))
        assertFalse(HealthProfileStateMachine.acceptsProfile(token, token, firstA, 9, 10))
        assertFalse(HealthProfileStateMachine.acceptsProfile(token, token, firstA, null, 0))
    }

    @Test
    fun revisionTargetAndSubjectMustMatchAndPaginationDeduplicatesRevisionIds() {
        val owner = HealthProfileOwner("account-a", "subject-1", 4)
        val token = HealthProfileRequestToken(owner, 10)
        val first = history(listOf(1, 2), next = 2)
        val next = history(listOf(2, 3), next = null)

        assertTrue(
            HealthProfileStateMachine.acceptsRevision(
                token,
                token,
                owner,
                expectedSubject = 9,
                expectedKind = "fact",
                expectedTargetId = 40,
                response = first,
            ),
        )
        assertFalse(
            HealthProfileStateMachine.acceptsRevision(
                token,
                token,
                owner,
                expectedSubject = 9,
                expectedKind = "goal",
                expectedTargetId = 40,
                response = first,
            ),
        )
        val merged = HealthProfileStateMachine.mergeRevisionPages(first, next)
        assertEquals(listOf(1L, 2L, 3L), merged.items.map { it.revision_id })
        assertEquals(null, merged.next_after_revision_id)
    }

    private fun history(ids: List<Long>, next: Long?) = HealthProfileRevisionList(
        subject_user_id = 9,
        target_kind = "fact",
        target_id = 40,
        items = ids.map {
            HealthProfileRevisionItem(
                revision_id = it,
                event_type = "updated",
                target_version = it.toInt(),
                created_at = "2026-07-15T08:00:00Z",
            )
        },
        next_after_revision_id = next,
    )
}
