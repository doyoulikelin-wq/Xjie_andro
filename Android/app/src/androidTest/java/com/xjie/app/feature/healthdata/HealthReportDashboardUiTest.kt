package com.xjie.app.feature.healthdata

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjie.app.quality.DeterministicXjieUiTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthReportDashboardUiTest : DeterministicXjieUiTest() {
    @Test
    fun scorePendingDashboardHasOnePrimaryUploadAndNoInternalIdentifiers() {
        openXAgeQuickAction("reports")

        waitFor(hasTestTag("health.report.dashboard.root"))
        compose.onNodeWithTag("health.report.state.scorePending", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onAllNodesWithTag("health.report.primaryUpload", useUnmergedTree = true)
            .assertCountEquals(1)
        compose.onNode(hasText("已完成解析 · 评分待更新", substring = false))
            .assertIsDisplayed()

        listOf(
            "workflow_id",
            "candidate_id",
            "observation_id",
            "event_id",
            "trace_id",
            "storage_key",
            "failure_code",
            "#501",
        ).forEach { forbidden ->
            compose.onNode(hasText(forbidden, substring = true), useUnmergedTree = true)
                .assertDoesNotExist()
        }

        compose.onNodeWithTag("health.report.latestAction", useUnmergedTree = true)
            .performClick()
        waitFor(hasText("按报告任务读取的权威记录", substring = false))
        compose.onNode(hasText("按报告任务读取的权威记录", substring = false))
            .assertIsDisplayed()
        compose.onNode(hasText("已确认入库 · 评分待更新", substring = false))
            .assertIsDisplayed()
    }
}
