package com.xjie.app.feature.medication

import com.xjie.app.core.model.MedicationInventoryEstimate
import com.xjie.app.core.model.MedicationDoseEvent
import com.xjie.app.core.model.MedicationPrefillCandidate
import com.xjie.app.core.model.MedicationTodaySummary
import com.xjie.app.core.model.MedicationTodayTask
import com.xjie.app.core.model.MedicationRecognizePrefillResult
import com.xjie.app.core.model.TrustedMedicationPlan
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationTrustPolicyTest {
    @Test
    fun possiblyMissedIsAlwaysPresentedAsPendingAndNeverConfirmedMissed() {
        val task = task(status = "possibly_missed")

        assertEquals("可能漏服（待确认）", MedicationTrustPolicy.taskStatusLabel(task))
        assertTrue(task.possibly_missed_is_not_confirmation)
        assertFalse(MedicationTrustPolicy.taskStatusLabel(task).contains("漏服已确认"))
        assertTrue(MedicationTrustPolicy.canRecordDose(task))
    }

    @Test
    fun singlePrimaryActionPrioritizesCurrentDoseThenReviewThenFirstPlan() {
        val pending = candidate()
        val current = today(next = task(status = "awaiting_confirmation"))

        assertEquals(
            MedicationPrimaryAction.ConfirmCurrentDose,
            MedicationTrustPolicy.primaryAction(current, listOf(pending), emptyList()),
        )
        assertEquals(
            MedicationPrimaryAction.ReviewPrefill,
            MedicationTrustPolicy.primaryAction(today(next = null), listOf(pending), emptyList()),
        )
        assertEquals(
            MedicationPrimaryAction.AddFirstMedication,
            MedicationTrustPolicy.primaryAction(today(next = null), emptyList(), emptyList()),
        )
        assertEquals(
            MedicationPrimaryAction.ViewTodayRecords,
            MedicationTrustPolicy.primaryAction(today(next = null), emptyList(), listOf(plan())),
        )
    }

    @Test
    fun doseActionUsesServerPlanAndOccurrenceVersionsWithStableEvent() {
        val task = task(status = "awaiting_confirmation").copy(
            plan_version = 7,
            occurrence_version = 4,
        )
        val body = MedicationTrustPolicy.buildDoseAction(
            subjectUserId = 99,
            task = task,
            clientEventId = "same-event-on-retry",
            action = "snooze",
            snoozedUntil = "2026-07-15T20:15:00+08:00",
        )

        assertEquals(7, body.expected_plan_version)
        assertEquals(4, body.expected_occurrence_version)
        assertEquals("same-event-on-retry", body.client_event_id)
        assertEquals("2026-07-15T20:15:00+08:00", body.snoozed_until)
        assertNull(body.corrected_status)
    }

    @Test
    fun sameDayCorrectionPinsLatestEventAndCannotOverwriteImplicitly() {
        val confirmed = task(status = "taken").copy(
            status_assertion = "user_confirmed",
            occurrence_version = 3,
            latest_event_id = 42,
        )
        val body = MedicationTrustPolicy.buildDoseCorrection(
            subjectUserId = 99,
            task = confirmed,
            clientEventId = "correction-event",
            correctedStatus = "pending",
            reason = "刚才点错了",
        )

        assertEquals("correct", body.action)
        assertEquals(42L, body.correction_of_event_id)
        assertEquals(3, body.expected_plan_version)
        assertEquals(3, body.expected_occurrence_version)
        assertEquals("correction-event", body.client_event_id)
        assertTrue(
            MedicationTrustPolicy.isTrustedDoseEvent(
                MedicationDoseEvent(
                    event_id = 43,
                    occurrence_key = "dose:v1:11:2026-07-15:20:00",
                    occurrence_version = 4,
                    action = "correct",
                    effective_status = "pending",
                    supersedes_event_id = 42,
                    confirmed_at = "2026-07-15T20:02:00+08:00",
                    trust_state = "user_confirmed",
                    notification_schedule_status = "not_requested",
                    reminder_management = "client_managed",
                ),
                body,
            ),
        )
        assertFalse(
            MedicationTrustPolicy.isTrustedDoseEvent(
                MedicationDoseEvent(
                    event_id = 44,
                    occurrence_key = "dose:v1:11:2026-07-15:20:00",
                    occurrence_version = 4,
                    action = "correct",
                    effective_status = "pending",
                    supersedes_event_id = 41,
                    confirmed_at = "2026-07-15T20:02:00+08:00",
                    trust_state = "user_confirmed",
                    notification_schedule_status = "not_requested",
                    reminder_management = "client_managed",
                ),
                body,
            ),
        )
    }

    @Test
    fun failedRetryKeepsEventIdentityUntilSuccessfulCompletion() {
        var sequence = 0
        val ids = StableMedicationEventIds { prefix -> "$prefix-${++sequence}" }

        val firstAttempt = ids.getOrCreate("dose-key", "dose")
        val failedRetry = ids.getOrCreate("dose-key", "dose")
        assertEquals(firstAttempt, failedRetry)

        ids.complete("dose-key")
        val nextIndependentAction = ids.getOrCreate("dose-key", "dose")
        assertFalse(firstAttempt == nextIndependentAction)
    }

    @Test
    fun ocrPrefillRequiresCandidateVersionAndExplicitPlanConfirmation() {
        val candidate = candidate()
        val draft = MedicationTrustPolicy.draftFrom(candidate)
        val body = MedicationTrustPolicy.buildPlanConfirmation(
            subjectUserId = candidate.subject_user_id,
            draft = draft,
            clientEventId = "confirm-event",
            candidate = candidate,
        )

        assertEquals(candidate.candidate_id, body.candidate_id)
        assertEquals(candidate.version, body.candidate_version)
        assertEquals("ocr", body.source_type)
        assertEquals("阿托伐他汀", body.generic_name)
        assertEquals(listOf("08:00", "20:00"), body.schedule_times)
        assertTrue(candidate.requires_user_confirmation)
        assertFalse(candidate.plan_created)
    }

    @Test
    fun mutationResponsesFailClosedBeforeShowingSuccess() {
        val request = MedicationTrustPolicy.buildDoseAction(
            subjectUserId = 99,
            task = task(status = "awaiting_confirmation"),
            clientEventId = "dose-event",
            action = "taken",
        )
        val trustedDose = MedicationDoseEvent(
            event_id = 1,
            occurrence_key = "dose:v1:11:2026-07-15:20:00",
            occurrence_version = 3,
            action = "taken",
            effective_status = "taken",
            confirmed_at = "2026-07-15T20:01:00+08:00",
            trust_state = "user_confirmed",
            notification_schedule_status = "not_requested",
            reminder_management = "client_managed",
        )
        assertTrue(MedicationTrustPolicy.isTrustedDoseEvent(trustedDose, request))
        assertFalse(
            MedicationTrustPolicy.isTrustedDoseEvent(
                trustedDose.copy(occurrence_key = "dose:v1:other-subject"),
                request,
            ),
        )

        val recognized = MedicationRecognizePrefillResult(
            candidate_id = 8,
            candidate_version = 1,
            client_event_id = "ocr-event",
            trust_state = "unconfirmed_prefill",
            requires_user_confirmation = true,
            plan_created = false,
            confirmation_endpoint = "/api/medications/trust/plans/confirm",
        )
        assertTrue(MedicationTrustPolicy.isUnconfirmedRecognizeResult(recognized, "ocr-event"))
        assertFalse(
            MedicationTrustPolicy.isUnconfirmedRecognizeResult(
                recognized.copy(plan_created = true),
                "ocr-event",
            ),
        )
    }

    @Test
    fun inventoryPresentationFailsClosedUnlessServerMarksEstimateAndConfirmedBasis() {
        val trusted = plan()
        assertEquals(
            "预计剩余：18 片（仅按已确认服药记录估算）",
            MedicationTrustPolicy.inventoryLine(trusted),
        )

        val untrusted = trusted.copy(
            inventory = trusted.inventory.copy(
                label = "准确库存",
                basis = "schedule_only",
            ),
        )
        assertEquals(
            "预计剩余：暂不可用（仅能按已确认服药记录估算）",
            MedicationTrustPolicy.inventoryLine(untrusted),
        )
    }

    @Test
    fun manualPlanValidationKeepsReminderTimesDatesAndInventoryPairsExplicit() {
        val valid = MedicationTrustPolicy.validatePlanDraft(
            MedicationPlanDraft(
                genericName = "药品",
                scheduleTimes = "20:00、08:00、20:00",
                courseStart = "2026-07-15",
                courseEnd = "2026-07-30",
                initialQuantity = "30",
                inventoryUnit = "片",
            ),
        )
        assertTrue(valid.isValid)
        assertEquals(listOf("08:00", "20:00"), valid.normalizedTimes)

        assertFalse(
            MedicationTrustPolicy.validatePlanDraft(
                MedicationPlanDraft(genericName = "药品", scheduleTimes = "25:00"),
            ).isValid,
        )
        assertFalse(
            MedicationTrustPolicy.validatePlanDraft(
                MedicationPlanDraft(genericName = "药品", initialQuantity = "30"),
            ).isValid,
        )
    }

    @Test
    fun trustedSnapshotFailsClosedOnMissingConfirmationOrReminderEvidence() {
        val confirmedTask = task(status = "taken")
        val trustedToday = today(next = confirmedTask)
        assertTrue(
            MedicationTrustPolicy.isTrustedSnapshot(
                trustedToday,
                listOf(plan()),
                emptyList(),
                emptyList(),
            ),
        )

        val scheduleDerivedTaken = confirmedTask.copy(status_assertion = "schedule_derived")
        assertFalse(
            MedicationTrustPolicy.isTrustedSnapshot(
                today(next = scheduleDerivedTaken),
                listOf(plan()),
                emptyList(),
                emptyList(),
            ),
        )
        assertFalse(
            MedicationTrustPolicy.isTrustedSnapshot(
                trustedToday,
                listOf(plan().copy(reminder_default_enabled = true)),
                emptyList(),
                emptyList(),
            ),
        )
    }

    private fun today(next: MedicationTodayTask?) = MedicationTodaySummary(
        subject_user_id = 99,
        local_date = "2026-07-15",
        planned_count = if (next == null) 0 else 1,
        taken_count = 0,
        awaiting_confirmation_count = if (next == null) 0 else 1,
        possibly_missed_count = 0,
        skipped_count = 0,
        snoozed_count = 0,
        adverse_reaction_count = 0,
        next_task = next,
        tasks = listOfNotNull(next),
        missed_assertion_policy = "elapsed_time_never_confirms_missed",
    )

    private fun task(status: String) = MedicationTodayTask(
        occurrence_key = "plan:11:2026-07-15:20:00",
        plan_id = 11,
        plan_version = 3,
        generic_name = "阿托伐他汀",
        dose_text = "20mg",
        scheduled_local_date = "2026-07-15",
        scheduled_time = "20:00",
        scheduled_at = "2026-07-15T20:00:00+08:00",
        status = status,
        status_label = status,
        status_assertion = if (status in setOf("taken", "skipped")) "user_confirmed" else "schedule_derived",
        occurrence_version = 2,
        possibly_missed_is_not_confirmation = status == "possibly_missed",
        notification_schedule_status = "client_managed",
    )

    private fun candidate() = MedicationPrefillCandidate(
        candidate_id = 8,
        subject_user_id = 99,
        client_event_id = "ocr-event",
        source_type = "ocr",
        source_ref = "ocr-text-sha256:redacted",
        extracted_data = buildJsonObject {
            put("generic_name", "阿托伐他汀")
            put("dose_text", "20mg")
            put("schedule_times", JsonArray(listOf(JsonPrimitive("20:00"), JsonPrimitive("08:00"))))
        },
        field_confidences = mapOf("generic_name" to 0.72),
        low_confidence_fields = listOf("generic_name"),
        review_status = "pending_review",
        version = 2,
        trust_state = "unconfirmed_prefill",
        requires_user_confirmation = true,
        plan_created = false,
        confirmation_endpoint = "/api/medications/trust/plans/confirm",
    )

    private fun plan() = TrustedMedicationPlan(
        plan_id = 11,
        subject_user_id = 99,
        generic_name = "阿托伐他汀",
        schedule_times = listOf("20:00"),
        source_type = "manual",
        source_ref = "manual",
        status = "active",
        version = 3,
        confirmed_at = "2026-07-15T10:00:00+08:00",
        trust_state = "user_confirmed",
        reminder_management = "client_managed",
        reminder_default_enabled = false,
        server_notification_scheduled = false,
        inventory = MedicationInventoryEstimate(
            is_estimate = true,
            label = "预计剩余",
            estimated_remaining = 18.0,
            estimated_consumed = 12.0,
            inventory_unit = "片",
            basis = "user_confirmed_taken_events_only",
        ),
    )
}
