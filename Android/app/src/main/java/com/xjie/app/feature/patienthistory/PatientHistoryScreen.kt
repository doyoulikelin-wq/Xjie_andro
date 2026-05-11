package com.xjie.app.feature.patienthistory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.PatientHistoryField
import com.xjie.app.core.model.PatientHistoryMetric
import com.xjie.app.core.ui.theme.cardStyle

private data class SectionMeta(val key: String, val title: String, val hint: String)

private val sectionMetas = listOf(
    SectionMeta("diagnoses", "既往明确诊断", "只写医生已经明确诊断过的疾病或长期健康问题。"),
    SectionMeta("surgeries", "手术或住院史", "写明年份、原因和主要经过；没有则标记明确无。"),
    SectionMeta("medications", "长期或当前用药", "写药名、用途和是否仍在使用。"),
    SectionMeta("allergies", "过敏或不良反应", "包括药物、食物和明显不耐受。"),
    SectionMeta("recent_findings", "近一年重要异常检查", "只保留真正会影响医生判断的异常结论。"),
    SectionMeta("care_goals", "本次就诊重点关注", "患者希望医生重点解释或核查的问题。"),
    SectionMeta("family_history", "家族史", "直系亲属的重要疾病史。"),
    SectionMeta("lifestyle_risks", "生活方式风险因素", "如吸烟、饮酒、睡眠差、久坐等。"),
)

private val statusOptions = listOf(
    "confirmed" to "已确认",
    "pending_review" to "待核对",
    "none" to "明确无",
    "missing" to "未填写",
    "documented" to "有资料",
)

