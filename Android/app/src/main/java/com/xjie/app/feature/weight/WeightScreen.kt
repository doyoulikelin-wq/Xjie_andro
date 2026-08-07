package com.xjie.app.feature.weight

import android.graphics.Paint
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val WeightInk = Color(0xFF173F64)
private val WeightBody = Color(0xFF496A83)
private val WeightMuted = Color(0xFF6C8194)
private val WeightBlue = Color(0xFF168BC0)
private val WeightMint = Color(0xFF20CDB1)
private val WeightDanger = Color(0xFFE44C4C)

private enum class WeightSheet { Height, Weight, Guidance }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(
    onBack: () -> Unit,
    viewModel: WeightViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var activeSheet by remember { mutableStateOf<WeightSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.ownerGeneration) {
        activeSheet = null
    }
    LaunchedEffect(state.saveRevision) {
        if (state.saveRevision > 0L) activeSheet = null
    }
    BackHandler(enabled = activeSheet == null, onBack = onBack)

    WeightLiquidBackground {
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.dashboard != null,
            onRefresh = viewModel::load,
            modifier = Modifier
                .fillMaxSize()
                .testTag("weight.dashboard"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(
                        top = 14.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() + 28.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WeightHeader(onBack = onBack, onRefresh = viewModel::load)
                when (state.phase) {
                    WeightScreenPhase.Loading -> WeightLoadingCard()
                    WeightScreenPhase.Error -> WeightErrorCard(
                        message = state.loadError.orEmpty(),
                        onRetry = viewModel::load,
                    )
                    WeightScreenPhase.Empty,
                    WeightScreenPhase.Ready,
                    -> state.dashboard?.let { dashboard ->
                        if (state.loadError != null) {
                            WeightInlineError(state.loadError.orEmpty(), viewModel::load)
                        }
                        WeightLatestCard(
                            dashboard = dashboard,
                            onRecordHeight = { activeSheet = WeightSheet.Height },
                        )
                        WeightTrendCard(
                            dashboard = dashboard,
                            onOpenGuidance = { activeSheet = WeightSheet.Guidance },
                        )
                        WeightRecordButton(
                            enabled = !state.isLoading && !state.isSaving,
                            onClick = { activeSheet = WeightSheet.Weight },
                        )
                        WeightTrustBoundaryNote(dashboard.sourceLabels)
                    }
                }
            }
        }
    }

    when (activeSheet) {
        WeightSheet.Height -> ModalBottomSheet(
            onDismissRequest = { if (!state.isSaving) activeSheet = null },
            sheetState = sheetState,
            containerColor = Color(0xFFF3F6FA),
            dragHandle = null,
        ) {
            WeightHeightSheet(
                initialHeight = state.dashboard?.heightCm?.toInt(),
                saving = state.isSaving,
                onClose = { activeSheet = null },
                onSave = viewModel::recordHeight,
            )
        }
        WeightSheet.Weight -> ModalBottomSheet(
            onDismissRequest = { if (!state.isSaving) activeSheet = null },
            sheetState = sheetState,
            containerColor = Color(0xFFF3F8FC),
        ) {
            WeightPickerSheet(
                initialWeight = state.dashboard?.latestWeightKg,
                saving = state.isSaving,
                onCancel = { activeSheet = null },
                onSave = viewModel::recordWeight,
            )
        }
        WeightSheet.Guidance -> ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
            containerColor = Color(0xFFF8FCFF),
        ) {
            WeightGuidanceSheet(onClose = { activeSheet = null })
        }
        null -> Unit
    }

    state.operationError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearOperationError,
            title = { Text("保存失败") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearOperationError) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun WeightLiquidBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEAF8FF), Color(0xFFF4FBFF), Color(0xFFEAF9F5)),
                ),
            ),
    ) {
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.TopEnd)
                .background(Color(0xFF73D6F6).copy(alpha = 0.12f), CircleShape),
        )
        content()
    }
}

