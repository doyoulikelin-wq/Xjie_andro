package com.xjie.app.feature.healthdata

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.HealthDocument
import com.xjie.app.core.ui.components.EmptyState
import com.xjie.app.core.ui.components.LoadingIndicator
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    docType: String,
    title: String,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    vm: DocumentListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { vm.upload(docType, it, it.lastPathSegment ?: "file") }
    }

    LaunchedEffect(Unit) { vm.fetch(docType) }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbar.showSnackbar(it); vm.clearToast() }
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
            OutlinedButton(
                onClick = { filePicker.launch("*/*") },
                enabled = !state.uploading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.CloudUpload, null)
                Spacer(Modifier.width(6.dp))
                Text(if (docType == "record") "上传病例" else "上传体检报告")
            }

            when {
                state.loading -> LoadingIndicator()
                state.items.isEmpty() ->
                    EmptyState(
                        if (docType == "record") "暂无病例记录" else "暂无体检记录",
                        description = "点击上方按钮上传",
                    )
                else -> state.items.forEach { doc ->
                    DocumentRow(
                        doc, onClick = { onItemClick(doc.id) },
                        onDelete = { pendingDeleteId = doc.id },
                    )
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
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
                val date = doc.doc_date?.take(10)
                Text(
                    if (!date.isNullOrBlank()) date else (doc.name ?: "未命名"),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
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
            Icon(Icons.Filled.KeyboardArrowRight, null,
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
    LaunchedEffect(docId) { vm.fetch(docId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { inner ->
        when {
            state.loading -> Box(Modifier.padding(inner).fillMaxSize(),
                contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.doc != null -> {
                val doc = state.doc!!
                Column(
                    Modifier
                        .padding(inner).fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.cardStyle()) {
                        Text(doc.name ?: "详情",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        if (!doc.doc_date.isNullOrBlank()) {
                            Text(doc.doc_date!!.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!doc.ai_summary.isNullOrBlank()) {
                        Column(Modifier.cardStyle(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("AI 整理",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Text(doc.ai_summary!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    doc.csv_data?.let { csv ->
                        val cols = csv.columns.orEmpty()
                        val rows = csv.rows.orEmpty()
                        if (cols.isNotEmpty() && rows.isNotEmpty()) {
                            CsvTable(cols, rows)
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
}

@Composable
private fun CsvTable(columns: List<String>, rows: List<List<String>>) {
    Column(Modifier.cardStyle()) {
        Text("数据", style = MaterialTheme.typography.titleSmall,
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
