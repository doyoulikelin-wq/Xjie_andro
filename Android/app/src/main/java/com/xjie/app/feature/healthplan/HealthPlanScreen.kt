package com.xjie.app.feature.healthplan

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.R
import com.xjie.app.core.model.HealthPlan
import com.xjie.app.core.model.HealthPlanDetail
import com.xjie.app.core.model.PlanTask
import com.xjie.app.core.model.TubeDay
import com.xjie.app.core.model.TubeTaskProgress
import com.xjie.app.core.model.TubeWeek
import com.xjie.app.core.ui.components.EmptyState
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle
import kotlinx.coroutines.delay
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthPlanScreen(
    vm: HealthPlanViewModel = hiltViewModel(),
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
                title = { Text("健康计划") },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.week?.let { week ->
                    HealthTreeWeekCard(
                        week = week,
                        currentWeek = state.weekStart == java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY),
                        completingType = state.completingType,
                        recentEffect = state.lastCompletedType,
                        onPrevious = vm::previousWeek,
                        onNext = vm::nextWeek,
                        onThisWeek = vm::backToThisWeek,
                        onComplete = vm::completeToday,
                        onEffectFinished = vm::clearCompletionEffect,
                    )
                }
                PlanOverviewCard(
                    plans = state.plans,
                    selectedId = state.selectedPlan?.id,
                    onSelect = vm::selectPlan,
                )
                PlanDetailCard(plan = state.selectedPlan)
                if (state.plans.isEmpty() && !state.loading) {
                    EmptyState(
                        title = "暂无健康计划",
                        description = "在助手小捷生成饮食、运动、用药方案后，点击「保存为健康计划」。",
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

private val healthTreeTaskTypes = listOf("exercise", "medication", "diet")

@Composable
private fun HealthTreeWeekCard(
    week: TubeWeek,
    currentWeek: Boolean,
    completingType: String?,
    recentEffect: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onComplete: (String) -> Unit,
    onEffectFinished: () -> Unit,
) {
    var dragX by remember { mutableStateOf(0f) }
    var selectedDate by remember(week.week_start) { mutableStateOf<String?>(null) }
    val today = week.days.firstOrNull { it.is_today }
    val activeDay = selectedDate
        ?.let { date -> week.days.firstOrNull { it.date == date } }
        ?: today
        ?: week.days.firstOrNull()
    val activeRatio = activeDay?.completion_ratio ?: 0.0

    Column(
        Modifier
            .cardStyle()
            .pointerInput(week.week_start) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onHorizontalDrag = { _, dragAmount -> dragX += dragAmount },
                    onDragEnd = {
                        if (dragX > 80f) onPrevious()
                        if (dragX < -80f) onNext()
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "健康树计划养成",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${week.week_start} - ${week.week_end}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "${(activeRatio * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Surface(
                onClick = onPrevious,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = CircleShape,
                modifier = Modifier
                    .size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChevronLeft, "上周", modifier = Modifier.size(18.dp))
                }
            }
            if (!currentWeek) {
                TextButton(onClick = onThisWeek) { Text("本周") }
            }
            Surface(
                onClick = onNext,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = CircleShape,
                modifier = Modifier
                    .size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChevronRight, "下周", modifier = Modifier.size(18.dp))
                }
            }
        }

        HealthTreeStage(
            stage = healthTreeStage(activeRatio),
            completionRatio = activeRatio,
            day = activeDay,
            hasOmicsData = week.has_omics_data,
            completingType = completingType,
            recentEffect = recentEffect,
            onComplete = onComplete,
            onEffectFinished = onEffectFinished,
            modifier = Modifier.fillMaxWidth(),
        )

        HealthTreeWeekStrip(
            days = week.days,
            selectedDate = activeDay?.date,
            onSelect = { selectedDate = it.date },
        )
    }
}

