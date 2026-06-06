package com.xjie.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xjie.app.core.model.DashboardHealth
import com.xjie.app.core.model.GlucoseFormat
import com.xjie.app.core.model.GlucoseSummary
import com.xjie.app.core.model.GlucoseUnit
import com.xjie.app.core.model.HealthTreeSummary
import com.xjie.app.core.model.MetabolicDayState
import com.xjie.app.core.model.MetabolicState
import com.xjie.app.core.model.ProactiveMessage
import com.xjie.app.core.model.WeeklyValidation
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.xjie.app.core.ui.components.BrandTitle
import com.xjie.app.core.ui.components.MetricItem
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle

@Composable
fun HomeScreen(
    vm: HomeViewModel = hiltViewModel(),
    onOpenSettings: () -> Unit = {},
    onOpenGlucose: () -> Unit = {},
    onOpenMeals: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenHealth: () -> Unit = {},
    onOpenHealthData: () -> Unit = {},
    onOpenElderlyHistory: () -> Unit = {},
    onOpenOmics: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val subjectId by vm.subjectId.collectAsState()
    val unit by vm.glucoseUnit.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) { vm.load() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WelcomeBar(subjectId = subjectId, onOpenSettings = onOpenSettings)

        MetabolicTopRow(
            metabolic = state.dashboard?.metabolic_state,
            weekly = state.dashboard?.weekly_validation,
        )

        if (state.elderlyMode) {
            // 老年模式：“关怀复查”取代主动提醒 + 干预滑块
            com.xjie.app.feature.elderly.ElderlyCareCard(onOpenHistory = onOpenElderlyHistory)
        } else {
            // 主动提醒：后端有真实文案时展示后端内容；否则轮播 18 条默认关怀文案
            ProactiveCard(state.proactive, onOpenChat = onOpenChat)
        }

        state.dashboard?.glucose?.last_24h?.let { g ->
            GlucoseCard(g, unit = unit, onOpen = onOpenGlucose)
        }

        HealthTreeSummaryCard(
            summary = state.treeSummary ?: HealthTreeSummary(),
            precision = state.contextPrecision,
            isLive = state.treeSummary != null,
            onOpenHealthData = onOpenHealthData,
            onOpenHistory = onOpenElderlyHistory,
            onOpenOmics = onOpenOmics,
        )

        MealsCard(state.dashboard)

        com.xjie.app.feature.exercise.ExerciseCard()

        QuickGrid(
            onOpenGlucose = onOpenGlucose,
            onOpenMeals = onOpenMeals,
            onOpenChat = onOpenChat,
            onOpenHealth = onOpenHealth,
        )

        if (!state.elderlyMode) {
            InterventionCard(
                index = state.interventionIndex,
                onChange = vm::setInterventionIndex,
            )
        }

        if (state.loading && state.dashboard == null) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun WelcomeBar(subjectId: String, onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.96f),
                        XjiePalette.Accent.copy(alpha = 0.86f),
                    ),
                ),
            )
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BrandTitle(
                    text = "你好",
                    colors = listOf(Color.White, Color.White.copy(alpha = 0.9f)),
                )
                Text(
                    "今天继续把代谢管理做得更稳一点",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.84f),
                )
                if (subjectId.isNotBlank()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            subjectId,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Surface(
                onClick = onOpenSettings,
                color = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
            ) {
                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            }
        }
    }
}

@Composable
private fun MetabolicTopRow(
    metabolic: MetabolicState?,
    weekly: WeeklyValidation?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetabolicStateCard(metabolic, Modifier.weight(1f))
        WeeklyValidationCard(weekly, Modifier.weight(1f))
    }
}

