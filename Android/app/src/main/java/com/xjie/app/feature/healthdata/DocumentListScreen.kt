package com.xjie.app.feature.healthdata

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.HealthDocument
import com.xjie.app.core.model.HealthReportCandidate
import com.xjie.app.core.ui.components.EmptyState
import com.xjie.app.core.ui.components.LoadingIndicator
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    docType: String,
    title: String,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    vm: DocumentListViewModel = hiltViewModel(),
    uploadVm: HealthDataViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val reportUploadState by uploadVm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var qualityWarning by remember { mutableStateOf<String?>(null) }
    var showReportUploadSources by remember { mutableStateOf(false) }
    var pendingRecoveryAssetIndex by remember { mutableStateOf<Int?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { selected ->
            val name = selected.lastPathSegment ?: "file_${System.currentTimeMillis()}"
            handleHealthDocUri(
                context = context,
                uri = selected,
                filename = name,
                onAccepted = {
                    if (docType == "exam") {
                        uploadVm.setUploadDocType("exam")
                        uploadVm.uploadFile(selected, name, HealthReportUploadSource.Document)
                    } else {
                        vm.upload(docType, selected, name)
                    }
                },
                onError = { qualityWarning = it },
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            val name = "doc_${System.currentTimeMillis()}.jpg"
            handleHealthDocUri(
                context = context,
                uri = uri,
                filename = name,
                onAccepted = {
                    if (docType == "exam") {
                        uploadVm.setUploadDocType("exam")
                        uploadVm.uploadFile(uri, name, HealthReportUploadSource.Camera)
                    } else {
                        vm.upload(docType, uri, name)
                    }
                },
                onError = { qualityWarning = it },
            )
        }
    }

    val recoveryFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val assetIndex = pendingRecoveryAssetIndex
        pendingRecoveryAssetIndex = null
        if (uri != null && assetIndex != null) {
            val name = uri.lastPathSegment ?: "report-page-$assetIndex"
            handleHealthDocUri(
                context = context,
                uri = uri,
                filename = name,
                onAccepted = {
                    uploadVm.recoverReportFile(uri, name, assetIndex)
                },
                onError = { qualityWarning = it },
            )
        }
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "health_docs").apply { mkdirs() }
        val file = File(dir, "doc_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera()
        else qualityWarning = "需要相机权限才能拍照。请到系统设置开启。"
    }

    LaunchedEffect(docType) {
        if (DocumentListDataPolicy.usesLegacyDocuments(docType)) {
            vm.fetch(docType)
        } else {
            pendingDeleteId = null
            uploadVm.refreshReports()
        }
    }
    LaunchedEffect(state.error, docType) {
        if (DocumentListDataPolicy.usesLegacyDocuments(docType)) {
            state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
        }
    }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbar.showSnackbar(it); vm.clearToast() }
    }
    LaunchedEffect(reportUploadState.error) {
        reportUploadState.error?.let { snackbar.showSnackbar(it); uploadVm.clearError() }
    }
    LaunchedEffect(reportUploadState.toast) {
        reportUploadState.toast?.let { snackbar.showSnackbar(it); uploadVm.clearToast() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            if (docType == "record") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("就医助手", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "上传门诊记录、出院小结等就医资料后，可在列表和详情中查看原件与资料整理结果。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "仅整理你上传的资料，不替代医生诊断、审方或安排随访。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (docType == "exam") {
                HealthReportDashboardContent(
                    state = reportUploadState.reportDashboard,
                    onUpload = { showReportUploadSources = true },
                    onOpenReport = { workflowId ->
                        onItemClick(HealthReportHistoryDestination.encode(workflowId))
                    },
                    onRefresh = uploadVm::refreshReports,
                    onRecoverAsset = { assetIndex ->
                        pendingRecoveryAssetIndex = assetIndex
                        recoveryFilePicker.launch("*/*")
                    },
                    onAbandonRecovery = uploadVm::abandonReportRecovery,
                )
            } else {
                OutlinedButton(
                    onClick = { filePicker.launch("*/*") },
                    enabled = !state.uploading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Filled.CloudUpload, null)
                    Spacer(Modifier.width(6.dp))
                    Text("选择就医资料（文件）")
                }
                OutlinedButton(
                    onClick = {
                        val perm = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        )
                        if (perm == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            launchCamera()
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    enabled = !state.uploading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Filled.CameraAlt, null)
                    Spacer(Modifier.width(6.dp))
                    Text("拍照添加就医资料")
                }
            }

            if (DocumentListDataPolicy.usesLegacyDocuments(docType)) {
                val visibleDocuments = DocumentListDataPolicy.visibleLegacyDocuments(
                    docType = docType,
                    items = state.items,
                )
                when {
                    state.loading -> LoadingIndicator()
                    visibleDocuments.isEmpty() ->
                        EmptyState(
                            "暂无就医资料",
                            description = "可通过上方按钮添加真实资料",
                        )
                    else -> visibleDocuments.forEach { doc ->
                        DocumentRow(
                            doc, onClick = { onItemClick(doc.id) },
                            onDelete = { pendingDeleteId = doc.id },
                        )
                    }
                }
            }
        }
    }

    pendingDeleteId
        ?.takeIf { DocumentListDataPolicy.canDeleteLegacyDocument(docType) }
        ?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("确认删除") },
            text = { Text("删除后无法恢复，确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(id, docType)
                    pendingDeleteId = null
                }) { Text("删除", color = XjiePalette.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            },
        )
    }

    qualityWarning?.let { msg ->
        AlertDialog(
            onDismissRequest = { qualityWarning = null },
            title = { Text("照片质量提示") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { qualityWarning = null }) { Text("我知道了") } },
        )
    }

    if (showReportUploadSources) {
        AlertDialog(
            onDismissRequest = { showReportUploadSources = false },
            title = { Text("上传新报告") },
            text = { Text("选择拍照，或从相册、文件中选择清晰完整的报告原件。") },
            confirmButton = {
                TextButton(onClick = {
                    showReportUploadSources = false
                    filePicker.launch("*/*")
                }) { Text("相册或文件") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReportUploadSources = false
                    val permission = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    )
                    if (permission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        launchCamera()
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }) { Text("拍照") }
            },
        )
    }
}

