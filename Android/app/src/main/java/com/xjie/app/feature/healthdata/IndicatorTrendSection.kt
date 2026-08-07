package com.xjie.app.feature.healthdata

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.IndicatorTrend
import com.xjie.app.core.model.TrendPoint
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle
import java.time.LocalDate
import kotlin.math.abs

internal object IndicatorTrendInteractionContract {
    private const val POINT_SPACING_DP = 52f
    private const val HORIZONTAL_PADDING_DP = 80f
    private const val MAX_CONTENT_WIDTH_DP = 6_000f

    fun orderedPoints(points: List<TrendPoint>): List<TrendPoint> =
        points
            .mapNotNull { point ->
                val dateKey = point.date.trim().take(10)
                val validDate = dateKey.length == 10 &&
                    runCatching { LocalDate.parse(dateKey) }.isSuccess
                point.takeIf { point.value.isFinite() && validDate }?.let { dateKey to it }
            }
            .sortedBy { it.first }
            .map { it.second }

    fun contentWidthDp(pointCount: Int, viewportWidthDp: Float): Float {
        val viewport = viewportWidthDp.coerceAtLeast(1f)
        if (pointCount <= 7) return viewport
        return (HORIZONTAL_PADDING_DP + (pointCount - 1) * POINT_SPACING_DP)
            .coerceIn(viewport, MAX_CONTENT_WIDTH_DP)
    }

    fun nearestIndex(x: Float, plotStart: Float, plotEnd: Float, pointCount: Int): Int? {
        if (pointCount <= 0 || plotEnd <= plotStart) return null
        if (pointCount == 1) return 0
        val ratio = ((x - plotStart) / (plotEnd - plotStart)).coerceIn(0f, 1f)
        return (ratio * (pointCount - 1)).toInt()
            .let { lower ->
                val upper = (lower + 1).coerceAtMost(pointCount - 1)
                val lowerX = plotStart + (plotEnd - plotStart) * lower / (pointCount - 1)
                val upperX = plotStart + (plotEnd - plotStart) * upper / (pointCount - 1)
                if (abs(x - lowerX) <= abs(x - upperX)) lower else upper
            }
    }

