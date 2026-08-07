package com.xjie.app.feature.login

import com.xjie.app.feature.settings.XAgeSupportCompliancePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginLegalConsentPolicyTest {
    @Test
    fun signupFailsClosedUntilBothDocumentsAreExplicitlyAccepted() {
        assertFalse(LoginLegalConsentPolicy.canSubmit(true, false, false))
        assertFalse(LoginLegalConsentPolicy.canSubmit(true, true, false))
        assertFalse(LoginLegalConsentPolicy.canSubmit(true, false, true))
        assertTrue(LoginLegalConsentPolicy.canSubmit(true, true, true))
        assertTrue(LoginLegalConsentPolicy.canSubmit(false, false, false))
        assertEquals(
            "请阅读并同意《用户协议》和《隐私政策》后再注册",
            LoginLegalConsentPolicy.REQUIRED_MESSAGE,
        )
    }

    @Test
    fun registrationPrivacyDocumentUsesTheSameVersionedSupportPolicy() {
        assertSame(
            XAgeSupportCompliancePolicy.privacySections,
            LoginLegalConsentPolicy.privacyPolicySections,
        )
        assertEquals(
            listOf("服务说明", "账号与使用", "健康信息与服务边界", "服务变更与终止", "联系我们"),
            LoginLegalConsentPolicy.userAgreementSections.map { it.title },
        )
    }
}
