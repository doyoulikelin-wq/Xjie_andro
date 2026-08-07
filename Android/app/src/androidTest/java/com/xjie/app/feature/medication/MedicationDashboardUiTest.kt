package com.xjie.app.feature.medication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjie.app.quality.DeterministicXjieUiTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicationDashboardUiTest : DeterministicXjieUiTest() {
    @Test
    fun nextDoseIsDominantAndEveryPrimaryActionRemainsIndependentlyAccessible() {
        openMedication()
        waitFor(hasTestTag("xage.medication.hero.next"))

        val hero = compose.onNodeWithTag("xage.medication.hero.next", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val window = compose.onRoot(useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("hero should occupy roughly one third of the full window", hero.height >= window.height * 0.30f)
        assertTrue("hero must leave room for real secondary destinations", hero.height <= window.height * 0.55f)

        val root = compose.onNodeWithTag("xage.medication.root", useUnmergedTree = true)
        listOf("confirm", "snooze", "skip", "reaction", "reminder").forEach { action ->
            root.performScrollToNode(hasTestTag("xage.medication.hero.$action"))
            compose.onNodeWithTag("xage.medication.hero.$action", useUnmergedTree = true)
                .assertIsDisplayed()
        }
        compose.onNodeWithTag("xage.medication.bottomAction", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun secondaryRowsOpenRealDestinationsAndBackReturnsToDashboard() {
        openMedication()
        waitFor(hasTestTag("xage.medication.root"))
        val root = compose.onNodeWithTag("xage.medication.root", useUnmergedTree = true)

        root.performScrollToNode(hasTestTag("xage.medication.destination.plans"))
        compose.onNodeWithTag("xage.medication.destination.plans", useUnmergedTree = true).performClick()
        waitFor(hasTestTag("xage.medication.detail.plans"))
        compose.onNodeWithTag("xage.medication.detail.plans").assertIsDisplayed()

        androidx.test.espresso.Espresso.pressBack()
        waitFor(hasTestTag("xage.medication.root"))
        compose.onNodeWithTag("xage.medication.root", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("xage.medication.destination.reactions"))
        compose.onNodeWithTag("xage.medication.destination.reactions", useUnmergedTree = true)
            .performClick()
        waitFor(hasTestTag("xage.medication.detail.reactions"))
        compose.onNodeWithTag("xage.medication.detail.reactions").assertIsDisplayed()
    }

    private fun openMedication() {
        openXAgeQuickAction("medications")
    }
}
