package com.xjie.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.GlucoseUnit
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    onOpenAdmin: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val unit by vm.glucoseUnit.collectAsState()
    val demo by vm.omicsDemo.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showChangePwd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccountCard(
                user = state.user,
                onEdit = { vm.showProfileEdit(true) },
            )
            InterventionCard(state.settings?.intervention_level, vm::updateLevel)
            GlucoseUnitCard(unit, vm::updateGlucoseUnit)
            DemoModeCard(demo, vm::toggleOmicsDemo)
            ConsentCard(
                aiChat = state.user?.consent?.allow_ai_chat ?: false,
                dataUpload = state.user?.consent?.allow_data_upload ?: false,
                onAiChat = { vm.toggleAiChat() },
                onDataUpload = { vm.toggleDataUpload() },
            )
            if (state.user?.is_admin == true) {
                Surface(
                    onClick = onOpenAdmin,
                    modifier = Modifier.cardStyle(),
                    color = Color.Transparent,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AdminPanelSettings, null, tint = XjiePalette.Warning)
                        Spacer(Modifier.width(8.dp))
                        Text("管理后台", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(
                onClick = { showChangePwd = true },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Filled.Lock, null)
                Spacer(Modifier.width(6.dp))
                Text("修改密码")
            }
            OutlinedButton(
                onClick = { vm.showLogoutAlert(true) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
            ) { Text("退出登录") }
        }
    }

    if (showChangePwd) {
        ChangePasswordDialog(onDismiss = { showChangePwd = false })
    }

    if (state.showLogoutAlert) {        AlertDialog(
            onDismissRequest = { vm.showLogoutAlert(false) },
            title = { Text("确认退出") },
            text = { Text("确定要退出登录吗？") },
            confirmButton = {
                TextButton(onClick = { vm.showLogoutAlert(false); vm.confirmLogout() }) {
                    Text("退出", color = XjiePalette.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.showLogoutAlert(false) }) { Text("取消") }
            },
        )
    }

    if (state.showProfileEdit) {
        ProfileEditDialog(
            current = state.user?.profile,
            onDismiss = { vm.showProfileEdit(false) },
            onSave = { sex, age, height, weight ->
                vm.updateProfile(sex, age, height.toDouble(), weight.toDouble())
            },
        )
    }
}

@Composable
private fun ProfileEditDialog(
    current: com.xjie.app.core.model.UserProfile?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int) -> Unit,
) {
    val sexOptions = remember { listOf("female" to "女", "male" to "男", "other" to "其他") }
    var sex by remember(current) {
        mutableStateOf(current?.sex?.lowercase()?.takeIf { sexOptions.any { p -> p.first == it } } ?: "female")
    }
    var age by remember(current) { mutableStateOf(current?.age ?: 30) }
    var heightCm by remember(current) { mutableStateOf(current?.height_cm?.toInt() ?: 165) }
    var weightKg by remember(current) { mutableStateOf(current?.weight_kg?.toInt() ?: 55) }

    val ageItems = remember { (1..100).map { "$it" } }
    val heightItems = remember { (120..220).map { "$it" } }
    val weightItems = remember { (30..200).map { "$it" } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改个人资料") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "上下滚动选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                com.xjie.app.core.ui.components.WheelPickerRow(
                    columns = listOf(
                        com.xjie.app.core.ui.components.WheelColumn(
                            label = "性别",
                            items = sexOptions.map { it.second },
                            selectedIndex = sexOptions.indexOfFirst { it.first == sex }.coerceAtLeast(0),
                            onSelected = { sex = sexOptions[it].first },
                        ),
                        com.xjie.app.core.ui.components.WheelColumn(
                            label = "年龄",
                            items = ageItems,
                            selectedIndex = (age - 1).coerceIn(0, ageItems.size - 1),
                            onSelected = { age = it + 1 },
                        ),
                        com.xjie.app.core.ui.components.WheelColumn(
                            label = "身高(cm)",
                            items = heightItems,
                            selectedIndex = (heightCm - 120).coerceIn(0, heightItems.size - 1),
                            onSelected = { heightCm = it + 120 },
                        ),
                        com.xjie.app.core.ui.components.WheelColumn(
                            label = "体重(kg)",
                            items = weightItems,
                            selectedIndex = (weightKg - 30).coerceIn(0, weightItems.size - 1),
                            onSelected = { weightKg = it + 30 },
                        ),
                    ),
                    itemHeight = 36.dp,
                    visibleItemCount = 5,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sex, age, heightCm, weightKg) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AccountCard(
    user: com.xjie.app.core.model.UserInfo?,
    onEdit: () -> Unit,
) {
    val profile = user?.profile
    Surface(
        onClick = onEdit,
        color = Color.Transparent,
        modifier = Modifier.cardStyle(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(Icons.Filled.Person, "账户信息")
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            InfoRow("手机号", user?.phone ?: user?.email ?: "--")
            InfoRow("用户名", user?.username ?: "--")
            InfoRow("性别", sexLabel(profile?.sex))
            InfoRow("年龄", profile?.age?.let { "$it 岁" } ?: "--")
            InfoRow("身高", profile?.height_cm?.let { "${it.toInt()} cm" } ?: "--")
            InfoRow("体重", profile?.weight_kg?.let { "${it.toInt()} kg" } ?: "--")
            InfoRow("注册时间", user?.created_at ?: "--")
            Text(
                "点击在线修改性别/年龄/身高/体重",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun sexLabel(raw: String?): String = when (raw?.lowercase()) {
    "female", "f", "女" -> "女"
    "male", "m", "男" -> "男"
    null, "" -> "--"
    else -> "其他"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InterventionCard(currentLevel: String?, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple("L1", "温和", "仅在高风险时提醒，每天最多 1 条"),
        Triple("L2", "标准", "中等风险时提醒，每天最多 2 条（默认）"),
        Triple("L3", "积极", "主动提醒，每天最多 4 条"),
    )
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(Icons.Filled.Bolt, "干预级别")
        items.forEach { (key, label, desc) ->
            val active = currentLevel == key
            val borderColor = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            Surface(
                onClick = { onSelect(key) },
                shape = RoundedCornerShape(8.dp),
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(if (active) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp)),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(key, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        if (active) Icon(Icons.Filled.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(desc, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GlucoseUnitCard(unit: GlucoseUnit, onSelect: (GlucoseUnit) -> Unit) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(Icons.Filled.WaterDrop, "血糖单位")
        Text("中国临床惯用 mmol/L，欧美多用 mg/dL。1 mmol/L = 18.018 mg/dL。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GlucoseUnit.entries.forEachIndexed { i, u ->
                SegmentedButton(
                    selected = unit == u,
                    onClick = { onSelect(u) },
                    shape = SegmentedButtonDefaults.itemShape(i, GlucoseUnit.entries.size),
                ) { Text(u.label) }
            }
        }
    }
}

@Composable
private fun DemoModeCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(Icons.Filled.Bolt, "多组学演示模式")
        Text("在尚无真实组学数据时，用合成的示例数据展示代谢指纹、蛋白炎症、基因风险与菌群画像。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("启用演示模式", Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ConsentCard(
    aiChat: Boolean,
    dataUpload: Boolean,
    onAiChat: () -> Unit,
    onDataUpload: () -> Unit,
) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(Icons.Filled.Lock, "隐私与同意")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("允许 AI 聊天", Modifier.weight(1f))
            Switch(checked = aiChat, onCheckedChange = { onAiChat() })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("允许数据上传", Modifier.weight(1f))
            Switch(checked = dataUpload, onCheckedChange = { onDataUpload() })
        }
    }
}
