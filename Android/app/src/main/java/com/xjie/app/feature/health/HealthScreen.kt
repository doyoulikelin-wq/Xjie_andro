package com.xjie.app.feature.health

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.ActionItem
import com.xjie.app.core.model.DailyPlan
import com.xjie.app.core.model.GlucoseFormat
import com.xjie.app.core.model.GlucoseStatus
import com.xjie.app.core.model.GlucoseUnit
import com.xjie.app.core.model.RescueItem
import com.xjie.app.core.ui.components.EmptyState
import com.xjie.app.core.ui.components.MarkdownText
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle
import com.xjie.app.core.util.DateUtils
import com.xjie.app.core.util.NumberUtils

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    onBack: (() -> Unit)? = null,
    onOpenMood: () -> Unit = {},
    vm: HealthViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.fetchData() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("健康总览") },
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
            state.briefing?.glucose_status?.let { GlucoseStatusCard(it) }
            state.briefing?.daily_plan?.let { DailyPlanCard(it) }
            state.briefing?.pending_rescues?.takeIf { it.isNotEmpty() }?.let { RescueCard(it) }
            state.briefing?.recent_actions?.takeIf { it.isNotEmpty() }?.let { ActionsCard(it) }
            HealthSummaryCard(
                summary = state.aiSummary,
                loading = state.summaryLoading,
                progress = state.summaryProgress,
                stage = state.summaryStage,
                onGenerate = vm::generateSummary,
            )
            MoodEntryCard(onOpenMood)
            if (state.briefing == null && state.reports == null && !state.loading) {
                EmptyState("暂无健康数据", description = "下拉刷新获取最新数据")
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, label: String, tint: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = tint ?: Color.Unspecified)
    }
}

@Composable
private fun GlucoseStatusCard(s: GlucoseStatus) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(Icons.Filled.BarChart, "当前血糖状态")
        Row(verticalAlignment = Alignment.Bottom) {
            s.current_mgdl?.let {
                Text(
                    GlucoseFormat.format(it, GlucoseUnit.MMOL, withUnit = false),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    GlucoseUnit.MMOL.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            s.trend?.let {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)) {
                    Text(it, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        s.tir_24h?.let {
            Text("24h TIR: ${NumberUtils.toFixed(it)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DailyPlanCard(plan: DailyPlan) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(Icons.Filled.Assignment, plan.payload.title ?: "今日计划")
        plan.payload.risk_windows?.takeIf { it.isNotEmpty() }?.let { windows ->
            SectionHeader(Icons.Filled.Warning, "风险窗口", tint = XjiePalette.Warning)
            windows.forEach { w ->
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${w.start ?: ""} - ${w.end ?: ""}",
                        style = MaterialTheme.typography.bodyMedium)
                    val isHigh = w.risk == "high"
                    Surface(
                        color = (if (isHigh) XjiePalette.Danger else XjiePalette.Warning)
                            .copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(w.risk ?: "",
                            color = if (isHigh) XjiePalette.Danger else XjiePalette.Warning,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        plan.payload.today_goals?.takeIf { it.isNotEmpty() }?.let { goals ->
            Text("目标", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
            goals.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun RescueCard(rescues: List<RescueItem>) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(Icons.Filled.Warning, "待处理救援", tint = XjiePalette.Danger)
        rescues.forEach { r ->
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.payload?.title ?: "", style = MaterialTheme.typography.bodyMedium)
                Text(r.payload?.risk_level ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ActionsCard(actions: List<ActionItem>) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(Icons.Filled.Notes, "最近操作")
        actions.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)) {
                    Text(a.action_type ?: "",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
                Text(DateUtils.formatDateTime(a.created_ts),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HealthSummaryCard(
    summary: String,
    loading: Boolean,
    progress: Float,
    stage: String,
    onGenerate: () -> Unit,
) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(Icons.Filled.Psychology, "AI 健康总结")
        if (summary.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.padding(12.dp)) { MarkdownText(summary) }
            }
        }
        if (loading) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stage, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        OutlinedButton(
            onClick = onGenerate, enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (summary.isBlank()) "生成 AI 健康总结" else "重新生成")
        }
    }
}

@Composable
private fun MoodEntryCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.cardStyle(),
        color = Color.Transparent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEmotions, null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("情绪日记", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("打卡 / 看曲线",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Filled.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
