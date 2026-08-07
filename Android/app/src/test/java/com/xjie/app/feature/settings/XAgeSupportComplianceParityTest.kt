package com.xjie.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XAgeSupportComplianceParityTest {
    @Test
    fun supportDestinationsAndPrivacyVersionMatchCurrentIosAuthority() {
        assertEquals(
            listOf("help", "version", "privacy", "permissions", "feedback"),
            XAgeSupportCompliancePolicy.destinationIds,
        )
        assertEquals("2026.07", XAgeSupportCompliancePolicy.PRIVACY_POLICY_VERSION)
        assertEquals("2026年7月26日", XAgeSupportCompliancePolicy.PRIVACY_POLICY_UPDATED_AT)
        assertEquals("2026年7月26日", XAgeSupportCompliancePolicy.PRIVACY_POLICY_EFFECTIVE_AT)
        assertEquals(
            XAgeSupportCompliancePolicy.privacyPolicyRequiredTopics.size,
            XAgeSupportCompliancePolicy.privacySections.size,
        )
        XAgeSupportCompliancePolicy.privacyPolicyRequiredTopics.forEach { topic ->
            assertTrue(
                XAgeSupportCompliancePolicy.privacySections.any { section ->
                    section.title.contains(topic)
                },
            )
        }
    }

    @Test
    fun AndroidPermissionDisclosuresCoverEveryRequestedCapabilityWithoutFakeDeviceAccess() {
        val disclosures = XAgeSupportCompliancePolicy.permissionDisclosures
        val ids = disclosures.map { it.id }

        assertEquals(ids.size, ids.distinct().size)
        assertTrue(
            ids.containsAll(
                listOf(
                    "health",
                    "notifications",
                    "exact-alarm",
                    "camera",
                    "photos",
                    "photo-save",
                    "microphone",
                    "speech",
                    "package-installs",
                    "boot-recovery",
                    "network",
                    "not-used",
                ),
            ),
        )
        disclosures.forEach { disclosure ->
            assertTrue(disclosure.title.isNotBlank())
            assertTrue(disclosure.badge.isNotBlank())
            assertTrue(disclosure.timing.isNotBlank())
            assertTrue(disclosure.purpose.isNotBlank())
            assertTrue(disclosure.refusalImpact.isNotBlank())
        }
        assertTrue(disclosures.single { it.id == "not-used" }.purpose.contains("蓝牙"))
        assertTrue(disclosures.single { it.id == "not-used" }.purpose.contains("NFC"))
        assertTrue(
            disclosures.single { it.id == "photos" }.purpose
                .contains("不因打开 App 扫描整个媒体库"),
        )
    }

    @Test
    fun feedbackValidationPreservesCurrentDraftAndLengthContract() {
        assertFalse(XAgeSupportCompliancePolicy.isFeedbackValid(" \n"))
        assertTrue(XAgeSupportCompliancePolicy.isFeedbackValid("可以提交"))
        assertTrue(XAgeSupportCompliancePolicy.isFeedbackValid("问".repeat(2_000)))
        assertFalse(XAgeSupportCompliancePolicy.isFeedbackValid("问".repeat(2_001)))
        assertFalse(XAgeSupportCompliancePolicy.hasFeedbackDraft(" \n", ""))
        assertTrue(XAgeSupportCompliancePolicy.hasFeedbackDraft("草稿", ""))
        assertTrue(XAgeSupportCompliancePolicy.hasFeedbackDraft("", "13800000000"))
    }

    @Test
    fun deviceManagementFailsClosedUntilRealHardwareContractsExist() {
        assertEquals(
            XAgeDeviceManagementState.Loading,
            XAgeSupportCompliancePolicy.deviceManagementState(isLoading = true),
        )
        assertEquals(
            XAgeDeviceManagementState.Unsupported,
            XAgeSupportCompliancePolicy.deviceManagementState(isLoading = false),
        )
        assertEquals("设备绑定暂未开放", XAgeSupportCompliancePolicy.DEVICE_UNSUPPORTED_TITLE)
    }
}
