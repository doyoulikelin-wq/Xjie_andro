package com.xjie.app.feature.login

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjie.app.core.quality.DebugUiAutomationTransport
import com.xjie.app.quality.DeterministicXjieUiTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginLegalConsentUiTest : DeterministicXjieUiTest() {
    override val launchAuthenticated: Boolean = false

    @Test
    fun registrationRequiresTwoExplicitAgreementsAndLegalDocumentsRemainReadable() {
        waitFor(hasText("没有账号？去注册"))
        compose.onNodeWithText("使用受试者 ID 登录").assertDoesNotExist()
        compose.onNodeWithText("没有账号？去注册").performClick()
        waitFor(hasTestTag("login.legal.consents"))

        compose.onNodeWithTag("login.legal.userAgreement").assertIsOff()
        compose.onNodeWithTag("login.legal.privacyPolicy").assertIsOff()
        compose.onNodeWithTag("login.legal.privacyPolicy.document")
            .performScrollTo()
            .performClick()
        waitFor(hasText("适用范围与重要提示"))
        compose.onNodeWithText("适用范围与重要提示").assertExists()
        compose.onNodeWithTag("login.legal.document.close").performClick()

        compose.onNodeWithTag("login.submit").performScrollTo().performClick()
        waitFor(hasText("请先阅读并同意协议"))
        assertTrue(
            DebugUiAutomationTransport.snapshot().requests.none {
                it == "POST /api/auth/signup"
            },
        )
        compose.onNodeWithText("暂不注册").performClick()
        compose.onNodeWithTag("login.legal.userAgreement").assertIsOff()
        compose.onNodeWithTag("login.legal.privacyPolicy").assertIsOff()

        compose.onNodeWithTag("login.legal.userAgreement").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithTag("login.submit").performScrollTo().performClick()
        waitFor(hasText("请先阅读并同意协议"))
        compose.onNodeWithTag("login.legal.confirmAndSignup").performClick()
        waitFor(hasText("请填写手机号和密码"))
        compose.onNodeWithTag("login.legal.userAgreement").assertIsOn()
        compose.onNodeWithTag("login.legal.privacyPolicy").assertIsOn()
        assertTrue(
            DebugUiAutomationTransport.snapshot().requests.none {
                it == "POST /api/auth/signup"
            },
        )
    }
}
