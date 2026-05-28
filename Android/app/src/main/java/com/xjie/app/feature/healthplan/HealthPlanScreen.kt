package com.xjie.app.feature.healthplan

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.HealthPlan
import com.xjie.app.core.model.HealthPlanDetail
import com.xjie.app.core.model.PlanTask
import com.xjie.app.core.model.TubeDay
import com.xjie.app.core.model.TubeTaskProgress
import com.xjie.app.core.model.TubeWeek
import com.xjie.app.core.ui.components.EmptyState
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle
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
                    TubeWeekCard(
                        week = week,
                        currentWeek = state.weekStart == java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY),
                        completingType = state.completingType,
                        onPrevious = vm::previousWeek,
                        onNext = vm::nextWeek,
                        onThisWeek = vm::backToThisWeek,
                        onComplete = vm::completeToday,
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
