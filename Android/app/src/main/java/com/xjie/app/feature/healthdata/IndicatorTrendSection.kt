package com.xjie.app.feature.healthdata

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.IndicatorTrend
import com.xjie.app.core.model.TrendPoint
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndicatorTrendSection(
    vm: IndicatorTrendViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var showSelector by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.fetchIndicators() }

    Column(
        Modifier.cardStyle(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ShowChart, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                "关注指标趋势",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showSelector = true }) {
                Icon(Icons.Filled.AddCircle, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("管理", style = MaterialTheme.typography.labelMedium)
            }
        }

        when {
            state.trendLoading -> {
                Box(
                    Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            }
            state.trends.isEmpty() -> {
                val hasIndicators = state.allIndicators.isNotEmpty()
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.ShowChart, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        if (hasIndicators) "暂未关注任何指标" else "还没有可关注的指标",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!hasIndicators) {
                        Text(
                            "请先在「健康数据」中上传体检报告，AI 识别完成后指标会自动出现。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    TextButton(onClick = { showSelector = true }) {
                        Text(if (hasIndicators) "添加关注指标" else "查看可选指标")
                    }
                }
            }
            else -> {
                state.trends.forEach { trend ->
                    IndicatorTrendCard(
                        trend = trend,
                        explanations = state.explanations,
                        onLoadExplanation = { vm.fetchExplanation(it) },
                    )
                }
            }
        }
    }

    if (showSelector) {
        IndicatorSelectorDialog(
            allIndicators = state.allIndicators,
            initialSelected = state.watchedNames.toSet(),
            onConfirm = { names ->
                showSelector = false
                vm.applySelection(names)
            },
            onDismiss = { showSelector = false },
        )
    }
}

