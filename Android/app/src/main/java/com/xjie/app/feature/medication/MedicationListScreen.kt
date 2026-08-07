@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.xjie.app.feature.medication

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import com.xjie.app.core.model.MedicationPrefillCandidate
import com.xjie.app.core.model.MedicationReaction
import com.xjie.app.core.model.MedicationTodaySummary
import com.xjie.app.core.model.MedicationTodayTask
import com.xjie.app.core.model.TrustedMedicationPlan
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private sealed interface MedicationEditorMode {
    data object AddOptions : MedicationEditorMode
    data object ManualPlan : MedicationEditorMode
    data object RawOcrText : MedicationEditorMode
    data class Prefill(val candidate: MedicationPrefillCandidate) : MedicationEditorMode
    data object Reaction : MedicationEditorMode
    data class PlanDetails(val planId: Long) : MedicationEditorMode
    data class Reminder(val planId: Long) : MedicationEditorMode
    data class Records(val returnPlanId: Long? = null) : MedicationEditorMode
    data object Plans : MedicationEditorMode
    data object Reactions : MedicationEditorMode
    data object Course : MedicationEditorMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationListScreen(
    onBack: () -> Unit,
    vm: MedicationViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var editor by remember { mutableStateOf<MedicationEditorMode?>(null) }
    var planDraft by remember { mutableStateOf(MedicationPlanDraft()) }
    var initialPlanDraft by remember { mutableStateOf(MedicationPlanDraft()) }
    var rawOcrText by remember { mutableStateOf("") }
    var reactionDraft by remember { mutableStateOf(MedicationReactionDraft()) }
    var initialReactionDraft by remember { mutableStateOf(MedicationReactionDraft()) }
    var reminderDraft by remember { mutableStateOf<TrustedMedicationReminderSettings?>(null) }
    var initialReminderDraft by remember {
        mutableStateOf<TrustedMedicationReminderSettings?>(null)
    }
    var pendingReminderSave by remember {
        mutableStateOf<TrustedMedicationReminderSettings?>(null)
    }
    var showPermissionRecovery by remember { mutableStateOf(false) }
    var showExactAlarmRecovery by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var pendingTakenTask by remember { mutableStateOf<MedicationTodayTask?>(null) }
    var pendingSnoozeTask by remember { mutableStateOf<MedicationTodayTask?>(null) }
    var pendingSkipTask by remember { mutableStateOf<MedicationTodayTask?>(null) }
    var selectedSnoozeMinutes by remember { mutableStateOf(15) }
    var skipReason by remember { mutableStateOf("") }

    fun dismissKeyboard() {
        focus.clearFocus(force = true)
        keyboard?.hide()
    }

    fun resetEditor() {
        editor = null
        planDraft = MedicationPlanDraft()
        initialPlanDraft = MedicationPlanDraft()
        rawOcrText = ""
        reactionDraft = MedicationReactionDraft()
        initialReactionDraft = MedicationReactionDraft()
        reminderDraft = null
        initialReminderDraft = null
        pendingReminderSave = null
        showPermissionRecovery = false
        showExactAlarmRecovery = false
    }

    fun openAddOptions() {
        dismissKeyboard()
        editor = MedicationEditorMode.AddOptions
    }

    fun openManualPlan() {
        dismissKeyboard()
        val draft = MedicationPlanDraft()
        planDraft = draft
        initialPlanDraft = draft
        editor = MedicationEditorMode.ManualPlan
    }

    fun openPrefill(candidate: MedicationPrefillCandidate) {
        dismissKeyboard()
        val draft = MedicationTrustPolicy.draftFrom(candidate)
        planDraft = draft
        initialPlanDraft = draft
        editor = MedicationEditorMode.Prefill(candidate)
    }

    fun openReaction() {
        dismissKeyboard()
        val draft = MedicationReactionDraft(
            planId = state.plans.firstOrNull { it.status == "active" }?.plan_id,
        )
        reactionDraft = draft
        initialReactionDraft = draft
        editor = MedicationEditorMode.Reaction
    }

    fun openPlanDetails(plan: TrustedMedicationPlan) {
        dismissKeyboard()
        editor = MedicationEditorMode.PlanDetails(plan.plan_id)
    }

    fun openReminder(plan: TrustedMedicationPlan) {
        dismissKeyboard()
        val settings = state.reminderSettings[plan.plan_id]
            ?: TrustedMedicationReminderPolicy.defaults(plan)
        reminderDraft = settings
        initialReminderDraft = settings
        editor = MedicationEditorMode.Reminder(plan.plan_id)
    }

    fun openRecords(returnPlanId: Long? = null) {
        dismissKeyboard()
        editor = MedicationEditorMode.Records(returnPlanId)
        vm.loadWeeklyRecords()
    }

    val isDirty = when (editor) {
        MedicationEditorMode.ManualPlan,
        is MedicationEditorMode.Prefill,
        -> planDraft != initialPlanDraft
        MedicationEditorMode.RawOcrText -> rawOcrText.isNotBlank()
        MedicationEditorMode.Reaction -> reactionDraft != initialReactionDraft
        is MedicationEditorMode.Reminder -> reminderDraft != initialReminderDraft
        MedicationEditorMode.AddOptions,
        is MedicationEditorMode.PlanDetails,
        is MedicationEditorMode.Records,
        MedicationEditorMode.Plans,
        MedicationEditorMode.Reactions,
        MedicationEditorMode.Course,
        null -> false
    }

    fun dismissCurrentEditor() {
        val destination = when (val current = editor) {
            is MedicationEditorMode.Reminder -> MedicationEditorMode.PlanDetails(current.planId)
            is MedicationEditorMode.Records -> current.returnPlanId?.let {
                MedicationEditorMode.PlanDetails(it)
            }
            else -> null
        }
        resetEditor()
        editor = destination
    }

    fun commitReminder(settings: TrustedMedicationReminderSettings) {
        vm.saveReminder(settings) {
            resetEditor()
            editor = MedicationEditorMode.PlanDetails(settings.planId)
        }
    }

    fun hasExactAlarmAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        runCatching {
            (context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager)
                .canScheduleExactAlarms()
        }.getOrDefault(false)

    val exactAlarmPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        val settings = pendingReminderSave
        pendingReminderSave = null
        vm.refreshReminderPermissionState()
        if (settings != null && hasExactAlarmAccess()) {
            commitReminder(settings)
        } else if (settings != null) {
            pendingReminderSave = settings
            showExactAlarmRecovery = true
        }
    }

    fun requestExactAlarmAccess(settings: TrustedMedicationReminderSettings) {
        pendingReminderSave = settings
        exactAlarmPermissionLauncher.launch(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            },
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val settings = pendingReminderSave
        pendingReminderSave = null
        if (granted && settings != null) {
            vm.refreshReminderPermissionState()
            if (hasExactAlarmAccess()) {
                commitReminder(settings)
            } else {
                requestExactAlarmAccess(settings)
            }
        } else if (settings != null) {
            vm.markNotificationPermissionRequested()
            pendingReminderSave = settings
            showPermissionRecovery = true
        }
    }

    fun saveReminder() {
        dismissKeyboard()
        val settings = reminderDraft ?: return
        val permissionRequired = settings.enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (permissionRequired) {
            pendingReminderSave = settings
            vm.markNotificationPermissionRequested()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (settings.enabled && !hasExactAlarmAccess()) {
            requestExactAlarmAccess(settings)
        } else {
            commitReminder(settings)
        }
    }

    fun openNotificationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                data = Uri.parse("package:${context.packageName}")
            },
        )
    }

    fun openExactAlarmSettings() {
        val settings = pendingReminderSave ?: reminderDraft ?: return
        requestExactAlarmAccess(settings)
    }

    fun requestBack() {
        dismissKeyboard()
        if (state.operationInProgress) return
        if (editor != null) {
            if (isDirty) showDiscard = true else dismissCurrentEditor()
        } else {
            onBack()
        }
    }

    val pendingPrefills = state.prefills.filter {
        it.review_status == "pending_review" && !it.plan_created
    }
    val dashboardPrimaryAction = MedicationTrustPolicy.primaryAction(
        state.today,
        pendingPrefills,
        state.plans,
    )

    fun performDashboardPrimaryAction() {
        when (dashboardPrimaryAction) {
            MedicationPrimaryAction.ConfirmCurrentDose -> {
                pendingTakenTask = state.today?.next_task
            }
            MedicationPrimaryAction.ReviewPrefill -> pendingPrefills.firstOrNull()?.let(::openPrefill)
            MedicationPrimaryAction.AddFirstMedication -> openAddOptions()
            MedicationPrimaryAction.ViewTodayRecords -> openRecords()
        }
    }

    val dismissKeyboardOnScroll = remember(focus, keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) dismissKeyboard()
                return Offset.Zero
            }
        }
    }

    BackHandler(enabled = true, onBack = ::requestBack)
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        modifier = if (editor == null && !state.loading && state.today != null) {
            Modifier.testTag("xage.medication.loaded")
        } else {
            Modifier
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (editor == null && state.today != null) {
                MedicationDashboardBottomAction(
                    action = dashboardPrimaryAction,
                    operationInProgress = state.operationInProgress,
                    onClick = ::performDashboardPrimaryAction,
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (editor) {
                            MedicationEditorMode.AddOptions -> "新增用药"
                            MedicationEditorMode.ManualPlan -> "手动新增用药"
                            MedicationEditorMode.RawOcrText -> "识别文字"
                            is MedicationEditorMode.Prefill -> "检查药物信息"
                            MedicationEditorMode.Reaction -> "记录不适"
                            is MedicationEditorMode.PlanDetails -> "用药计划详情"
                            is MedicationEditorMode.Reminder -> "提醒设置"
                            is MedicationEditorMode.Records -> "服药记录"
                            MedicationEditorMode.Plans -> "当前用药计划"
                            MedicationEditorMode.Reactions -> "不适与反应"
                            MedicationEditorMode.Course -> "疗程与余量"
                            null -> "用药记录"
                        },
                        modifier = if (editor == null) {
                            Modifier.testTag("xage.medication.title")
                        } else {
                            Modifier
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = ::requestBack,
                        enabled = !state.operationInProgress,
                        modifier = Modifier.size(48.dp).testTag("xage.medication.back"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (editor == null) {
                        IconButton(
                            onClick = ::openAddOptions,
                            enabled = state.today != null && !state.operationInProgress,
                            modifier = Modifier.testTag("xage.medication.add"),
                        ) {
                            Text("+", fontSize = 28.sp)
                        }
                        IconButton(
                            onClick = { dismissKeyboard(); vm.load() },
                            enabled = !state.loading && !state.refreshing && !state.operationInProgress,
                        ) {
                            Icon(Icons.Filled.Refresh, "刷新可信用药")
                        }
                    }
                },
            )
        },
    ) { inner ->
        when (val mode = editor) {
            MedicationEditorMode.AddOptions -> MedicationAddOptionsPage(
                modifier = Modifier.padding(inner),
                capabilities = TrustedMedicationReminderPolicy.addCapabilities(state.prefills),
                onSelect = { capability ->
                    when (capability.action) {
                        MedicationAddAction.PrescriptionImport,
                        MedicationAddAction.HistoryRestart,
                        -> capability.candidate?.let(::openPrefill)
                        MedicationAddAction.RawOcrText -> {
                            rawOcrText = ""
                            editor = MedicationEditorMode.RawOcrText
                        }
                        MedicationAddAction.Manual -> openManualPlan()
                    }
                },
            )
            MedicationEditorMode.ManualPlan -> PlanEditor(
                modifier = Modifier.padding(inner),
                draft = planDraft,
                onDraftChange = { planDraft = it },
                candidate = null,
                operationInProgress = state.operationInProgress,
                dismissKeyboardOnScroll = dismissKeyboardOnScroll,
                onCancel = ::requestBack,
                onConfirm = {
                    dismissKeyboard()
                    vm.confirmPlan(planDraft, null, ::resetEditor)
                },
            )
            MedicationEditorMode.RawOcrText -> RawOcrEditor(
                modifier = Modifier.padding(inner),
                text = rawOcrText,
                onTextChange = { rawOcrText = it },
                operationInProgress = state.operationInProgress,
                dismissKeyboardOnScroll = dismissKeyboardOnScroll,
                onCancel = ::requestBack,
                onRecognize = {
                    dismissKeyboard()
                    vm.recognizeRawText(rawOcrText, ::resetEditor)
                },
            )
            is MedicationEditorMode.Prefill -> PlanEditor(
                modifier = Modifier.padding(inner),
                draft = planDraft,
                onDraftChange = { planDraft = it },
                candidate = mode.candidate,
                operationInProgress = state.operationInProgress,
                dismissKeyboardOnScroll = dismissKeyboardOnScroll,
                onCancel = ::requestBack,
                onConfirm = {
                    dismissKeyboard()
                    vm.confirmPlan(planDraft, mode.candidate, ::resetEditor)
                },
            )
            MedicationEditorMode.Reaction -> ReactionEditor(
                modifier = Modifier.padding(inner),
                draft = reactionDraft,
                onDraftChange = { reactionDraft = it },
                plans = state.plans.filter { it.status == "active" },
                operationInProgress = state.operationInProgress,
                dismissKeyboardOnScroll = dismissKeyboardOnScroll,
                onCancel = ::requestBack,
                onConfirm = {
                    dismissKeyboard()
                    vm.createReaction(reactionDraft, ::resetEditor)
                },
            )
            is MedicationEditorMode.PlanDetails -> {
                val plan = state.plans.firstOrNull { it.plan_id == mode.planId }
                if (plan == null) {
                    MissingTrustedContent(
                        modifier = Modifier.padding(inner),
                        text = "该用药计划已更新或不再可用，请返回刷新。",
                    )
                } else {
                    MedicationPlanDetailsPage(
                        modifier = Modifier.padding(inner),
                        plan = plan,
                        settings = state.reminderSettings[plan.plan_id],
                        onReminder = { openReminder(plan) },
                        onRecords = { openRecords(plan.plan_id) },
                    )
                }
            }
            is MedicationEditorMode.Reminder -> {
                val plan = state.plans.firstOrNull { it.plan_id == mode.planId }
                val draft = reminderDraft
                if (plan == null || draft == null) {
                    MissingTrustedContent(
                        modifier = Modifier.padding(inner),
                        text = "提醒所对应的用药计划已更新，请返回刷新后重新设置。",
                    )
                } else {
                    MedicationReminderEditor(
                        modifier = Modifier.padding(inner),
                        plan = plan,
                        draft = draft,
                        onDraftChange = { reminderDraft = it },
                        permissionRecoveryRequired = showPermissionRecovery,
                        exactAlarmRecoveryRequired = showExactAlarmRecovery,
                        operationInProgress = state.operationInProgress,
                        dismissKeyboardOnScroll = dismissKeyboardOnScroll,
                        onOpenPermissionSettings = ::openNotificationSettings,
                        onOpenExactAlarmSettings = ::openExactAlarmSettings,
                        onSave = ::saveReminder,
                        onCancel = ::requestBack,
                    )
                }
            }
            is MedicationEditorMode.Records -> MedicationRecordsPage(
                modifier = Modifier.padding(inner),
                state = state,
                onRetryWeekly = vm::loadWeeklyRecords,
                onCorrect = vm::correctDose,
            )
            MedicationEditorMode.Plans -> MedicationPlansPage(
                modifier = Modifier.padding(inner),
                plans = state.plans,
                prefills = state.prefills.filter {
                    it.review_status == "pending_review" && !it.plan_created
                },
                reminderSettings = state.reminderSettings,
                scheduledCounts = state.scheduledReminderCountByPlan,
                onAdd = ::openAddOptions,
                onPlan = ::openPlanDetails,
                onPrefill = ::openPrefill,
            )
            MedicationEditorMode.Reactions -> MedicationReactionsPage(
                modifier = Modifier.padding(inner),
                reactions = state.reactions,
                canRecord = state.plans.any { it.status == "active" },
                onRecord = ::openReaction,
            )
            MedicationEditorMode.Course -> MedicationCoursePage(
                modifier = Modifier.padding(inner),
                plans = state.plans,
            )
            null -> TrustedMedicationContent(
                modifier = Modifier.padding(inner),
                state = state,
                dismissKeyboardOnScroll = dismissKeyboardOnScroll,
                onRetry = vm::load,
                onPrimaryAction = { action ->
                    when (action) {
                        MedicationPrimaryAction.ConfirmCurrentDose -> {
                            pendingTakenTask = state.today?.next_task
                        }
                        MedicationPrimaryAction.ReviewPrefill -> {
                            state.prefills.firstOrNull {
                                it.review_status == "pending_review" && !it.plan_created
                            }?.let(::openPrefill)
                        }
                        MedicationPrimaryAction.AddFirstMedication -> openAddOptions()
                        MedicationPrimaryAction.ViewTodayRecords -> openRecords()
                    }
                },
                onConfirm = { pendingTakenTask = it },
                onSnooze = {
                    selectedSnoozeMinutes = state.reminderSettings[it.plan_id]?.snoozeMinutes ?: 15
                    pendingSnoozeTask = it
                },
                onSkip = {
                    skipReason = ""
                    pendingSkipTask = it
                },
                onAdd = ::openAddOptions,
                onReviewPrefill = ::openPrefill,
                onReaction = ::openReaction,
                onPlan = ::openPlanDetails,
                onReminder = ::openReminder,
                onRecords = { openRecords() },
                onPlans = { editor = MedicationEditorMode.Plans },
                onReactions = { editor = MedicationEditorMode.Reactions },
                onCourse = { editor = MedicationEditorMode.Course },
            )
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("放弃未保存修改？") },
            text = { Text("返回后，本页尚未提交的用药信息会丢失。") },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; dismissCurrentEditor() }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("继续编辑") }
            },
        )
    }

    pendingTakenTask?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingTakenTask = null },
            title = { Text("确认已经服用？") },
            text = {
                Text(
                    "将按你的明确确认记录 ${task.generic_name} ${task.dose_text.orEmpty()}。" +
                        "提醒时间经过本身不会产生这条记录。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingTakenTask = null
                        vm.recordDose(task, "taken")
                    },
                ) { Text("确认已服用") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTakenTask = null }) { Text("取消") }
            },
        )
    }
    pendingSnoozeTask?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingSnoozeTask = null },
            title = { Text("稍后提醒") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("本次仍保持待确认；只有服务端接受版本化操作后才会安排本机闹钟。")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 30, 60).forEach { minutes ->
                            FilterChip(
                                selected = selectedSnoozeMinutes == minutes,
                                onClick = { selectedSnoozeMinutes = minutes },
                                label = { Text("$minutes 分钟") },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSnoozeTask = null
                    vm.recordDose(task, "snooze", snoozeMinutes = selectedSnoozeMinutes)
                }) { Text("确认稍后提醒") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSnoozeTask = null }) { Text("取消") }
            },
        )
    }
    pendingSkipTask?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingSkipTask = null },
            title = { Text("确认本次跳过？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("这只记录你的明确选择，不代表系统判断应该停药。")
                    OutlinedTextField(
                        value = skipReason,
                        onValueChange = { skipReason = it },
                        label = { Text("原因（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSkipTask = null
                    vm.recordDose(
                        task,
                        "skip",
                        skipReason.trim().ifBlank { "用户选择本次跳过" },
                    )
                }) { Text("确认本次跳过") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSkipTask = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TrustedMedicationContent(
    modifier: Modifier,
    state: MedicationUiState,
    dismissKeyboardOnScroll: NestedScrollConnection,
    onRetry: () -> Unit,
    onPrimaryAction: (MedicationPrimaryAction) -> Unit,
    onConfirm: (MedicationTodayTask) -> Unit,
    onSnooze: (MedicationTodayTask) -> Unit,
    onSkip: (MedicationTodayTask) -> Unit,
    onAdd: () -> Unit,
    onReviewPrefill: (MedicationPrefillCandidate) -> Unit,
    onReaction: () -> Unit,
    onPlan: (TrustedMedicationPlan) -> Unit,
    onReminder: (TrustedMedicationPlan) -> Unit,
    onRecords: () -> Unit,
    onPlans: () -> Unit,
    onReactions: () -> Unit,
    onCourse: () -> Unit,
) {
    val heroState = MedicationDashboardPresentation.hero(
        today = state.today,
        plans = state.plans,
        loading = state.loading,
    )
    val today = state.today
    if (today == null && heroState !is MedicationDashboardHeroState.Loading) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error ?: "尚未取得可信用药状态", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("重试") }
            }
        }
        return
    }
    val pendingPrefills = state.prefills.filter {
        it.review_status == "pending_review" && !it.plan_created
    }
    val heroMinimumHeight = maxOf(
        (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp / 3).dp,
        270.dp,
    )
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .nestedScroll(dismissKeyboardOnScroll)
            .padding(horizontal = 20.dp)
            .testTag("xage.medication.root"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            MedicationDashboardSummaryRow(
                planned = today?.planned_count ?: 0,
                taken = today?.taken_count ?: 0,
                pending = today?.let {
                    it.awaiting_confirmation_count + it.possibly_missed_count + it.snoozed_count
                } ?: 0,
            )
        }
        item {
            MedicationDashboardHero(
                state = heroState,
                plans = state.plans,
                reminderSettings = state.reminderSettings,
                scheduledCounts = state.scheduledReminderCountByPlan,
                notificationPermission = state.notificationPermission,
                exactAlarmAccess = state.exactAlarmAccess,
                minimumHeight = heroMinimumHeight,
                operationInProgress = state.operationInProgress,
                onAdd = onAdd,
                onConfirm = onConfirm,
                onSnooze = onSnooze,
                onSkip = onSkip,
                onReaction = onReaction,
                onReminder = onReminder,
            )
        }
        if (state.plans.any { it.status != "retracted" }) {
            item {
                MedicationTodayDashboardCard(
                    tasks = today?.tasks.orEmpty(),
                    operationInProgress = state.operationInProgress,
                    onTask = { task ->
                        if (MedicationTrustPolicy.canRecordDose(task)) onConfirm(task) else onRecords()
                    },
                )
            }
            item {
                MedicationDashboardDestinations(
                    planCount = state.plans.count { it.status != "retracted" },
                    today = today,
                    reactionCount = state.reactions.size,
                    onPlans = onPlans,
                    onRecords = onRecords,
                    onReactions = onReactions,
                    onCourse = onCourse,
                )
            }
        }
        if (pendingPrefills.isNotEmpty()) {
            item { SectionTitle("待确认的识别结果") }
            items(pendingPrefills, key = { it.candidate_id }) { candidate ->
                PrefillCard(candidate, onReview = { onReviewPrefill(candidate) })
            }
        }
        item {
            SupportingCard("提醒超时不会自动确认漏服或已服；剂量调整请联系医生或药师。")
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun MedicationDashboardSummaryRow(planned: Int, taken: Int, pending: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple("今日计划", planned, "xage.medication.summary.planned"),
            Triple("已服用", taken, "xage.medication.summary.taken"),
            Triple("待确认", pending, "xage.medication.summary.pending"),
        ).forEach { (title, value, tag) ->
            Card(
                modifier = Modifier.weight(1f).heightIn(min = 86.dp).testTag(tag),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("${value}次", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MedicationDashboardHero(
    state: MedicationDashboardHeroState,
    plans: List<TrustedMedicationPlan>,
    reminderSettings: Map<Long, TrustedMedicationReminderSettings>,
    scheduledCounts: Map<Long, Int>,
    notificationPermission: MedicationNotificationPermissionState,
    exactAlarmAccess: MedicationExactAlarmAccessState,
    minimumHeight: androidx.compose.ui.unit.Dp,
    operationInProgress: Boolean,
    onAdd: () -> Unit,
    onConfirm: (MedicationTodayTask) -> Unit,
    onSnooze: (MedicationTodayTask) -> Unit,
    onSkip: (MedicationTodayTask) -> Unit,
    onReaction: () -> Unit,
    onReminder: (TrustedMedicationPlan) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = minimumHeight).testTag(
            when (state) {
                MedicationDashboardHeroState.Loading -> "xage.medication.hero.loading"
                MedicationDashboardHeroState.NoMedication -> "xage.medication.hero.empty"
                is MedicationDashboardHeroState.NextDose -> "xage.medication.hero.next"
                is MedicationDashboardHeroState.AllHandled -> "xage.medication.hero.handled"
            },
        ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        ),
    ) {
        when (state) {
            MedicationDashboardHeroState.Loading -> Box(
                Modifier.fillMaxWidth().heightIn(min = minimumHeight),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在读取下一次用药…", fontWeight = FontWeight.SemiBold)
                }
            }
            MedicationDashboardHeroState.NoMedication -> Column(
                Modifier.fillMaxWidth().heightIn(min = minimumHeight).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.MedicalServices,
                    null,
                    modifier = Modifier.size(54.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "还没有添加用药提醒哦，\n快去添加第一条用药提醒吧",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onAdd,
                    enabled = !operationInProgress,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) { Text("添加第一条用药提醒") }
            }
            is MedicationDashboardHeroState.AllHandled -> Column(
                Modifier.fillMaxWidth().heightIn(min = minimumHeight).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("✓", fontSize = 52.sp, color = MaterialTheme.colorScheme.primary)
                Text("今天的用药已处理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is MedicationDashboardHeroState.NextDose -> {
                val task = state.task
                val plan = plans.firstOrNull { it.plan_id == task.plan_id }
                val setting = reminderSettings[task.plan_id]
                val owner = setting?.let {
                    MedicationReminderOwner(it.accountScope, it.selectedSubjectId, it.authGeneration)
                } ?: MedicationReminderOwner("", "", -1L)
                val reminder = MedicationReminderPresentation.resolve(
                    task = task,
                    plans = plans,
                    settings = setting,
                    notificationPermission = notificationPermission,
                    exactAlarmAccess = exactAlarmAccess,
                    scheduledCount = scheduledCounts[task.plan_id] ?: 0,
                    owner = owner,
                    timezoneId = java.time.ZoneId.systemDefault().id,
                )
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("下一次服药", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { if (plan != null) onReminder(plan) },
                            enabled = plan != null && !operationInProgress,
                            modifier = Modifier.heightIn(min = 44.dp).testTag("xage.medication.hero.reminder"),
                        ) {
                            Text(
                                reminder.compactTitle,
                                color = when (reminder.tone) {
                                    MedicationReminderTone.Active -> MaterialTheme.colorScheme.primary
                                    MedicationReminderTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
                                    MedicationReminderTone.Warning -> MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                    Text(
                        task.scheduled_time,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        listOfNotNull(task.generic_name, task.dose_text).joinToString(" "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    plan?.let {
                        val instruction = it.instructions?.takeIf(String::isNotBlank)
                            ?: it.meal_relation.takeUnless { relation -> relation == "unspecified" }
                                ?.let(::mealRelationLabel)
                        instruction?.let { text ->
                            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (task.status == "possibly_missed") {
                        Text(
                            "提醒时间已过，仍需你确认；请勿自行在下一次加倍。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = { onConfirm(task) },
                        enabled = !operationInProgress,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("xage.medication.hero.confirm"),
                    ) { Text("确认已服用") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onSnooze(task) },
                            enabled = !operationInProgress,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                .testTag("xage.medication.hero.snooze"),
                        ) { Text("稍后提醒") }
                        OutlinedButton(
                            onClick = { onSkip(task) },
                            enabled = !operationInProgress,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                .testTag("xage.medication.hero.skip"),
                        ) { Text("本次跳过") }
                    }
                    TextButton(
                        onClick = onReaction,
                        enabled = !operationInProgress,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .testTag("xage.medication.hero.reaction"),
                    ) { Text("记录不适") }
                }
            }
        }
    }
}

@Composable
private fun MedicationTodayDashboardCard(
    tasks: List<MedicationTodayTask>,
    operationInProgress: Boolean,
    onTask: (MedicationTodayTask) -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("今日用药", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (tasks.isEmpty()) {
                Text("今天没有计划剂次", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                tasks.take(3).forEach { task ->
                    TextButton(
                        onClick = { onTask(task) },
                        enabled = !operationInProgress,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)
                            .testTag("xage.medication.today.${task.occurrence_key}"),
                    ) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(
                                listOfNotNull(task.generic_name, task.dose_text).joinToString(" "),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${task.scheduled_time} · ${MedicationTrustPolicy.taskStatusLabel(task)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(if (task.status == "taken") "已服用" else "查看")
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationDashboardDestinations(
    planCount: Int,
    today: MedicationTodaySummary?,
    reactionCount: Int,
    onPlans: () -> Unit,
    onRecords: () -> Unit,
    onReactions: () -> Unit,
    onCourse: () -> Unit,
) {
    val destinations = listOf(
        Triple("当前用药计划", "$planCount 种药物正在管理", onPlans),
        Triple("服药记录", "今天已确认 ${today?.taken_count ?: 0} 次", onRecords),
        Triple("不适与反应", if (reactionCount == 0) "记录与服药时间接近的身体感受" else "$reactionCount 条记录", onReactions),
        Triple("疗程与余量", "查看疗程进度与预计剩余", onCourse),
    )
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            destinations.forEachIndexed { index, (title, subtitle, action) ->
                TextButton(
                    onClick = action,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).testTag(
                        "xage.medication.destination.${listOf("plans", "records", "reactions", "course")[index]}",
                    ),
                ) {
                    Icon(Icons.Filled.MedicalServices, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun MedicationDashboardBottomAction(
    action: MedicationPrimaryAction,
    operationInProgress: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !operationInProgress,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .heightIn(min = 54.dp).testTag("xage.medication.bottomAction"),
        shape = RoundedCornerShape(28.dp),
    ) {
        if (operationInProgress) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            when (action) {
                MedicationPrimaryAction.ConfirmCurrentDose -> "确认本次服药"
                MedicationPrimaryAction.ReviewPrefill -> "检查药物信息"
                MedicationPrimaryAction.AddFirstMedication -> "添加第一种药物"
                MedicationPrimaryAction.ViewTodayRecords -> "查看用药记录"
            },
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TodaySummaryCard(
    today: MedicationTodaySummary,
    nextPlan: TrustedMedicationPlan?,
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MedicalServices, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("今日用药", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(today.local_date, style = MaterialTheme.typography.labelMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryChip("计划 ${today.planned_count}")
                SummaryChip("已服 ${today.taken_count}")
                SummaryChip("待确认 ${today.awaiting_confirmation_count}")
                SummaryChip("可能漏服 ${today.possibly_missed_count}")
                SummaryChip("跳过 ${today.skipped_count}")
                SummaryChip("稍后 ${today.snoozed_count}")
                SummaryChip("不适 ${today.adverse_reaction_count}")
            }
            today.next_task?.let { task ->
                Text(
                    "下一次：${task.scheduled_time} · ${task.generic_name}" +
                        task.dose_text?.let { " · $it" }.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val requirement = listOfNotNull(
                    nextPlan?.meal_relation?.let(::mealRelationLabel),
                    nextPlan?.instructions,
                ).filter(String::isNotBlank).joinToString(" · ")
                if (requirement.isNotBlank()) {
                    Text(
                        "服用要求：$requirement",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            today.empty_state?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "提醒超时不会自动确认漏服，也不要自行在下一次加倍剂量。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryChip(text: String) {
    AssistChip(onClick = {}, label = { Text(text) })
}

@Composable
private fun CurrentTaskCard(
    task: MedicationTodayTask,
    operationInProgress: Boolean,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onReaction: () -> Unit,
) {
    val possiblyMissed = task.status == "possibly_missed"
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (possiblyMissed) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("当前服药任务", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(task.generic_name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(task.scheduled_time, task.dose_text).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                MedicationTrustPolicy.taskStatusLabel(task),
                style = MaterialTheme.typography.bodyMedium,
                color = if (possiblyMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            taskTimingDetail(task)?.let { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (possiblyMissed) {
                Text(
                    "这只是计划时间经过后的待确认状态，不是已确认漏服。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (MedicationTrustPolicy.canRecordDose(task)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSnooze, enabled = !operationInProgress) {
                        Text("稍后 $snoozeMinutes 分钟")
                    }
                    TextButton(onClick = onSkip, enabled = !operationInProgress) {
                        Text("本次跳过")
                    }
                }
            }
            TextButton(onClick = onReaction, enabled = !operationInProgress) {
                Text("记录不适")
            }
        }
    }
}

@Composable
private fun MedicationPlansPage(
    modifier: Modifier,
    plans: List<TrustedMedicationPlan>,
    prefills: List<MedicationPrefillCandidate>,
    reminderSettings: Map<Long, TrustedMedicationReminderSettings>,
    scheduledCounts: Map<Long, Int>,
    onAdd: () -> Unit,
    onPlan: (TrustedMedicationPlan) -> Unit,
    onPrefill: (MedicationPrefillCandidate) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)
            .testTag("xage.medication.detail.plans"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("新增用药") }
        }
        if (prefills.isNotEmpty()) {
            item { SectionTitle("待确认的识别结果") }
            items(prefills, key = { "prefill-${it.candidate_id}" }) { candidate ->
                PrefillCard(candidate) { onPrefill(candidate) }
            }
        }
        item { SectionTitle("已确认计划") }
        if (plans.none { it.status != "retracted" }) {
            item { SupportingCard("暂无已确认用药计划。") }
        } else {
            items(plans.filter { it.status != "retracted" }, key = { it.plan_id }) { plan ->
                TrustedPlanCard(
                    plan = plan,
                    settings = reminderSettings[plan.plan_id],
                    scheduledCount = scheduledCounts[plan.plan_id] ?: 0,
                    onClick = { onPlan(plan) },
                )
            }
        }
        item { SupportingCard("识别结果只有逐项确认后才会成为可信用药计划。") }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MedicationReactionsPage(
    modifier: Modifier,
    reactions: List<MedicationReaction>,
    canRecord: Boolean,
    onRecord: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)
            .testTag("xage.medication.detail.reactions"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SupportingCard("这里只记录症状与服药时间接近，不能据此认定症状由药物导致。")
        }
        item {
            Button(
                onClick = onRecord,
                enabled = canRecord,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("记录身体感受") }
        }
        if (reactions.isEmpty()) {
            item { SupportingCard("今天尚未记录不适。") }
        } else {
            items(reactions, key = { it.reaction_key }) { reaction -> ReactionCard(reaction) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MedicationCoursePage(
    modifier: Modifier,
    plans: List<TrustedMedicationPlan>,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)
            .testTag("xage.medication.detail.course"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SupportingCard("预计余量只按你明确确认的服药记录计算，不代表实际库存。")
        }
        if (plans.none { it.status != "retracted" }) {
            item { SupportingCard("暂无可展示的已确认用药计划。") }
        } else {
            items(plans.filter { it.status != "retracted" }, key = { it.plan_id }) { plan ->
                MedicationCourseRecordCard(plan)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TrustedPlanCard(
    plan: TrustedMedicationPlan,
    settings: TrustedMedicationReminderSettings?,
    scheduledCount: Int,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.generic_name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(planStatus(plan.status), style = MaterialTheme.typography.labelMedium)
            }
            plan.brand_name?.let { Text("商品名：$it", style = MaterialTheme.typography.bodyMedium) }
            val dose = listOfNotNull(plan.strength, plan.dose_text).joinToString(" · ")
            if (dose.isNotBlank()) Text("剂量：$dose", style = MaterialTheme.typography.bodyMedium)
            plan.frequency?.let { Text("频次：$it", style = MaterialTheme.typography.bodyMedium) }
            if (plan.schedule_times.isNotEmpty()) {
                Text("时间：${plan.schedule_times.joinToString("、")}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${mealRelationLabel(plan.meal_relation)} · ${plan.course_start ?: "未确认开始"} 至 " +
                    (plan.course_end ?: "未确认结束"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "来源：${medicationSourceLabel(plan.source_type)} · 最近确认 ${friendlyDateTime(plan.confirmed_at)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("疗程与余量（预计剩余）", style = MaterialTheme.typography.labelMedium)
            Text(
                MedicationTrustPolicy.inventoryLine(plan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (settings?.enabled == true && scheduledCount > 0) {
                    "本机提醒已安排 $scheduledCount 个 · ${reminderCadenceLabel(settings.cadence)} · " +
                        "提前 ${settings.advanceMinutes} 分钟"
                } else if (settings?.enabled == true) {
                    "提醒偏好已保存，但当前没有实际成功排期"
                } else if (!plan.reminder_default_enabled && plan.reminder_management == "client_managed") {
                    "提醒默认关闭 · 点此查看详情并主动开启"
                } else {
                    "提醒状态需要重新核对，客户端未自动开启"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrefillCard(candidate: MedicationPrefillCandidate, onReview: () -> Unit) {
    val name = MedicationTrustPolicy.draftFrom(candidate).genericName.ifBlank { "未识别药名" }
    OutlinedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "待复核 · 版本 ${candidate.version} · 尚未创建计划",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (candidate.low_confidence_fields.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "低置信度：${candidate.low_confidence_fields.joinToString("、")}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            OutlinedButton(onClick = onReview, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("检查并确认")
            }
        }
    }
}

@Composable
private fun ReactionCard(reaction: MedicationReaction) {
    OutlinedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(reaction.symptoms, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("严重程度：${severityLabel(reaction.severity)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "该症状发生在服药后，不能据此认定由药物导致",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (reaction.severity == "severe") {
                Text(reaction.safety_guidance, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MissingTrustedContent(modifier: Modifier, text: String) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        SupportingCard(text, isError = true)
    }
}

@Composable
private fun MedicationAddOptionsPage(
    modifier: Modifier,
    capabilities: List<MedicationAddCapability>,
    onSelect: (MedicationAddCapability) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)
            .testTag("xage.medication.addOptions"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SupportingCard(
                "所有新增信息都要由你逐项确认。未接入的来源会明确显示不可用，不会生成模拟处方或把旧提醒当成用药事实。",
            )
        }
        items(capabilities, key = { it.action.name }) { capability ->
            OutlinedCard(
                onClick = { onSelect(capability) },
                enabled = capability.available,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        capability.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        capability.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (capability.available) "可继续" else "暂不可用",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (capability.available) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MedicationPlanDetailsPage(
    modifier: Modifier,
    plan: TrustedMedicationPlan,
    settings: TrustedMedicationReminderSettings?,
    onReminder: () -> Unit,
    onRecords: () -> Unit,
) {
    val course = TrustedMedicationReminderPolicy.coursePresentation(plan)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(plan.generic_name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                plan.brand_name?.let { Text("商品名：$it") }
                Text("状态：${planStatus(plan.status)}")
                Text("规格 / 剂量：${listOfNotNull(plan.strength, plan.dose_text).joinToString(" · ").ifBlank { "未确认" }}")
                Text("频次：${plan.frequency ?: "未确认"}")
                Text("服药时间：${plan.schedule_times.joinToString("、").ifBlank { "未确认" }}")
                Text("进餐关系：${mealRelationLabel(plan.meal_relation)}")
                plan.instructions?.let { Text("服用说明：$it") }
                Text("疗程：${course.dateRange}")
                Text("已进行天数：${course.elapsedDays?.let { "第 $it 天" } ?: "暂不可用"}")
                Text("处方医生 / 来源：${plan.prescriber ?: medicationSourceLabel(plan.source_type)}")
                Text("最近确认：${friendlyDateTime(plan.confirmed_at)}")
                Text("可信计划版本：${plan.version}", style = MaterialTheme.typography.bodySmall)
            }
        }
        SupportingCard(MedicationTrustPolicy.inventoryLine(plan))
        SupportingCard(course.refillEligibility)
        if (course.endingSoon) {
            SupportingCard("已确认疗程将在 7 天内结束，请按原处方安排复诊或向医生、药师确认后续。")
        }
        SupportingCard(course.confirmedRate)
        if (plan.status == "active") {
            Button(
                onClick = onReminder,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (settings?.enabled == true) "调整服药提醒" else "主动开启服药提醒")
            }
        } else {
            SupportingCard("该计划不是服用中状态，不能开启新的本机提醒。")
        }
        OutlinedButton(
            onClick = onRecords,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("查看服药记录")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MedicationReminderEditor(
    modifier: Modifier,
    plan: TrustedMedicationPlan,
    draft: TrustedMedicationReminderSettings,
    onDraftChange: (TrustedMedicationReminderSettings) -> Unit,
    permissionRecoveryRequired: Boolean,
    exactAlarmRecoveryRequired: Boolean,
    operationInProgress: Boolean,
    dismissKeyboardOnScroll: NestedScrollConnection,
    onOpenPermissionSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .nestedScroll(dismissKeyboardOnScroll)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SupportingCard(
            "提醒只在本机生效，不会改变已确认的药名、剂量或服药记录。提醒默认关闭，只有你保存开启后才会安排。",
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("开启本机提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("关闭后会取消这项计划的所有本机提醒", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = draft.enabled,
                onCheckedChange = { onDraftChange(draft.copy(enabled = it)) },
                enabled = plan.status == "active" && plan.schedule_times.isNotEmpty(),
            )
        }
        Text("提醒时点", style = MaterialTheme.typography.titleSmall)
        Text(
            plan.schedule_times.joinToString("、").ifBlank { "计划尚未确认服药时间，暂不能开启提醒" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("频次", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TrustedMedicationReminderSettings.CADENCE_DAILY to "每日",
                TrustedMedicationReminderSettings.CADENCE_ALTERNATE_DAYS to "隔日",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = draft.cadence == value,
                    onClick = { onDraftChange(draft.copy(cadence = value)) },
                    label = { Text(label) },
                )
            }
        }
        Text("服用要求：${mealRelationLabel(plan.meal_relation)}", style = MaterialTheme.typography.bodyMedium)
        Text("提前提醒", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 15, 30, 60).forEach { minutes ->
                FilterChip(
                    selected = draft.advanceMinutes == minutes,
                    onClick = { onDraftChange(draft.copy(advanceMinutes = minutes)) },
                    label = { Text(if (minutes == 0) "准时" else "提前 $minutes 分钟") },
                )
            }
        }
        Text("稍后提醒间隔", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30, 60).forEach { minutes ->
                FilterChip(
                    selected = draft.snoozeMinutes == minutes,
                    onClick = { onDraftChange(draft.copy(snoozeMinutes = minutes)) },
                    label = { Text("$minutes 分钟") },
                )
            }
        }
        PlanTextField("提醒结束日期 YYYY-MM-DD", draft.reminderEndDate.orEmpty()) {
            onDraftChange(draft.copy(reminderEndDate = it.ifBlank { null }))
        }
        Text(
            "已确认疗程结束：${plan.course_end ?: "未提供"}。提醒结束不能晚于已确认疗程。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("播放通知声音", modifier = Modifier.weight(1f))
            Switch(
                checked = draft.soundEnabled,
                onCheckedChange = { onDraftChange(draft.copy(soundEnabled = it)) },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("锁屏显示药名")
                Text("关闭时只显示通用“用药提醒”", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = draft.showMedicationNameOnLockScreen,
                onCheckedChange = {
                    onDraftChange(draft.copy(showMedicationNameOnLockScreen = it))
                },
            )
        }
        if (permissionRecoveryRequired) {
            SupportingCard("通知权限未开启。设置已保留在编辑页，请先到系统设置允许小捷通知，再回来保存。", isError = true)
            OutlinedButton(
                onClick = onOpenPermissionSettings,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("打开系统通知设置")
            }
        }
        if (exactAlarmRecoveryRequired) {
            SupportingCard(
                "系统精确闹钟权限尚未开启。当前设置仍未排期，也不会显示为“已安排”。",
                isError = true,
            )
            OutlinedButton(
                onClick = onOpenExactAlarmSettings,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("打开系统精确闹钟设置")
            }
        }
        Button(
            onClick = onSave,
            enabled = !operationInProgress && (!draft.enabled || plan.schedule_times.isNotEmpty()),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(if (draft.enabled) "保存并开启提醒" else "保存关闭状态")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }
        Spacer(Modifier.height(24.dp))
    }
}

private enum class MedicationRecordScope { Daily, Weekly, Course }

@Composable
private fun MedicationRecordsPage(
    modifier: Modifier,
    state: MedicationUiState,
    onRetryWeekly: () -> Unit,
    onCorrect: (MedicationTodayTask, String) -> Unit,
) {
    var scope by remember { mutableStateOf(MedicationRecordScope.Daily) }
    LaunchedEffect(scope, state.weeklyRecords.isEmpty()) {
        if (scope == MedicationRecordScope.Weekly && state.weeklyRecords.isEmpty()) {
            onRetryWeekly()
        }
    }
    val today = state.today
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)
            .testTag("xage.medication.detail.records"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    MedicationRecordScope.Daily to "每日",
                    MedicationRecordScope.Weekly to "每周",
                    MedicationRecordScope.Course to "疗程",
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = scope == value,
                        onClick = { scope = value },
                        label = { Text(label) },
                    )
                }
            }
        }
        when (scope) {
            MedicationRecordScope.Daily -> {
                if (today == null) {
                    item { SupportingCard("今日可信记录尚未加载。", isError = true) }
                } else {
                    item { SupportingCard(TrustedMedicationReminderPolicy.confirmedRate(listOf(today)).label) }
                    if (today.tasks.isEmpty()) {
                        item { SupportingCard("今天没有已确认用药计划。") }
                    } else {
                        items(today.tasks, key = { it.occurrence_key }) { task ->
                            MedicationRecordTaskCard(
                                task = task,
                                allowCorrection = MedicationTrustPolicy.canCorrectDose(task),
                                operationInProgress = state.operationInProgress,
                                onCorrect = { corrected -> onCorrect(task, corrected) },
                            )
                        }
                    }
                }
            }
            MedicationRecordScope.Weekly -> {
                when {
                    state.weeklyRecordsLoading -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.weeklyRecordsError != null -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SupportingCard(state.weeklyRecordsError, isError = true)
                            OutlinedButton(onClick = onRetryWeekly) { Text("重试每周记录") }
                        }
                    }
                    else -> {
                        item {
                            SupportingCard(
                                TrustedMedicationReminderPolicy.confirmedRate(state.weeklyRecords).label,
                            )
                        }
                        items(state.weeklyRecords.asReversed(), key = { it.local_date }) { summary ->
                            WeeklyMedicationSummaryCard(summary)
                        }
                    }
                }
            }
            MedicationRecordScope.Course -> {
                item {
                    SupportingCard(
                        "当前服务端未提供完整疗程聚合，不能计算疗程已确认率。以下只展示已确认疗程日期、预计余量和续配证据状态。",
                    )
                }
                if (state.plans.isEmpty()) {
                    item { SupportingCard("暂无可信用药计划。") }
                } else {
                    items(state.plans, key = { "course-${it.plan_id}" }) { plan ->
                        MedicationCourseRecordCard(plan)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MedicationRecordTaskCard(
    task: MedicationTodayTask,
    allowCorrection: Boolean,
    operationInProgress: Boolean,
    onCorrect: (String) -> Unit,
) {
    OutlinedCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${task.scheduled_time} · ${task.generic_name}", fontWeight = FontWeight.SemiBold)
            Text(MedicationTrustPolicy.taskStatusLabel(task))
            task.confirmed_at?.let { Text("实际确认：${friendlyDateTime(it)}", style = MaterialTheme.typography.bodySmall) }
            if (task.status == "skipped") {
                Text(
                    "跳过原因：当前服务端今日记录接口未返回原因字段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (allowCorrection) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onCorrect("pending") }, enabled = !operationInProgress) {
                        Text("修正为待确认")
                    }
                    TextButton(
                        onClick = { onCorrect(if (task.status == "taken") "skipped" else "taken") },
                        enabled = !operationInProgress,
                    ) {
                        Text(if (task.status == "taken") "改为本次跳过" else "改为已服用")
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyMedicationSummaryCard(summary: MedicationTodaySummary) {
    OutlinedCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(summary.local_date, fontWeight = FontWeight.SemiBold)
            Text(TrustedMedicationReminderPolicy.confirmedRate(listOf(summary)).label)
            Text(
                "已服 ${summary.taken_count} · 跳过 ${summary.skipped_count} · " +
                    "待确认 ${summary.awaiting_confirmation_count} · 可能漏服 ${summary.possibly_missed_count}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MedicationCourseRecordCard(plan: TrustedMedicationPlan) {
    val course = TrustedMedicationReminderPolicy.coursePresentation(plan)
    OutlinedCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(plan.generic_name, fontWeight = FontWeight.SemiBold)
            Text("疗程：${course.dateRange}")
            Text("已进行：${course.elapsedDays?.let { "$it 天" } ?: "暂不可用"}")
            Text(MedicationTrustPolicy.inventoryLine(plan), style = MaterialTheme.typography.bodySmall)
            Text(course.refillEligibility, style = MaterialTheme.typography.bodySmall)
            Text(course.confirmedRate, style = MaterialTheme.typography.bodySmall)
            if (course.endingSoon) Text("疗程将在 7 天内结束", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PlanEditor(
    modifier: Modifier,
    draft: MedicationPlanDraft,
    onDraftChange: (MedicationPlanDraft) -> Unit,
    candidate: MedicationPrefillCandidate?,
    operationInProgress: Boolean,
    dismissKeyboardOnScroll: NestedScrollConnection,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .nestedScroll(dismissKeyboardOnScroll)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (candidate != null) {
            SupportingCard("这是未确认的识别结果。请逐项检查，确认后才会创建可信用药计划。")
            if (candidate.low_confidence_fields.isNotEmpty()) {
                SupportingCard(
                    "低置信度字段：${candidate.low_confidence_fields.joinToString("、")}。这些字段必须重点核对。",
                    isError = true,
                )
            }
        } else {
            SupportingCard("手动信息只有在你点击确认后才成为可信用药计划。")
        }
        PlanTextField("药品通用名 *", draft.genericName) { onDraftChange(draft.copy(genericName = it)) }
        PlanTextField("商品名", draft.brandName) { onDraftChange(draft.copy(brandName = it)) }
        PlanTextField("规格（如 20mg/片）", draft.strength) { onDraftChange(draft.copy(strength = it)) }
        PlanTextField("单次剂量（如 1 片）", draft.doseText) { onDraftChange(draft.copy(doseText = it)) }
        PlanTextField("单次数量（用于余量估算）", draft.doseQuantity) { onDraftChange(draft.copy(doseQuantity = it)) }
        PlanTextField("频次（如 每日 1 次）", draft.frequency) { onDraftChange(draft.copy(frequency = it)) }
        PlanTextField("服药时间（如 08:00、20:00）", draft.scheduleTimes) { onDraftChange(draft.copy(scheduleTimes = it)) }
        Text("与进餐关系", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "unspecified" to "未指定",
                "before_meal" to "饭前",
                "after_meal" to "饭后",
                "with_meal" to "随餐",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = draft.mealRelation == value,
                    onClick = { onDraftChange(draft.copy(mealRelation = value)) },
                    label = { Text(label) },
                )
            }
        }
        OutlinedTextField(
            value = draft.instructions,
            onValueChange = { onDraftChange(draft.copy(instructions = it)) },
            label = { Text("服用说明") },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        )
        PlanTextField("开始日期 YYYY-MM-DD", draft.courseStart) { onDraftChange(draft.copy(courseStart = it)) }
        PlanTextField("结束日期 YYYY-MM-DD", draft.courseEnd) { onDraftChange(draft.copy(courseEnd = it)) }
        PlanTextField("处方医生 / 来源", draft.prescriber) { onDraftChange(draft.copy(prescriber = it)) }
        PlanTextField("初始药量", draft.initialQuantity) { onDraftChange(draft.copy(initialQuantity = it)) }
        PlanTextField("药量单位（如 片）", draft.inventoryUnit) { onDraftChange(draft.copy(inventoryUnit = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = draft.isLongTerm, onCheckedChange = { onDraftChange(draft.copy(isLongTerm = it)) })
            Text("长期用药")
        }
        SupportingCard("提醒默认关闭，确认计划不会自动申请通知权限或创建系统提醒。")
        Button(
            onClick = onConfirm,
            enabled = draft.genericName.isNotBlank() && !operationInProgress,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (operationInProgress) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("确认并创建计划")
        }
        TextButton(onClick = onCancel, enabled = !operationInProgress, modifier = Modifier.fillMaxWidth()) {
            Text("取消")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RawOcrEditor(
    modifier: Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    operationInProgress: Boolean,
    dismissKeyboardOnScroll: NestedScrollConnection,
    onCancel: () -> Unit,
    onRecognize: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .nestedScroll(dismissKeyboardOnScroll)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SupportingCard(
            "当前版本没有接入相机。请粘贴处方或药盒已经 OCR 得到的原始文字；识别只生成待复核信息，不会自动创建计划。",
        )
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("OCR 原始文字") },
            minLines = 8,
            maxLines = 16,
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
        )
        Button(
            onClick = onRecognize,
            enabled = text.isNotBlank() && !operationInProgress,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (operationInProgress) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("解析为待复核信息")
        }
        TextButton(onClick = onCancel, enabled = !operationInProgress, modifier = Modifier.fillMaxWidth()) {
            Text("取消")
        }
    }
}

@Composable
private fun ReactionEditor(
    modifier: Modifier,
    draft: MedicationReactionDraft,
    onDraftChange: (MedicationReactionDraft) -> Unit,
    plans: List<TrustedMedicationPlan>,
    operationInProgress: Boolean,
    dismissKeyboardOnScroll: NestedScrollConnection,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .nestedScroll(dismissKeyboardOnScroll)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SupportingCard("这里只记录症状与服药时间接近，不能据此认定症状由药物导致。")
        Text("关联用药", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            plans.forEach { plan ->
                FilterChip(
                    selected = draft.planId == plan.plan_id,
                    onClick = { onDraftChange(draft.copy(planId = plan.plan_id)) },
                    label = { Text(plan.generic_name) },
                )
            }
        }
        OutlinedTextField(
            value = draft.symptoms,
            onValueChange = { onDraftChange(draft.copy(symptoms = it)) },
            label = { Text("不适症状 *") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )
        Text("严重程度", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("mild" to "轻度", "moderate" to "中度", "severe" to "严重").forEach { (value, label) ->
                FilterChip(
                    selected = draft.severity == value,
                    onClick = { onDraftChange(draft.copy(severity = value)) },
                    label = { Text(label) },
                )
            }
        }
        if (draft.severity == "severe") {
            SupportingCard(
                "若出现呼吸困难、意识异常、严重胸痛或快速恶化，请立即联系医生、药师或当地急救服务。",
                isError = true,
            )
        }
        PlanTextField("持续时间（分钟）", draft.durationMinutes) {
            onDraftChange(draft.copy(durationMinutes = it))
        }
        OutlinedTextField(
            value = draft.notes,
            onValueChange = { onDraftChange(draft.copy(notes = it)) },
            label = { Text("备注") },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "出现时间将在提交时记录；你可以在备注中补充更准确的时间。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onConfirm,
            enabled = plans.isNotEmpty() && draft.planId != null && draft.symptoms.isNotBlank() && !operationInProgress,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (operationInProgress) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("确认记录不适")
        }
        TextButton(onClick = onCancel, enabled = !operationInProgress, modifier = Modifier.fillMaxWidth()) {
            Text("取消")
        }
    }
}

@Composable
private fun PlanTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun SupportingCard(text: String, isError: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
        ),
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun planStatus(status: String): String = when (status) {
    "active" -> "服用中"
    "paused" -> "已暂停"
    "completed" -> "已结束"
    "retracted" -> "已撤回"
    else -> "状态待核对"
}

private fun severityLabel(value: String): String = when (value) {
    "mild" -> "轻度"
    "moderate" -> "中度"
    "severe" -> "严重"
    else -> "待核对"
}

private fun mealRelationLabel(value: String): String = when (value) {
    "before_meal" -> "饭前"
    "after_meal" -> "饭后"
    "with_meal" -> "随餐"
    else -> "未指定进餐关系"
}

private fun medicationSourceLabel(value: String): String = when (value) {
    "manual" -> "用户手动确认"
    "prescription_import" -> "已确认处方"
    "ocr" -> "处方 / 药盒识别后确认"
    "history" -> "历史用药重新确认"
    else -> "来源待核对"
}

private fun reminderCadenceLabel(value: String): String = when (value) {
    TrustedMedicationReminderSettings.CADENCE_ALTERNATE_DAYS -> "隔日"
    else -> "每日"
}

private fun friendlyDateTime(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}.getOrElse { value.replace('T', ' ').take(16) }

private fun taskTimingDetail(task: MedicationTodayTask): String? = when (task.status) {
    "upcoming" -> runCatching {
        val duration = Duration.between(OffsetDateTime.now(), OffsetDateTime.parse(task.scheduled_at))
        val minutes = duration.toMinutes().coerceAtLeast(0)
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        if (hours > 0) "距离计划时间还有 $hours 小时 $remainingMinutes 分钟" else "距离计划时间还有 $minutes 分钟"
    }.getOrNull()
    "awaiting_confirmation", "possibly_missed" -> "计划时间：${friendlyDateTime(task.scheduled_at)}"
    "snoozed" -> task.snoozed_until?.let { "新的提醒时间：${friendlyDateTime(it)}" }
    "taken" -> task.confirmed_at?.let { "实际确认时间：${friendlyDateTime(it)}" }
    "skipped" -> task.confirmed_at?.let { "用户确认跳过时间：${friendlyDateTime(it)}" }
    else -> null
}
