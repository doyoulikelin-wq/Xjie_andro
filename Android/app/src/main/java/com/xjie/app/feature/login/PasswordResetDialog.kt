package com.xjie.app.feature.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.PasswordResetConfirmBody
import com.xjie.app.core.model.PasswordResetRequestBody
import com.xjie.app.core.network.api.AuthApi
import com.xjie.app.core.network.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class PasswordResetUiState(
    val phone: String = "",
    val code: String = "",
    val newPassword: String = "",
    val codeSent: Boolean = false,
    val sending: Boolean = false,
    val confirming: Boolean = false,
    val message: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class PasswordResetViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val json: Json,
) : ViewModel() {
    private val _state = MutableStateFlow(PasswordResetUiState())
    val state: StateFlow<PasswordResetUiState> = _state.asStateFlow()

    fun setPhone(v: String) = _state.update { it.copy(phone = v.filter { c -> c.isDigit() }.take(11)) }
    fun setCode(v: String) = _state.update { it.copy(code = v.filter { c -> c.isDigit() }.take(8)) }
    fun setNewPassword(v: String) = _state.update { it.copy(newPassword = v.take(64)) }
    fun clearMessage() = _state.update { it.copy(message = null) }
    fun reset() = _state.update { PasswordResetUiState() }

    fun requestCode() = viewModelScope.launch {
        val phone = _state.value.phone
        if (phone.length < 11) { _state.update { it.copy(message = "请输入正确的手机号") }; return@launch }
        _state.update { it.copy(sending = true, message = null) }
        runCatching { safeApiCall(json) { authApi.requestPasswordReset(PasswordResetRequestBody(phone)) } }
            .onSuccess {
                _state.update { it.copy(sending = false, codeSent = true, message = "验证码已发送（开发模式：见后端日志）") }
            }
            .onFailure { e -> _state.update { it.copy(sending = false, message = e.message ?: "发送失败") } }
    }

    fun confirm() = viewModelScope.launch {
        val s = _state.value
        if (s.code.length < 4 || s.newPassword.length < 8) {
            _state.update { it.copy(message = "请输入验证码与至少 8 位的新密码（含字母+数字）") }; return@launch
        }
        _state.update { it.copy(confirming = true, message = null) }
        runCatching {
            safeApiCall(json) {
                authApi.confirmPasswordReset(PasswordResetConfirmBody(s.phone, s.code, s.newPassword))
            }
        }.onSuccess {
            _state.update { it.copy(confirming = false, done = true, message = "密码已重置，请用新密码登录") }
        }.onFailure { e ->
            _state.update { it.copy(confirming = false, message = e.message ?: "重置失败") }
        }
    }
}

@Composable
fun PasswordResetDialog(
    onDismiss: () -> Unit,
    vm: PasswordResetViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("找回密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = vm::setPhone,
                    label = { Text("手机号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = vm::setCode,
                        label = { Text("验证码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { vm.requestCode() },
                        enabled = !state.sending && state.phone.length == 11,
                    ) { Text(if (state.sending) "发送中" else if (state.codeSent) "重新发送" else "获取验证码") }
                }
                OutlinedTextField(
                    value = state.newPassword,
                    onValueChange = vm::setNewPassword,
                    label = { Text("新密码（≥8位含字母数字）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.confirming,
                onClick = { vm.confirm() },
            ) { Text(if (state.confirming) "提交中…" else "重置密码") }
        },
        dismissButton = {
            TextButton(onClick = { vm.reset(); onDismiss() }) { Text("取消") }
        },
    )
}
