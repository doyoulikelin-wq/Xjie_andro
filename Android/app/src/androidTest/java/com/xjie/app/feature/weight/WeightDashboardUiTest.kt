package com.xjie.app.feature.weight

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjie.app.quality.DeterministicXjieUiTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeightDashboardUiTest : DeterministicXjieUiTest() {
    @Test
    fun bodyWeightDataCardOpensTheSameDedicatedWeightFlow() {
        waitFor(hasTestTag("xage.data.manage"))
        compose.onNodeWithTag("xage.data.manage").performClick()
        waitFor(hasTestTag("xage.data.manager.add.steps"))
        compose.onNodeWithTag("xage.data.manager.add.steps", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("xage.data.manager.back")
            .assertIsDisplayed()
            .performClick()

        waitForAndScrollToTag(
            rootTag = "xage.data.scroll",
            readinessTag = "xage.data.metrics.loaded",
            targetTag = "xage.data.metric.bodyWeight",
        )
        compose.onNodeWithTag("xage.data.metric.bodyWeight", useUnmergedTree = true)
            .performClick()

        waitFor(hasTestTag("weight.latest.card"))
        compose.onNodeWithTag("weight.dashboard").assertIsDisplayed()
        compose.onNodeWithText("体重记录").assertIsDisplayed()
    }

    @Test
    fun quickActionOpensTrustedWeightDetailAndInputSheetsReturnSafely() {
        openXAgeQuickAction("weight")

        waitFor(hasTestTag("weight.latest.card"))
        compose.onNodeWithTag("weight.dashboard").assertIsDisplayed()
        compose.onNodeWithText("体重记录").assertIsDisplayed()
        compose.onNodeWithText("最新一次记录").assertIsDisplayed()
        compose.onNodeWithText("70.0").assertIsDisplayed()
        compose.onNodeWithTag("weight.bmi.value", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("还没有记录身高，无法计算BMI").assertIsDisplayed()
        compose.onNode(hasContentDescription("近三个月体重趋势", substring = true))
            .assertIsDisplayed()

        compose.onNodeWithTag("weight.recordHeight", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        waitFor(hasTestTag("weight.height.sheet"))
        tapHeightDigit(4)
        tapHeightDigit(9)
        compose.onNodeWithTag("weight.height.save", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("数据范围异常，请填写正确数字。")
            .assertIsDisplayed()

        compose.onNodeWithTag("weight.height.clear", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        tapHeightDigit(2)
        tapHeightDigit(1)
        tapHeightDigit(1)
        compose.onNodeWithTag("weight.height.save", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("数据范围异常，请填写正确数字。")
            .assertIsDisplayed()

        closeAppOwnedModal("weight.height.close")
        compose.waitForIdle()
        compose.onNodeWithTag("weight.height.sheet").assertDoesNotExist()

        compose.onNodeWithTag("weight.record", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        waitFor(hasTestTag("weight.picker.sheet"))
        compose.onNodeWithTag("weight.picker.integer", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("weight.picker.tenth", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithText("当前选择 70.0 公斤").assertIsDisplayed()
        compose.onNodeWithTag("weight.picker.cancel", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("weight.picker.sheet").assertDoesNotExist()

        compose.onNodeWithTag("weight.trend.guidance", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        waitFor(hasTestTag("weight.guidance.sheet"))
        compose.onNodeWithText("怎么看体重变化").assertIsDisplayed()
        compose.onNodeWithText("看趋势，不只看今天").assertIsDisplayed()
        compose.onNodeWithText("记录条件尽量一致").assertIsDisplayed()
        closeAppOwnedModal("weight.guidance.close")
        compose.waitForIdle()
        compose.onNodeWithTag("weight.guidance.sheet").assertDoesNotExist()

        navigateBackThroughAppOwner("weight.back", hasTestTag("xage.data.manage"))
        compose.onNodeWithTag("xage.data.manage").assertIsDisplayed()
    }

    private fun tapHeightDigit(digit: Int) {
        compose.onNodeWithTag("weight.height.digit.$digit", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
    }
}
