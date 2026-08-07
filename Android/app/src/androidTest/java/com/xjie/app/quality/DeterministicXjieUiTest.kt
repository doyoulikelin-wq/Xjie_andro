package com.xjie.app.quality

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xjie.app.MainActivity
import com.xjie.app.core.quality.DebugUiAutomationTransport
import com.xjie.app.core.quality.UiAutomationRuntime
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * Shared black-box factory for every connected UI test.
 *
 * It launches the real Hilt application with the exact Debug-only deterministic token. Network
 * clients are therefore production clients with the Debug interceptor installed before first use;
 * no test-only Hilt graph can accidentally hide a production wiring error.
 */
abstract class DeterministicXjieUiTest {
    @get:Rule
    val compose: ComposeTestRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    protected open val launchAuthenticated: Boolean = true

    @Before
    fun launchDeterministicXjie() {
        val target = ApplicationProvider.getApplicationContext<Context>()
        assertRequiredDeviceProfile(target)
        target.getSharedPreferences("xage_quick_action_order_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        java.io.File(target.applicationInfo.dataDir, "shared_prefs")
            .listFiles()
            .orEmpty()
            .filter { it.name.startsWith("xage_card_layout_v2_") && it.extension == "xml" }
            .forEach { file ->
                target.getSharedPreferences(file.nameWithoutExtension, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }

        val intent = Intent(target, MainActivity::class.java)
            .putExtra(UiAutomationRuntime.INTENT_EXTRA, UiAutomationRuntime.EXACT_DEBUG_TOKEN)
            .putExtra(UiAutomationRuntime.INTENT_AUTHENTICATED_EXTRA, launchAuthenticated)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun closeDeterministicXjie() {
        try {
            compose.waitForIdle()
            DebugUiAutomationTransport.assertNoRequestEscapedStub()
        } finally {
            scenario?.close()
            scenario = null
        }
    }

    protected fun waitFor(matcher: SemanticsMatcher, timeoutMillis: Long = 15_000L) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodes(matcher, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    protected fun waitForAbsent(matcher: SemanticsMatcher, timeoutMillis: Long = 15_000L) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodes(matcher, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        }
    }

    /**
     * Reveals a quick action through the same child-strip gesture a user performs. Compose's
     * performScrollToNode must not be used here: with two nested horizontal containers it can
     * scroll the outer pager and silently change the selected XAGE page.
     */
    protected fun revealXAgeQuickAction(id: String, towardEnd: Boolean = true) {
        waitFor(hasTestTag("xage.quickActions"))
        val strip = compose.onNodeWithTag("xage.quickActions", useUnmergedTree = true)
        val target = compose.onNodeWithTag("xage.quickAction.$id", useUnmergedTree = true)
        repeat(6) {
            if (runCatching { target.assertIsDisplayed() }.isSuccess) {
                assertXAgeDataPageSelected()
                return
            }
            strip.performTouchInput {
                if (towardEnd) swipeLeft() else swipeRight()
            }
        }
        target.assertIsDisplayed()
        assertXAgeDataPageSelected()
    }

    protected fun openXAgeQuickAction(id: String) {
        revealXAgeQuickAction(id)
        compose.onNodeWithTag("xage.quickAction.$id", useUnmergedTree = true).performClick()
    }

    protected fun assertXAgeDataPageSelected() {
        compose.onNodeWithTag("xage.shell.pager", useUnmergedTree = true).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "当前页：数据",
            ),
        )
    }

    private fun assertRequiredDeviceProfile(context: Context) {
        check(Build.VERSION.SDK_INT == 35) {
            "connected UI tests require API 35, found ${Build.VERSION.SDK_INT}"
        }
        val profile = InstrumentationRegistry.getArguments().getString(PROFILE_ARGUMENT)
            ?: error("missing required instrumentation argument $PROFILE_ARGUMENT")
        val configuration = context.resources.configuration
        val width = configuration.screenWidthDp
        val height = configuration.screenHeightDp
        val fontScale = configuration.fontScale

        val matches = when (profile) {
            "standard_api35" -> width in 360..480 && height >= 700 && fontScale in 0.95f..1.10f
            "compact_api35" -> width in 300..359 && height in 540..699 && fontScale in 0.95f..1.10f
            "large_text_api35" -> width in 360..480 && height >= 700 && fontScale >= 1.30f
            else -> false
        }
        check(matches) {
            "device profile $profile does not match API=${Build.VERSION.SDK_INT}, " +
                "${width}x${height}dp, fontScale=$fontScale"
        }
    }

    private companion object {
        const val PROFILE_ARGUMENT = "xjie.ui.profile"
    }
}
