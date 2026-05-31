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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.xjie.app.core.model.HealthPlanQuestionnaireRequest
import com.xjie.app.core.model.PlanTask
import com.xjie.app.core.model.TubeDay
import com.xjie.app.core.model.TubeTaskProgress
import com.xjie.app.core.model.TubeWeek
import com.xjie.app.core.ui.components.EmptyState
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun HealthPlanScreen(
    vm: HealthPlanViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showPlanQuestionnaire by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "健康计划",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            )
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
                    onGeneratePlan = { showPlanQuestionnaire = true },
                )
            }
            PlanOverviewCard(
                plans = state.plans,
                selectedId = state.selectedPlan?.id,
                onSelect = vm::selectPlan,
                onGeneratePlan = { showPlanQuestionnaire = true },
            )
            PlanDetailCard(plan = state.selectedPlan)
            if (state.plans.isEmpty() && !state.loading) {
                EmptyState(
                    title = "暂无健康计划",
                    description = "在助手小捷生成饮食、运动、用药方案后，点击「保存为健康计划」。",
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        if (state.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
    if (showPlanQuestionnaire) {
        HealthPlanQuestionnaireDialog(
            creating = state.creatingPlan,
            onCreate = { request ->
                vm.createFromQuestionnaire(request)
                showPlanQuestionnaire = false
            },
            onDismiss = { showPlanQuestionnaire = false },
        )
    }
}

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
    onGeneratePlan: () -> Unit,
) {
    var dragX by remember { mutableStateOf(0f) }
    var selectedDate by remember(week.week_start) { mutableStateOf<String?>(null) }
    var showMedicationNeed by remember(week.week_start) { mutableStateOf(week.has_medication_need) }
    var showPlanDialog by remember { mutableStateOf(false) }
    var showGrowthPath by remember { mutableStateOf(false) }
    val today = week.days.firstOrNull { it.is_today }
    val activeDay = selectedDate
        ?.let { date -> week.days.firstOrNull { it.date == date } }
        ?: today
        ?: week.days.firstOrNull()
    val activeTasks = activeDay?.tasks
        ?.filter { showMedicationNeed || it.task_type != "medication" }
        .orEmpty()
    val activeDateLabel = activeDay?.let {
        "${planRelativeLabel(it, week.today)} · ${it.date}"
    } ?: "未选择日期"
    val isActiveDayToday = activeDay?.is_today == true
    val growthProgress = remember(week) { growthTreeProgress(week) }

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
                    "${growthProgress.exp}/${GrowthTreeProgress.MaxExp} EXP",
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

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { showPlanDialog = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("我的计划")
                }
                Button(
                    onClick = onGeneratePlan,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("生成计划")
                }
                OutlinedButton(
                    onClick = { showGrowthPath = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("成长路径", maxLines = 1)
                }
            }
        }

        HealthTreeStage(
            progress = growthProgress,
            today = today,
            isActiveDayToday = isActiveDayToday,
            recentEffect = recentEffect,
            onBackToToday = {
                selectedDate = null
                if (!currentWeek) onThisWeek()
            },
            onEffectFinished = onEffectFinished,
            modifier = Modifier.fillMaxWidth(),
        )

        GrowthPlanDaySelector(
            choices = growthPlanChoices(week),
            selectedDate = activeDay?.date,
            onSelect = { selectedDate = it.date },
        )

        HealthTreePlanPreview(
            title = "${planRelativeLabel(activeDay, week.today)}计划",
            dateLabel = activeDateLabel,
            day = activeDay,
            showMedicationNeed = showMedicationNeed,
            onMedicationNeedChange = { showMedicationNeed = it },
            completingType = completingType,
            onComplete = onComplete,
            onOpenDetail = { showPlanDialog = true },
            onGeneratePlan = onGeneratePlan,
        )
    }

    if (showPlanDialog) {
        HealthTreePlanDialog(
            day = activeDay,
            tasks = activeTasks,
            showMedicationNeed = showMedicationNeed,
            onMedicationNeedChange = { showMedicationNeed = it },
            onGeneratePlan = {
                showPlanDialog = false
                onGeneratePlan()
            },
            onDismiss = { showPlanDialog = false },
        )
    }
    if (showGrowthPath) {
        HealthTreeGrowthPathDialog(
            progress = growthProgress,
            onDismiss = { showGrowthPath = false },
        )
    }
}

