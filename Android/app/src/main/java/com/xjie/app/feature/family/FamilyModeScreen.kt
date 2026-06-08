package com.xjie.app.feature.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.FamilyInvite
import com.xjie.app.core.model.FamilyMember
import com.xjie.app.core.model.FamilyPermissionField
import com.xjie.app.core.model.FamilySubject
import com.xjie.app.core.model.FamilySubjectSummary
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyModeScreen(
    onBack: () -> Unit,
    vm: FamilyViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showInvite by remember { mutableStateOf(false) }
    var showAccept by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("家庭模式") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeaderCard()
            ActionRow(
                onInvite = { showInvite = true },
                onAccept = { showAccept = true },
            )
            state.latestInvite?.let { InviteCodeCard(it) }
            SubjectsCard(
                subjects = state.subjects,
                selected = state.selectedSubject,
                onSelect = vm::selectSubject,
            )
            state.selectedSummary?.let {
                SummaryCard(summary = it, onCare = { type, message -> vm.sendCareEvent(type, message) })
            }
            MembersPermissionCard(
                members = state.members.filter { it.user_id != state.currentUserId },
                value = vm::permissionValue,
                onChange = vm::updatePermission,
            )
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }

    if (showInvite) {
        InviteDialog(
            onDismiss = { showInvite = false },
            onSubmit = { phone, relation ->
                vm.createInvite(phone, relation)
                showInvite = false
            },
        )
    }
    if (showAccept) {
        AcceptDialog(
            onDismiss = { showAccept = false },
            onSubmit = { code, displayName ->
                vm.acceptInvite(code, displayName)
                showAccept = false
            },
        )
    }
}

@Composable
private fun HeaderCard() {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Person, null, tint = XjiePalette.Primary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("家庭照护协作", fontWeight = FontWeight.SemiBold)
                Text(
                    "家人可查看你授权的摘要，不能修改你的计划。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BadgeText("默认隐藏病史")
            BadgeText("单独授权")
            BadgeText("计划只读")
        }
    }
}

@Composable
private fun BadgeText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = XjiePalette.Primary,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(XjiePalette.Primary.copy(alpha = 0.08f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
private fun ActionRow(onInvite: () -> Unit, onAccept: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onInvite, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
            Text("邀请家人")
        }
        OutlinedButton(onClick = onAccept, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
            Text("输入邀请码")
        }
    }
}

@Composable
private fun InviteCodeCard(invite: FamilyInvite) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("最新邀请码", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(invite.invite_code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("7 天内有效", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "家人在自己的账号里输入该邀请码即可加入。加入后仍需你单独授权敏感数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubjectsCard(
    subjects: List<FamilySubject>,
    selected: FamilySubject?,
    onSelect: (FamilySubject) -> Unit,
) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("我关心的人", fontWeight = FontWeight.SemiBold)
        if (subjects.isEmpty()) {
            Text(
                "暂无家庭成员。可以先邀请家人，或输入家人给你的邀请码。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            subjects.forEach { subject ->
                Surface(
                    onClick = { onSelect(subject) },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(subject.display_name, fontWeight = FontWeight.SemiBold)
                            Text(subject.relation ?: "家庭成员", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (subject.user_id == selected?.user_id) {
                            Icon(Icons.Filled.CheckCircle, null, tint = XjiePalette.Primary)
                        } else {
                            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: FamilySubjectSummary, onCare: (String, String?) -> Unit) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${summary.subject.display_name} 今日摘要", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                summary.health_status.levelLabel,
                color = levelColor(summary.health_status.level),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricBox("计划", "${summary.plan.tasks_completed}/${summary.plan.tasks_total}", "${summary.plan.completion_pct}%", Modifier.weight(1f))
            MetricBox("血糖数据", "${summary.health_status.reading_count}", "条", Modifier.weight(1f))
            MetricBox("关怀记录", "${summary.care.today_checkins}", "次", Modifier.weight(1f))
        }
        if (summary.health_status.avg != null) {
            Text(
                "已授权血糖明细：平均 ${summary.health_status.avg.toInt()} mg/dL，TIR ${(summary.health_status.tir_70_180_pct ?: 0.0).toInt()}%。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "血糖明细未授权，仅显示数据量与风险等级。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        summary.alerts.forEach { alert ->
            Text(alert, style = MaterialTheme.typography.bodySmall, color = XjiePalette.Warning)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onCare("care_reminder", "家人提醒：记得完成今日计划") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text("提醒计划") }
            OutlinedButton(
                onClick = { onCare("care_message", "家人关心：今天感觉怎么样？") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text("发送关心") }
        }
    }
}

@Composable
private fun MetricBox(title: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(2.dp))
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MembersPermissionCard(
    members: List<FamilyMember>,
    value: (Long, FamilyPermissionField) -> Boolean,
    onChange: (Long, FamilyPermissionField, Boolean) -> Unit,
) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("授权管理", fontWeight = FontWeight.SemiBold)
        if (members.isEmpty()) {
            Text(
                "邀请家人加入后，可以在这里逐项授权。病例、体检、多组学等敏感数据默认不共享。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            members.forEach { member ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(member.bestName, fontWeight = FontWeight.SemiBold)
                    Text(member.relation ?: member.role, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FamilyPermissionField.entries.forEach { field ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(field.title)
                                Text(
                                    field.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = value(member.user_id, field),
                                onCheckedChange = { onChange(member.user_id, field, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteDialog(onDismiss: () -> Unit, onSubmit: (String?, String?) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("邀请家人") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(phone, onValueChange = { phone = it }, label = { Text("对方手机号（可选）") })
                OutlinedTextField(relation, onValueChange = { relation = it }, label = { Text("关系，如 父亲/母亲/配偶") })
                Text(
                    "邀请码只用于加入家庭。加入后仍需你单独授权敏感数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(phone.trim().ifBlank { null }, relation.trim().ifBlank { null }) }) {
                Text("生成")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AcceptDialog(onDismiss: () -> Unit, onSubmit: (String, String?) -> Unit) {
    var code by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入家庭") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(code, onValueChange = { code = it }, label = { Text("输入 8 位邀请码") })
                OutlinedTextField(displayName, onValueChange = { displayName = it }, label = { Text("显示名（可选）") })
                Text(
                    "加入家庭后，你可以关心多个家人。默认只能查看对方授权后的摘要，不能修改对方计划。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.trim().length >= 4,
                onClick = { onSubmit(code, displayName.trim().ifBlank { null }) },
            ) { Text("加入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun levelColor(level: String): Color = when (level) {
    "stable" -> XjiePalette.Primary
    "watch" -> XjiePalette.Warning
    "risk" -> XjiePalette.Danger
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
