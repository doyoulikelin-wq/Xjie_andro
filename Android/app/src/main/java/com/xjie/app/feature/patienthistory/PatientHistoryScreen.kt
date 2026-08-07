package com.xjie.app.feature.patienthistory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.HealthProfileGoal
import com.xjie.app.core.model.HealthProfileLongTermMedicationSummaryItem
import com.xjie.app.core.model.HealthProfileManagementPlan
import com.xjie.app.core.model.HealthProfileRevisionItem
import com.xjie.app.core.model.HealthProfileTrustCandidate
import com.xjie.app.core.model.HealthProfileTrustFact
import com.xjie.app.core.model.HealthProfileTrustOverview
import com.xjie.app.core.model.HealthProfileTrustProfile
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ProfileSection(
    val category: String,
    val title: String,
    val description: String,
)

private val profileSections = listOf(
    ProfileSection("basic", "基础资料", "逐项记录来源和更新时间；体重只读取服务端已确认事实，不能在此编辑。"),
    ProfileSection("long_term_health", "长期健康标签", "报告和趋势只能提出候选，不能静默改写长期事实。"),
    ProfileSection("safety", "安全信息", "修改或删除前都需要你的第二次确认。"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PatientHistoryScreen(
    onBack: () -> Unit,
    onOpenHealthDataFocus: (String) -> Unit,
    onOpenMedications: () -> Unit,
    onOpenHealthPlan: () -> Unit,
    vm: PatientHistoryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var showDiscard by remember { mutableStateOf(false) }
    var leaveAfterDiscard by remember { mutableStateOf(false) }

    fun dismissKeyboard() {
        focus.clearFocus(force = true)
        keyboard?.hide()
    }

    fun discardEditor() {
        vm.cancelFactEditing()
        vm.cancelGoalEditing()
    }

    fun requestBack() {
        dismissKeyboard()
        if (state.operationInProgress) return
        if (state.historyTarget != null) {
            vm.closeHistory()
        } else if (state.hasUnsavedEditor) {
            leaveAfterDiscard = true
            showDiscard = true
        } else {
            onBack()
        }
    }

    fun requestCancelEditor() {
        dismissKeyboard()
        if (state.operationInProgress) return
        if (state.hasUnsavedEditor) {
            leaveAfterDiscard = false
            showDiscard = true
        } else {
            discardEditor()
        }
    }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbar.showSnackbar(it); vm.clearToast() }
    }
    BackHandler(enabled = true, onBack = ::requestBack)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.historyTarget?.title ?: "健康画像") },
                navigationIcon = {
                    IconButton(onClick = ::requestBack, enabled = !state.operationInProgress) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { inner ->
        if (state.historyTarget != null) {
            RevisionHistoryContent(
                state = state,
                onLoadMore = vm::loadMoreHistory,
                modifier = Modifier.padding(inner),
            )
        } else {
            ProfileBody(
                state = state,
                vm = vm,
                onOpenHealthDataFocus = onOpenHealthDataFocus,
                onOpenMedications = onOpenMedications,
                onOpenHealthPlan = onOpenHealthPlan,
                dismissKeyboard = ::dismissKeyboard,
                onCancelEditor = ::requestCancelEditor,
                modifier = Modifier.padding(inner),
            )
        }
    }

    state.confirmation?.let { confirmation ->
        ConfirmationDialog(
            confirmation = confirmation,
            onConfirm = vm::confirmAction,
            onDismiss = vm::dismissConfirmation,
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = {
                showDiscard = false
                leaveAfterDiscard = false
            },
            icon = { Icon(Icons.Filled.WarningAmber, null) },
            title = { Text("放弃未保存的画像修改？") },
            text = { Text("你刚才修改的内容还没有保存。放弃后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val shouldLeave = leaveAfterDiscard
                        showDiscard = false
                        leaveAfterDiscard = false
                        discardEditor()
                        if (shouldLeave) onBack()
                    },
                ) { Text("放弃修改", color = XjiePalette.Danger) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscard = false
                        leaveAfterDiscard = false
                    },
                ) { Text("继续编辑") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileBody(
    state: PatientHistoryUiState,
    vm: PatientHistoryViewModel,
    onOpenHealthDataFocus: (String) -> Unit,
    onOpenMedications: () -> Unit,
    onOpenHealthPlan: () -> Unit,
    dismissKeyboard: () -> Unit,
    onCancelEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.phase) {
        HealthProfileScreenPhase.Loading -> CenteredState(
            modifier = modifier,
            tag = "healthProfile.loading",
        ) { CircularProgressIndicator() }

        HealthProfileScreenPhase.Error -> CenteredState(modifier, "healthProfile.error") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.loadError ?: "暂时无法读取健康画像")
                Button(onClick = vm::load, modifier = Modifier.heightIn(min = 48.dp)) { Text("重新读取") }
            }
        }

        HealthProfileScreenPhase.Empty -> CenteredState(modifier, "healthProfile.empty") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("尚未建立健康画像")
                Button(onClick = vm::load, modifier = Modifier.heightIn(min = 48.dp)) { Text("读取健康画像") }
            }
        }

        HealthProfileScreenPhase.Ready -> {
            val profile = requireNotNull(state.profile)
            val candidates = remember { BringIntoViewRequester() }
            val facts = remember { BringIntoViewRequester() }
            val goals = remember { BringIntoViewRequester() }
            val editor = remember { BringIntoViewRequester() }
            val scope = rememberCoroutineScope()
            val dismissOnScroll = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y != 0f) dismissKeyboard()
                        return Offset.Zero
                    }
                }
            }

            fun bringIntoView(requester: BringIntoViewRequester) {
                scope.launch { delay(60); requester.bringIntoView() }
            }

            Column(
                modifier
                    .fillMaxSize()
                    .imePadding()
                    .nestedScroll(dismissOnScroll)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("healthProfile.root"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.operationInProgress) LinearProgressIndicator(Modifier.fillMaxWidth())
                ProfileOverviewCard(
                    overview = profile.overview,
                    enabled = !state.operationInProgress && !state.hasPendingRetry,
                    onPrimaryAction = {
                        dismissKeyboard()
                        val action = profile.overview.primary_action ?: return@ProfileOverviewCard
                        when (action.kind to action.route) {
                            "review_updates" to "profile_updates" -> bringIntoView(candidates)
                            "complete_profile" to "profile_safety_editor" -> {
                                val missing = profile.overview.missing_required_fact_keys
                                    .mapNotNull(HealthProfileTrustPolicy::field)
                                    .firstOrNull { it.category == "safety" }
                                if (missing != null) vm.startEditing(missing)
                                bringIntoView(if (missing != null) editor else facts)
                            }
                            "complete_profile" to "profile_editor" -> {
                                if (profile.overview.missing_required_fact_keys == listOf("goal.primary")) {
                                    bringIntoView(goals)
                                } else {
                                    bringIntoView(facts)
                                }
                            }
                            "edit_profile" to "profile_editor" -> bringIntoView(facts)
                            else -> Unit
                        }
                    },
                )
                PendingRetryCard(state, vm)
                ProfileUseBoundaryCard()
                if (profile.candidates.isNotEmpty()) {
                    Box(Modifier.bringIntoViewRequester(candidates)) {
                        CandidateSection(
                            candidates = profile.candidates,
                            enabled = !state.operationInProgress && !state.hasPendingRetry,
                            onReview = vm::requestCandidateReview,
                            onOpenReports = { onOpenHealthDataFocus("exams") },
                        )
                    }
                }
                MissingInformationCard(profile)
                Column(
                    Modifier.bringIntoViewRequester(facts),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    profileSections.forEach { section ->
                        ProfileSectionCard(
                            section = section,
                            profile = profile,
                            state = state,
                            vm = vm,
                            dismissKeyboard = dismissKeyboard,
                            onCancelEditor = onCancelEditor,
                            editorRequester = editor,
                        )
                    }
                    ConfirmedFactsCard(profile.facts, state, vm)
                }
                MedicationSummarySection(
                    items = state.longTermMedications,
                    loading = state.medicationSummaryLoading,
                    error = state.medicationSummaryError,
                    onOpenMedications = onOpenMedications,
                )
                Box(Modifier.bringIntoViewRequester(goals)) {
                    GoalsAndPlansSection(
                        profile = profile,
                        state = state,
                        vm = vm,
                        dismissKeyboard = dismissKeyboard,
                        onCancelEditor = onCancelEditor,
                        onOpenHealthPlan = onOpenHealthPlan,
                    )
                }
                if (profile.facts.isEmpty() && profile.candidates.isEmpty() &&
                    profile.goals.isEmpty() && profile.management_plans.isEmpty()
                ) {
                    EmptyProfileContentCard()
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CenteredState(
    modifier: Modifier,
    tag: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.fillMaxSize().testTag(tag),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ProfileOverviewCard(
    overview: HealthProfileTrustOverview,
    enabled: Boolean,
    onPrimaryAction: () -> Unit,
) {
    val action = overview.primary_action
    val supported = HealthProfileTrustPolicy.isSupportedPrimaryAction(action)
    Surface(
        modifier = Modifier.cardStyle().testTag("healthProfile.overview"),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("持续更新的个人健康模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileMetric("画像完整度", "${overview.completeness_percent}%", Modifier.weight(1f))
                ProfileMetric("待确认更新", "${overview.pending_update_count} 项", Modifier.weight(1f))
                ProfileMetric("独立来源", "${overview.independent_source_count} 个", Modifier.weight(1f))
            }
            Text(
                "完整度只表示资料是否已处理，不评价健康好坏；明确没有、不适用和暂不回答都由服务端计为已处理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                HealthProfileTrustPolicy.primaryActionStatus(action),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (action?.kind == "review_updates") MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = onPrimaryAction,
                enabled = enabled && supported,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("healthProfile.primaryAction"),
            ) { Text(HealthProfileTrustPolicy.primaryActionTitle(action)) }
        }
    }
}

@Composable
private fun ProfileMetric(title: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PendingRetryCard(state: PatientHistoryUiState, vm: PatientHistoryViewModel) {
    if (state.pendingMutationLabel == null) return
    Surface(modifier = Modifier.cardStyle(), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${state.pendingMutationLabel}结果尚未确认", fontWeight = FontWeight.SemiBold)
            Text("重试会复用同一 client_event_id，避免重复写入。", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = vm::retryPendingMutation,
                enabled = state.hasPendingRetry,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("healthProfile.retry"),
            ) { Text(if (state.operationInProgress) "正在重试…" else "重试上次修改") }
        }
    }
}

@Composable
private fun ProfileUseBoundaryCard() {
    Surface(modifier = Modifier.cardStyle(), color = Color.Transparent) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("画像使用范围", fontWeight = FontWeight.SemiBold)
                Text(
                    "已确认事实可用于健康问答、建议、风险提示和长期趋势解释；候选更新不会进入这些场景。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "X年龄暂不消费健康画像；待服务端评分版本和验证契约完成后再接入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("healthProfile.xage.notConsumed"),
                )
            }
        }
    }
}