internal object DocumentListDataPolicy {
    fun usesLegacyDocuments(docType: String): Boolean = docType == "record"

    fun visibleLegacyDocuments(
        docType: String,
        items: List<HealthDocument>,
    ): List<HealthDocument> = if (usesLegacyDocuments(docType)) items else emptyList()

    fun canDeleteLegacyDocument(docType: String): Boolean = usesLegacyDocuments(docType)
}

/**
 * 校验文件大小与图片尺寸，过小则拒绝上传，避免拍错/滥用。
 * - 文件 < 30KB：拒绝（几乎肯定是占位图或纯色图）
 * - 图片最短边 < 600px：拒绝（OCR 识别率过低）
 */
private fun handleHealthDocUri(
    context: android.content.Context,
    uri: Uri,
    filename: String,
    onAccepted: () -> Unit,
    onError: (String) -> Unit,
) {
    try {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: ""
        val size = runCatching {
            cr.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (size in 1 until 30_000L) {
            onError("文件过小（${size / 1024}KB），可能不是有效的体检/病例文件。请重新选择清晰完整的 PDF 或图片。")
            return
        }
        if (mime.startsWith("image/")) {
            val opts = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            val w = opts.outWidth
            val h = opts.outHeight
            val short = minOf(w, h)
            if (short in 1 until 600) {
                onError("照片分辨率过低（${w}×${h}），无法识别。请使用清晰、对焦良好的照片。")
                return
            }
        }
        onAccepted()
    } catch (e: Throwable) {
        onError("读取照片失败：${e.message ?: "未知错误"}")
    }
}

@Composable
private fun DocumentRow(
    doc: HealthDocument,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.cardStyle(),
        color = Color.Transparent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    doc.name?.takeIf { it.isNotBlank() } ?: "未命名${doc.reportTypeLabel()}",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    doc.reportHistoryMetadata(),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    ReportTrustPresentation.title(doc),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ReportTrustPresentation.isAdmitted(doc)) {
                        XjiePalette.Success
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
                if (!doc.ai_brief.isNullOrBlank()) {
                    Text(doc.ai_brief!!,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, null,
                    tint = XjiePalette.Danger.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    docId: String,
    onBack: () -> Unit,
    vm: DocumentDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var showManualCandidateForm by remember(docId) { mutableStateOf(false) }
    var manualCandidateDraft by remember(docId) { mutableStateOf(ManualReportCandidateDraft()) }
    var showDiscardManualCandidate by remember { mutableStateOf(false) }
    var showingInterpretation by remember(docId) { mutableStateOf(false) }
    val context = LocalContext.current
    val hasUnsavedDrafts = ReportReviewPolicy.hasUnsavedDrafts(state.decisions) ||
        manualCandidateDraft.isDirty

    fun requestBack() {
        focusManager.clearFocus(force = true)
        if (showingInterpretation) {
            showingInterpretation = false
            return
        }
        val mutationInFlight = state.confirming || state.addingManualCandidate
        if (hasUnsavedDrafts && !mutationInFlight) {
            showDiscardConfirmation = true
        } else if (!mutationInFlight) {
            onBack()
        }
    }

    BackHandler { requestBack() }
    LaunchedEffect(docId) { vm.fetch(docId) }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) focusManager.clearFocus(force = true)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            vm.clearNotice()
        }
    }
    LaunchedEffect(state.originalFileUri) {
        state.originalFileUri?.let { uri ->
            val originalName = state.interpretation?.document?.name ?: state.doc?.name.orEmpty()
            val mime = if (originalName.endsWith(".pdf", ignoreCase = true)) {
                "application/pdf"
            } else {
                "image/*"
            }
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )
            } catch (_: ActivityNotFoundException) {
                snackbar.showSnackbar("没有可打开该原件格式的应用")
            } finally {
                vm.consumeOriginalFile()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (showingInterpretation) "本次报告解读" else "详情") },
                navigationIcon = {
                    IconButton(
                        onClick = ::requestBack,
                        enabled = !state.confirming && !state.addingManualCandidate,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { inner ->
        when {
            state.loading -> Box(Modifier.padding(inner).fillMaxSize(),
                contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.doc != null || state.review != null -> {
                val doc = state.doc
                Column(
                    Modifier
                        .padding(inner).fillMaxSize()
                        .verticalScroll(scrollState)
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showingInterpretation) {
                        ReportInterpretationContent(
                            state = state,
                            onRetry = { vm.loadInterpretation(force = true) },
                            onOpenOriginal = vm::prepareOriginalDocument,
                        )
                    } else {
                    if (doc != null) {
                        Column(Modifier.cardStyle()) {
                            Text(doc.name ?: "详情",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text(doc.reportHistoryMetadata(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                ReportTrustPresentation.title(doc),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (ReportTrustPresentation.isAdmitted(doc)) {
                                    XjiePalette.Success
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                            )
                            Text(
                                ReportTrustPresentation.nextStep(doc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Column(Modifier.cardStyle()) {
                            Text(
                                "健康报告",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "按报告任务读取的权威记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ReportReviewSection(
                        state = state,
                        vm = vm,
                        showManualCandidateForm = showManualCandidateForm,
                        manualCandidateDraft = manualCandidateDraft,
                        onShowManualCandidateForm = { showManualCandidateForm = true },
                        onManualCandidateDraftChange = { manualCandidateDraft = it },
                        onCloseManualCandidateForm = {
                            if (manualCandidateDraft.isDirty) {
                                showDiscardManualCandidate = true
                            } else {
                                showManualCandidateForm = false
                            }
                        },
                        onManualCandidateAdded = {
                            manualCandidateDraft = ManualReportCandidateDraft()
                            showManualCandidateForm = false
                        },
                        onOpenInterpretation = {
                            showingInterpretation = true
                            vm.loadInterpretation()
                        },
                    )
                    if (state.authoritativeWorkflowId != null || !doc?.file_url.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = vm::prepareOriginalDocument,
                            enabled = !state.originalLoading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            if (state.originalLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.originalLoading) "正在读取原件…" else "查看报告原件")
                        }
                    }
                    if (doc != null &&
                        !ReportTrustPresentation.isAdmitted(doc) &&
                        !doc.ai_summary.isNullOrBlank()
                    ) {
                        Column(Modifier.cardStyle(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "历史识别草稿（尚未验证）",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Text(doc.ai_summary!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    doc?.csv_data?.let { csv ->
                        val cols = csv.columns.orEmpty()
                        val rows = csv.rows.orEmpty()
                        if (cols.isNotEmpty() && rows.isNotEmpty()) {
                            CsvTable(
                                columns = cols,
                                rows = rows,
                                title = if (ReportTrustPresentation.isAdmitted(doc)) {
                                    "已确认结构化数据"
                                } else {
                                    "识别候选（尚未入库）"
                                },
                            )
                        }
                    }
                    }
                }
            }
            else -> Box(Modifier.padding(inner).fillMaxSize(),
                contentAlignment = Alignment.Center) {
                Text(state.error ?: "未找到")
            }
        }
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("放弃未提交的复核？") },
            text = { Text("你的复核选择或手动补录还没有提交。离开后这些草稿会丢失。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        onBack()
                    },
                ) {
                    Text("放弃并离开", color = XjiePalette.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text("继续复核")
                }
            },
        )
    }

    if (showDiscardManualCandidate) {
        AlertDialog(
            onDismissRequest = { showDiscardManualCandidate = false },
            title = { Text("放弃手动补录？") },
            text = { Text("尚未添加到待确认列表的内容会丢失。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        manualCandidateDraft = ManualReportCandidateDraft()
                        showManualCandidateForm = false
                        showDiscardManualCandidate = false
                    },
                ) { Text("放弃", color = XjiePalette.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardManualCandidate = false }) { Text("继续填写") }
            },
        )
    }
}

@Composable
private fun ReportReviewSection(
    state: DocumentDetailViewModel.UiState,
    vm: DocumentDetailViewModel,
    showManualCandidateForm: Boolean,
    manualCandidateDraft: ManualReportCandidateDraft,
    onShowManualCandidateForm: () -> Unit,
    onManualCandidateDraftChange: (ManualReportCandidateDraft) -> Unit,
    onCloseManualCandidateForm: () -> Unit,
    onManualCandidateAdded: () -> Unit,
    onOpenInterpretation: () -> Unit,
) {
    if (state.reviewLoading) {
        Column(
            Modifier.cardStyle(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("正在加载识别候选…", fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }

    val review = state.review ?: return
    val eventId = state.confirmationClientEventId.orEmpty()
    val canSubmit = ReportReviewPolicy.canSubmit(review, eventId, state.decisions)

    Column(
        Modifier.cardStyle(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            when (review.status) {
                "recognizing" -> "报告识别中"
                "awaiting_confirmation" -> "检查识别结果"
                "committing" -> "确认结果已保存，继续完成入库"
                "completed_score_pending" -> "已确认入库 · 评分待更新"
                "completed" -> "已确认入库"
                "failed" -> "报告处理失败"
                else -> "报告状态待确认"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "待检查 ${review.pending_review_count} 项 · 自动通过 ${review.auto_accepted_count} 项 · 已入库 ${review.admitted_observation_count} 项",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (review.auto_accepted_count > 0 && review.requires_report_confirmation) {
            Text(
                "高置信且正常的字段可自动通过字段检查，但整份报告仍须你确认后才能入库。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        review.failure_recovery?.let { recovery ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        recovery.failure_code.reportFailureLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        recovery.recovery_action.reportRecoveryActionLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (recovery.retryable) {
                        OutlinedButton(
                            onClick = vm::reloadReview,
                            modifier = Modifier.heightIn(min = 48.dp),
                            enabled = !state.addingManualCandidate && !state.confirming,
                        ) { Text("重新检查状态") }
                    }
                }
            }
        }

        review.candidates.forEach { candidate ->
            ReportCandidateReviewCard(
                candidate = candidate,
                draft = state.decisions[candidate.candidate_id],
                enabled = !state.confirming && review.status == "awaiting_confirmation",
                onChoose = { vm.chooseDecision(candidate.candidate_id, it) },
                onValueChange = { vm.updateCorrection(candidate.candidate_id, value = it) },
                onUnitChange = { vm.updateCorrection(candidate.candidate_id, unit = it) },
            )
        }

        val allowsManualCandidate = review.status == "awaiting_confirmation" ||
            review.failure_recovery?.allows_manual_candidate == true
        if (allowsManualCandidate) {
            if (showManualCandidateForm) {
                ManualReportCandidateForm(
                    draft = manualCandidateDraft,
                    enabled = !state.addingManualCandidate && !state.confirming,
                    loading = state.addingManualCandidate,
                    onChange = onManualCandidateDraftChange,
                    onCancel = onCloseManualCandidateForm,
                    onSubmit = {
                        vm.addManualCandidate(manualCandidateDraft, onManualCandidateAdded)
                    },
                )
            } else {
                OutlinedButton(
                    onClick = onShowManualCandidateForm,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = !state.confirming,
                ) {
                    Text("手动补录未识别字段")
                }
                Text(
                    "手动补录只会新增一条待确认候选；仍需逐项复核并确认整份报告。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (review.status) {
            "awaiting_confirmation", "committing" -> {
                Button(
                    onClick = vm::confirmReport,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = canSubmit && !state.confirming,
                ) {
                    if (state.confirming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (review.status == "committing") "继续完成入库" else "确认整份报告并入库")
                }
                if (!canSubmit && review.status == "awaiting_confirmation") {
                    Text(
                        "请先为每个待检查指标选择“确认、修正或不入库”。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            "recognizing" -> OutlinedButton(
                onClick = vm::reloadReview,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("刷新识别状态")
            }
            "completed", "completed_score_pending" -> {
                val actionTitle = requireNotNull(
                    ReportTrustPresentation.interpretationPrimaryAction(review.status),
                )
                Button(
                    onClick = onOpenInterpretation,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(actionTitle)
                }
            }
        }
    }
}

@Composable
private fun ManualReportCandidateForm(
    draft: ManualReportCandidateDraft,
    enabled: Boolean,
    loading: Boolean,
    onChange: (ManualReportCandidateDraft) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("手动补录待确认字段", fontWeight = FontWeight.Bold)
            Text(
                "请按报告原文填写。添加后不会直接进入趋势、画像、AI 或评分。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                label = { Text("指标名称 *") },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.value,
                onValueChange = { onChange(draft.copy(value = it)) },
                label = { Text("报告原值 *") },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.unit,
                onValueChange = { onChange(draft.copy(unit = it)) },
                label = { Text("单位（可选）") },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.referenceLow,
                    onValueChange = { onChange(draft.copy(referenceLow = it)) },
                    label = { Text("参考下限") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.referenceHigh,
                    onValueChange = { onChange(draft.copy(referenceHigh = it)) },
                    label = { Text("参考上限") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = draft.referenceText,
                onValueChange = { onChange(draft.copy(referenceText = it)) },
                label = { Text("报告上的参考范围原文（可选）") },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                minLines = 1,
                maxLines = 3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onCancel,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("收起") }
                Button(
                    onClick = onSubmit,
                    enabled = enabled && draft.name.isNotBlank() && draft.value.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(if (loading) "正在添加…" else "加入待确认") }
            }
        }
    }
}

@Composable
private fun ReportCandidateReviewCard(
    candidate: HealthReportCandidate,
    draft: ReportDecisionDraft?,
    enabled: Boolean,
    onChoose: (ReportDecisionAction) -> Unit,
    onValueChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(candidate.canonical_name, fontWeight = FontWeight.Bold)
                    if (candidate.raw_name != candidate.canonical_name) {
                        Text(
                            "原文：${candidate.raw_name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    candidate.review_status.reportReviewStatusLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (candidate.review_status == "auto_accepted") {
                        XjiePalette.Success
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
            }

            Text(
                "识别值：${candidate.displayValue()}${candidate.displayUnit()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            candidate.referenceLabel()?.let {
                Text(
                    "参考范围：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val flags = buildList {
                if (candidate.abnormal_state == "abnormal") add("异常")
                if (candidate.low_confidence) add("低置信度")
                candidate.conflict_reasons.forEach { add(it.reportConflictLabel()) }
                candidate.confidence?.let { add("置信度 ${(it * 100).toInt()}%") }
                candidate.sourcePage()?.let { add("原图第 $it 页") }
            }
            if (flags.isNotEmpty()) {
                Text(
                    flags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (candidate.abnormal_state == "abnormal" ||
                        candidate.low_confidence || candidate.conflict_reasons.isNotEmpty()
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (candidate.requires_review && candidate.review_status == "pending_review") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReportDecisionAction.entries.forEach { action ->
                        val selected = draft?.action == action
                        OutlinedButton(
                            onClick = { onChoose(action) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            enabled = enabled,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            Text(
                                when (action) {
                                    ReportDecisionAction.Confirm -> "确认"
                                    ReportDecisionAction.Correct -> "修正"
                                    ReportDecisionAction.Reject -> "不入库"
                                },
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (draft?.action == ReportDecisionAction.Correct) {
                    OutlinedTextField(
                        value = draft.correctedValue,
                        onValueChange = onValueChange,
                        label = { Text("修正后的值") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.correctedUnit,
                        onValueChange = onUnitChange,
                        label = { Text("单位（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        singleLine = true,
                    )
                }
            }
        }
    }
}

internal fun HealthDocument.reportTypeLabel(): String = when (doc_type) {
    "exam" -> "报告"
    "record" -> "病历"
    else -> "资料"
}

internal fun HealthDocument.reportHistoryMetadata(): String = listOfNotNull(
    doc_date?.take(10)?.takeIf { it.isNotBlank() },
    hospital?.trim()?.takeIf { it.isNotBlank() },
    reportTypeLabel(),
).joinToString(" · ")

private fun HealthReportCandidate.displayValue(): String = normalized_value?.let {
    if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
} ?: normalized_text ?: raw_value ?: "未识别"

private fun HealthReportCandidate.displayUnit(): String =
    (normalized_unit ?: raw_unit)?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()

private fun HealthReportCandidate.referenceLabel(): String? = reference_text
    ?: if (reference_low != null || reference_high != null) {
        "${reference_low?.toString() ?: "--"} – ${reference_high?.toString() ?: "--"}"
    } else {
        null
    }

private fun HealthReportCandidate.sourcePage(): String? =
    source_locator["page"]?.toString()?.trim('"')

private fun String.reportReviewStatusLabel(): String = when (this) {
    "pending_review" -> "待检查"
    "auto_accepted" -> "字段自动通过"
    "confirmed" -> "已确认"
    "corrected" -> "已修正"
    "rejected" -> "不入库"
    else -> "状态待刷新"
}

private fun String.reportConflictLabel(): String = when (this) {
    "unit_conflict" -> "单位冲突"
    "reference_range_conflict" -> "参考范围冲突"
    "duplicate_value_conflict" -> "重复值冲突"
    else -> "数据冲突"
}

private fun String.reportFailureLabel(): String = when (this) {
    "blur", "blurry_image" -> "图片模糊，无法可靠识别"
    "missing_page" -> "报告可能缺页"
    "no_reviewable_candidates" -> "没有识别出可复核字段"
    "extraction_failed", "processing_failed" -> "报告识别失败"
    else -> "报告暂时无法继续处理"
}

private fun String.reportRecoveryActionLabel(): String = when (this) {
    "retake_image" -> "请重新拍摄清晰、完整的报告后上传。"
    "upload_missing_pages" -> "请补齐缺失页面并重新上传整份报告。"
    "manual_entry_or_reupload" -> "可手动补录报告原文中的字段，或重新上传更清晰的文件。"
    "retry_processing" -> "可以重新检查处理状态；如仍失败，请重新上传。"
    "reupload_report" -> "请重新上传完整、清晰的报告文件。"
    "open_existing_report" -> "这份报告已存在，请打开原有报告继续复核。"
    "none" -> "当前无需恢复操作。"
    else -> "请按页面提示恢复；失败结果不会进入可信健康数据。"
}

@Composable
private fun CsvTable(columns: List<String>, rows: List<List<String>>, title: String) {
    Column(Modifier.cardStyle()) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            columns.forEach { c ->
                Text(c, Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider()
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                row.forEach { v ->
                    Text(v, Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
        }
    }
}
