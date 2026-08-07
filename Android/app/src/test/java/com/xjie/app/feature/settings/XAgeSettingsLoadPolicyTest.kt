package com.xjie.app.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XAgeSettingsLoadPolicyTest {
    @Test
    fun supportAndDeviceRoutesNeverPrefetchAccountSettingsWhileAccountRoutesDo() {
        assertTrue(XAgeSettingsLoadPolicy.requiresAccountSettings(null))
        assertTrue(XAgeSettingsLoadPolicy.requiresAccountSettings("account"))
        listOf(
            "device",
            "support",
            "support_help",
            "support_version",
            "support_privacy",
            "support_permissions",
            "support_feedback",
        ).forEach { route ->
            assertFalse(route, XAgeSettingsLoadPolicy.requiresAccountSettings(route))
        }
    }
}