@Composable
private fun CandidateSection(
    candidates: List<HealthProfileTrustCandidate>,
    enabled: Boolean,
    onReview: (Long, String) -> Unit,
    onOpenReports: () -> Unit,
) {
    Surface(modifier = Modifier.cardStyle().testTag("healthProfile.candidates"), color = Color.Transparent) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("待确认更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "报告或 AI 只能提出候选。候选不会自动成为事实，每次接受或忽略都需要你的明确确认。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            candidates.forEach { candidate ->
                CandidateCard(candidate, enabled, onReview, onOpenReports)
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: HealthProfileTrustCandidate,
    enabled: Boolean,
    onReview: (Long, String) -> Unit,
    onOpenReports: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(6.dp))
                Text(
                    HealthProfileTrustPolicy.field(candidate.fact_key)?.title
                        ?: HealthProfileTrustPolicy.candidateLabel(candidate),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text("v${candidate.version}", style = MaterialTheme.typography.labelSmall)
            }
            Text(HealthProfileTrustPolicy.displayValue(candidate.proposed_value))
            Text(
                "独立报告来源 ${HealthProfileTrustPolicy.independentReportCount(candidate)} 个",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val neverAccept = candidate.category == "goal" ||
                candidate.category == "safety" || candidate.is_safety_critical
            if (neverAccept) {
                Text(
                    if (candidate.category == "goal") {
                        "健康目标只能由你主动创建或调整，不能接受 AI / 报告候选。"
                    } else {
                        "安全候选不能直接接受，请由你手动填写并再次确认。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!neverAccept) {
                    Button(
                        onClick = { onReview(candidate.candidate_id, "accept") },
                        enabled = enabled && HealthProfileTrustPolicy.canReviewCandidate(candidate, "accept"),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .testTag("healthProfile.candidate.${candidate.candidate_id}.accept"),
                    ) { Text("确认到画像") }
                }
                OutlinedButton(
                    onClick = { onReview(candidate.candidate_id, "reject") },
                    enabled = enabled && HealthProfileTrustPolicy.canReviewCandidate(candidate, "reject"),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("healthProfile.candidate.${candidate.candidate_id}.reject"),
                ) { Text("忽略") }
            }
            TextButton(onClick = onOpenReports, enabled = enabled) {
                Icon(Icons.Filled.Source, null)
                Spacer(Modifier.width(4.dp))
                Text("查看报告来源")
            }
        }
    }
}

@Composable
private fun MissingInformationCard(profile: HealthProfileTrustProfile) {
    if (profile.overview.missing_required_fact_keys.isEmpty()) return
    Surface(modifier = Modifier.cardStyle().testTag("healthProfile.missing"), color = Color.Transparent) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("待完善资料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            profile.overview.missing_required_fact_keys.forEach { key ->
                Text("• ${HealthProfileTrustPolicy.field(key)?.title ?: key}")
            }
            Text(
                "缺失项和完整度均来自服务端，客户端不会根据本地列表重算。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileSectionCard(
    section: ProfileSection,
    profile: HealthProfileTrustProfile,
    state: PatientHistoryUiState,
    vm: PatientHistoryViewModel,
    dismissKeyboard: () -> Unit,
    onCancelEditor: () -> Unit,
    editorRequester: BringIntoViewRequester,
) {
    val definitions = HealthProfileTrustPolicy.fields.filter { it.category == section.category }
    Surface(
        modifier = Modifier.cardStyle().testTag("healthProfile.module.${section.category}"),
        color = Color.Transparent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(section.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            definitions.forEach { definition ->
                val fact = profile.facts.firstOrNull { it.fact_key == definition.factKey }
                ProfileFactEditorCard(
                    definition = definition,
                    fact = fact,
                    state = state,
                    vm = vm,
                    dismissKeyboard = dismissKeyboard,
                    onCancelEditor = onCancelEditor,
                    modifier = if (state.factEditor?.definition?.factKey == definition.factKey) {
                        Modifier.bringIntoViewRequester(editorRequester)
                    } else {
                        Modifier
                    },
                )
            }
            if (section.category == "basic") DerivedBmiCard(HealthProfileTrustPolicy.derivedBmi(profile.facts))
        }
    }
}

@Composable
private fun ProfileFactEditorCard(
    definition: HealthProfileFieldDefinition,
    fact: HealthProfileTrustFact?,
    state: PatientHistoryUiState,
    vm: PatientHistoryViewModel,
    dismissKeyboard: () -> Unit,
    onCancelEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.factEditor?.takeIf { it.definition.factKey == definition.factKey }
    Surface(
        modifier = modifier.fillMaxWidth().testTag("healthProfile.edit.${definition.factKey}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(definition.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (definition.safetyCritical) {
                    Text("安全信息", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(definition.hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (draft != null) {
                Text("回答状态", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                ResponseStateChips(draft.responseState, vm::updateDraftResponseState)
                if (draft.responseState == HealthProfileResponseState.Value) {
                    OutlinedTextField(
                        value = draft.value,
                        onValueChange = vm::updateDraftValue,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                            .testTag("healthProfile.editor.value"),
                        minLines = 3,
                        maxLines = 8,
                        label = { Text("确认后的内容") },
                        enabled = !state.operationInProgress,
                    )
                } else {
                    Text(
                        "该回答会作为明确状态保存，不会被当作未回答。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { dismissKeyboard(); vm.requestSaveFact() },
                        enabled = draft.isDirty && !state.operationInProgress && !state.hasPendingRetry,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("healthProfile.editor.save"),
                    ) { Text("确认保存") }
                    OutlinedButton(
                        onClick = onCancelEditor,
                        enabled = !state.operationInProgress,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("取消") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            fact?.let { HealthProfileTrustPolicy.displayValue(it.value_data) } ?: "未回答",
                            color = if (fact == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        fact?.let { ProfileFactMetadata(it) }
                    }
                    IconButton(
                        onClick = { vm.startEditing(definition) },
                        enabled = !state.operationInProgress && !state.hasPendingRetry &&
                            state.factEditor == null && state.goalEditor == null,
                    ) { Icon(Icons.Filled.Edit, "编辑${definition.title}") }
                }
            }
            fact?.let { current ->
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { vm.openFactHistory(current) }, enabled = !state.operationInProgress) {
                        Icon(Icons.Filled.History, null)
                        Spacer(Modifier.width(4.dp))
                        Text("修订历史")
                    }
                    TextButton(
                        onClick = { dismissKeyboard(); vm.requestRetractFact(current) },
                        enabled = !state.operationInProgress && !state.hasPendingRetry,
                    ) {
                        Icon(Icons.Filled.DeleteOutline, null, tint = XjiePalette.Danger)
                        Spacer(Modifier.width(4.dp))
                        Text("删除当前信息", color = XjiePalette.Danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmedFactsCard(
    facts: List<HealthProfileTrustFact>,
    state: PatientHistoryUiState,
    vm: PatientHistoryViewModel,
) {
    if (facts.isEmpty()) return
    Surface(modifier = Modifier.cardStyle().testTag("healthProfile.facts"), color = Color.Transparent) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("已确认事实与来源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            facts.forEach { fact ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(HealthProfileTrustPolicy.field(fact.fact_key)?.title ?: fact.fact_key, fontWeight = FontWeight.SemiBold)
                        Text(HealthProfileTrustPolicy.displayValue(fact.value_data))
                        ProfileFactMetadata(fact)
                        TextButton(
                            onClick = { vm.openFactHistory(fact) },
                            enabled = !state.operationInProgress,
                            modifier = Modifier.testTag("healthProfile.fact.${fact.fact_id}.history"),
                        ) { Text("查看修订历史") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFactMetadata(fact: HealthProfileTrustFact) {
    val sources = fact.sources.distinctBy { it.source_type to it.source_ref }
    val confirmed = HealthProfileTrustPolicy.isServerConfirmed(fact)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (confirmed) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                null,
                tint = if (confirmed) XjiePalette.Success else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (confirmed) {
                    "已确认 · ${HealthProfileTrustPolicy.timestamp(fact.updated_at)} · ${HealthProfileTrustPolicy.sourceLabel(sources)}"
                } else {
                    "尚未确认，不用于 AI · ${HealthProfileTrustPolicy.sourceLabel(sources)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("当前版本 v${fact.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DerivedBmiCard(bmi: HealthProfileDerivedBmi) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("healthProfile.basic.derivedBMI"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BMI", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    bmi.value?.let { String.format("%.1f", it) } ?: "待补充",
                    fontWeight = FontWeight.Bold,
                    color = if (bmi.value == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
            }
            Text(bmi.sourceDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "更新：${HealthProfileTrustPolicy.timestamp(bmi.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MedicationSummarySection(
    items: List<HealthProfileLongTermMedicationSummaryItem>,
    loading: Boolean,
    error: String?,
    onOpenMedications: () -> Unit,
) {
    Surface(modifier = Modifier.cardStyle().testTag("healthProfile.medication"), color = Color.Transparent) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("长期用药摘要", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "这里只展示服务端已确认摘要；剂量、提醒和服药操作统一在用药记录管理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("正在同步长期用药摘要…")
                }
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                items.isEmpty() -> Text("暂无服务端已确认的长期用药摘要。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> items.forEach { MedicationSummaryCard(it) }
            }
            Button(
                onClick = onOpenMedications,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("healthProfile.medication.open"),
            ) { Text("查看当前用药") }
        }
    }
}

@Composable
private fun MedicationSummaryCard(item: HealthProfileLongTermMedicationSummaryItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HealthProfileTrustPolicy.medicationDisplayFields(item).forEach { field ->
                Text("${field.title}：${field.value}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun GoalsAndPlansSection(
    profile: HealthProfileTrustProfile,
    state: PatientHistoryUiState,
    vm: PatientHistoryViewModel,
    dismissKeyboard: () -> Unit,
    onCancelEditor: () -> Unit,
    onOpenHealthPlan: () -> Unit,
) {
    Surface(modifier = Modifier.cardStyle().testTag("healthProfile.goals"), color = Color.Transparent) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("健康目标与计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (state.goalEditor == null) {
                    OutlinedButton(
                        onClick = vm::beginCreatingGoal,
                        enabled = !state.operationInProgress && !state.hasPendingRetry && state.factEditor == null,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("healthProfile.goal.add"),
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Text("添加")
                    }
                }
            }
            Text(
                "目标只能由你主动创建；支持同时管理多个目标。AI 和报告候选不能自动替你设定。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("健康计划模块同步", fontWeight = FontWeight.SemiBold)
            if (profile.management_plans.isEmpty()) {
                Text("暂无进行中的健康计划。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                profile.management_plans.forEach { ManagementPlanCard(it) }
            }
            OutlinedButton(
                onClick = onOpenHealthPlan,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("healthProfile.managementPlan.open"),
            ) { Text("进入健康计划") }
            if (profile.goals.isEmpty() && state.goalEditor == null) {
                Text("尚未添加健康目标。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            profile.goals.forEach { goal -> GoalCard(goal, state, vm) }
            state.goalEditor?.let { draft ->
                GoalEditorCard(draft, state, vm, dismissKeyboard, onCancelEditor)
            }
        }
    }
}

@Composable
private fun ManagementPlanCard(plan: HealthProfileManagementPlan) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("healthProfile.managementPlan.${plan.plan_id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(plan.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${plan.completed_task_count}/${plan.task_count}")
            }
            plan.goal?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("${plan.start_date} 至 ${plan.end_date}", style = MaterialTheme.typography.labelSmall)
            Text("状态：${plan.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GoalCard(goal: HealthProfileGoal, state: PatientHistoryUiState, vm: PatientHistoryViewModel) {
    val status = HealthProfileTrustPolicy.goalStatus(goal.status)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("healthProfile.goal.${goal.goal_id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(goal.name, fontWeight = FontWeight.SemiBold)
                    Text(status?.label ?: "状态待确认", style = MaterialTheme.typography.labelSmall)
                }
                Text("v${goal.version}", style = MaterialTheme.typography.labelSmall)
            }
            Text("开始：${goal.started_on}", style = MaterialTheme.typography.bodySmall)
            Text(
                "关联指标：${goal.metrics.joinToString("、") { it.display_label?.takeIf(String::isNotBlank) ?: it.metric_key }}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "最近确认：${HealthProfileTrustPolicy.timestamp(goal.confirmed_at)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (goal.status != "archived") {
                    OutlinedButton(
                        onClick = { vm.beginEditingGoal(goal) },
                        enabled = !state.operationInProgress && !state.hasPendingRetry,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("healthProfile.goal.${goal.goal_id}.edit"),
                    ) { Text("编辑") }
                }
                OutlinedButton(
                    onClick = { vm.openGoalHistory(goal) },
                    enabled = !state.operationInProgress,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("healthProfile.goal.${goal.goal_id}.history"),
                ) { Text("历史") }
                if (status != null && status != HealthProfileGoalStatus.Archived) {
                    HealthProfileGoalAction.entries.filter {
                        HealthProfileTrustPolicy.allowsGoalAction(it, status)
                    }.forEach { action ->
                        OutlinedButton(
                            onClick = { vm.requestGoalStatus(goal, action) },
                            enabled = !state.operationInProgress && !state.hasPendingRetry,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(action.label) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalEditorCard(
    draft: HealthProfileGoalEditorDraft,
    state: PatientHistoryUiState,
    vm: PatientHistoryViewModel,
    dismissKeyboard: () -> Unit,
    onCancelEditor: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(if (draft.isCreating) "添加健康目标" else "编辑健康目标", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = draft.name,
            onValueChange = vm::updateGoalName,
            modifier = Modifier.fillMaxWidth().testTag("healthProfile.goal.editor.name"),
            label = { Text("目标名称") },
            singleLine = true,
            enabled = !state.operationInProgress,
        )
        OutlinedTextField(
            value = draft.startedOn,
            onValueChange = vm::updateGoalStartedOn,
            modifier = Modifier.fillMaxWidth().testTag("healthProfile.goal.editor.startedOn"),
            label = { Text("开始日期（YYYY-MM-DD）") },
            singleLine = true,
            enabled = !state.operationInProgress,
        )
        OutlinedTextField(
            value = draft.metricsText,
            onValueChange = vm::updateGoalMetrics,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                .testTag("healthProfile.goal.editor.metrics"),
            label = { Text("关联指标（逗号、顿号或换行分隔）") },
            minLines = 3,
            maxLines = 7,
            enabled = !state.operationInProgress,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCancelEditor,
                enabled = !state.operationInProgress,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("取消") }
            Button(
                onClick = { dismissKeyboard(); vm.saveGoal() },
                enabled = draft.isDirty && !state.operationInProgress && !state.hasPendingRetry,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .testTag("healthProfile.goal.editor.save"),
            ) { Text("保存目标") }
        }
    }
}

@Composable
private fun ResponseStateChips(
    selected: HealthProfileResponseState,
    onSelected: (HealthProfileResponseState) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HealthProfileResponseState.entries.forEach { item ->
            FilterChip(
                selected = selected == item,
                onClick = { onSelected(item) },
                label = { Text(item.label) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun EmptyProfileContentCard() {
    Surface(modifier = Modifier.cardStyle(), color = Color.Transparent) {
        Text(
            "服务端尚未返回已确认事实、候选、目标或计划。可以从缺失资料开始主动填写。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RevisionHistoryContent(
    state: PatientHistoryUiState,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            .testTag("healthProfile.history"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            state.historyLoading && state.revisionHistory == null -> CenteredState(
                Modifier.fillMaxWidth().heightIn(min = 180.dp),
                "healthProfile.history.loading",
            ) { CircularProgressIndicator() }
            state.historyError != null -> Text(
                state.historyError,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("healthProfile.history.error"),
            )
            state.revisionHistory?.items.isNullOrEmpty() -> Text(
                "暂无可展示的修订记录。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("healthProfile.history.empty"),
            )
            else -> state.revisionHistory?.items?.forEach { RevisionCard(it) }
        }
        if (state.revisionHistory?.next_after_revision_id != null) {
            OutlinedButton(
                onClick = onLoadMore,
                enabled = !state.historyLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("healthProfile.history.loadMore"),
            ) { Text(if (state.historyLoading) "正在加载…" else "加载更多") }
        }
    }
}

@Composable
private fun RevisionCard(revision: HealthProfileRevisionItem) {
    Surface(
        modifier = Modifier.cardStyle().testTag("healthProfile.history.revision.${revision.revision_id}"),
        color = Color.Transparent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(revisionEventTitle(revision.event_type), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("v${revision.target_version}")
            }
            Text(HealthProfileTrustPolicy.timestamp(revision.created_at), style = MaterialTheme.typography.labelSmall)
            if (revision.before_data.isNotEmpty()) {
                Text("修改前：${HealthProfileTrustPolicy.displayValue(revision.before_data)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (revision.after_data.isNotEmpty()) {
                Text("修改后：${HealthProfileTrustPolicy.displayValue(revision.after_data)}")
            }
        }
    }
}

private fun revisionEventTitle(raw: String): String = when (raw) {
    "created", "create" -> "已创建"
    "updated", "update" -> "已修改"
    "retracted", "retract" -> "已删除"
    "status_changed", "status" -> "状态已变更"
    else -> raw.replace('_', ' ')
}

@Composable
private fun ConfirmationDialog(
    confirmation: HealthProfileConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (confirmation) {
        is HealthProfileConfirmation.SaveSafety -> "确认安全信息"
        is HealthProfileConfirmation.RetractFact -> "确认删除画像事实"
        is HealthProfileConfirmation.ReviewCandidate -> if (confirmation.action == "accept") {
            "确认加入健康画像？"
        } else {
            "确认暂不加入？"
        }
        is HealthProfileConfirmation.ChangeGoalStatus -> "确认${confirmation.action.label}？"
    }
    val message = when (confirmation) {
        is HealthProfileConfirmation.SaveSafety ->
            "这项信息会影响用药、检查和健康建议。请再次确认内容准确；本次修改会保留版本记录。"
        is HealthProfileConfirmation.RetractFact ->
            "删除后不再作为当前健康事实使用，但服务端修订历史仍会保留。"
        is HealthProfileConfirmation.ReviewCandidate -> if (confirmation.action == "accept") {
            "只有确认后候选才会成为画像事实，并保留来源和版本。"
        } else {
            "候选会从待确认列表移除，现有画像事实不会被覆盖。"
        }
        is HealthProfileConfirmation.ChangeGoalStatus ->
            "目标状态变更会写入版本历史；归档后不能继续编辑。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.WarningAmber, null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        modifier = Modifier.testTag("healthProfile.confirmation"),
    )
}
