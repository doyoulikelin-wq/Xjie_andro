package com.xjie.app.feature.xage

import com.xjie.app.core.model.TrendPoint
import com.xjie.app.feature.healthdata.IndicatorTrendInteractionContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XAgeInformationArchitectureTest {
    @Test
    fun quickActionsExposeOnlyTheFiveCurrentIosTasksWithoutDuplicateDestinations() {
        val actions = XAgeInformationArchitecture.quickActions

        assertEquals(
            listOf("饮食", "体重", "报告", "用药", "就医助手"),
            actions.map { it.title },
        )
        assertEquals(5, actions.map { it.id }.distinct().size)
        assertEquals(5, actions.mapNotNull { it.destination }.distinct().size)
        assertTrue(actions.none { it.id in setOf("mood", "health-plan", "data-manager") })

        val ordered = IndicatorTrendInteractionContract.orderedPoints(
            listOf(
                TrendPoint("2026-07-12", 8.0, false),
                TrendPoint("2026-07-10", 12.5, true),
                TrendPoint("2026-07-11", Double.NaN, false),
                TrendPoint("not-a-date", 99.0, true),
            ),
        )
        assertEquals(listOf("2026-07-10", "2026-07-12"), ordered.map { it.date })
        assertTrue(ordered.first().abnormal)
        assertTrue(IndicatorTrendInteractionContract.contentWidthDp(30, 320f) > 320f)
        assertEquals(0, IndicatorTrendInteractionContract.nearestIndex(40f, 40f, 300f, 2))
        assertEquals(1, IndicatorTrendInteractionContract.nearestIndex(300f, 40f, 300f, 2))
    }

    @Test
    fun moreMenuMatchesCurrentIosProfileDeviceAccountFamilyAndFiveDirectSupportEntries() {
        assertEquals(
            listOf(
                XAgeInformationArchitecture.PROFILE_DESTINATION,
                XAgeInformationArchitecture.DEVICE_DESTINATION,
                XAgeInformationArchitecture.ACCOUNT_DESTINATION,
                XAgeInformationArchitecture.FAMILY_DESTINATION,
                XAgeInformationArchitecture.SUPPORT_HELP_DESTINATION,
                XAgeInformationArchitecture.SUPPORT_VERSION_DESTINATION,
                XAgeInformationArchitecture.SUPPORT_PRIVACY_DESTINATION,
                XAgeInformationArchitecture.SUPPORT_PERMISSIONS_DESTINATION,
                XAgeInformationArchitecture.SUPPORT_FEEDBACK_DESTINATION,
            ),
            XAgeInformationArchitecture.moreDestinations,
        )
        assertTrue(XAgeInformationArchitecture.quickActions.none { it.destination == "profile" })
        assertEquals(
            listOf("help", "version", "privacy", "permissions", "feedback"),
            XAgeInformationArchitecture.supportDestinationIds,
        )
        assertTrue(!XAgeInformationArchitecture.isFeedbackValid(" "))
        assertTrue(XAgeInformationArchitecture.isFeedbackValid("可以提交"))
        assertTrue(XAgeInformationArchitecture.isFeedbackValid("问".repeat(2_000)))
        assertTrue(!XAgeInformationArchitecture.isFeedbackValid("问".repeat(2_001)))
        assertTrue(!XAgeInformationArchitecture.hasFeedbackDraft(" \n", ""))
        assertTrue(XAgeInformationArchitecture.hasFeedbackDraft("草稿", ""))
        assertTrue(XAgeInformationArchitecture.hasFeedbackDraft("", "13800000000"))
    }

    @Test
    fun bodyWeightMetricCardSharesTheDedicatedWeightFlow() {
        assertEquals("weight", XAgeInformationArchitecture.destinationForMetric("bodyWeight"))
        assertEquals(null, XAgeInformationArchitecture.destinationForMetric("sleep"))
    }

    @Test
    fun homeChromeKeepsCaptionAndReorderGuidanceOutOfVisibleRows() {
        val header = XAgeHomeChromePresentationPolicy.header("暂无同步数据")

        assertEquals("暂无同步数据", header.semanticStatus)
        assertEquals(null, header.visibleCaption)
        assertEquals(null, XAgeHomeChromePresentationPolicy.visibleQuickActionReorderHint())
    }
}