private val sourceOptions = listOf(
    "user" to "患者填写",
    "document" to "资料提取",
    "both" to "两者结合",
    "system" to "系统汇总",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHistoryScreen(
    onBack: () -> Unit,
    onOpenHealthDataFocus: (String) -> Unit,
    vm: PatientHistoryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbar.showSnackbar(it); vm.clearToast() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("病史整理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = vm::save, enabled = !state.loading && !state.saving) {
                        Text(if (state.saving) "保存中" else "保存")
                    }
                },
            )
        },
    ) { inner ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.saving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                IntroCard(completeness = state.profile.completeness)
                DoctorSummaryCard(
                    summary = state.profile.doctor_summary,
                    updatedAt = state.profile.updated_at,
                    onValueChange = vm::updateDoctorSummary,
                )
                EvidenceOverviewCard(
                    state = state,
                    onOpenHealthDataFocus = onOpenHealthDataFocus,
                )
                KeyMetricsCard(
                    metrics = state.profile.key_metrics,
                    onOpenHealthDataFocus = onOpenHealthDataFocus,
                )
                MissingTasksCard(
                    state = state,
                    onOpenHealthDataFocus = onOpenHealthDataFocus,
                )

                sectionMetas.forEach { meta ->
                    val field = state.profile.sections[meta.key] ?: PatientHistoryField()
                    SectionEditorCard(
                        title = meta.title,
                        hint = meta.hint,
                        field = field,
                        onValueChange = { value ->
                            vm.updateSection(meta.key) { it.copy(value = value) }
                        },
                        onDateChange = { value ->
                            vm.updateSection(meta.key) { it.copy(date_label = value.ifBlank { null }) }
                        },
                        onStatusChange = { value ->
                            vm.updateSection(meta.key) { it.copy(status = value) }
                        },
                        onSourceTypeChange = { value ->
                            vm.updateSection(meta.key) { it.copy(source_type = value) }
                        },
                        onSourceRefChange = { value ->
                            vm.updateSection(meta.key) { it.copy(source_ref = value.ifBlank { null }) }
                        },
                        onVerifiedChange = { checked ->
                            vm.updateSection(meta.key) { it.copy(verified_by_user = checked) }
                        },
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun IntroCard(completeness: Double) {
    Surface(
        modifier = Modifier.cardStyle(),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "给医生看的病史摘要",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "这里应只保留已确认或有资料支持的事实。缺失项会单独列出，不混入医生摘要。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "当前完整度 ${(completeness * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DoctorSummaryCard(
    summary: String,
    updatedAt: String?,
    onValueChange: (String) -> Unit,
) {
    Surface(modifier = Modifier.cardStyle(), color = Color.Transparent) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EditNote, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    "医生摘要",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedTextField(
                value = summary,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("给医生看的摘要") },
                placeholder = { Text("例如：2 年前确诊脂肪肝，目前口服二甲双胍，无药物过敏。") },
            )
            Text(
                updatedAt?.let { "最近更新 ${it.replace('T', ' ').take(16)}" } ?: "尚未保存",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EvidenceOverviewCard(
    state: PatientHistoryUiState,
    onOpenHealthDataFocus: (String) -> Unit,
) {
    val overview = state.profile.evidence_overview
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OverviewTile(
            modifier = Modifier.weight(1f),
            title = "历史病例",
            value = overview.record_count.toString(),
            subtitle = overview.latest_record_date ?: "未上传",
            onClick = { onOpenHealthDataFocus("records") },
        )
        OverviewTile(
            modifier = Modifier.weight(1f),
            title = "历史体检",
            value = overview.exam_count.toString(),
            subtitle = overview.latest_exam_date ?: "未上传",
            onClick = { onOpenHealthDataFocus("exams") },
        )
        OverviewTile(
            modifier = Modifier.weight(1f),
            title = "资料完整度",
            value = "${(state.profile.completeness * 100).toInt()}%",
            subtitle = "点此补资料",
            onClick = { onOpenHealthDataFocus("upload") },
        )
    }
}

@Composable
private fun OverviewTile(
    modifier: Modifier,
    title: String,
    value: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KeyMetricsCard(
    metrics: List<PatientHistoryMetric>,
    onOpenHealthDataFocus: (String) -> Unit,
) {
    Surface(modifier = Modifier.cardStyle(), color = Color.Transparent) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MonitorHeart, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("关键异常数值", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            if (metrics.isEmpty()) {
                Text(
                    "还没有从体检资料中提取到可定位的关键数值。上传体检后，这里会出现可点击的异常指标。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                metrics.forEach { metric ->
                    MetricRow(metric = metric, onOpenHealthDataFocus = onOpenHealthDataFocus)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    metric: PatientHistoryMetric,
    onOpenHealthDataFocus: (String) -> Unit,
) {
    val clickable = !metric.date_label.isNullOrBlank() && !metric.source_ref.isNullOrBlank()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (clickable) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            if (clickable) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(metric.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(metric.date_label, metric.source_ref?.let { "来源 $it" }).joinToString(" · ").ifBlank { "证据待补充" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                buildString {
                    append(metric.value)
                    metric.unit?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (clickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .then(
                        if (clickable) {
                            Modifier.background(Color.Transparent)
                        } else {
                            Modifier
                        },
                    ),
            )
            if (clickable) {
                TextButton(onClick = { onOpenHealthDataFocus(metric.focus) }) {
                    Text("核对")
                }
            }
        }
    }
}

@Composable
private fun MissingTasksCard(
    state: PatientHistoryUiState,
    onOpenHealthDataFocus: (String) -> Unit,
) {
    val missing = state.profile.missing_sections
    Surface(modifier = Modifier.cardStyle(), color = Color.Transparent) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("待补充与待核对", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            if (missing.isEmpty()) {
                Text(
                    "目前核心病史项已覆盖。如有新资料，可继续从健康数据页补充。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "缺失项：${missing.joinToString("、") { it.label }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onOpenHealthDataFocus("upload") }) {
                    Icon(Icons.Filled.UploadFile, null)
                    Spacer(Modifier.width(4.dp))
                    Text("去补资料")
                }
                if (state.profile.evidence_overview.record_count == 0) {
                    TextButton(onClick = { onOpenHealthDataFocus("records") }) {
                        Text("去补病例")
                    }
                }
                if (state.profile.evidence_overview.exam_count == 0) {
                    TextButton(onClick = { onOpenHealthDataFocus("exams") }) {
                        Text("去补体检")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionEditorCard(
    title: String,
    hint: String,
    field: PatientHistoryField,
    onValueChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onSourceTypeChange: (String) -> Unit,
    onSourceRefChange: (String) -> Unit,
    onVerifiedChange: (Boolean) -> Unit,
) {
    Surface(modifier = Modifier.cardStyle(), color = Color.Transparent) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = field.value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("内容") },
                minLines = 2,
            )
            ChipRow(
                items = statusOptions,
                selected = field.status,
                onSelected = onStatusChange,
            )
            OutlinedTextField(
                value = field.date_label.orEmpty(),
                onValueChange = onDateChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("时间 / 年份") },
                placeholder = { Text("如 2024-08 或 2 年前") },
            )
            ChipRow(
                items = sourceOptions,
                selected = field.source_type,
                onSelected = onSourceTypeChange,
            )
            OutlinedTextField(
                value = field.source_ref.orEmpty(),
                onValueChange = onSourceRefChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("来源标记") },
                placeholder = { Text("如 文档 ID、化验单、患者口述") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = field.verified_by_user, onCheckedChange = onVerifiedChange)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("已由患者核对", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "仅在确认无误后打开，医生摘要会优先采用已核对信息。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipRow(
    items: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label) },
            )
        }
    }
}