@Composable
private fun HealthTreeStage(
    stage: Int,
    completionRatio: Double,
    day: TubeDay?,
    hasOmicsData: Boolean,
    completingType: String?,
    recentEffect: String?,
    onComplete: (String) -> Unit,
    onEffectFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse by animateFloatAsState(
        targetValue = if (recentEffect != null) 1.06f else 1f,
        animationSpec = tween(durationMillis = 450),
        label = "treePulse",
    )

    Box(
        modifier
            .height(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF6FBF8),
                        Color(0xFFE8F5EE),
                        Color(0xFFF8FBFF),
                    )
                )
            )
            .padding(12.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HealthTreeActionRow(
                day = day,
                completingType = completingType,
                onComplete = onComplete,
            )
            Box(
                Modifier.height(166.dp).fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (hasOmicsData) {
                    Image(
                        painter = painterResource(R.drawable.healthtree_pot_rich_soil),
                        contentDescription = null,
                        modifier = Modifier.size(width = 94.dp, height = 49.dp).graphicsLayer { alpha = 0.92f },
                        contentScale = ContentScale.Fit,
                    )
                }
                Image(
                    painter = painterResource(healthTreeStageRes(stage)),
                    contentDescription = null,
                    modifier = Modifier.size(156.dp).graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    },
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                healthTreeStageLabel(stage),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        recentEffect?.let {
            HealthTreeEffectOverlay(type = it, onFinished = onEffectFinished)
        }
    }
}