    fun displayValue(value: Double): String {
        if (value % 1.0 == 0.0) return value.toLong().toString()
        return "%.2f".format(value).trimEnd('0').trimEnd('.')
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndicatorTrendSection(
    vm: IndicatorTrendViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var showSelector by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }

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
            TextButton(onClick = { showManual = true }) {
                Icon(Icons.Filled.AddCircle, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("手动录入", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { showSelector = true }) {
                Text("管理", style = MaterialTheme.typography.labelMedium)
            }
        }

        state.error?.let { error ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("indicator.trend.error"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = IndicatorTrendErrorPresentation.message(error),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = vm::clearError) {
                        Text("知道了")
                    }
                }
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
                            "请先在「健康数据」中上传体检报告；识别后检查并确认入库，指标才会出现在趋势中。",
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
    if (showManual) {
        ManualIndicatorDialog(
            onDismiss = { showManual = false },
            onSaved = { vm.fetchIndicators() },
        )
    }
}

internal object IndicatorTrendErrorPresentation {
    private const val OWNER_CHANGED = "账号或健康数据所属用户已变化，请重新打开后再试。"
    private const val GENERIC = "关注指标暂时无法更新，请稍后重试。"

    fun message(error: String): String =
        if (error.contains("账号或健康数据所属用户已变化")) OWNER_CHANGED else GENERIC
}

@Composable
private fun IndicatorTrendCard(
    trend: IndicatorTrend,
    explanations: Map<String, com.xjie.app.core.model.IndicatorExplanation>,
    onLoadExplanation: (String) -> Unit,
) {
    var showExplain by remember { mutableStateOf(false) }
    val orderedPoints = remember(trend.points) {
        IndicatorTrendInteractionContract.orderedPoints(trend.points)
    }
    val last = orderedPoints.lastOrNull()
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
                    IndicatorTrendInteractionContract.displayValue(it.value),
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

        if (orderedPoints.isNotEmpty()) {
            IndicatorTrendChart(trend.copy(points = orderedPoints))
        } else {
            Box(
                Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无可用历史趋势",
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
                "${orderedPoints.size} 个数据点",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (orderedPoints.size >= 2) {
                Text(
                    "${orderedPoints.first().date} → ${orderedPoints.last().date}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun IndicatorTrendChart(
    trend: IndicatorTrend,
    modifier: Modifier = Modifier,
) {
    val points = remember(trend.points) {
        IndicatorTrendInteractionContract.orderedPoints(trend.points)
    }
    if (points.isEmpty()) {
        Box(
            modifier.fillMaxWidth().height(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "暂无可用历史趋势",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val values = points.map { it.value }
    val candidateRefLow = trend.ref_low?.takeIf { it.isFinite() }
    val candidateRefHigh = trend.ref_high?.takeIf { it.isFinite() }
    val validReferenceRange = if (
        candidateRefLow != null && candidateRefHigh != null && candidateRefLow <= candidateRefHigh
    ) {
        candidateRefLow to candidateRefHigh
    } else {
        null
    }
    val refLow = validReferenceRange?.first
    val refHigh = validReferenceRange?.second
    val rawMin = listOfNotNull(values.minOrNull(), refLow, refHigh).minOrNull() ?: 0.0
    val rawMax = listOfNotNull(values.maxOrNull(), refLow, refHigh).maxOrNull() ?: 1.0
    val padding = maxOf(abs(rawMin), abs(rawMax), 1.0) * 0.05
    val ymin = rawMin - padding
    val ymax = rawMax + padding
    val yRange = (ymax - ymin).takeIf { it > 0 } ?: 1.0

    val primary = XjiePalette.Primary
    val danger = XjiePalette.Danger
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val unit = trend.unit?.takeIf { it.isNotBlank() }.orEmpty()

    var selectedIdx by remember(points) { mutableStateOf<Int?>(points.lastIndex) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "轻点选择；长按拖动查看连续数据；左右滑动查看历史",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val sel = selectedIdx?.let { points.getOrNull(it) }
        if (sel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        primary.copy(alpha = 0.08f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics {
                        contentDescription = buildString {
                            append(trend.name)
                            append("，")
                            append(sel.date)
                            append("，")
                            append(IndicatorTrendInteractionContract.displayValue(sel.value))
                            if (unit.isNotEmpty()) append(" $unit")
                            if (sel.abnormal) append("，异常")
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    sel.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    IndicatorTrendInteractionContract.displayValue(sel.value) +
                        if (unit.isNotEmpty()) " $unit" else "",
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
                            "异常",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = danger,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // 计算 Y 轴 gutter 宽度：用最长 Y 标签宽度自适应，避免与折线重叠
        val labelTextSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 10.sp.toPx() }
        val measurePaint = remember { Paint().apply { textSize = labelTextSizePx; isAntiAlias = true } }
        val gutterW = remember(ymin, ymax, labelTextSizePx) {
            val candidates = listOf(ymax, (ymax + ymin) / 2.0, ymin)
            (candidates.maxOf { measurePaint.measureText("%.1f".format(it)) } + 12f)
                .coerceAtLeast(36f)
        }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .testTag("indicator.trend.scroll")
                .semantics {
                    contentDescription = buildString {
                        append(trend.name)
                        append("趋势图，共")
                        append(points.size)
                        append("个数据点。")
                        selectedIdx?.let { index ->
                            points.getOrNull(index)?.let { point ->
                                append("当前选择")
                                append(point.date)
                                append("，")
                                append(IndicatorTrendInteractionContract.displayValue(point.value))
                                if (unit.isNotEmpty()) append(" $unit")
                                if (point.abnormal) append("，异常")
                                append("。")
                            }
                        }
                        append("左右滑动查看历史；轻点选择；长按拖动查看连续数据。")
                    }
                    customActions = listOf(
                        CustomAccessibilityAction("上一个数据点") {
                            val current = selectedIdx ?: points.lastIndex
                            if (current > 0) {
                                selectedIdx = current - 1
                                true
                            } else {
                                false
                            }
                        },
                        CustomAccessibilityAction("下一个数据点") {
                            val current = selectedIdx ?: points.lastIndex
                            if (current < points.lastIndex) {
                                selectedIdx = current + 1
                                true
                            } else {
                                false
                            }
                        },
                    )
                },
        ) {
            val viewportWidth = maxWidth
            val contentWidth = IndicatorTrendInteractionContract.contentWidthDp(
                pointCount = points.size,
                viewportWidthDp = viewportWidth.value,
            ).dp
            val horizontalScrollState = rememberScrollState()

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState),
            ) {
                Canvas(
                    modifier = Modifier
                        .width(contentWidth)
                        .height(200.dp)
                        .pointerInput(points, gutterW) {
                        detectTapGestures { tap ->
                            selectedIdx = IndicatorTrendInteractionContract.nearestIndex(
                                x = tap.x,
                                plotStart = gutterW,
                                plotEnd = size.width - 12f,
                                pointCount = points.size,
                            )
                        }
                        }
                        .pointerInput(points, gutterW) {
                        // 普通拖动不在这里消费，由父级 horizontalScroll 处理；
                        // 只有长按成立后的拖动才消费并进入连续选点。
                        fun selectAt(x: Float) {
                            selectedIdx = IndicatorTrendInteractionContract.nearestIndex(
                                x = x,
                                plotStart = gutterW,
                                plotEnd = size.width - 12f,
                                pointCount = points.size,
                            )
                        }
                        detectDragGesturesAfterLongPress(
                            onDragStart = { selectAt(it.x) },
                            onDrag = { change, _ ->
                                change.consume()
                                selectAt(change.position.x)
                            },
                        )
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
                        "请先在「健康数据」页面上传体检报告（PDF / 图片）。\n识别后检查并确认入库，带有数值的指标（如 ALT、血糖、胆固醇等）才会出现在这里。",
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
