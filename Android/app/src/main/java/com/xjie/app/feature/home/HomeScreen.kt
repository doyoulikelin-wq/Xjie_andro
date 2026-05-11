package com.xjie.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
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
import com.xjie.app.core.model.DashboardHealth
import com.xjie.app.core.model.GlucoseFormat
import com.xjie.app.core.model.GlucoseSummary
import com.xjie.app.core.model.GlucoseUnit
import com.xjie.app.core.model.ProactiveMessage
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
) {
    val state by vm.state.collectAsState()
    val subjectId by vm.subjectId.collectAsState()
    val unit by vm.glucoseUnit.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WelcomeBar(subjectId = subjectId, onOpenSettings = onOpenSettings)

        // 主动提醒：后端有真实文案时展示后端内容；否则轮播 18 条默认关怀文案
        ProactiveCard(state.proactive, onOpenChat = onOpenChat)

        state.dashboard?.glucose?.last_24h?.let { g ->
            GlucoseCard(g, unit = unit)
        }

        MealsCard(state.dashboard)

        QuickGrid(
            onOpenGlucose = onOpenGlucose,
            onOpenMeals = onOpenMeals,
            onOpenChat = onOpenChat,
            onOpenHealth = onOpenHealth,
        )

        InterventionCard(
            index = state.interventionIndex,
            onChange = vm::setInterventionIndex,
        )

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
    val labels = listOf("温和", "标准", "积极")
    val descs = listOf("仅高风险时提醒", "中等风险时提醒", "主动积极提醒")
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
                    labels[index.coerceIn(0, 2)],
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 2)) },
            valueRange = 0f..2f,
            steps = 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            ),
        )
        Text(
            descs[index.coerceIn(0, 2)],
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GlucoseCard(g: GlucoseSummary, unit: GlucoseUnit) {
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
