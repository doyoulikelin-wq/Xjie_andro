package com.xjie.app.feature.xage

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjie.app.quality.DeterministicXjieUiTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class XAgeShellSwipeUiTest : DeterministicXjieUiTest() {
    @Test
    fun capsuleTapsAndHorizontalSwipesShareOnePageState() {
        waitFor(hasTestTag("xage.shell.pager"))
        waitFor(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "暂无同步数据",
            ),
        )
        compose.onNodeWithTag("xage.data.header").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "暂无同步数据",
            ),
        )
        compose.onNodeWithText("暂无同步数据").assertDoesNotExist()
        compose.onNodeWithTag("xage.data.manage")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        compose.onNodeWithTag("xage.shell.pager").performTouchInput { swipeLeft() }
        waitFor(hasText("你可以这样问"))
        compose.onNodeWithText("你可以这样问").assertIsDisplayed()

        compose.onNodeWithTag("xage.segment.X年龄").performClick()
        waitFor(hasTestTag("xage.particle.ring"))
        compose.onNodeWithTag("xage.particle.ring").assertIsDisplayed()

        compose.onNodeWithTag("xage.shell.pager").performTouchInput { swipeRight() }
        waitFor(hasText("你可以这样问"))
        compose.onNodeWithText("你可以这样问").assertIsDisplayed()

        compose.onNodeWithTag("xage.segment.数据").performClick()
        waitFor(hasTestTag("xage.data.manage"))
        compose.onNodeWithTag("xage.data.manage").assertIsDisplayed()
    }

    @Test
    fun quickActionsAreExactAndAccessibilityReorderable() {
        waitFor(hasTestTag("xage.quickActions"))
        val active = listOf("meals", "weight", "reports", "medications", "medical")
        compose.onNodeWithText("长按拖动排序").assertDoesNotExist()
        active.forEach { id ->
            revealXAgeQuickAction(id)
        }
        listOf("mood", "health_plan", "manager").forEach { legacy ->
            compose.onNodeWithTag("xage.quickAction.$legacy", useUnmergedTree = true)
                .assertDoesNotExist()
        }

        revealXAgeQuickAction("meals", towardEnd = false)
        val meals = compose.onNodeWithTag("xage.quickAction.meals", useUnmergedTree = true)
        meals.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "第 1 项，共 5 项；长按拖动可排序",
            ),
        )
        meals.performCustomAccessibilityActionWithLabel("向右移动")
        waitFor(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "第 2 项，共 5 项；长按拖动可排序",
            ),
        )
    }

    @Test
    fun zeroEvidenceShowsNeutralDailyScoreAndIndependentWarning() {
        waitForLoadedXAgeData()
        compose.onNodeWithTag("xage.data.score.pressure", useUnmergedTree = true)
            .assertContentDescriptionEquals("压力评分 50 分，数据完整度 0%，置信度较低")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        waitFor(hasText("压力详情"))
        compose.onNodeWithText("压力详情").assertIsDisplayed()
        closeAppOwnedModal("xage.dialog.close")
        compose.onNodeWithTag("xage.data.score.pressure.info", useUnmergedTree = true)
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        waitFor(hasText("压力评分说明"))
        compose.onNodeWithText("先看结论").assertIsDisplayed()
        compose.onNodeWithText("当前数据完整度 0%", substring = true).assertDoesNotExist()
        closeAppOwnedModal("xage.dialog.close")
        compose.onNodeWithTag(
            "xage.data.score.pressure.confidenceWarning",
            useUnmergedTree = true,
        ).assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        waitFor(hasText("当前数据完整度 0%", substring = true))
        compose.onNodeWithText("当前数据完整度 0%", substring = true).assertIsDisplayed()
    }

    @Test
    fun medicalAssistantUsesDeterministicOverviewAndReturnsToDataPage() {
        openXAgeQuickAction("medical")
        waitFor(hasText("已确认资料摘要，仅供就诊沟通参考。"))
        compose.onNodeWithText("已确认资料摘要，仅供就诊沟通参考。").assertIsDisplayed()
        compose.onNodeWithText("生成病人概况").performClick()
        waitFor(hasText("无信息更新"))
        compose.onNodeWithText("无信息更新").assertIsDisplayed()
        waitFor(hasTestTag("medicalAssistant.notice"))
        compose.onNodeWithText("知道了").performClick()
        waitForAbsent(hasTestTag("medicalAssistant.notice"))

        compose.onNodeWithTag("medicalAssistant.close", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        waitFor(hasTestTag("xage.more"))
        assertXAgeDataPageSelected()
        waitFor(hasTestTag("xage.data.manage"))
        compose.onNodeWithTag("xage.data.manage").assertIsDisplayed()
    }

    @Test
    fun mealsAndProfileUseAllowlistedEmptyStatesAndReturnToDataPage() {
        openXAgeQuickAction("meals")
        waitFor(hasText("饮食记录"))
        waitForAndScrollToText("本日暂无已确认餐食；识别草稿不会自动进入这里")
        navigateBackThroughAppOwner("xage.meals.back", hasTestTag("xage.more"))
        compose.onNodeWithTag("xage.more").performClick()
        waitFor(hasTestTag("xage.more.profile"))
        compose.onNodeWithTag("xage.more.profile", useUnmergedTree = true).performClick()
        waitFor(hasTestTag("healthProfile.root"))
        compose.onNodeWithTag("healthProfile.overview", useUnmergedTree = true).assertIsDisplayed()
        waitForAndScrollToText("暂无服务端已确认的长期用药摘要。")
        navigateBackThroughAppOwner("healthProfile.back", hasTestTag("xage.data.manage"))
    }

    @Test
    fun moreMenuRoutesDeviceAndSupportWithoutDeadAffordances() {
        waitFor(hasTestTag("xage.more"))
        compose.onNodeWithTag("xage.more").performClick()
        waitFor(hasTestTag("xage.more.device"))
        compose.onNodeWithTag("xage.dialog.close")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("xage.more.device", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        waitFor(hasText("设备绑定暂未开放"))
        compose.onNodeWithText("设备绑定暂未开放").assertIsDisplayed()
        compose.onNodeWithText("添加设备").assertDoesNotExist()

        navigateBackThroughAppOwner("xage.settings.back", hasTestTag("xage.more"))
        compose.onNodeWithTag("xage.more").performClick()
        waitFor(hasTestTag("xage.support.permissions"))
        compose.onNodeWithTag("xage.support.permissions", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        waitFor(hasTestTag("xage.support.permissions.page"))
        compose.onNodeWithTag("xage.support.permissions.page")
            .performScrollToNode(hasTestTag("xage.permission.exact-alarm"))
        compose.onNodeWithTag("xage.permission.exact-alarm", useUnmergedTree = true)
            .assertIsDisplayed()

        navigateBackThroughAppOwner("xage.support.permissions.back", hasTestTag("xage.data.manage"))
        compose.onNodeWithTag("xage.more").performClick()
        waitFor(hasTestTag("xage.support.feedback"))
        compose.onNodeWithTag("xage.support.feedback", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        waitFor(hasTestTag("xage.feedback.dialog"))
        compose.onNodeWithText("取消").performClick()
        waitFor(hasTestTag("xage.data.manage"))
        compose.onNodeWithTag("xage.data.manage").assertIsDisplayed()
    }

    @Test
    fun swipingFocusedChatInputUsesStubbedAnswerAndClearsEditorPage() {
        waitFor(hasTestTag("xage.segment.问答"))
        compose.onNodeWithTag("xage.segment.问答").performClick()
        waitFor(hasTestTag("xage.chat.input"))

        compose.onNodeWithTag("xage.chat.plus").performClick()
        waitFor(hasTestTag("xage.chat.attachment.documents"))
        compose.onNodeWithTag("xage.chat.attachment.camera").assertIsDisplayed()
        compose.onNodeWithTag("xage.chat.attachment.documents").assertIsDisplayed()
        compose.onNodeWithTag("xage.chat.attachment.photos").assertIsDisplayed()
        compose.onNodeWithTag("xage.chat.attachment.new").assertIsDisplayed()
        closeAppOwnedModal("xage.dialog.close")

        compose.onNodeWithTag("xage.chat.input")
            .performClick()
            .assertIsFocused()
            .performTextInput("今天的数据怎么样？")
        compose.onNodeWithTag("xage.chat.send").performClick()
        waitFor(hasText("证据展示"))
        waitForDisplayedNativeText("这是确定性 UI 回答。[2]")
        compose.onNodeWithText("证据展示").performClick()
        waitFor(hasText("正文显式引用的确定性测试证据。"))
        compose.onNodeWithText("正文显式引用的确定性测试证据。").assertIsDisplayed()
        compose.onNodeWithText("未被正文引用的测试证据。").assertDoesNotExist()
        closeAppOwnedModal("xage.dialog.close")

        compose.onNodeWithTag("xage.shell.pager").performTouchInput { swipeLeft() }
        waitFor(hasTestTag("xage.particle.ring"))
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }
}
