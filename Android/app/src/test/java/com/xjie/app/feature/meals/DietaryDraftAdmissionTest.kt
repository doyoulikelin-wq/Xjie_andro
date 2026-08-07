package com.xjie.app.feature.meals

import com.xjie.app.core.model.DietaryAdmissionPolicy
import com.xjie.app.core.model.DietaryFoodItem
import com.xjie.app.core.model.DietaryMealDraft
import com.xjie.app.core.model.DietaryMealRecord
import com.xjie.app.core.network.api.MealsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.POST
import retrofit2.http.PUT

class DietaryDraftAdmissionTest {
    @Test
    fun recognitionAndReuseStayPendingUntilExplicitVersionedConfirmation() {
        val recognized = draft(source = "photo_library")
        val reused = draft(source = "recent")

        assertTrue(DietaryAdmissionPolicy.acceptsPendingDraft(recognized, 7L))
        assertTrue(DietaryAdmissionPolicy.reuseRemainsPending(reused, 7L))
        assertFalse(
            DietaryAdmissionPolicy.acceptsPendingDraft(
                recognized.copy(formal_record_created = true),
                7L,
            ),
        )
        assertFalse(
            DietaryAdmissionPolicy.acceptsPendingDraft(
                recognized.copy(status = "confirmed"),
                7L,
            ),
        )
    }

    @Test
    fun onlyTrustedExplicitConfirmationIsAcceptedAsAFormalRecord() {
        val confirmed = record()

        assertTrue(DietaryAdmissionPolicy.acceptsFormalRecord(confirmed, 7L))
        assertFalse(DietaryAdmissionPolicy.acceptsFormalRecord(confirmed.copy(trust_state = null), 7L))
        assertFalse(DietaryAdmissionPolicy.acceptsFormalRecord(confirmed.copy(status = "deleted"), 7L))
        assertFalse(DietaryAdmissionPolicy.acceptsFormalRecord(confirmed, 8L))
    }

    @Test
    fun retrofitPinsDraftPhotoAndConfirmRoutesInsteadOfLegacyMealWrites() {
        val create = MealsApi::class.java.declaredMethods.single { it.name == "createDietaryDraft" }
        val photo = MealsApi::class.java.declaredMethods.single { it.name == "createDietaryPhotoDraft" }
        val confirm = MealsApi::class.java.declaredMethods.single { it.name == "confirmDietaryDraft" }

        assertEquals(
            "api/dietary-records/drafts",
            requireNotNull(create.getAnnotation(POST::class.java)).value,
        )
        assertEquals(
            "api/dietary-records/drafts/photo",
            requireNotNull(photo.getAnnotation(PUT::class.java)).value,
        )
        assertEquals(
            "api/dietary-records/drafts/{draftId}/confirm",
            requireNotNull(confirm.getAnnotation(POST::class.java)).value,
        )
    }

    private fun draft(source: String) = DietaryMealDraft(
        draft_id = 11L,
        subject_user_id = 7L,
        source_type = source,
        diet_date = "2026-07-15",
        meal_type = "lunch",
        eaten_at = "2026-07-15T12:30:00+08:00",
        food_items = listOf(DietaryFoodItem(name = "番茄炒蛋")),
        recognition_status = "completed",
        status = "pending_confirmation",
        version = 2,
        requires_user_confirmation = true,
        formal_record_created = false,
    )

    private fun record() = DietaryMealRecord(
        record_id = 21L,
        source_draft_id = 11L,
        subject_user_id = 7L,
        diet_date = "2026-07-15",
        meal_type = "lunch",
        eaten_at = "2026-07-15T12:30:00+08:00",
        source_type = "photo_library",
        food_items = listOf(DietaryFoodItem(name = "番茄炒蛋")),
        status = "user_confirmed",
        version = 1,
        trust_state = "user_confirmed",
    )
}