@Composable
private fun WeightHeader(onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).testTag("weight.back"),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上一页", tint = WeightBlue)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Brush.linearGradient(listOf(WeightBlue, WeightMint)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MonitorWeight, contentDescription = null, tint = Color.White)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("体重记录", color = WeightInk, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "关注最近三个月的体重变化",
                color = WeightMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(48.dp).testTag("weight.refresh"),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新体重记录", tint = WeightBlue)
        }
    }
}

@Composable
private fun WeightGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.95f)),
        shadowElevation = 0.dp,
    ) {
        content()
    }
}

@Composable
private fun WeightLoadingCard() {
    WeightGlassCard(Modifier.testTag("weight.loading")) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 220.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = WeightBlue)
            Spacer(Modifier.height(14.dp))
            Text("正在读取体重记录…", color = WeightBody, fontWeight = FontWeight.SemiBold)
            Text(
                "只展示当前账号中服务器已确认的趋势数据",
                color = WeightMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WeightErrorCard(message: String, onRetry: () -> Unit) {
    WeightGlassCard(Modifier.testTag("weight.error")) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("暂时无法读取体重记录", color = WeightInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(message.ifBlank { "请检查网络后重试。" }, color = WeightBody)
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("weight.retry"),
                colors = ButtonDefaults.buttonColors(containerColor = WeightBlue),
            ) { Text("重新读取") }
        }
    }
}

