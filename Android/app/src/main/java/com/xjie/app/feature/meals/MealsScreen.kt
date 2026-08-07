package com.xjie.app.feature.meals

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.DietaryDailySummary
import com.xjie.app.core.model.DietaryBusinessDay
import com.xjie.app.core.model.DietaryDraftEditor
import com.xjie.app.core.model.DietaryEntrySource
import com.xjie.app.core.model.DietaryMealDraft
import com.xjie.app.core.model.DietaryMealRecord
import com.xjie.app.core.model.DietaryMealType
import com.xjie.app.core.model.DietaryRecordEditor
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val DietaryInk = Color(0xFF173B59)
private val DietaryMuted = Color(0xFF607B91)
private val DietaryBlue = Color(0xFF1675DB)
private val DietaryMint = Color(0xFF43D1B8)
private val DietaryOrange = Color(0xFFE27A22)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsScreen(
    onBack: (() -> Unit)? = null,
    vm: MealsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showSources by remember { mutableStateOf(false) }
    var textSource by remember { mutableStateOf(DietaryEntrySource.Text) }
    var showTextEntry by remember { mutableStateOf(false) }
    var draftText by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var deletingRecord by remember { mutableStateOf<DietaryMealRecord?>(null) }

    if (onBack != null) BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.fetchData() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(state.activeDraftEditor?.original?.draft_id) {
        if (state.activeDraftEditor != null) draftText = ""
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.createPhotoDraft(it, DietaryEntrySource.PhotoLibrary) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) vm.createPhotoDraft(uri, DietaryEntrySource.Camera)
    }

    fun launchCamera() {
        val directory = File(context.cacheDir, "dietary_photos").apply { mkdirs() }
        val file = File(directory, "dietary_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera() else coroutineScope.launch {
            snackbar.showSnackbar("需要相机权限才能拍照；也可改用相册或文字描述。")
        }
    }

    fun openCamera() {
        showSources = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    DietaryLiquidBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                DietaryPrimaryBar(
                    enabled = !state.mutating && !state.loading,
                    busy = state.mutating,
                    onClick = { showSources = true },
                )
            },
        ) { inner ->
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.fetchData(refresh = true) },
                modifier = Modifier.fillMaxSize().padding(inner),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DietaryHeader(onBack = onBack, onRefresh = { vm.fetchData(refresh = true) })
                    DateSwitcher(
                        dateKey = state.selectedDateKey,
                        canMoveForward = state.selectedDateKey < DietaryBusinessDay.dateKey(Instant.now()),
                        onPrevious = { vm.moveDate(-1) },
                        onNext = { vm.moveDate(1) },
                    )

                    when {
                        state.loading && state.dashboard == null -> LoadingCard()
                        state.loadState == DietaryLoadState.Error && state.dashboard == null ->
                            ErrorCard(state.error.orEmpty(), onRetry = vm::fetchData)
                        else -> {
                            OverviewCard(state)
                            SummaryCard(
                                summary = if (state.dashboard?.is_today == true) {
                                    state.dashboard?.displayed_summary
                                } else {
                                    state.dashboard?.selected_day_summary
                                },
                                title = if (state.dashboard?.is_today == true) "昨日饮食总结" else "当日饮食总结",
                                fallbackMessage = state.dailySummaryStatus?.message,
                            )
                            PendingDraftsCard(
                                drafts = state.pendingDrafts,
                                mutating = state.mutating,
                                onEdit = vm::activateDraft,
                                onRetry = vm::retryRecognition,
                            )
                            FormalRecordsCard(
                                records = state.records,
                                onEdit = vm::activateRecord,
                                onReuse = vm::reuseRecord,
                                onDelete = { deletingRecord = it },
                            )
                            RecentHistoryCard(state.recentRecords, onReuse = vm::reuseRecord)
                            CompletionCard(
                                pendingCount = state.dashboard?.pending_count ?: 0,
                                recordCount = state.dashboard?.recorded_meal_count ?: 0,
                                enabled = !state.mutating && state.dashboard != null,
                                onComplete = vm::completeSelectedDay,
                            )
                            DietarySafetyNote()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showSources) {
        EntrySourceDialog(
            onDismiss = { showSources = false },
            onCamera = ::openCamera,
            onGallery = {
                showSources = false
                galleryPicker.launch("image/*")
            },
            onTextSource = { source ->
                showSources = false
                textSource = source
                showTextEntry = true
            },
        )
    }

    if (showTextEntry) {
        TextDraftDialog(
            source = textSource,
            value = draftText,
            onValueChange = { draftText = it },
            onDismiss = { showTextEntry = false },
            onSubmit = {
                vm.createTextDraft(draftText, textSource)
                showTextEntry = false
            },
        )
    }

    state.activeDraftEditor?.let { editor ->
        DraftEditorDialog(editor = editor, vm = vm)
    }
    state.activeRecordEditor?.let { editor ->
        RecordEditorDialog(editor = editor, vm = vm, onDelete = { deletingRecord = editor.original })
    }
    deletingRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { deletingRecord = null },
            title = { Text("删除正式饮食记录？") },
            text = { Text("删除会携带当前版本 ${record.version}，版本已变化时服务端会拒绝。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteRecord(record); deletingRecord = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingRecord = null }) { Text("取消") } },
        )
    }

    if (state.error != null && state.dashboard != null) {
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("饮食记录未完成") },
            text = { Text(state.error.orEmpty()) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("知道了") } },
        )
    }
}