@Composable
private fun BoxScope.HealthTreeEffectOverlay(
    type: String,
    onFinished: () -> Unit,
) {
    var running by remember(type) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (running) 1f else 0f,
        animationSpec = tween(durationMillis = 950),
        label = "treeEffect",
    )

    LaunchedEffect(type) {
        running = true
        delay(1050)
        onFinished()
    }

    Image(
        painter = painterResource(healthTreeActionRes(type)),
        contentDescription = null,
        modifier = Modifier
            .size(48.dp)
            .align(Alignment.TopCenter)
            .offset(y = (8f + 60f * progress).dp)
            .graphicsLayer {
                alpha = 1f - progress
                scaleX = 0.82f + progress * 0.26f
                scaleY = 0.82f + progress * 0.26f
            },
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun HealthTreeActionRow(
    day: TubeDay?,
    completingType: String?,
    onComplete: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        healthTreeTaskTypes.forEach { type ->
            val task = day?.tasks?.firstOrNull { it.task_type == type }
            HealthTreeActionChip(
                type = type,
                task = task,
                isActiveDay = day?.is_today == true,
                isCompleting = completingType == type,
                isBusy = completingType != null,
                onComplete = onComplete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HealthTreeActionChip(
    type: String,
    task: TubeTaskProgress?,
    isActiveDay: Boolean,
    isCompleting: Boolean,
    isBusy: Boolean,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = (task?.ratio ?: 0.0) >= 1.0
    Surface(
        onClick = { onComplete(type) },
        enabled = isActiveDay && !done && !isBusy,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            typeColor(type).copy(alpha = if (isActiveDay) 0.24f else 0.1f),
        ),
        modifier = modifier.graphicsLayer { alpha = if (done) 0.56f else if (isActiveDay) 1f else 0.68f },
    ) {
        Row(
            Modifier.height(48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(typeColor(type).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (isCompleting) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 1.7.dp,
                        color = typeColor(type),
                    )
                } else {
                    Image(
                        painter = painterResource(healthTreeActionRes(type)),
                        contentDescription = null,
                        modifier = Modifier.size(29.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    careLabel(type),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    healthTreeProgressText(task),
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColor(type),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HealthTreeWeekStrip(
    days: List<TubeDay>,
    selectedDate: String?,
    onSelect: (TubeDay) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { day ->
            HealthTreeDayMarker(
                day = day,
                selected = day.date == selectedDate,
                onSelect = { onSelect(day) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HealthTreeDayMarker(
    day: TubeDay,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { onSelect() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            painter = painterResource(healthTreeStageRes(if (day.is_future) 1 else healthTreeStage(day.completion_ratio))),
            contentDescription = null,
            modifier = Modifier.size(32.dp).graphicsLayer { alpha = if (day.is_future) 0.36f else 1f },
            contentScale = ContentScale.Fit,
        )
        Box(
            Modifier
                .size(width = 26.dp, height = 24.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                weekdayName(day.weekday),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            if (day.is_today) "今天" else "${(day.completion_ratio * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun TubeWeekCard(
    week: TubeWeek,
    currentWeek: Boolean,
    completingType: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onComplete: (String) -> Unit,
) {
    var dragX by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .cardStyle()
            .pointerInput(week.week_start) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onHorizontalDrag = { _, dragAmount -> dragX += dragAmount },
                    onDragEnd = {
                        if (dragX > 80f) onPrevious()
                        if (dragX < -80f) onNext()
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("试管式计划执行", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text("${week.week_start} - ${week.week_end}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (!currentWeek) {
                TextButton(onClick = onThisWeek) { Text("本周") }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape),
            ) { Icon(Icons.Filled.ChevronLeft, "上周") }

            Row(
                Modifier
                    .weight(1f)
                    .height(252.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                week.days.forEach { day ->
                    TubeDayColumn(
                        day = day,
                        completingType = completingType,
                        onComplete = onComplete,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape),
            ) { Icon(Icons.Filled.ChevronRight, "下周") }
        }

        Text(
            "点击今天上方图标完成任务，试管液位会按运动、用药、饮食分层上升。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TubeDayColumn(
    day: TubeDay,
    completingType: String?,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (day.is_today) {
            TodayTaskButtons(
                tasks = day.tasks,
                completingType = completingType,
                onComplete = onComplete,
                modifier = Modifier.height(94.dp),
            )
        } else {
            Spacer(Modifier.height(94.dp))
        }

        TestTube(
            tasks = day.tasks,
            isToday = day.is_today,
            isFuture = day.is_future,
            modifier = Modifier
                .width(34.dp)
                .height(112.dp),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (day.is_today) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                weekdayName(day.weekday),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (day.is_today) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (day.is_today) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("今天", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold)
        } else {
            Spacer(Modifier.height(38.dp))
        }
    }
}

@Composable
private fun TodayTaskButtons(
    tasks: List<TubeTaskProgress>,
    completingType: String?,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf("exercise", "medication", "diet").forEach { type ->
            val task = tasks.firstOrNull { it.task_type == type } ?: return@forEach
            Surface(
                onClick = { onComplete(type) },
                enabled = task.ratio < 1.0 && completingType == null,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, typeColor(type).copy(alpha = 0.35f)),
                modifier = Modifier.width(62.dp).height(26.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (completingType == type) {
                        CircularProgressIndicator(
                            Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = typeColor(type),
                        )
                    } else {
                        Icon(typeIcon(type), null, modifier = Modifier.size(14.dp), tint = typeColor(type))
                    }
                    Text(
                        progressText(task),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = typeColor(type),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TestTube(
    tasks: List<TubeTaskProgress>,
    isToday: Boolean,
    isFuture: Boolean,
    modifier: Modifier = Modifier,
) {
    val strokeColor = if (isToday) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
    val tickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    Canvas(modifier) {
        val stroke = 1.6.dp.toPx()
        val rimH = 10.dp.toPx()
        val width = size.width
        val height = size.height
        val bodyTop = rimH
        val bodyH = height - rimH - stroke
        val corner = width / 2f

        drawRoundRect(
            color = Color.White.copy(alpha = if (isFuture) 0.45f else 0.86f),
            topLeft = Offset(stroke, bodyTop),
            size = Size(width - stroke * 2, bodyH),
            cornerRadius = CornerRadius(corner, corner),
        )

        if (!isFuture) {
            var y = height - stroke * 2
            val innerH = bodyH - stroke * 3
            listOf("diet", "medication", "exercise").forEach { type ->
                val ratio = tasks.firstOrNull { it.task_type == type }?.ratio?.coerceIn(0.0, 1.0) ?: 0.0
                val layerH = innerH * ratio.toFloat() / 3f
                y -= layerH
                drawRoundRect(
                    color = typeColor(type),
                    topLeft = Offset(stroke + 4.dp.toPx(), y),
                    size = Size(width - (stroke + 4.dp.toPx()) * 2, max(layerH, 0f)),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                )
            }
        }

        repeat(5) { idx ->
            val y = bodyTop + 24.dp.toPx() + idx * 12.dp.toPx()
            drawLine(
                color = tickColor,
                start = Offset(stroke + 6.dp.toPx(), y),
                end = Offset(stroke + 14.dp.toPx(), y),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(stroke, bodyTop),
            size = Size(width - stroke * 2, bodyH),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = if (isToday) 2.2.dp.toPx() else 1.5.dp.toPx()),
        )
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            size = Size(width - 2.dp.toPx(), rimH),
            cornerRadius = CornerRadius(rimH, rimH),
            style = Stroke(width = if (isToday) 2.2.dp.toPx() else 1.5.dp.toPx()),
        )
    }
}

@Composable
private fun PlanOverviewCard(
    plans: List<HealthPlan>,
    selectedId: String?,
    onSelect: (HealthPlan) -> Unit,
) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(Icons.Filled.CalendarMonth, "健康计划")
            Spacer(Modifier.weight(1f))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("${plans.size} 个",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            plans.forEach { plan ->
                Column(
                    Modifier
                        .width(210.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = if (selectedId == plan.id) 0.12f else 0.05f,
                            )
                        )
                        .clickable { onSelect(plan) }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(plan.title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text("${short(plan.start_date)} - ${short(plan.end_date)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(
                        progress = {
                            if (plan.task_count <= 0) 0f
                            else (plan.completed_task_count.toFloat() / plan.task_count.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanDetailCard(plan: HealthPlanDetail?) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(Icons.Filled.Assignment, "计划详情")
        if (plan == null) {
            Text("保存计划后，这里会展示目标、周期和每日任务。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Text(plan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        plan.goal?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill("${plan.task_count}", "任务", Modifier.weight(1f))
            StatPill("${plan.completed_task_count}", "完成", Modifier.weight(1f))
            StatPill(short(plan.start_date), "开始", Modifier.weight(1f))
        }
        HorizontalDivider()
        plan.tasks.take(8).forEach { task ->
            TaskRow(task)
        }
    }
}

@Composable
private fun TaskRow(task: PlanTask) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(typeIcon(task.task_type), null, tint = typeColor(task.task_type), modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("${short(task.date)} · ${task.completed_count}/${max(task.target_count, 1)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (task.status == "completed") {
            Icon(Icons.Filled.CheckCircle, null, tint = XjiePalette.Success)
        }
    }
}

@Composable
private fun StatPill(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun progressText(task: TubeTaskProgress): String {
    val completed = task.completed_value
    val target = task.target_value
    if (task.unit == "kcal" && completed != null && target != null && completed > 0) {
        return "${completed.toInt()}/${target.toInt()}"
    }
    return "${task.completed}/${max(task.target, 1)}"
}

private fun weekdayName(weekday: Int): String = when (weekday) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}

private fun short(day: String): String = day.takeLast(5)

private fun typeIcon(type: String): ImageVector = when (type) {
    "exercise" -> Icons.Filled.DirectionsRun
    "medication" -> Icons.Filled.MedicalServices
    "diet" -> Icons.Filled.LocalDining
    else -> Icons.Filled.Science
}

private fun typeColor(type: String): Color = when (type) {
    "exercise" -> Color(0xFF75C043)
    "medication" -> Color(0xFF2F80ED)
    "diet" -> Color(0xFFFF8A1F)
    else -> XjiePalette.Primary
}

private fun healthTreeStage(ratio: Double): Int {
    val value = ratio.coerceIn(0.0, 1.0)
    return when {
        value < 0.12 -> 1
        value < 0.28 -> 2
        value < 0.48 -> 3
        value < 0.72 -> 4
        value < 0.92 -> 5
        else -> 6
    }
}

@DrawableRes
private fun healthTreeStageRes(stage: Int): Int = when (stage) {
    1 -> R.drawable.healthtree_tree_01_seed
    2 -> R.drawable.healthtree_tree_02_sprout
    3 -> R.drawable.healthtree_tree_03_seedling
    4 -> R.drawable.healthtree_tree_04_young_tree
    5 -> R.drawable.healthtree_tree_05_flowering
    else -> R.drawable.healthtree_tree_06_fruiting
}

private fun healthTreeStageLabel(stage: Int): String = when (stage) {
    1 -> "种子期"
    2 -> "发芽期"
    3 -> "幼苗期"
    4 -> "成长中"
    5 -> "开花期"
    else -> "结果期"
}

@DrawableRes
private fun healthTreeIconRes(type: String): Int = when (type) {
    "exercise" -> R.drawable.healthtree_icon_exercise_sun
    "diet" -> R.drawable.healthtree_icon_diet_water
    "medication" -> R.drawable.healthtree_icon_medication_dew
    else -> R.drawable.healthtree_icon_multiomics_precision
}

@DrawableRes
private fun healthTreeActionRes(type: String): Int = when (type) {
    "exercise" -> R.drawable.healthtree_env_sun
    "medication" -> R.drawable.healthtree_env_medkit
    "diet" -> R.drawable.healthtree_env_watercan
    else -> healthTreeIconRes(type)
}

private fun careLabel(type: String): String = when (type) {
    "exercise" -> "运动"
    "diet" -> "饮食"
    "medication" -> "用药"
    else -> "照护"
}

private fun healthTreeProgressText(task: TubeTaskProgress?): String {
    if (task == null) return "0/1"
    val completed = task.completed_value
    val target = task.target_value
    if (task.unit == "kcal" && completed != null && target != null && completed > 0) {
        return "${completed.toInt()}/${target.toInt()} kcal"
    }
    return "${task.completed}/${max(task.target, 1)}"
}