@Composable
private fun IndicatorTrendCard(
    trend: IndicatorTrend,
    explanations: Map<String, com.xjie.app.core.model.IndicatorExplanation>,
    onLoadExplanation: (String) -> Unit,
) {
    var showExplain by remember { mutableStateOf(false) }
    val last = trend.points.lastOrNull()
    val abnormalLast = last?.abnormal == true

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                trend.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            trend.unit?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.width(4.dp))
                Text(
                    "($it)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    showExplain = !showExplain
                    if (showExplain) onLoadExplanation(trend.name)
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Filled.Info, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            last?.let {
                Text(
                    "%.1f".format(it.value),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (abnormalLast) XjiePalette.Danger else XjiePalette.Primary,
                )
            }
        }

        if (showExplain) {
            val exp = explanations[trend.name]
            if (exp != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(exp.brief, style = MaterialTheme.typography.labelMedium)
                    exp.normal_range?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "参考范围: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    exp.clinical_meaning?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "加载解释中...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (trend.points.size >= 2) {
            TrendChart(trend)
        } else {
            Box(
                Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "数据点不足，无法绘制趋势图",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ShowChart, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "${trend.points.size} 个数据点",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (trend.points.size >= 2) {
                Text(
                    "${trend.points.first().date} → ${trend.points.last().date}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrendChart(trend: IndicatorTrend) {
    val points: List<TrendPoint> = trend.points
    val values = points.map { it.value }
    val refLow = trend.ref_low
    val refHigh = trend.ref_high
    val ymin = (listOfNotNull(values.min(), refLow).min()).let { it - (it.coerceAtLeast(1.0) * 0.05) }
    val ymax = (listOfNotNull(values.max(), refHigh).max()).let { it + (it.coerceAtLeast(1.0) * 0.05) }
    val yRange = (ymax - ymin).takeIf { it > 0 } ?: 1.0

    val primary = XjiePalette.Primary
    val danger = XjiePalette.Danger
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val unit = trend.unit?.takeIf { it.isNotBlank() }.orEmpty()

    var selectedIdx by remember(points) { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 选中点信息条：点击后显示该点的时间、数值
        val sel = selectedIdx?.let { points.getOrNull(it) }
        if (sel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        primary.copy(alpha = 0.08f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    sel.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "%.2f".format(sel.value) + if (unit.isNotEmpty()) " $unit" else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sel.abnormal) danger else primary,
                )
                if (sel.abnormal) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = danger.copy(alpha = 0.15f),
                    ) {
                        Text(
                            "偏常",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = danger,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else {
            Text(
                "点击图中数据点可查看具体时间与数值",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 计算 Y 轴 gutter 宽度：用最长 Y 标签宽度自适应，避免与折线重叠
        val labelTextSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 10.sp.toPx() }
        val measurePaint = remember { Paint().apply { textSize = labelTextSizePx; isAntiAlias = true } }
        val gutterW = remember(ymin, ymax, labelTextSizePx) {
            val candidates = listOf(ymax, (ymax + ymin) / 2.0, ymin)
            (candidates.maxOf { measurePaint.measureText("%.1f".format(it)) } + 12f)
                .coerceAtLeast(36f)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(points, gutterW) {
                    detectTapGestures { tap ->
                        if (points.isEmpty()) return@detectTapGestures
                        val padLeft = gutterW
                        val padRight = 12f
                        val plotW = size.width - padLeft - padRight
                        val n = points.size
                        val xOf: (Int) -> Float = { i ->
                            if (n == 1) padLeft + plotW / 2f
                            else padLeft + plotW * i / (n - 1).toFloat()
                        }
                        var bestI = 0
                        var bestD = Float.MAX_VALUE
                        for (i in points.indices) {
                            val d = abs(xOf(i) - tap.x)
                            if (d < bestD) { bestD = d; bestI = i }
                        }
                        selectedIdx = if (bestD < 60f) bestI else null
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val padLeft = gutterW
            val padRight = 12f
            val padTop = 12f
            val padBottom = 28f   // 专属 X 轴标签带
            val plotW = w - padLeft - padRight
            val plotH = h - padTop - padBottom

            fun yToPx(v: Double) = (padTop + (1 - (v - ymin) / yRange) * plotH).toFloat()
            fun xToPx(i: Int) =
                if (points.size == 1) padLeft + plotW / 2f
                else padLeft + plotW * i / (points.size - 1).toFloat()

            val axisPaint = Paint().apply {
                color = axisColor.toArgb()
                textSize = labelTextSizePx
                isAntiAlias = true
            }
            val yLabelPaint = Paint(axisPaint).apply {
                textAlign = Paint.Align.RIGHT
            }
            val fm = axisPaint.fontMetrics
            val textHalfHeight = (fm.descent - fm.ascent) / 2f - fm.descent

            // 网格 + Y 轴标签（标签放在左侧 gutter，不进入绘图区）
            val yTicks = listOf(ymax, (ymax + ymin) / 2.0, ymin)
            yTicks.forEach { v ->
                val y = yToPx(v)
                drawLine(
                    color = gridColor,
                    start = Offset(padLeft, y),
                    end = Offset(w - padRight, y),
                    strokeWidth = 0.8f,
                )
                // 顶部和底部标签往内收，避免被裁
                val labelY = y + textHalfHeight
                val clampedY = labelY.coerceIn(padTop + textHalfHeight * 2f, h - padBottom - 2f)
                drawContext.canvas.nativeCanvas.drawText(
                    "%.1f".format(v),
                    padLeft - 6f, clampedY, yLabelPaint,
                )
            }

            // 参考带
            if (refLow != null && refHigh != null) {
                drawRect(
                    color = XjiePalette.Success.copy(alpha = 0.10f),
                    topLeft = Offset(padLeft, yToPx(refHigh)),
                    size = androidx.compose.ui.geometry.Size(plotW, yToPx(refLow) - yToPx(refHigh)),
                )
            }
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            refHigh?.let {
                val y = yToPx(it)
                drawLine(
                    color = XjiePalette.Danger.copy(alpha = 0.5f),
                    start = Offset(padLeft, y), end = Offset(w - padRight, y),
                    strokeWidth = 1f, pathEffect = dash,
                )
            }
            refLow?.let {
                val y = yToPx(it)
                drawLine(
                    color = XjiePalette.Primary.copy(alpha = 0.5f),
                    start = Offset(padLeft, y), end = Offset(w - padRight, y),
                    strokeWidth = 1f, pathEffect = dash,
                )
            }

            // 折线
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(xToPx(0), yToPx(points[0].value))
                    for (i in 1 until points.size) lineTo(xToPx(i), yToPx(points[i].value))
                }
                drawPath(path = path, color = primary, style = Stroke(width = 4f))
            }

            // 数据点
            points.forEachIndexed { i, p ->
                val isSel = selectedIdx == i
                val baseR = if (p.abnormal) 6f else 4f
                if (isSel) {
                    drawCircle(
                        color = primary.copy(alpha = 0.20f),
                        radius = baseR + 8f,
                        center = Offset(xToPx(i), yToPx(p.value)),
                    )
                }
                drawCircle(
                    color = if (p.abnormal) danger else primary,
                    radius = if (isSel) baseR + 2f else baseR,
                    center = Offset(xToPx(i), yToPx(p.value)),
                )
            }

            selectedIdx?.let { i ->
                val x = xToPx(i)
                drawLine(
                    color = primary.copy(alpha = 0.4f),
                    start = Offset(x, padTop),
                    end = Offset(x, padTop + plotH),
                    strokeWidth = 1f,
                    pathEffect = dash,
                )
            }

            // X 轴标签：放在专属底部带内，自动稀疏化避免重叠
            val n = points.size
            val firstDate = points[0].date
            val lastDate = points[n - 1].date
            val sampleW = maxOf(
                axisPaint.measureText(firstDate),
                axisPaint.measureText(lastDate),
            )
            // 根据可用宽度估算最多能放几个标签
            val maxLabels = ((plotW / (sampleW + 24f)).toInt()).coerceIn(2, 5)
            val xLabelIdx = when {
                n == 1 -> listOf(0)
                n <= maxLabels -> points.indices.toList()
                else -> {
                    val step = (n - 1).toFloat() / (maxLabels - 1)
                    (0 until maxLabels).map { (it * step).toInt().coerceIn(0, n - 1) }.distinct()
                }
            }
            val labelY = h - padBottom + (-fm.ascent) + 2f
            xLabelIdx.forEachIndexed { pos, i ->
                val date = points[i].date
                val tw = axisPaint.measureText(date)
                val rawX = xToPx(i)
                val drawX = when (pos) {
                    0 -> (rawX - 2f).coerceAtLeast(padLeft)
                    xLabelIdx.lastIndex -> (rawX - tw + 2f).coerceAtMost(w - padRight - tw)
                    else -> rawX - tw / 2f
                }
                drawContext.canvas.nativeCanvas.drawText(date, drawX, labelY, axisPaint)
            }
        }
    }
}

@Composable
private fun IndicatorSelectorDialog(
    allIndicators: List<com.xjie.app.core.model.IndicatorInfo>,
    initialSelected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(initialSelected) { mutableStateOf(initialSelected) }
    val grouped = remember(allIndicators) {
        allIndicators.groupBy { it.category ?: "其他" }.toSortedMap()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择关注指标") },
        text = {
            if (allIndicators.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Info, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        "还没有可关注的指标",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "请先在「健康数据」页面上传体检报告（PDF / 图片）。\nAI 识别完成后，那些带有数值的指标（如 ALT、血糖、胆固醇等）会自动出现在这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        "提示：“偏高/偏低”等定性描述的项目不会计入趋势，只有数值型结果才会进入指标库。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                return@AlertDialog
            }
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                grouped.forEach { (cat, items) ->
                    item {
                        Text(
                            cat,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    items(items, key = { it.name }) { ind ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val isOn = selected.contains(ind.name)
                            IconButton(
                                onClick = {
                                    selected = if (isOn) selected - ind.name
                                    else selected + ind.name
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    if (isOn) Icons.Filled.CheckCircle
                                    else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isOn) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                ind.name, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${ind.count}次",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
