package com.xjie.app.feature.glucose

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.GlucoseFormat
import com.xjie.app.core.model.GlucoseSummary
import com.xjie.app.core.model.GlucoseUnit
import com.xjie.app.core.ui.components.EmptyState
import com.xjie.app.core.ui.components.LoadingIndicator
import com.xjie.app.core.ui.components.MetricItem
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle
import com.xjie.app.core.util.ChartConstants
import com.xjie.app.core.util.NumberUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GlucoseScreen(
    onBack: (() -> Unit)? = null,
    vm: GlucoseViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val unit by vm.unit.collectAsState()

    LaunchedEffect(Unit) { vm.loadInitial() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("血糖曲线") },
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
            WindowTabs(current = state.window, onSelect = vm::setWindow)

            state.summary?.let { SummaryCard(it, unit) }

            ChartCard(
                loading = state.loading,
                points = state.chart,
                window = state.window,
                unit = unit,
            )
        }
    }
}

@Composable
private fun WindowTabs(current: GlucoseWindow, onSelect: (GlucoseWindow) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(4.dp),
    ) {
        GlucoseWindow.entries.forEach { w ->
            val selected = w == current
            val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            val fg = if (selected) Color.White else MaterialTheme.colorScheme.primary
            Surface(
                onClick = { onSelect(w) },
                color = bg,
                contentColor = fg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    w.label,
                    modifier = Modifier.padding(vertical = 8.dp),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(s: GlucoseSummary, unit: GlucoseUnit) {
    Row(
        Modifier.cardStyle(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MetricItem(
            modifier = Modifier.weight(1f),
            label = "平均 ${unit.label}",
            value = GlucoseFormat.format(s.avg, unit, withUnit = false),
        )
        MetricItem(
            modifier = Modifier.weight(1f),
            label = "TIR",
            value = s.tir_70_180_pct?.let { "${NumberUtils.toFixed(it)}%" } ?: "--",
            accent = XjiePalette.Success,
        )
        MetricItem(
            modifier = Modifier.weight(1f),
            label = "变异性",
            value = s.variability ?: "--",
        )
    }
}

@Composable
private fun ChartCard(
    loading: Boolean,
    points: List<ChartPoint>,
    window: GlucoseWindow,
    unit: GlucoseUnit,
) {
    Column(Modifier.cardStyle()) {
        Text("血糖曲线", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        when {
            loading -> LoadingIndicator(modifier = Modifier.height(240.dp))
            points.isEmpty() -> EmptyState("暂无血糖数据", modifier = Modifier.height(240.dp))
            else -> {
                GlucoseCanvas(
                    points = points,
                    window = window,
                    unit = unit,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
                Spacer(Modifier.height(8.dp))
                Legend(unit = unit, window = window)
                Spacer(Modifier.height(4.dp))
                Text(
                    "提示：双指缩放 · 拖动平移 · 单击查看数值 · 双击重置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Legend(unit: GlucoseUnit, window: GlucoseWindow) {
    val tl = GlucoseFormat.threshold(ChartConstants.TARGET_LOW, unit)
    val th = GlucoseFormat.threshold(ChartConstants.TARGET_HIGH, unit)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendItem(color = XjiePalette.Success.copy(alpha = 0.3f),
            label = "目标 $tl-$th ${unit.label}")
        LegendItem(color = MaterialTheme.colorScheme.primary, label = "血糖值")
        if (window == GlucoseWindow.H24) {
            LegendItem(color = XjiePalette.Warning, label = "基线均值")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, shape = RoundedCornerShape(4.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GlucoseCanvas(
    points: List<ChartPoint>,
    window: GlucoseWindow,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val targetBg = XjiePalette.Success.copy(alpha = 0.12f)
    val pastColor = MaterialTheme.colorScheme.onSurfaceVariant
    val baselineColor = XjiePalette.Warning
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val tooltipBg = MaterialTheme.colorScheme.surface.toArgb()
    val tooltipFg = MaterialTheme.colorScheme.onSurface.toArgb()

    val padLeftPx = with(density) { ChartConstants.PAD_LEFT.dp.toPx() }
    val padRightPx = with(density) { ChartConstants.PAD_RIGHT.dp.toPx() }
    val padTopPx = with(density) { ChartConstants.PAD_TOP.dp.toPx() }
    val padBottomPx = with(density) { ChartConstants.PAD_BOTTOM.dp.toPx() }
    val labelTextSizePx = with(density) { 10.sp.toPx() }
    val tipTextSizePx = with(density) { 11.sp.toPx() }

    // Pan / zoom state. scaleX >= 1; offsetXFrac is left-edge fraction of full timeline [0,1].
    var scaleX by remember(points, window) { mutableStateOf(1f) }
    var offsetXFrac by remember(points, window) { mutableStateOf(0f) }
    var highlightIdx by remember(points, window) { mutableStateOf<Int?>(null) }
    var canvasWidthPx by remember { mutableStateOf(0f) }

    Canvas(
        modifier = modifier
            .pointerInput(points, window) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val w = canvasWidthPx
                    if (w <= 0f) return@detectTransformGestures
                    val chartW = (w - padLeftPx - padRightPx).coerceAtLeast(1f)
                    val newScale = (scaleX * zoom).coerceIn(1f, 8f)
                    // Apply pan (drag left = move right in time)
                    val visibleFrac = 1f / newScale
                    val panFrac = -pan.x / (chartW * newScale)
                    val newOffset = (offsetXFrac + panFrac).coerceIn(0f, 1f - visibleFrac)
                    scaleX = newScale
                    offsetXFrac = newOffset
                    highlightIdx = null
                }
            }
            .pointerInput(points, window) {
                detectTapGestures(
                    onDoubleTap = {
                        scaleX = 1f
                        offsetXFrac = 0f
                        highlightIdx = null
                    },
                    onTap = { pos ->
                        val w = canvasWidthPx
                        if (w <= 0f || points.isEmpty()) return@detectTapGestures
                        val chartW = (w - padLeftPx - padRightPx).coerceAtLeast(1f)
                        if (pos.x < padLeftPx || pos.x > w - padRightPx) {
                            highlightIdx = null
                            return@detectTapGestures
                        }
                        val xFrac = (pos.x - padLeftPx) / chartW
                        val tFrac = offsetXFrac + xFrac / scaleX
                        val minT = points.first().epochMs
                        val maxT = points.last().epochMs
                        val target = minT + ((maxT - minT) * tFrac).toLong()
                        val nearest = points.indices.minByOrNull {
                            kotlin.math.abs(points[it].epochMs - target)
                        }
                        highlightIdx = nearest
                    },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        canvasWidthPx = w
        val chartW = w - padLeftPx - padRightPx
        val chartH = h - padTopPx - padBottomPx

        val values = points.map { it.value }
        val minVal = kotlin.math.min(values.min(), 50.0)
        val maxVal = kotlin.math.max(values.max(), 200.0)
        val vRange = (maxVal - minVal).coerceAtLeast(1.0)

        fun yFor(v: Double): Float =
            (padTopPx + chartH * (1f - ((v - minVal) / vRange).toFloat()))

        // Target zone background
        val y180 = yFor(ChartConstants.TARGET_HIGH)
        val y70 = yFor(ChartConstants.TARGET_LOW)
        drawRect(
            color = targetBg,
            topLeft = Offset(padLeftPx, kotlin.math.max(y180, padTopPx)),
            size = Size(chartW, (y70 - y180).coerceAtLeast(0f)),
        )

        // Y reference lines + labels
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        ChartConstants.REF_LINES.forEach { rv ->
            val y = yFor(rv)
            drawLine(
                color = grid,
                start = Offset(padLeftPx, y),
                end = Offset(w - padRightPx, y),
                strokeWidth = 1f,
                pathEffect = dash,
            )
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = labelColor
                    textSize = labelTextSizePx
                    isAntiAlias = true
                }
                canvas.nativeCanvas.drawText(
                    GlucoseFormat.threshold(rv, unit), 4f, y - 2f, paint,
                )
            }
        }

        if (points.size < 2) return@Canvas

        val timestamps = points.map { it.epochMs }
        val minT = timestamps.min()
        val maxT = timestamps.max()
        val tRange = (maxT - minT).coerceAtLeast(1L)

        // Visible time window based on pan/zoom
        val visibleFrac = 1f / scaleX
        val visStartT = minT + (tRange * offsetXFrac).toLong()
        val visEndT = minT + (tRange * (offsetXFrac + visibleFrac)).toLong()
        val visTRange = (visEndT - visStartT).coerceAtLeast(1L)

        fun xForTs(ts: Long): Float =
            padLeftPx + chartW * ((ts - visStartT).toFloat() / visTRange.toFloat())

        fun xFor(idx: Int): Float = xForTs(timestamps[idx])

        // X tick labels (over visible window)
        val tickCount = when (window) {
            GlucoseWindow.H24 -> 6
            GlucoseWindow.D7 -> 7
            GlucoseWindow.ALL -> 5
        }
        val pattern = if (window == GlucoseWindow.H24) "HH:mm" else "M/d"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = labelColor
                textSize = labelTextSizePx
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            for (i in 0..tickCount) {
                val frac = i.toFloat() / tickCount
                val x = padLeftPx + chartW * frac
                val tickTs = visStartT + (visTRange * frac).toLong()
                canvas.nativeCanvas.drawText(fmt.format(Date(tickTs)), x, h - 6f, paint)
            }
        }

        // Build smooth curve (cubic) over visible+adjacent points
        val visIdx = timestamps.indices.filter {
            timestamps[it] in (visStartT - visTRange / 6)..(visEndT + visTRange / 6)
        }
        if (visIdx.size < 2) return@Canvas

        val xs = visIdx.map { xFor(it) }
        val ys = visIdx.map { yFor(values[it]) }

        fun buildSmoothPath(xs: List<Float>, ys: List<Float>): Path {
            val p = Path()
            if (xs.isEmpty()) return p
            p.moveTo(xs[0], ys[0])
            for (i in 1 until xs.size) {
                val midX = (xs[i - 1] + xs[i]) / 2f
                p.cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i])
            }
            return p
        }

        // Gradient area fill (under curve)
        val areaPath = buildSmoothPath(xs, ys).apply {
            lineTo(xs.last(), padTopPx + chartH)
            lineTo(xs.first(), padTopPx + chartH)
            close()
        }
        drawPath(
            areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primary.copy(alpha = 0.35f),
                    primary.copy(alpha = 0.05f),
                ),
                startY = padTopPx,
                endY = padTopPx + chartH,
            ),
        )

        if (window == GlucoseWindow.H24) {
            val avg = values.average()
            val yAvg = yFor(avg)
            drawLine(
                color = baselineColor,
                start = Offset(padLeftPx, yAvg),
                end = Offset(w - padRightPx, yAvg),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f),
            )
            val boundaryTs = System.currentTimeMillis() - 12 * 3600_000L
            val pastSubset = visIdx.takeWhile { timestamps[it] < boundaryTs }
            val curSubset = visIdx.dropWhile { timestamps[it] < boundaryTs }
            if (pastSubset.size >= 2) {
                drawPath(
                    buildSmoothPath(pastSubset.map { xFor(it) }, pastSubset.map { yFor(values[it]) }),
                    pastColor,
                    style = Stroke(width = 2f),
                )
            }
            if (curSubset.size >= 2) {
                drawPath(
                    buildSmoothPath(curSubset.map { xFor(it) }, curSubset.map { yFor(values[it]) }),
                    primary,
                    style = Stroke(width = 3f),
                )
            }
        } else {
            drawPath(buildSmoothPath(xs, ys), primary, style = Stroke(width = 2.5f))
        }

        // Highlight selected point
        highlightIdx?.let { idx ->
            if (idx in points.indices) {
                val px = xFor(idx)
                val py = yFor(values[idx])
                if (px in padLeftPx..(w - padRightPx)) {
                    drawLine(
                        color = primary.copy(alpha = 0.5f),
                        start = Offset(px, padTopPx),
                        end = Offset(px, padTopPx + chartH),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                    )
                    drawCircle(primary, radius = 5f, center = Offset(px, py))
                    drawCircle(Color.White, radius = 2.5f, center = Offset(px, py))

                    val tipText = "${GlucoseFormat.format(values[idx], unit, withUnit = true)}  " +
                        SimpleDateFormat(
                            if (window == GlucoseWindow.H24) "HH:mm" else "M/d HH:mm",
                            Locale.getDefault(),
                        ).format(Date(timestamps[idx]))
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            color = tooltipFg
                            textSize = tipTextSizePx
                            isAntiAlias = true
                        }
                        val textW = paint.measureText(tipText)
                        val padH = 10f; val padV = 6f
                        var rx = px - textW / 2f - padH
                        rx = rx.coerceIn(padLeftPx, w - padRightPx - textW - padH * 2)
                        val ry = (py - 28f).coerceAtLeast(padTopPx + 2f)
                        val bgPaint = Paint().apply {
                            color = tooltipBg
                            isAntiAlias = true
                            setShadowLayer(8f, 0f, 2f, 0x33000000)
                        }
                        canvas.nativeCanvas.drawRoundRect(
                            rx, ry, rx + textW + padH * 2, ry + tipTextSizePx + padV * 2,
                            8f, 8f, bgPaint,
                        )
                        canvas.nativeCanvas.drawText(
                            tipText, rx + padH, ry + tipTextSizePx + padV / 2, paint,
                        )
                    }
                }
            }
        }
    }
}
