package com.xjie.app.feature.settings

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.auth.TestAuthManagerFactory
import com.xjie.app.core.network.api.AuthApi
import com.xjie.app.core.network.api.UserApi
import com.xjie.app.core.storage.PreferencesStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Tag

class SettingsAccountGenerationIsolationTest {
    @Test
    fun accountDeletionCompletionCannotLogoutAReplacementSession() = runTest {
        val auth = TestAuthManagerFactory.create()
        auth.establishSession(jwt("account-a", "first"), "refresh-a", "subject-a")
        val ownerA = requireNotNull(auth.captureAccountScope())
        val userApi = mockk<UserApi>()
        coEvery { userApi.deleteMeForOwner(ownerA) } coAnswers {
            auth.establishSession(jwt("account-b", "replacement"), "refresh-b", "subject-b")
            Unit
        }
        val repository = repository(userApi, auth)

        val cleared = repository.deleteAccount(ownerA)

        assertFalse(cleared)
        assertTrue(auth.isLoggedIn)
        assertEquals("subject-b", auth.state.value.subjectId)
        coVerify(exactly = 1) { userApi.deleteMeForOwner(ownerA) }
    }

    @Test
    fun everyAccountBoundSettingsEndpointCarriesAnOwnerTag() {
        val userMethods = listOf(
            "meForOwner",
            "settingsForOwner",
            "updateSettingsForOwner",
            "updateConsentForOwner",
            "updateProfileForOwner",
            "updateOnboardingForOwner",
            "submitFeedbackForOwner",
            "deleteMeForOwner",
        )

        userMethods.forEach { name ->
            val method = UserApi::class.java.methods.single { it.name == name }
            assertTrue("$name must carry an account owner tag", method.hasOwnerTag())
        }
        val passwordMethod = AuthApi::class.java.methods.single { it.name == "changePasswordForOwner" }
        assertTrue(passwordMethod.hasOwnerTag())
    }

    private fun repository(userApi: UserApi, auth: AuthManager) = SettingsRepository(
        userApi = userApi,
        authApi = mockk<AuthApi>(relaxed = true),
        authManager = auth,
        prefs = mockk<PreferencesStore>(relaxed = true),
        json = Json { ignoreUnknownKeys = true },
    )

    private fun java.lang.reflect.Method.hasOwnerTag(): Boolean =
        parameterAnnotations.flatten().any { it is Tag }

    private fun jwt(subject: String, nonce: String): String {
        fun segment(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        return "${segment("""{"alg":"none"}""")}." +
            "${segment("""{"sub":"$subject","nonce":"$nonce"}""")}.signature"
    }
}
