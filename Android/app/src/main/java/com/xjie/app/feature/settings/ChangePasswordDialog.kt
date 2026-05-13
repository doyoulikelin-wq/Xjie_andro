package com.xjie.app.feature.settings

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
import com.xjie.app.core.model.PasswordChangeBody
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

data class ChangePwdUiState(
    val oldPwd: String = "",
    val newPwd: String = "",
    val confirmPwd: String = "",
    val saving: Boolean = false,
    val message: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val json: Json,
) : ViewModel() {
    private val _state = MutableStateFlow(ChangePwdUiState())
    val state: StateFlow<ChangePwdUiState> = _state.asStateFlow()

    fun setOld(v: String) = _state.update { it.copy(oldPwd = v.take(64)) }
    fun setNew(v: String) = _state.update { it.copy(newPwd = v.take(64)) }
    fun setConfirm(v: String) = _state.update { it.copy(confirmPwd = v.take(64)) }
    fun reset() = _state.update { ChangePwdUiState() }

    fun submit() = viewModelScope.launch {
        val s = _state.value
        when {
            s.oldPwd.isBlank() -> _state.update { it.copy(message = "请输入旧密码") }
            s.newPwd.length < 8 -> _state.update { it.copy(message = "新密码至少 8 位（含字母+数字）") }
            s.newPwd != s.confirmPwd -> _state.update { it.copy(message = "两次输入的新密码不一致") }
            s.newPwd == s.oldPwd -> _state.update { it.copy(message = "新密码不能与旧密码相同") }
            else -> {
                _state.update { it.copy(saving = true, message = null) }
                runCatching {
                    safeApiCall(json) { authApi.changePassword(PasswordChangeBody(s.oldPwd, s.newPwd)) }
                }.onSuccess {
                    _state.update { it.copy(saving = false, done = true, message = "密码已更新") }
                }.onFailure { e ->
                    _state.update { it.copy(saving = false, message = e.message ?: "修改失败") }
                }
            }
        }
    }
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    vm: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.oldPwd,
                    onValueChange = vm::setOld,
                    label = { Text("当前密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.newPwd,
                    onValueChange = vm::setNew,
                    label = { Text("新密码（≥8位含字母数字）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.confirmPwd,
                    onValueChange = vm::setConfirm,
                    label = { Text("再次输入新密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
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
            TextButton(enabled = !state.saving, onClick = { vm.submit() }) {
                Text(if (state.saving) "保存中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.reset(); onDismiss() }) { Text("取消") }
        },
    )
}
