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
    val isSignup: Boolean = true,
    // 注册阶段个人资料（默认值：女 / 30岁 / 165cm / 55kg）
    val sex: String = "female",
    val age: Int = 30,
    val heightCm: Int = 165,
    val weightKg: Int = 55,
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val toast: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: LoginRepository,
) : ViewModel() {

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
        when {
            s.phone.isBlank() || s.password.isBlank() ->
                _state.update { it.copy(toast = "请填写手机号和密码") }
            s.password.length < 8 ->
                _state.update { it.copy(toast = "密码至少 8 位") }
            s.isSignup && s.username.isBlank() ->
                _state.update { it.copy(toast = "请填写用户名") }
            else -> launchWithLoading {
                repo.loginOrSignupPhone(
                    phone = s.phone,
                    username = s.username,
                    password = s.password,
                    signup = s.isSignup,
                    sex = if (s.isSignup) s.sex else null,
                    age = if (s.isSignup) s.age else null,
                    heightCm = if (s.isSignup) s.heightCm.toDouble() else null,
                    weightKg = if (s.isSignup) s.weightKg.toDouble() else null,
                )
            }
        }
    }

    private fun launchWithLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                block()
            } catch (e: Throwable) {
                AppLogger.auth.w(e, "login failed")
                val msg = (e as? ApiException)?.message ?: e.localizedMessage ?: "登录失败"
                _state.update { it.copy(toast = msg) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
