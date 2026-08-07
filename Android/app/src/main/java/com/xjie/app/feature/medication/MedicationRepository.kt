package com.xjie.app.feature.medication

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.Medication
import com.xjie.app.core.model.MedicationBody
import com.xjie.app.core.model.MedicationRecognizeBody
import com.xjie.app.core.model.MedicationRecognizeResult
import com.xjie.app.core.model.MedicationDoseActionBody
import com.xjie.app.core.model.MedicationDoseEvent
import com.xjie.app.core.model.MedicationPlanConfirmBody
import com.xjie.app.core.model.MedicationPlanReviseBody
import com.xjie.app.core.model.MedicationPlanStatusBody
import com.xjie.app.core.model.MedicationPrefillCandidate
import com.xjie.app.core.model.MedicationPrefillList
import com.xjie.app.core.model.MedicationPrefillRejectBody
import com.xjie.app.core.model.MedicationReaction
import com.xjie.app.core.model.MedicationReactionCorrectBody
import com.xjie.app.core.model.MedicationReactionCreateBody
import com.xjie.app.core.model.MedicationReactionList
import com.xjie.app.core.model.MedicationReactionRetractBody
import com.xjie.app.core.model.MedicationRecognizePrefillBody
import com.xjie.app.core.model.MedicationRecognizePrefillResult
import com.xjie.app.core.model.MedicationTodaySummary
import com.xjie.app.core.model.SimpleOk
import com.xjie.app.core.model.TrustedMedicationPlan
import com.xjie.app.core.model.TrustedMedicationPlanList
import com.xjie.app.core.network.api.MedicationApi
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepository @Inject constructor(
    private val api: MedicationApi,
    private val json: Json,
) {
    suspend fun list(owner: AuthManager.AccountScopeSnapshot): List<Medication> =
        safeApiCall(json) { api.list(owner) }.items

    suspend fun create(
        owner: AuthManager.AccountScopeSnapshot,
        body: MedicationBody,
    ): Medication = safeApiCall(json) { api.create(owner, body) }

    suspend fun update(
        owner: AuthManager.AccountScopeSnapshot,
        id: Long,
        body: MedicationBody,
    ): Medication = safeApiCall(json) { api.update(owner, id, body) }

    suspend fun delete(owner: AuthManager.AccountScopeSnapshot, id: Long): SimpleOk =
        safeApiCall(json) { api.delete(owner, id) }

    suspend fun recognize(
        owner: AuthManager.AccountScopeSnapshot,
        rawText: String,
    ): MedicationRecognizeResult = safeApiCall(json) {
        api.recognize(owner, MedicationRecognizeBody(rawText))
    }

    suspend fun trustedToday(
        owner: AuthManager.AccountScopeSnapshot,
        localDate: String,
        timezoneOffsetMinutes: Int,
        subjectUserId: Long? = null,
    ): MedicationTodaySummary = safeApiCall(json) {
        api.trustedToday(
            owner = owner,
            subjectUserId = subjectUserId,
            localDate = localDate,
            timezoneOffsetMinutes = timezoneOffsetMinutes,
        )
    }

    suspend fun trustedPlans(
        owner: AuthManager.AccountScopeSnapshot,
        subjectUserId: Long? = null,
    ): TrustedMedicationPlanList = safeApiCall(json) {
        api.trustedPlans(owner, subjectUserId)
    }

    suspend fun trustedPrefills(
        owner: AuthManager.AccountScopeSnapshot,
        subjectUserId: Long? = null,
    ): MedicationPrefillList = safeApiCall(json) {
        api.trustedPrefills(owner, subjectUserId)
    }

    suspend fun trustedReactions(
        owner: AuthManager.AccountScopeSnapshot,
        subjectUserId: Long? = null,
    ): MedicationReactionList = safeApiCall(json) {
        api.trustedReactions(owner, subjectUserId)
    }

    suspend fun recognizePrefill(
        owner: AuthManager.AccountScopeSnapshot,
        body: MedicationRecognizePrefillBody,
    ): MedicationRecognizePrefillResult = safeApiCall(json) { api.recognizePrefill(owner, body) }

    suspend fun confirmTrustedPlan(
        owner: AuthManager.AccountScopeSnapshot,
        body: MedicationPlanConfirmBody,
    ): TrustedMedicationPlan = safeApiCall(json) { api.confirmTrustedPlan(owner, body) }

    suspend fun reviseTrustedPlan(
        owner: AuthManager.AccountScopeSnapshot,
        planId: Long,
        body: MedicationPlanReviseBody,
    ): TrustedMedicationPlan = safeApiCall(json) { api.reviseTrustedPlan(owner, planId, body) }

    suspend fun updateTrustedPlanStatus(
        owner: AuthManager.AccountScopeSnapshot,
        planId: Long,
        body: MedicationPlanStatusBody,
    ): TrustedMedicationPlan = safeApiCall(json) {
        api.updateTrustedPlanStatus(owner, planId, body)
    }

    suspend fun rejectPrefill(
        owner: AuthManager.AccountScopeSnapshot,
        candidateId: Long,
        body: MedicationPrefillRejectBody,
    ): MedicationPrefillCandidate = safeApiCall(json) {
        api.rejectPrefill(owner, candidateId, body)
    }

    suspend fun recordDoseAction(
        owner: AuthManager.AccountScopeSnapshot,
        body: MedicationDoseActionBody,
    ): MedicationDoseEvent = safeApiCall(json) { api.recordDoseAction(owner, body) }

    suspend fun createReaction(
        owner: AuthManager.AccountScopeSnapshot,
        body: MedicationReactionCreateBody,
    ): MedicationReaction = safeApiCall(json) { api.createReaction(owner, body) }

    suspend fun correctReaction(
        owner: AuthManager.AccountScopeSnapshot,
        reactionKey: String,
        body: MedicationReactionCorrectBody,
    ): MedicationReaction = safeApiCall(json) { api.correctReaction(owner, reactionKey, body) }

    suspend fun retractReaction(
        owner: AuthManager.AccountScopeSnapshot,
        reactionKey: String,
        body: MedicationReactionRetractBody,
    ): MedicationReaction = safeApiCall(json) { api.retractReaction(owner, reactionKey, body) }
}