@Composable
private fun DietaryHeader(onBack: (() -> Unit)?, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(DietaryBlue, DietaryMint))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Restaurant, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f).semantics { heading() },
        ) {
            Text("饮食记录", color = DietaryInk, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("先识别为草稿，逐项确认后才正式入库", color = DietaryMuted, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新饮食记录")
        }
    }
}

@Composable
private fun DateSwitcher(
    dateKey: String,
    canMoveForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.ChevronLeft, "前一天")
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDate(dateKey), color = DietaryInk, fontWeight = FontWeight.Bold)
                Text("每日 04:00（Asia/Shanghai）切换饮食日", color = DietaryMuted, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onNext, enabled = canMoveForward, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.ChevronRight, "后一天")
            }
        }
    }
}

@Composable
private fun OverviewCard(state: MealsUiState) {
    val dashboard = state.dashboard
    GlassCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCell("正式记录", dashboard?.recorded_meal_count?.toString() ?: "0", Modifier.weight(1f))
            StatCell("待确认", dashboard?.pending_count?.toString() ?: "0", Modifier.weight(1f), DietaryOrange)
            StatCell("连续记录", "${dashboard?.streak_days ?: 0} 天", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when (dashboard?.day_state) {
                "waiting_confirmation" -> "仍有草稿待确认，未纳入正式总结"
                "ready" -> "当日已结束，显示服务端权威总结"
                "stale", "recalculating" -> "记录已变化，服务端正在重算总结"
                "failed" -> "总结生成失败，可保留记录后重试"
                else -> "当日仍开放，可继续添加并确认餐食"
            },
            color = DietaryMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier, accent: Color = DietaryBlue) {
    Surface(modifier = modifier.heightIn(min = 76.dp), shape = RoundedCornerShape(18.dp), color = accent.copy(alpha = 0.09f)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(value, color = accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, color = DietaryMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable private fun LoadingCard() = GlassCard {
    Row(Modifier.fillMaxWidth().heightIn(min = 140.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
        Spacer(Modifier.width(10.dp))
        Text("正在读取可信饮食记录…", color = DietaryMuted)
    }
}

@Composable private fun ErrorCard(message: String, onRetry: () -> Unit) = GlassCard {
    Column(Modifier.fillMaxWidth().heightIn(min = 140.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = DietaryMuted)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试") }
    }
}

@Composable
private fun SummaryCard(summary: DietaryDailySummary?, title: String, fallbackMessage: String?) {
    GlassCard {
        Text(title, color = DietaryInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (summary == null) {
            Text(fallbackMessage ?: "暂无可展示的服务端总结", color = DietaryMuted)
        } else {
            Text(summary.conclusion, color = DietaryInk, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = DietaryMint.copy(alpha = 0.1f)) {
                Text("今日建议：${summary.today_suggestion}", Modifier.padding(12.dp), color = DietaryInk)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "依据 ${summary.confirmed_meal_count} 条已确认记录 · 置信度 ${(summary.confidence * 100).toInt()}% · ${summary.rule_version}",
                color = DietaryMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun PendingDraftsCard(
    drafts: List<DietaryMealDraft>,
    mutating: Boolean,
    onEdit: (DietaryMealDraft) -> Unit,
    onRetry: (DietaryMealDraft) -> Unit,
) {
    GlassCard {
        SectionTitle(Icons.Filled.AutoAwesome, "待逐项确认", "${drafts.size} 项")
        if (drafts.isEmpty()) {
            EmptyLine("没有待确认草稿")
        } else drafts.forEachIndexed { index, draft ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    draft.food_items.joinToString("、") { it.name }.ifBlank { "识别未完成，请手动补充" },
                    color = DietaryInk,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${draft.source.title} · v${draft.version} · ${draft.mealType.title}",
                    color = DietaryMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (draft.low_confidence_fields.isNotEmpty()) {
                    Text("低置信字段：${draft.low_confidence_fields.joinToString("、")}", color = DietaryOrange, style = MaterialTheme.typography.labelSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onEdit(draft) }, enabled = !mutating, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("核对并确认")
                    }
                    if (draft.canRetryRecognition) {
                        TextButton(onClick = { onRetry(draft) }, enabled = !mutating, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("重新识别")
                        }
                    }
                }
            }
            if (index < drafts.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun FormalRecordsCard(
    records: List<DietaryMealRecord>,
    onEdit: (DietaryMealRecord) -> Unit,
    onReuse: (DietaryMealRecord) -> Unit,
    onDelete: (DietaryMealRecord) -> Unit,
) {
    GlassCard {
        SectionTitle(Icons.Filled.VerifiedUser, "已确认正式记录", "${records.size} 条")
        if (records.isEmpty()) {
            EmptyLine("本日暂无已确认餐食；识别草稿不会自动进入这里")
        } else records.forEachIndexed { index, record ->
            RecordRow(record, onEdit, onReuse, onDelete)
            if (index < records.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun RecordRow(
    record: DietaryMealRecord,
    onEdit: (DietaryMealRecord) -> Unit,
    onReuse: (DietaryMealRecord) -> Unit,
    onDelete: (DietaryMealRecord) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(record.mealType.title, color = DietaryBlue, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("v${record.version} · ${record.status}", color = DietaryMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text(record.foodSummary.ifBlank { "已确认餐食" }, color = DietaryInk, style = MaterialTheme.typography.bodyLarge)
        record.portion_text?.takeIf { it.isNotBlank() }?.let { Text(it, color = DietaryMuted) }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(onClick = { onEdit(record) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("编辑") }
            TextButton(onClick = { onReuse(record) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("复用为草稿") }
            TextButton(onClick = { onDelete(record) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RecentHistoryCard(records: List<DietaryMealRecord>, onReuse: (DietaryMealRecord) -> Unit) {
    GlassCard {
        SectionTitle(Icons.Filled.History, "最近餐食", "历史")
        if (records.isEmpty()) EmptyLine("暂无可复用的已确认历史餐食")
        else records.take(6).forEach { record ->
            Surface(onClick = { onReuse(record) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(record.foodSummary.ifBlank { record.mealType.title }, color = DietaryInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${record.diet_date} · 复用后仍需确认", color = DietaryMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(Icons.Filled.Add, contentDescription = "复用为待确认草稿", tint = DietaryBlue)
                }
            }
        }
    }
}

@Composable
private fun CompletionCard(pendingCount: Int, recordCount: Int, enabled: Boolean, onComplete: () -> Unit) {
    GlassCard {
        Text("结束当日记录", color = DietaryInk, fontWeight = FontWeight.Bold)
        Text(
            if (pendingCount > 0) "仍有 $pendingCount 项待确认；可仅以 $recordCount 条已确认记录结束，待确认项不会进入总结。"
            else "将以 $recordCount 条已确认记录生成服务端权威总结。",
            color = DietaryMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onComplete, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("按已确认记录结束当日")
        }
    }
}

@Composable
private fun DietarySafetyNote() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.VerifiedUser, null, tint = DietaryMuted, modifier = Modifier.size(18.dp))
        Text("识别结果只是候选；只有您逐项确认并提交后，才会成为正式饮食记录并参与总结。", Modifier.weight(1f), color = DietaryMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EntrySourceDialog(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onTextSource: (DietaryEntrySource) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加一餐") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SourceButton(Icons.Filled.CameraAlt, "拍照识别", onCamera)
                SourceButton(Icons.Filled.Image, "从相册选择", onGallery)
                SourceButton(Icons.Filled.TextFields, "文字描述") { onTextSource(DietaryEntrySource.Text) }
                SourceButton(Icons.Filled.Waves, "粘贴语音转写文本") { onTextSource(DietaryEntrySource.Voice) }
                SourceButton(Icons.Filled.ChatBubble, "粘贴问答草稿") { onTextSource(DietaryEntrySource.Chat) }
                Text("当前版本不伪装系统语音或聊天接入；语音/问答内容通过同一文字草稿契约提交。", color = DietaryMuted, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SourceButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(icon, null); Spacer(Modifier.width(10.dp)); Text(label, Modifier.weight(1f))
    }
}

@Composable
private fun TextDraftDialog(
    source: DietaryEntrySource,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(source.title) },
        text = {
            Column {
                if (source == DietaryEntrySource.Voice || source == DietaryEntrySource.Chat) {
                    Text("请粘贴已获得的文本；本页不会自行调用麦克风或读取聊天。", color = DietaryOrange, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.length <= 4_000) onValueChange(it) },
                    label = { Text("这餐吃了什么、份量大约多少") },
                    supportingText = { Text("${value.length}/4000；整段原文交由服务端识别，不会伪装成食物名") },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onSubmit, enabled = value.isNotBlank()) { Text("生成待确认草稿") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DraftEditorDialog(editor: DietaryDraftEditor, vm: MealsViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissDraftEditor,
        title = { Text("逐项确认识别草稿 · v${editor.original.version}") },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MealTypePicker(editor.mealType, vm::updateDraftMealType)
                editor.foodItems.forEachIndexed { index, item ->
                    FoodItemEditor(
                        index = index,
                        name = item.name,
                        portion = item.portion_text.orEmpty(),
                        confidence = item.confidence,
                        onName = { vm.updateDraftFood(index, name = it) },
                        onPortion = { vm.updateDraftFood(index, portion = it) },
                        onRemove = { vm.removeDraftFoodItem(index) },
                        canRemove = editor.foodItems.size > 1,
                    )
                }
                TextButton(onClick = vm::addDraftFoodItem, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("添加食物项")
                }
                Text("只有点击“确认并正式入库”才会产生正式记录。", color = DietaryOrange, style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = { Button(onClick = vm::confirmActiveDraft, enabled = editor.isValid) { Text("确认并正式入库") } },
        dismissButton = { TextButton(onClick = vm::dismissDraftEditor) { Text("继续保留草稿") } },
    )
}

@Composable
private fun RecordEditorDialog(editor: DietaryRecordEditor, vm: MealsViewModel, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = vm::dismissRecordEditor,
        title = { Text("编辑正式记录 · v${editor.original.version}") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("保存与删除都会携带 expected_version=${editor.original.version}。", color = DietaryMuted, style = MaterialTheme.typography.labelMedium)
                MealTypePicker(editor.mealType, vm::updateRecordMealType)
                editor.foodItems.forEachIndexed { index, item ->
                    FoodItemEditor(
                        index,
                        item.name,
                        item.portion_text.orEmpty(),
                        item.confidence,
                        onName = { vm.updateRecordFood(index, name = it) },
                        onPortion = { vm.updateRecordFood(index, portion = it) },
                        onRemove = {},
                        canRemove = false,
                    )
                }
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp)); Text("删除此版本", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { Button(onClick = vm::saveActiveRecord, enabled = editor.isValid) { Text("保存新版本") } },
        dismissButton = { TextButton(onClick = vm::dismissRecordEditor) { Text("取消") } },
    )
}

@Composable
private fun MealTypePicker(selected: DietaryMealType, onSelect: (DietaryMealType) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(DietaryMealType.Breakfast, DietaryMealType.Lunch, DietaryMealType.Dinner, DietaryMealType.Snack).forEach { type ->
            FilterChip(selected = selected == type, onClick = { onSelect(type) }, label = { Text(type.title) })
        }
    }
}

@Composable
private fun FoodItemEditor(
    index: Int,
    name: String,
    portion: String,
    confidence: Double?,
    onName: (String) -> Unit,
    onPortion: (String) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("食物 ${index + 1}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                confidence?.let {
                    Text("识别 ${(it * 100).toInt()}%", color = if (it < 0.7) DietaryOrange else DietaryMuted, style = MaterialTheme.typography.labelSmall)
                }
                if (canRemove) IconButton(onClick = onRemove, Modifier.size(48.dp)) { Icon(Icons.Filled.Close, "删除食物项") }
            }
            OutlinedTextField(value = name, onValueChange = onName, label = { Text("食物名称") }, singleLine = false, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = portion, onValueChange = onPortion, label = { Text("大致份量") }, singleLine = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DietaryPrimaryBar(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shadowElevation = 8.dp) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(DietaryBlue, DietaryMint))),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "正在准备可信草稿…" else "添加一餐", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, trailing: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = DietaryBlue, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(7.dp))
        Text(title, Modifier.weight(1f), color = DietaryInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(trailing, color = DietaryMuted, style = MaterialTheme.typography.labelMedium)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable private fun EmptyLine(text: String) = Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
    Text(text, color = DietaryMuted, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        shape = RoundedCornerShape(25.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
        shadowElevation = 4.dp,
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
private fun DietaryLiquidBackground(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        if (dark) Color(0xFF0B1D30) else Color(0xFFF4FBFF),
        if (dark) Color(0xFF102A38) else Color(0xFFE8F6F5),
    )))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(DietaryBlue.copy(alpha = 0.1f), size.minDimension * 0.55f, androidx.compose.ui.geometry.Offset(size.width, size.height * 0.1f))
            drawCircle(DietaryMint.copy(alpha = 0.12f), size.minDimension * 0.48f, androidx.compose.ui.geometry.Offset(0f, size.height * 0.72f))
        }
        content()
    }
}

private fun formatDate(key: String): String = runCatching {
    LocalDate.parse(key).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
}.getOrDefault(key)