@Composable
private fun HealthTreeStage(
    progress: GrowthTreeProgress,
    today: TubeDay?,
    isActiveDayToday: Boolean,
    recentEffect: String?,
    onBackToToday: () -> Unit,
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
            .height(350.dp)
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
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier
                    .height(192.dp)
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                GrowthTreeImage(
                    stage = progress.stage,
                    modifier = Modifier
                        .size(190.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        },
                )
            }
            Text(
                "今日",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                today?.date ?: "未同步日期",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                growthTreeStageLabel(progress.stage),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Lv.${progress.stage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (progress.isMaxStage) "已进入结果期" else "距下一阶段 ${progress.expToNextStage} EXP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress.stageProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "健康问答、添加病例/健康数据、持续佩戴血糖仪都会累积成长经验。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        recentEffect?.let {
            HealthTreeEffectOverlay(type = it, onFinished = onEffectFinished)
        }
        if (!isActiveDayToday) {
            FilledTonalButton(
                onClick = onBackToToday,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("回到今天", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GrowthTreeImage(
    stage: Int,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(growthTreePrimaryRes(stage)),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
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

    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (8f + 64f * progress).dp)
            .graphicsLayer {
                alpha = 1f - progress
                scaleX = 0.82f + progress * 0.26f
                scaleY = 0.82f + progress * 0.26f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(healthTreeActionRes(type)),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            contentScale = ContentScale.Fit,
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                "+10 EXP",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GrowthPlanDaySelector(
    choices: List<GrowthPlanDayChoice>,
    selectedDate: String?,
    onSelect: (TubeDay) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        choices.forEach { choice ->
            val selected = choice.day?.date == selectedDate
            Surface(
                onClick = { choice.day?.let(onSelect) },
                enabled = choice.day != null,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        choice.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            selected -> MaterialTheme.colorScheme.onPrimary
                            choice.day == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        choice.day?.date?.let(::shortDate) ?: "--",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            selected -> MaterialTheme.colorScheme.onPrimary
                            choice.day == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthTreePlanPreview(
    title: String,
    dateLabel: String,
    day: TubeDay?,
    showMedicationNeed: Boolean,
    onMedicationNeedChange: (Boolean) -> Unit,
    completingType: String?,
    onComplete: (String) -> Unit,
    onOpenDetail: () -> Unit,
    onGeneratePlan: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onOpenDetail, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Text("详情", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        MedicationNeedToggle(
            checked = showMedicationNeed,
            onCheckedChange = onMedicationNeedChange,
            modifier = Modifier.fillMaxWidth(),
            description = "勾选后显示用药计划",
        )
        HealthTreeActionRow(
            day = day,
            showMedicationNeed = showMedicationNeed,
            completingType = completingType,
            onComplete = onComplete,
        )
        if (day?.is_today != true) {
            Text(
                "仅今日计划支持点击完成；前后日期用于查看安排。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (day?.tasks?.isEmpty() != false) {
            Button(onClick = onGeneratePlan) {
                Text("生成计划")
            }
        }
    }
}

@Composable
private fun HealthTreeGrowthPathDialog(
    progress: GrowthTreeProgress,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("成长路径", fontWeight = FontWeight.SemiBold)
                Text(
                    "当前 ${growthTreeStageLabel(progress.stage)} · ${progress.exp}/${GrowthTreeProgress.MaxExp} EXP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                growthStageMilestones.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (item.stage == progress.stage) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(growthTreeFrameRes(item.stage).first()),
                            contentDescription = null,
                            modifier = Modifier.size(46.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                item.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${item.requiredExp}+",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.stage <= progress.stage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    "经验来源设计：健康相关问答 +5 EXP；添加病例或健康数据 +15 EXP；持续佩戴血糖仪每日 +30 EXP；完成今日计划任务 +10 EXP。真实持久经验值接口接入后沿用此界面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

private data class GrowthTreeProgress(
    val exp: Int,
) {
    val stage: Int = (exp / 100 + 1).coerceIn(1, 5)
    val stageProgress: Float = if (stage >= 5) 1f else ((exp % 100).toFloat() / 100f).coerceIn(0f, 1f)
    val expToNextStage: Int = if (stage >= 5) 0 else (stage * 100 - exp).coerceAtLeast(0)
    val isMaxStage: Boolean = stage >= 5

    companion object {
        const val MaxExp = 500
    }
}

private data class GrowthPlanDayChoice(
    val offset: Int,
    val label: String,
    val day: TubeDay?,
)

private data class GrowthStageMilestone(
    val stage: Int,
    val title: String,
    val description: String,
    val requiredExp: Int,
)

private val growthStageMilestones = listOf(
    GrowthStageMilestone(1, "种子期", "开始记录健康目标，完成第一次互动。", 0),
    GrowthStageMilestone(2, "发芽期", "持续完成计划，小苗从土壤里冒出。", 100),
    GrowthStageMilestone(3, "树苗期", "病例、健康数据和问答逐步形成个人上下文。", 200),
    GrowthStageMilestone(4, "成长期", "连续数据让计划更稳定，树冠开始展开。", 300),
    GrowthStageMilestone(5, "结果期", "长期坚持后结出果实，记录阶段性成果。", 400),
)

private fun growthTreeProgress(week: TubeWeek): GrowthTreeProgress {
    val tasks = week.days.flatMap { it.tasks }
    if (tasks.isEmpty()) return GrowthTreeProgress(0)
    val planExp = (tasks.size * 5).coerceAtMost(120)
    val ratioExp = ((tasks.sumOf { it.ratio.coerceIn(0.0, 1.0) } / tasks.size.coerceAtLeast(1)) * 260).toInt()
    val completedUnitExp = tasks.sumOf { task ->
        task.completed.coerceAtMost(task.target.coerceAtLeast(1)) * 8
    }.coerceAtMost(120)
    return GrowthTreeProgress((planExp + ratioExp + completedUnitExp).coerceIn(0, GrowthTreeProgress.MaxExp - 1))
}

private fun growthPlanChoices(week: TubeWeek): List<GrowthPlanDayChoice> {
    val offsets = listOf(-2 to "前天", -1 to "昨天", 0 to "今日", 1 to "明天", 2 to "后天")
    val today = runCatching { java.time.LocalDate.parse(week.today) }.getOrNull()
    return offsets.map { (offset, label) ->
        val date = today?.plusDays(offset.toLong())?.toString()
        GrowthPlanDayChoice(
            offset = offset,
            label = label,
            day = date?.let { key -> week.days.firstOrNull { it.date == key } }
                ?: if (offset == 0) week.days.firstOrNull { it.is_today } else null,
        )
    }
}

private fun planRelativeLabel(day: TubeDay?, today: String): String {
    if (day == null || day.is_today) return "今日"
    val todayDate = runCatching { java.time.LocalDate.parse(today) }.getOrNull()
    val dayDate = runCatching { java.time.LocalDate.parse(day.date) }.getOrNull()
    val diff = if (todayDate != null && dayDate != null) java.time.temporal.ChronoUnit.DAYS.between(todayDate, dayDate).toInt() else 0
    return when (diff) {
        -2 -> "前天"
        -1 -> "昨天"
        1 -> "明天"
        2 -> "后天"
        else -> weekdayName(day.weekday)
    }
}

private fun shortDate(date: String): String = date.takeLast(5)

private fun growthTreeFrameRes(stage: Int): List<Int> = when (stage) {
    1 -> listOf(R.drawable.growth_tree_seed_0, R.drawable.growth_tree_seed_1, R.drawable.growth_tree_seed_2, R.drawable.growth_tree_seed_3)
    2 -> listOf(R.drawable.growth_tree_sprout_0, R.drawable.growth_tree_sprout_1, R.drawable.growth_tree_sprout_2, R.drawable.growth_tree_sprout_3)
    3 -> listOf(R.drawable.growth_tree_sapling_0, R.drawable.growth_tree_sapling_1, R.drawable.growth_tree_sapling_2, R.drawable.growth_tree_sapling_3, R.drawable.growth_tree_sapling_4)
    4 -> listOf(R.drawable.growth_tree_tree_0, R.drawable.growth_tree_tree_1, R.drawable.growth_tree_tree_2, R.drawable.growth_tree_tree_3, R.drawable.growth_tree_tree_4, R.drawable.growth_tree_tree_5)
    else -> listOf(R.drawable.growth_tree_fruit_0, R.drawable.growth_tree_fruit_1, R.drawable.growth_tree_fruit_2, R.drawable.growth_tree_fruit_3)
}

@DrawableRes
private fun growthTreePrimaryRes(stage: Int): Int = growthTreeFrameRes(stage).first()

private fun growthTreeStageLabel(stage: Int): String = when (stage) {
    1 -> "种子期"
    2 -> "发芽期"
    3 -> "树苗期"
    4 -> "成长期"
    else -> "结果期"
}

@Composable
private fun HealthTreeActionRow(
    day: TubeDay?,
    showMedicationNeed: Boolean,
    completingType: String?,
    onComplete: (String) -> Unit,
) {
    val tasks = day?.tasks
        ?.filter { showMedicationNeed || it.task_type != "medication" }
        .orEmpty()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tasks.forEach { task ->
            val type = task.task_type
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
        if (tasks.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "今天暂无执行任务",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
            Modifier.height(58.dp).padding(horizontal = 8.dp),
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
                task?.title?.takeIf { it.isNotBlank() }?.let { title ->
                    Text(
                        title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    task?.summary ?: healthTreeProgressText(task),
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
private fun MedicationNeedToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val accent = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
            Spacer(Modifier.width(4.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "有用药需求",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthTreePlanDialog(
    day: TubeDay?,
    tasks: List<TubeTaskProgress>,
    showMedicationNeed: Boolean,
    onMedicationNeedChange: (Boolean) -> Unit,
    onGeneratePlan: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("我的计划", fontWeight = FontWeight.SemiBold)
                Text(
                    day?.date ?: "未选择日期",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MedicationNeedToggle(
                    checked = showMedicationNeed,
                    onCheckedChange = onMedicationNeedChange,
                    modifier = Modifier.fillMaxWidth(),
                    description = "勾选后才显示用药计划；没有医生或本人确认时不默认展示。",
                )

                if (tasks.isEmpty()) {
                    Text(
                        "当前日期暂无可执行计划。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    tasks.forEach { task ->
                        HealthTreePlanTaskRow(task)
                    }
                }

                if (showMedicationNeed && day?.tasks?.none { it.task_type == "medication" } != false) {
                    Text(
                        "当前计划没有用药任务；如需要，请在生成计划时明确说明用药需求，或先完善用药记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = XjiePalette.Warning,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onGeneratePlan) { Text("生成计划") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun HealthPlanQuestionnaireDialog(
    creating: Boolean,
    onCreate: (HealthPlanQuestionnaireRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val targets = listOf("控糖稳定", "减重控脂", "提升体能", "改善睡眠", "饮食规律", "综合健康")
    val durations = listOf(7 to "7 天", 14 to "14 天", 30 to "30 天", 60 to "60 天")
    val frequencies = listOf(
        "daily" to "每天",
        "three_per_week" to "每周 3 次",
        "five_per_week" to "每周 5 次",
        "weekdays" to "工作日",
    )
    val contents = listOf(
        "fitness" to "健身",
        "diet_control" to "饮食控制",
        "sleep" to "睡眠",
        "hydration" to "饮水",
        "medication" to "用药",
    )
    var target by rememberSaveable { mutableStateOf("控糖稳定") }
    var durationDays by rememberSaveable { mutableStateOf(7) }
    var frequency by rememberSaveable { mutableStateOf("daily") }
    var selectedContents by rememberSaveable { mutableStateOf(listOf("fitness", "diet_control")) }
    var medicationNeeded by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成健康计划", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                QuestionnaireSection("目标") {
                    ChoiceRows(
                        options = targets.map { it to it },
                        selectedKeys = setOf(target),
                        onPick = { target = it },
                    )
                }
                QuestionnaireSection("时间") {
                    ChoiceRows(
                        options = durations.map { it.first.toString() to it.second },
                        selectedKeys = setOf(durationDays.toString()),
                        onPick = { durationDays = it.toIntOrNull() ?: 7 },
                    )
                }
                QuestionnaireSection("频次") {
                    ChoiceRows(
                        options = frequencies,
                        selectedKeys = setOf(frequency),
                        onPick = { frequency = it },
                    )
                }
                QuestionnaireSection("涉及内容") {
                    ChoiceRows(
                        options = contents,
                        selectedKeys = selectedContents.toSet(),
                        onPick = { key ->
                            selectedContents = if (selectedContents.contains(key)) {
                                selectedContents.filterNot { it == key }
                            } else {
                                selectedContents + key
                            }
                            if (key == "medication" && !selectedContents.contains("medication")) {
                                medicationNeeded = false
                            }
                        },
                    )
                }
                if (selectedContents.contains("medication")) {
                    Surface(
                        onClick = { medicationNeeded = !medicationNeeded },
                        shape = RoundedCornerShape(12.dp),
                        color = XjiePalette.Warning.copy(alpha = 0.08f),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Checkbox(
                                checked = medicationNeeded,
                                onCheckedChange = { medicationNeeded = it },
                            )
                            Column(Modifier.weight(1f)) {
                                Text("确认有用药需求", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "勾选后才会生成用药任务；未确认时只保存问卷选择，不自动安排用药。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("补充说明") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !creating,
                onClick = {
                    onCreate(
                        HealthPlanQuestionnaireRequest(
                            target = target,
                            duration_days = durationDays,
                            frequency = frequency,
                            contents = selectedContents,
                            medication_needed = selectedContents.contains("medication") && medicationNeeded,
                            notes = notes.trim().ifBlank { null },
                            title = "${target}健康计划",
                        )
                    )
                },
            ) {
                if (creating) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun QuestionnaireSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun ChoiceRows(
    options: List<Pair<String, String>>,
    selectedKeys: Set<String>,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedKeys.contains(key),
                        onClick = { onPick(key) },
                        label = { Text(label, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HealthTreePlanTaskRow(task: TubeTaskProgress) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, typeColor(task.task_type).copy(alpha = 0.18f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(healthTreeActionRes(task.task_type)),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title ?: task.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        task.summary ?: healthTreeProgressText(task),
                        style = MaterialTheme.typography.labelMedium,
                        color = typeColor(task.task_type),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            task.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            task.details.take(4).forEach { detail ->
                Text(
                    "- $detail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
            painter = painterResource(healthTreeStageRes(if (day.is_future) 1 else healthTreeStage(day.completion_ratio), day.date)),
            contentDescription = null,
            modifier = Modifier.size(32.dp).graphicsLayer { alpha = if (day.is_future) 0.36f else 1f },
            contentScale = ContentScale.Fit,
        )
        Box(
            Modifier
                .size(width = 42.dp, height = 24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                weekdayName(day.weekday),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
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
                .size(width = 42.dp, height = 24.dp)
                .clip(RoundedCornerShape(999.dp))
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
    onGeneratePlan: () -> Unit,
) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(Icons.Filled.CalendarMonth, "健康计划")
            Spacer(Modifier.weight(1f))
            Button(onClick = onGeneratePlan) {
                Text("生成计划")
            }
            Spacer(Modifier.width(8.dp))
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
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
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
private fun healthTreeStageRes(stage: Int, date: String? = null): Int = when (stage) {
    1 -> R.drawable.healthtree_tree_01_seed
    2 -> R.drawable.healthtree_tree_02_sprout
    3 -> R.drawable.healthtree_tree_03_seedling
    4 -> R.drawable.healthtree_tree_04_young_tree
    5 -> R.drawable.healthtree_tree_05_flowering
    else -> healthTreeFruitingRes(date)
}

@DrawableRes
private fun healthTreeIdleSheetRes(stage: Int, date: String? = null): Int = when (stage) {
    1 -> R.drawable.healthtree_tree_01_seed_idle_sheet
    2 -> R.drawable.healthtree_tree_02_sprout_idle_sheet
    3 -> R.drawable.healthtree_tree_03_seedling_idle_sheet
    4 -> R.drawable.healthtree_tree_04_young_tree_idle_sheet
    5 -> R.drawable.healthtree_tree_05_flowering_idle_sheet
    else -> healthTreeFruitingIdleRes(date)
}

@DrawableRes
private fun healthTreeFruitingRes(date: String?): Int = when (stableTreeSkinSeed(date.orEmpty())) {
    in 0..19 -> R.drawable.healthtree_tree_06_fruiting
    in 20..39 -> R.drawable.healthtree_tree_06_apple
    in 40..59 -> R.drawable.healthtree_tree_06_pear
    in 60..74 -> R.drawable.healthtree_tree_06_golden
    in 75..89 -> R.drawable.healthtree_tree_06_yuanbao
    else -> R.drawable.healthtree_tree_06_peach_immortal
}

@DrawableRes
private fun healthTreeFruitingIdleRes(date: String?): Int = when (stableTreeSkinSeed(date.orEmpty())) {
    in 0..19 -> R.drawable.healthtree_tree_06_fruiting_idle_sheet
    in 20..39 -> R.drawable.healthtree_tree_06_apple_idle_sheet
    in 40..59 -> R.drawable.healthtree_tree_06_pear_idle_sheet
    in 60..74 -> R.drawable.healthtree_tree_06_golden_idle_sheet
    in 75..89 -> R.drawable.healthtree_tree_06_yuanbao_idle_sheet
    else -> R.drawable.healthtree_tree_06_peach_immortal_idle_sheet
}

private fun stableTreeSkinSeed(text: String): Int {
    var value = 0
    text.forEachIndexed { index, ch ->
        value = (value + ch.code * (index + 17)) % 100
    }
    return value
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