@Composable
private fun MetabolicStateCard(state: MetabolicState?, modifier: Modifier = Modifier) {
    var showOverview by remember { mutableStateOf(false) }
    Surface(
        onClick = { showOverview = true },
        modifier = modifier.heightIn(min = 154.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state?.title ?: "今日健康状态",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text("${state?.score ?: 0}", color = metabolicLevelColor(state?.level), fontWeight = FontWeight.Bold)
            }
            Text(
                state?.headline ?: "先建立今天的健康基线",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
            Text(
                state?.action ?: "记录一餐、完成一次计划或上传健康资料，小捷就能给出今天的最小行动。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Text(
                "依据：${sourceSummary(state?.data_sources)} · ${confidenceText(state)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("总览", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (showOverview) {
        MetabolicOverviewDialog(
            days = state?.overview.orEmpty(),
            onDismiss = { showOverview = false },
        )
    }
}

@Composable
private fun WeeklyValidationCard(weekly: WeeklyValidation?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 154.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("周验证", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${weekly?.adherence_pct ?: 0}%", color = XjiePalette.Success, fontWeight = FontWeight.Bold)
            }
            Text(
                weekly?.headline ?: "等待本周验证",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
            Text(
                weekly?.summary ?: "完成计划后，小捷会对比执行率和血糖变化。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniMetric("${weekly?.completed_actions ?: 0}/${weekly?.total_actions ?: 0}", "执行", Modifier.weight(1f))
                MiniMetric(deltaText(weekly?.tir_delta_pct), "TIR", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetabolicOverviewDialog(days: List<MetabolicDayState>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("健康状态总览") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "有 CGM 时优先结合连续血糖；没有 CGM 时根据饮食、计划、运动、健康资料和状态反馈生成低负担行动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                days.forEach { day ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(day.date.takeLast(5), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Text(day.headline, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("${day.score}", color = metabolicLevelColor(day.level), fontWeight = FontWeight.Bold)
                        }
                        Text(day.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("行动：${day.action}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "依据：${sourceSummary(day.data_sources)} · ${confidenceText(day.confidence)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

private fun sourceSummary(sources: List<String>?): String {
    val values = sources.orEmpty().filter { it.isNotBlank() }
    if (values.isEmpty()) return "待补数据"
    return values.take(3).joinToString("/")
}

private fun confidenceText(state: MetabolicState?): String {
    state?.confidence_label?.takeIf { it.isNotBlank() }?.let { return it }
    return confidenceText(state?.confidence)
}

private fun confidenceText(confidence: String?): String = when (confidence) {
    "high" -> "依据充分"
    "medium" -> "依据一般"
    else -> "信息较少"
}

private fun metabolicLevelColor(level: String?): Color = when (level) {
    "stable" -> XjiePalette.Success
    "watch" -> XjiePalette.Warning
    "risk" -> XjiePalette.Danger
    else -> Color(0xFF8A9491)
}

private fun deltaText(value: Double?): String =
    value?.let { "${if (it >= 0) "+" else ""}${"%.1f".format(it)}%" } ?: "--"

@Composable
private fun ProactiveCard(p: ProactiveMessage?, onOpenChat: () -> Unit) {
    val backendMessage = p?.message?.takeIf { it.isNotBlank() }
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "主动提醒",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(id = com.xjie.app.R.drawable.ic_logo),
                contentDescription = "Xjie",
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            if (backendMessage != null) {
                Text(
                    backendMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            } else {
                RotatingProactiveText(modifier = Modifier.weight(1f))
            }
        }
        if (p?.has_rescue == true) {
            Surface(
                onClick = onOpenChat,
                shape = RoundedCornerShape(14.dp),
                color = XjiePalette.Danger.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, XjiePalette.Danger.copy(alpha = 0.2f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = XjiePalette.Danger,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "有待处理的救援建议",
                        color = XjiePalette.Danger,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun RotatingProactiveText(modifier: Modifier = Modifier) {
    val pool = remember { PROACTIVE_FALLBACK_MESSAGES.shuffled() }
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(pool) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            index = (index + 1) % pool.size
        }
    }
    androidx.compose.animation.Crossfade(
        targetState = pool[index],
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 700),
        label = "proactive-rotation",
        modifier = modifier,
    ) { text ->
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InterventionCard(index: Int, onChange: (Int) -> Unit) {
    val labels = listOf("温和", "标准", "积极", "强化", "全场景")
    val descs = listOf(
        "仅高风险时提醒（1条/日）",
        "高风险提醒 + 每日复查（2条/日）",
        "中风险提醒 + 餐后建议（4条/日）",
        "低风险提醒 + 餐后复查 + 运动提醒（6条/日）",
        "错餐推送 + 夜间安眠 + 服药提醒（10条/日）",
    )
    val safeIdx = index.coerceIn(0, 4)
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("主动交互", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    labels[safeIdx],
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Slider(
            value = safeIdx.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 4)) },
            valueRange = 0f..4f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, name ->
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == safeIdx) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            descs[safeIdx],
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GlucoseCard(g: GlucoseSummary, unit: GlucoseUnit, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.cardStyle()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShowChart, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("今日血糖", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricItem(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = "平均",
                    value = GlucoseFormat.format(g.avg, unit, withUnit = false),
                    unit = unit.label,
                )
                MetricItem(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = "TIR",
                    value = g.tir_70_180_pct?.let { "%.1f".format(it) } ?: "--",
                    unit = "%",
                    accent = XjiePalette.Success,
                )
                MetricItem(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = "范围",
                    value = "${GlucoseFormat.threshold(g.min ?: 0.0, unit)}~${GlucoseFormat.threshold(g.max ?: 0.0, unit)}",
                    unit = unit.label,
                )
            }
        }
    }
}

@Composable
private fun HealthTreeSummaryCard(
    summary: HealthTreeSummary,
    precision: ContextPrecisionSummary,
    isLive: Boolean,
    onOpenHealthData: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenOmics: () -> Unit,
) {
    var showPrecisionDetails by remember { mutableStateOf(false) }
    Column(Modifier.cardStyle()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = com.xjie.app.R.drawable.healthtree_tree_06_fruiting),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "健康树",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (!isLive) {
                Text(
                    "同步中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricItem(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                label = "已养成",
                value = summary.trees_grown.toString(),
                unit = "棵",
            )
            MetricItem(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                label = "结果次数",
                value = summary.fruiting_count.toString(),
                unit = "次",
                accent = XjiePalette.Success,
            )
            Surface(
                onClick = { showPrecisionDetails = true },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "精准度",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${precision.score}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
    if (showPrecisionDetails) {
        ContextPrecisionDialog(
            precision = precision,
            onDismiss = { showPrecisionDetails = false },
            onOpenHealthData = {
                showPrecisionDetails = false
                onOpenHealthData()
            },
            onOpenHistory = {
                showPrecisionDetails = false
                onOpenHistory()
            },
            onOpenOmics = {
                showPrecisionDetails = false
                onOpenOmics()
            },
        )
    }
}

@Composable
private fun ContextPrecisionDialog(
    precision: ContextPrecisionSummary,
    onDismiss: () -> Unit,
    onOpenHealthData: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenOmics: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("数据精准度 ${precision.score}%", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "根据已上传健康资料、通知反馈和多组学特征估算。资料越完整，小捷给出的计划和建议越贴近个人情况。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ContextPrecisionRow(
                    icon = Icons.Default.Favorite,
                    title = "健康数据",
                    subtitle = precision.healthDataDescription,
                    value = "${precision.healthRecordCount + precision.healthExamCount} 份",
                    onClick = onOpenHealthData,
                )
                ContextPrecisionRow(
                    icon = Icons.Default.History,
                    title = "历史记录",
                    subtitle = precision.historyDescription,
                    value = "${precision.historyFeedbackCount} 条",
                    onClick = onOpenHistory,
                )
                ContextPrecisionRow(
                    icon = Icons.Default.Hub,
                    title = "多组学数据",
                    subtitle = precision.omicsDescription,
                    value = "${precision.omicsCategoryCount} 类",
                    onClick = onOpenOmics,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun ContextPrecisionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun MealsCard(d: DashboardHealth?) {
    Column(Modifier.cardStyle()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Restaurant, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("今日膳食", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${d?.kcal_today?.toInt() ?: 0} kcal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${d?.meals_today?.size ?: 0} 餐",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class QuickEntry(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun QuickGrid(
    onOpenGlucose: () -> Unit,
    onOpenMeals: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    val items = listOf(
        QuickEntry(Icons.Default.Monitor, "血糖曲线", onOpenGlucose),
        QuickEntry(Icons.Default.CameraAlt, "记录膳食", onOpenMeals),
        QuickEntry(Icons.Default.Chat, "助手小捷", onOpenChat),
        QuickEntry(Icons.Default.ListAlt, "健康总览", onOpenHealth),
    )
    // Use a simple 2-column grid via Rows (LazyVerticalGrid inside scroll has conflicts)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "快捷入口",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { entry ->
                    Surface(
                        onClick = entry.onClick,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        ),
                        shadowElevation = 6.dp,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Box(
                                    modifier = Modifier.padding(12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        entry.icon,
                                        contentDescription = entry.label,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
