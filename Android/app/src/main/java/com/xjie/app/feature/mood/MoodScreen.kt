package com.xjie.app.feature.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.MoodDay
import com.xjie.app.core.model.MoodGlucoseCorrelation
import com.xjie.app.core.model.MoodLevel
import com.xjie.app.core.model.MoodSegment
import com.xjie.app.core.ui.components.EmptyState
import com.xjie.app.core.ui.components.LoadingIndicator
import com.xjie.app.core.ui.theme.cardStyle
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MoodScreen(
    onBack: (() -> Unit)? = null,
    vm: MoodViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("情绪日记") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.loading && state.days.isEmpty()) LoadingIndicator(label = "加载中...")
            TodayCard(state.today, state.saving, vm::checkIn)
            TrendCard(state.days, state.lookbackDays, vm::setLookback)
            state.correlation?.let { CorrelationCard(it) }
        }
    }
}

@Composable
private fun TodayCard(
    today: MoodDay?,
    saving: Boolean,
    onCheckIn: (MoodSegment, MoodLevel) -> Unit,
) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEmotions, null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("今日打卡", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (saving) CircularProgressIndicator(strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp))
        }
        Text("点击对应时段的 emoji 完成打卡，可重复点击修改",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoodSegment.entries.forEach { seg ->
            val current = today?.level(seg)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(seg.label, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text(seg.window, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    current?.let {
                        Text(MoodLevel.fromValue(it)?.emoji ?: "·",
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MoodLevel.entries.forEach { lvl ->
                        val selected = current == lvl.value
                        Surface(
                            onClick = { onCheckIn(seg, lvl) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f).heightIn(min = 36.dp)
                                .border(
                                    if (selected) 1.5.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    RoundedCornerShape(8.dp),
                                ),
                        ) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center) {
                                Text(lvl.emoji, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendCard(
    days: List<MoodDay>,
    lookback: Int,
    onLookback: (Int) -> Unit,
) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ShowChart, null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("近 $lookback 天情绪曲线",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(7, 14, 30).forEachIndexed { i, n ->
                SegmentedButton(
                    selected = lookback == n,
                    onClick = { onLookback(n) },
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                ) { Text("$n 天") }
            }
        }
        if (days.isEmpty()) {
            EmptyState("暂无打卡数据", description = "打卡几天后即可看到情绪走势")
        } else {
            HeatmapGrid(days)
        }
    }
}

@Composable
private fun HeatmapGrid(days: List<MoodDay>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 6.dp)) {
        Text("时段细览", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoodSegment.entries.forEach { seg ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(seg.label, modifier = Modifier.width(40.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                days.forEach { d ->
                    val v = d.level(seg)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 18.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (v == null) MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(v?.let { MoodLevel.fromValue(it)?.emoji } ?: "·",
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CorrelationCard(corr: MoodGlucoseCorrelation) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("情绪 × 血糖耦合度",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("Pearson r", corr.pearson_r?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                MaterialTheme.colorScheme.primary)
            Stat("配对样本", "${corr.paired_samples}", null)
            corr.p_value?.let {
                Stat("p", String.format(Locale.US, "%.3f", it), null)
            }
        }
        Text(corr.interpretation, style = MaterialTheme.typography.bodyMedium)
        Text("基于近 ${corr.days} 天的情绪打卡与同时段平均血糖计算，仅供参考。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Stat(label: String, value: String, accent: Color?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent ?: MaterialTheme.colorScheme.onSurface)
    }
}
