package com.xjie.app.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.ui.theme.XjiePalette

@Composable
fun LoginScreen(
    vm: LoginViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadSubjects() }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            LogoArea()
            ModeSwitch(mode = state.mode, onToggle = vm::toggleMode)

            when (state.mode) {
                LoginMode.Subject -> SubjectSection(
                    state = state,
                    onSelect = vm::setSelectedSubject,
                    onLogin = vm::loginSubject,
                )
                LoginMode.Phone -> PhoneSection(
                    state = state,
                    onPhoneChange = vm::setPhone,
                    onUsernameChange = vm::setUsername,
                    onPasswordChange = vm::setPassword,
                    onSexChange = vm::setSex,
                    onAgeChange = vm::setAge,
                    onHeightChange = vm::setHeightCm,
                    onWeightChange = vm::setWeightKg,
                    onToggleSignup = vm::toggleSignup,
                    onSubmit = vm::loginPhone,
                    onForgot = { showReset = true },
                )
            }
        }
    }
    if (showReset) {
        PasswordResetDialog(onDismiss = { showReset = false })
    }
}

@Composable
private fun LogoArea() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 40.dp),
    ) {
        Image(
            painter = painterResource(id = com.xjie.app.R.drawable.ic_logo),
            contentDescription = "Xjie Logo",
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape),
        )
        Text("Xjie", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text(
            "智能代谢健康管理",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeSwitch(mode: LoginMode, onToggle: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        HorizontalDivider()
        TextButton(onClick = onToggle) {
            Text(
                if (mode == LoginMode.Subject) "使用手机号登录" else "使用受试者 ID 登录",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SubjectSection(
    state: LoginUiState,
    onSelect: (String) -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "选择受试者",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.subjects.isEmpty()) {
            Text(
                "暂无可用受试者",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 280.dp),
            ) {
                state.subjects.forEach { sub ->
                    SubjectRow(
                        id = sub.subject_id,
                        cohort = sub.cohort,
                        selected = state.selectedSubject == sub.subject_id,
                        onClick = { onSelect(sub.subject_id) },
                    )
                }
            }
        }
        GradientButton(
            label = "登录",
            enabled = state.selectedSubject.isNotBlank() && !state.loading,
            loading = state.loading,
            onClick = onLogin,
        )
    }
}

@Composable
private fun SubjectRow(
    id: String,
    cohort: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor,
        ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(id, modifier = Modifier.weight(1f))
            CohortBadge(cohort)
        }
    }
}

@Composable
private fun CohortBadge(cohort: String?) {
    val isCgm = cohort == "cgm"
    val bg = if (isCgm) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    else XjiePalette.Success.copy(alpha = 0.1f)
    val fg = if (isCgm) MaterialTheme.colorScheme.primary else XjiePalette.Success
    Surface(shape = RoundedCornerShape(4.dp), color = bg) {
        Text(
            if (isCgm) "CGM" else "肝脏",
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PhoneSection(
    state: LoginUiState,
    onPhoneChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSexChange: (String) -> Unit,
    onAgeChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onWeightChange: (Int) -> Unit,
    onToggleSignup: () -> Unit,
    onSubmit: () -> Unit,
    onForgot: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LabeledField(label = "手机号") {
            OutlinedTextField(
                value = state.phone,
                onValueChange = onPhoneChange,
                placeholder = { Text("请输入手机号") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.isSignup) {
            LabeledField(label = "用户名") {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    placeholder = { Text("请输入用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        LabeledField(label = "密码") {
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                placeholder = { Text("至少 8 位") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.isSignup) {
            LabeledField(label = "个人资料（上下滚动选择）") {
                ProfileWheelGrid(
                    sex = state.sex,
                    age = state.age,
                    heightCm = state.heightCm,
                    weightKg = state.weightKg,
                    onSexChange = onSexChange,
                    onAgeChange = onAgeChange,
                    onHeightChange = onHeightChange,
                    onWeightChange = onWeightChange,
                )
            }
        }
        GradientButton(
            label = if (state.isSignup) "注册" else "登录",
            enabled = !state.loading,
            loading = state.loading,
            onClick = onSubmit,
        )
        TextButton(
            onClick = onToggleSignup,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.isSignup) "已有账号？去登录" else "没有账号？去注册",
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (!state.isSignup) {
            TextButton(onClick = onForgot, modifier = Modifier.fillMaxWidth()) {
                Text("忘记密码？", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ProfileWheelGrid(
    sex: String,
    age: Int,
    heightCm: Int,
    weightKg: Int,
    onSexChange: (String) -> Unit,
    onAgeChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onWeightChange: (Int) -> Unit,
) {
    val sexOptions = remember { listOf("female" to "女", "male" to "男", "other" to "其他") }
    val ageItems = remember { (1..100).map { "$it" } }
    val heightItems = remember { (120..220).map { "$it" } }
    val weightItems = remember { (30..200).map { "$it" } }

    com.xjie.app.core.ui.components.WheelPickerRow(
        columns = listOf(
            com.xjie.app.core.ui.components.WheelColumn(
                label = "性别",
                items = sexOptions.map { it.second },
                selectedIndex = sexOptions.indexOfFirst { it.first == sex }.coerceAtLeast(0),
                onSelected = { onSexChange(sexOptions[it].first) },
            ),
            com.xjie.app.core.ui.components.WheelColumn(
                label = "年龄",
                items = ageItems,
                selectedIndex = (age - 1).coerceIn(0, ageItems.size - 1),
                onSelected = { onAgeChange(it + 1) },
            ),
            com.xjie.app.core.ui.components.WheelColumn(
                label = "身高(cm)",
                items = heightItems,
                selectedIndex = (heightCm - 120).coerceIn(0, heightItems.size - 1),
                onSelected = { onHeightChange(it + 120) },
            ),
            com.xjie.app.core.ui.components.WheelColumn(
                label = "体重(kg)",
                items = weightItems,
                selectedIndex = (weightKg - 30).coerceIn(0, weightItems.size - 1),
                onSelected = { onWeightChange(it + 30) },
            ),
        ),
        itemHeight = 36.dp,
        visibleItemCount = 5,
    )
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun GradientButton(
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            XjiePalette.GradientStart,
                            XjiePalette.GradientEnd,
                        )
                    )
                )
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    label,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
