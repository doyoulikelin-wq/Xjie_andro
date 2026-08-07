package com.xjie.app.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.SubjectItem
import com.xjie.app.core.network.ApiException
import com.xjie.app.core.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginMode { Subject, Phone }

data class LoginUiState(
    val mode: LoginMode = LoginMode.Phone,
    val subjects: List<SubjectItem> = emptyList(),
    val selectedSubject: String = "",
    val phone: String = "",
    val username: String = "",
    val password: String = "",
    val isSignup: Boolean = false,
    // 注册阶段个人资料（默认值：女 / 30岁 / 165cm / 55kg）
    val sex: String = "female",
    val age: Int = 30,
    val heightCm: Int = 165,
    val weightKg: Int = 55,
    val onboardingTarget: String = "控糖稳定",
    val onboardingContents: Set<String> = setOf("fitness", "diet_control"),
    val onboardingGeneratePlan: Boolean = true,
    val medicationNeeded: Boolean = false,
    val hasAcceptedUserAgreement: Boolean = false,
    val hasAcceptedPrivacyPolicy: Boolean = false,
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val toast: String? = null,
) {
    val hasAcceptedRequiredLegalAgreements: Boolean
        get() = hasAcceptedUserAgreement && hasAcceptedPrivacyPolicy
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: LoginRepository,
) : ViewModel() {
    private val submissionGate = LoginSubmissionGate()

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun setMode(mode: LoginMode) = _state.update { it.copy(mode = mode) }
    fun toggleMode() = _state.update {
        it.copy(mode = if (it.mode == LoginMode.Subject) LoginMode.Phone else LoginMode.Subject)
    }
    fun toggleSignup() = _state.update { it.copy(isSignup = !it.isSignup) }
    fun setSelectedSubject(id: String) = _state.update { it.copy(selectedSubject = id) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v) }
    fun setUsername(v: String) = _state.update { it.copy(username = v) }
    fun setPassword(v: String) = _state.update { it.copy(password = v) }
    fun setSex(v: String) = _state.update { it.copy(sex = v) }
    fun setAge(v: Int) = _state.update { it.copy(age = v) }
    fun setHeightCm(v: Int) = _state.update { it.copy(heightCm = v) }
    fun setWeightKg(v: Int) = _state.update { it.copy(weightKg = v) }
    fun setOnboardingTarget(v: String) = _state.update { it.copy(onboardingTarget = v) }
    fun toggleOnboardingContent(v: String) = _state.update {
        val next = it.onboardingContents.toMutableSet()
        if (next.contains(v)) next.remove(v) else next.add(v)
        it.copy(
            onboardingContents = next,
            medicationNeeded = if (v == "medication" && it.onboardingContents.contains(v)) false else it.medicationNeeded,
        )
    }
    fun setOnboardingGeneratePlan(v: Boolean) = _state.update { it.copy(onboardingGeneratePlan = v) }
    fun setMedicationNeeded(v: Boolean) = _state.update { it.copy(medicationNeeded = v) }
    fun setUserAgreementAccepted(v: Boolean) =
        _state.update { it.copy(hasAcceptedUserAgreement = v) }
    fun setPrivacyPolicyAccepted(v: Boolean) =
        _state.update { it.copy(hasAcceptedPrivacyPolicy = v) }
    fun acceptRequiredLegalAgreements() = _state.update {
        it.copy(hasAcceptedUserAgreement = true, hasAcceptedPrivacyPolicy = true)
    }
    fun clearToast() = _state.update { it.copy(toast = null) }

    fun loadSubjects() {
        viewModelScope.launch {
            runCatching { repo.loadSubjects() }
                .onSuccess { list -> _state.update { it.copy(subjects = list) } }
                .onFailure { e ->
                    AppLogger.auth.w(e, "loadSubjects failed")
                    _state.update { it.copy(errorMessage = e.localizedMessage) }
                }
        }
    }

    fun loginSubject() {
        val subject = _state.value.selectedSubject
        if (subject.isBlank()) {
            _state.update { it.copy(toast = "请选择受试者") }
            return
        }
        launchWithLoading { repo.loginSubject(subject) }
    }

    fun loginPhone() {
        val s = _state.value
        val normalizedPhone = s.phone.filterNot(Char::isWhitespace)
        val normalizedUsername = s.username.trim()
        val normalizedPassword = s.password.trim()
        when {
            normalizedPhone.isBlank() || normalizedPassword.isBlank() ->
                _state.update { it.copy(toast = "请填写手机号和密码") }
            normalizedPassword.length < 8 ->
                _state.update { it.copy(toast = "密码至少 8 位") }
            s.isSignup && normalizedUsername.isBlank() ->
                _state.update { it.copy(toast = "请填写用户名") }
            !LoginLegalConsentPolicy.canSubmit(
                isSignup = s.isSignup,
                acceptedUserAgreement = s.hasAcceptedUserAgreement,
                acceptedPrivacyPolicy = s.hasAcceptedPrivacyPolicy,
            ) -> _state.update { it.copy(toast = LoginLegalConsentPolicy.REQUIRED_MESSAGE) }
            else -> launchWithLoading {
                repo.loginOrSignupPhone(
                    phone = normalizedPhone,
                    username = normalizedUsername,
                    password = normalizedPassword,
                    signup = s.isSignup,
                    sex = if (s.isSignup) s.sex else null,
                    age = if (s.isSignup) s.age else null,
                    heightCm = if (s.isSignup) s.heightCm.toDouble() else null,
                    weightKg = if (s.isSignup) s.weightKg.toDouble() else null,
                    onboardingTarget = if (s.isSignup) s.onboardingTarget else null,
                    onboardingContents = if (s.isSignup) s.onboardingContents.toList() else emptyList(),
                    onboardingGeneratePlan = s.isSignup && s.onboardingGeneratePlan,
                    medicationNeeded = s.isSignup && s.medicationNeeded,
                )
            }
        }
    }

    private fun launchWithLoading(block: suspend () -> Unit) {
        if (!submissionGate.tryAcquire()) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Throwable) {
                AppLogger.auth.w(e, "login failed")
                val msg = (e as? ApiException)?.message ?: e.localizedMessage ?: "登录失败"
                _state.update { it.copy(toast = msg) }
            } finally {
                submissionGate.release()
                _state.update { it.copy(loading = false) }
            }
        }
    }
}

/** Synchronous acquisition closes the gap between repeated taps and coroutine dispatch. */
internal class LoginSubmissionGate {
    private var active = false

    @Synchronized
    fun tryAcquire(): Boolean {
        if (active) return false
        active = true
        return true
    }

    @Synchronized
    fun release() {
        active = false
    }
}