@Composable
private fun WeightInlineError(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("weight.refreshError"),
        color = Color(0xFFFFF2E7),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), color = Color(0xFF8E531E), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun WeightLatestCard(
    dashboard: WeightDashboardPresentation,
    onRecordHeight: () -> Unit,
) {
    val accessibility = buildString {
        append("最新一次体重")
        append(dashboard.latestWeightKg?.let { "${formatOneDecimal(it)}公斤" } ?: "暂无记录")
        dashboard.latestDate?.let { append("，${formatChineseDate(it)}") }
        append("，BMI ")
        append(dashboard.bmi?.let(::formatOneDecimal) ?: "暂不可计算")
    }
    WeightGlassCard(Modifier.testTag("weight.latest.card")) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("最新一次记录", color = WeightBody, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = accessibility },
            ) {
                val stack = maxWidth < 330.dp || LocalConfiguration.current.fontScale >= 1.25f
                if (stack) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WeightLatestValue(dashboard)
                        WeightBmiValue(dashboard)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        WeightLatestValue(dashboard)
                        WeightBmiValue(dashboard)
                    }
                }
            }
            if (dashboard.needsHeight) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("还没有记录身高，无法计算BMI", color = WeightBody, style = MaterialTheme.typography.bodySmall)
                    Surface(
                        onClick = onRecordHeight,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("weight.recordHeight"),
                        color = Color.White.copy(alpha = 0.62f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, WeightBlue.copy(alpha = 0.2f)),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Icon(Icons.Filled.Straighten, contentDescription = null, tint = WeightBlue)
                            Text("记录身高", color = WeightBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightLatestValue(dashboard: WeightDashboardPresentation) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                dashboard.latestWeightKg?.let(::formatOneDecimal) ?: "--",
                color = Color(0xFF102B4C),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(5.dp))
            Text("kg", color = WeightMuted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(
            dashboard.latestDate?.let(::formatChineseDate) ?: "暂无记录日期",
            color = WeightMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        dashboard.latestSource?.let { source ->
            Text("来源：${source.label}", color = WeightBlue, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun WeightBmiValue(dashboard: WeightDashboardPresentation) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("BMI", color = WeightMuted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(
            dashboard.bmi?.let(::formatOneDecimal) ?: "--",
            modifier = Modifier.testTag("weight.bmi.value"),
            color = WeightInk,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun WeightTrendCard(
    dashboard: WeightDashboardPresentation,
    onOpenGuidance: () -> Unit,
) {
    WeightGlassCard(Modifier.testTag("weight.trend")) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("体重变化", color = WeightInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = onOpenGuidance,
                            modifier = Modifier.size(48.dp).testTag("weight.trend.guidance"),
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = "体重变化说明", tint = WeightBlue)
                        }
                    }
                    Text("近三个月 · 左右滑动查看", color = WeightMuted, style = MaterialTheme.typography.labelSmall)
                }
                Text("长按查看", color = WeightBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }

            if (dashboard.recentSamples.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp).testTag("weight.trend.empty"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.ShowChart, contentDescription = null, tint = WeightMuted, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("近三个月暂无体重记录", color = WeightBody, fontWeight = FontWeight.Bold)
                    Text("记录体重后，这里会按日期生成趋势", color = WeightMuted, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                WeightTrendChart(dashboard)
            }
        }
    }
}

@Composable
private fun WeightRecordButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("weight.record"),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = WeightBlue),
    ) {
        Icon(Icons.Filled.AddCircle, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("记录体重", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeightTrustBoundaryNote(sourceLabels: List<String>) {
    val sources = sourceLabels.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "暂无已确认来源"
    Text(
        "数据边界：只展示服务器已确认入库、Health Connect/设备同步或你手动记录的数据；不补造缺失值。当前来源：$sources。",
        color = WeightMuted,
        style = MaterialTheme.typography.bodySmall,
        lineHeight = 18.sp,
        modifier = Modifier.testTag("weight.trustBoundary"),
    )
}

@Composable
private fun WeightTrendChart(dashboard: WeightDashboardPresentation) {
    val samples = dashboard.recentSamples
    val domain = remember(samples) {
        WeightDashboardPolicy.weightAxisDomain(samples.map { it.weightKg })
    }
    val ticks = remember(samples) {
        WeightDashboardPolicy.weightAxisTicks(samples.map { it.weightKg })
    }
    var selectedIndex by remember(samples) { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val plotLeftPx = with(density) { 20.dp.toPx() }
    val plotRightPx = with(density) { 14.dp.toPx() }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .testTag("weight.trend.chart"),
    ) {
        val viewportDp = (maxWidth - 30.dp).value.coerceAtLeast(1f)
        val contentWidth = WeightDashboardPolicy.chartContentWidthDp(
            dashboard.windowStart,
            dashboard.windowEnd,
            viewportDp,
        ).dp
        val scrollState = rememberScrollState()
        LaunchedEffect(scrollState.maxValue, samples) {
            if (scrollState.maxValue > 0) scrollState.scrollTo(scrollState.maxValue)
        }

        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(30.dp).height(216.dp).padding(bottom = 18.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                ticks.asReversed().forEach { tick ->
                    Text(tick.toString(), color = WeightMuted, fontSize = 10.sp)
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
            ) {
                val selected = selectedIndex?.let(samples::getOrNull)
                val chartDescription = buildString {
                    append("近三个月体重趋势，共${samples.size}个数据点。")
                    selected?.let {
                        append("当前选择${formatChineseDate(it.date)}，${formatOneDecimal(it.weightKg)}公斤，来源${it.source.label}。")
                    }
                }
                Canvas(
                    modifier = Modifier
                        .width(contentWidth)
                        .height(224.dp)
                        .semantics {
                            contentDescription = chartDescription
                            customActions = listOf(
                                CustomAccessibilityAction("上一个体重记录") {
                                    selectedIndex = ((selectedIndex ?: samples.lastIndex) - 1).coerceAtLeast(0)
                                    true
                                },
                                CustomAccessibilityAction("下一个体重记录") {
                                    selectedIndex = ((selectedIndex ?: (samples.lastIndex - 1)) + 1)
                                        .coerceAtMost(samples.lastIndex)
                                    true
                                },
                            )
                        }
                        .pointerInput(samples, dashboard.windowStart, dashboard.windowEnd) {
                            detectTapGestures(onLongPress = { position ->
                                selectedIndex = nearestSampleIndex(
                                    x = position.x,
                                    width = size.width.toFloat(),
                                    plotLeft = plotLeftPx,
                                    plotRight = plotRightPx,
                                    samples = samples,
                                    start = dashboard.windowStart,
                                    end = dashboard.windowEnd,
                                )
                            })
                        }
                        .pointerInput(samples, dashboard.windowStart, dashboard.windowEnd) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { position ->
                                    selectedIndex = nearestSampleIndex(
                                        position.x,
                                        size.width.toFloat(),
                                        plotLeftPx,
                                        plotRightPx,
                                        samples,
                                        dashboard.windowStart,
                                        dashboard.windowEnd,
                                    )
                                },
                                onDrag = { change, _ ->
                                    selectedIndex = nearestSampleIndex(
                                        change.position.x,
                                        size.width.toFloat(),
                                        plotLeftPx,
                                        plotRightPx,
                                        samples,
                                        dashboard.windowStart,
                                        dashboard.windowEnd,
                                    )
                                    change.consume()
                                },
                                onDragEnd = {},
                                onDragCancel = {},
                            )
                        },
                ) {
                    val plotTop = 18.dp.toPx()
                    val plotBottom = size.height - 30.dp.toPx()
                    val plotStart = plotLeftPx
                    val plotEnd = size.width - plotRightPx
                    val chartHeight = plotBottom - plotTop
                    val span = (domain.upperKg - domain.lowerKg).coerceAtLeast(1.0)
                    val totalDays = ChronoUnit.DAYS.between(
                        dashboard.windowStart,
                        dashboard.windowEnd,
                    ).coerceAtLeast(1L).toFloat()

                    ticks.forEach { tick ->
                        val y = plotBottom - ((tick - domain.lowerKg) / span).toFloat() * chartHeight
                        drawLine(
                            color = WeightMuted.copy(alpha = 0.16f),
                            start = Offset(plotStart, y),
                            end = Offset(plotEnd, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    val points = samples.map { sample ->
                        val day = ChronoUnit.DAYS.between(dashboard.windowStart, sample.date).toFloat()
                        val x = plotStart + (day / totalDays) * (plotEnd - plotStart)
                        val y = plotBottom - ((sample.weightKg - domain.lowerKg) / span).toFloat() * chartHeight
                        Offset(x, y)
                    }
                    if (points.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { point -> lineTo(point.x, point.y) }
                        }
                        drawPath(path, color = WeightBlue, style = Stroke(width = 2.5.dp.toPx()))
                        points.forEachIndexed { index, point ->
                            drawCircle(
                                color = if (selectedIndex == index) WeightMint else WeightBlue,
                                radius = if (selectedIndex == index) 6.dp.toPx() else 3.5.dp.toPx(),
                                center = point,
                            )
                        }
                    }

                    selectedIndex?.let { index ->
                        val sample = samples.getOrNull(index) ?: return@let
                        val point = points.getOrNull(index) ?: return@let
                        drawLine(
                            color = WeightBlue.copy(alpha = 0.55f),
                            start = Offset(point.x, plotTop),
                            end = Offset(point.x, plotBottom),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                        )
                        val tooltip = "${sample.date}   ${formatOneDecimal(sample.weightKg)}kg"
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = WeightInk.toArgb()
                            textSize = 12.sp.toPx()
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val textWidth = paint.measureText(tooltip)
                        val bubbleLeft = (point.x - textWidth / 2 - 10.dp.toPx())
                            .coerceIn(plotStart, plotEnd - textWidth - 20.dp.toPx())
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.94f),
                            topLeft = Offset(bubbleLeft, 0f),
                            size = androidx.compose.ui.geometry.Size(textWidth + 20.dp.toPx(), 30.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            tooltip,
                            bubbleLeft + 10.dp.toPx(),
                            20.dp.toPx(),
                            paint,
                        )
                    }

                    val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = WeightMuted.toArgb()
                        textSize = 9.sp.toPx()
                        textAlign = Paint.Align.CENTER
                    }
                    var axisDate = dashboard.windowStart
                    while (!axisDate.isAfter(dashboard.windowEnd)) {
                        val day = ChronoUnit.DAYS.between(dashboard.windowStart, axisDate).toFloat()
                        val x = plotStart + (day / totalDays) * (plotEnd - plotStart)
                        drawContext.canvas.nativeCanvas.drawText(
                            "${axisDate.monthValue}/${axisDate.dayOfMonth}",
                            x,
                            size.height - 8.dp.toPx(),
                            axisPaint,
                        )
                        axisDate = axisDate.plusDays(15)
                    }
                }
            }
        }
    }
}

private fun nearestSampleIndex(
    x: Float,
    width: Float,
    plotLeft: Float,
    plotRight: Float,
    samples: List<WeightTrendSample>,
    start: LocalDate,
    end: LocalDate,
): Int? {
    if (samples.isEmpty() || width <= plotLeft + plotRight) return null
    val plotWidth = width - plotLeft - plotRight
    val totalDays = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1L).toFloat()
    return samples.indices.minByOrNull { index ->
        val day = ChronoUnit.DAYS.between(start, samples[index].date).toFloat()
        val sampleX = plotLeft + (day / totalDays) * plotWidth
        abs(sampleX - x)
    }
}

@Composable
private fun WeightHeightSheet(
    initialHeight: Int?,
    saving: Boolean,
    onClose: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var input by remember(initialHeight) { mutableStateOf(initialHeight?.toString().orEmpty()) }
    var validation by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = saving) { }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
            .testTag("weight.height.sheet"),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(48.dp))
            Column(
                Modifier.weight(1f).semantics { heading() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("记录身高", color = WeightInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("单位：cm", color = WeightMuted, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(
                onClick = onClose,
                enabled = !saving,
                modifier = Modifier.size(48.dp).testTag("weight.height.close"),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭记录身高")
            }
        }

        Column(
            Modifier.fillMaxWidth().background(Color.White).padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    input.ifEmpty { "---" },
                    modifier = Modifier.testTag("weight.height.value"),
                    color = if (input.isEmpty()) Color(0xFFAAB5C1) else Color(0xFF15B88A),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text("cm", color = Color(0xFF15B88A), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.width(210.dp).height(2.dp).background(Color(0xFF15B88A)))
            Text(
                validation ?: "请输入 60–210 cm 之间的整数",
                modifier = Modifier.testTag("weight.height.validation"),
                color = if (validation == null) WeightMuted else WeightDanger,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (validation == null) FontWeight.Medium else FontWeight.Bold,
            )
        }

        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { digit ->
                        WeightHeightKey(
                            label = digit.toString(),
                            modifier = Modifier.weight(1f).testTag("weight.height.digit.$digit"),
                            enabled = !saving,
                        ) {
                            input = WeightDashboardPolicy.appendHeightDigit(input, digit)
                            validation = null
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightHeightKey(
                    label = "清除",
                    modifier = Modifier.weight(1f).testTag("weight.height.clear"),
                    enabled = !saving,
                ) {
                    input = ""
                    validation = null
                }
                WeightHeightKey(
                    label = "0",
                    modifier = Modifier.weight(1f).testTag("weight.height.digit.0"),
                    enabled = !saving,
                ) {
                    input = WeightDashboardPolicy.appendHeightDigit(input, 0)
                    validation = null
                }
                Surface(
                    onClick = {
                        input = WeightDashboardPolicy.deleteHeightDigit(input)
                        validation = null
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f).height(60.dp).testTag("weight.height.delete"),
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Backspace, contentDescription = "退格", tint = WeightInk)
                    }
                }
            }
            Button(
                onClick = {
                    val height = WeightDashboardPolicy.validatedHeight(input)
                    if (height == null) validation = WeightDashboardPolicy.HEIGHT_ERROR else onSave(height)
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(60.dp).testTag("weight.height.save"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15B88A)),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (saving) "保存中" else "保存", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WeightHeightKey(
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(60.dp),
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = WeightInk, fontSize = if (label.length == 1) 24.sp else 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WeightPickerSheet(
    initialWeight: Double?,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: (Double) -> Unit,
) {
    val initial = remember(initialWeight) { WeightDashboardPolicy.pickerSelection(initialWeight) }
    var integer by remember(initial) { mutableIntStateOf(initial.integer) }
    var tenth by remember(initial) { mutableIntStateOf(initial.tenth) }
    val selected = WeightDashboardPolicy.weightFromPicker(integer, tenth) ?: 65.0
    BackHandler(enabled = saving) { }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 18.dp)
            .testTag("weight.picker.sheet"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !saving,
                modifier = Modifier.heightIn(min = 48.dp).testTag("weight.picker.cancel"),
            ) { Text("取消") }
            Text(
                "选择体重",
                modifier = Modifier.weight(1f).semantics { heading() },
                color = WeightInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = { onSave(selected) },
                enabled = !saving,
                modifier = Modifier.heightIn(min = 48.dp).testTag("weight.picker.save"),
            ) { Text(if (saving) "保存中" else "保存", fontWeight = FontWeight.Bold) }
        }
        HorizontalDivider(color = WeightMuted.copy(alpha = 0.2f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeightNumberPicker(
                value = integer,
                range = WeightDashboardPolicy.weightIntegerRange,
                description = "体重整数，当前 $integer",
                modifier = Modifier.width(130.dp).testTag("weight.picker.integer"),
                onValueChanged = { integer = it },
            )
            Text(".", color = WeightInk, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            WeightNumberPicker(
                value = tenth,
                range = WeightDashboardPolicy.weightTenthRange,
                description = "体重小数，当前 $tenth",
                modifier = Modifier.width(88.dp).testTag("weight.picker.tenth"),
                onValueChanged = { tenth = it },
            )
            Text("公斤", color = WeightInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (saving) {
            CircularProgressIndicator(
                Modifier.align(Alignment.CenterHorizontally).size(24.dp),
                color = WeightBlue,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                "当前选择 ${formatOneDecimal(selected)} 公斤",
                modifier = Modifier.fillMaxWidth().testTag("weight.picker.value"),
                color = WeightMuted,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WeightNumberPicker(
    value: Int,
    range: IntRange,
    description: String,
    modifier: Modifier,
    onValueChanged: (Int) -> Unit,
) {
    AndroidView(
        factory = { context ->
            NumberPicker(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                minValue = range.first
                maxValue = range.last
                wrapSelectorWheel = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                this.value = value
                contentDescription = description
                setOnValueChangedListener { _, _, newValue -> onValueChanged(newValue) }
            }
        },
        update = { picker ->
            if (picker.value != value) picker.value = value
            picker.contentDescription = description
        },
        modifier = modifier.height(210.dp).semantics { contentDescription = description },
    )
}

@Composable
private fun WeightGuidanceSheet(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("weight.guidance.sheet"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "怎么看体重变化",
                modifier = Modifier.weight(1f).semantics { heading() },
                color = WeightInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp).testTag("weight.guidance.close"),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭体重变化说明")
            }
        }
        WeightGuidanceSection(
            title = "看趋势，不只看今天",
            body = "体重会受水分、饮食盐分、进食时间和排便等因素影响。一次上涨或下降，未必代表脂肪发生了相同幅度的变化。",
        )
        WeightGuidanceSection(
            title = "记录条件尽量一致",
            body = "建议在早晨起床、如厕后、进食前记录，连续观察一到两周的变化会更有参考价值。",
        )
        Surface(color = WeightMint.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp)) {
            Text(
                "今天的数字只是一个记录点，不是对你的评价。稳稳地记录下去，你会更了解自己的身体。",
                modifier = Modifier.padding(16.dp),
                color = Color(0xFF167D90),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WeightGuidanceSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = WeightInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(body, color = WeightBody, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
    }
}

private fun formatOneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun formatChineseDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE))
