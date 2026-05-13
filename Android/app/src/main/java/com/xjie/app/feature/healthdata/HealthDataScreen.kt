package com.xjie.app.feature.healthdata

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.ui.components.BrandTitle
import com.xjie.app.core.ui.components.LoadingIndicator
import com.xjie.app.core.ui.components.MarkdownText
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle
import com.xjie.app.core.util.DateUtils

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HealthDataScreen(
    onOpenRecords: () -> Unit,
    onOpenExams: () -> Unit,
    initialFocus: String? = null,
    vm: HealthDataViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showUploadSheet by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: "file"
            vm.uploadFile(uri, name)
        }
    }

    LaunchedEffect(Unit) { vm.fetchAll() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbar.showSnackbar(it); vm.clearToast() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { BrandTitle("健康数据") }) },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            initialFocus?.let { focus ->
                FocusHintCard(focus = focus)
            }

            AiSummaryCard(state, vm::generateSummary)

            FocusWrapper(highlighted = initialFocus == "indicator") {
                IndicatorTrendSection()
            }

            SectionRow(
                Icons.Filled.LocalHospital,
                "历史病例", state.recordCount, onOpenRecords,
                highlighted = initialFocus == "records",
            )
            SectionRow(
                Icons.Filled.Science,
                "历史体检", state.examCount, onOpenExams,
                highlighted = initialFocus == "exams",
            )

            OutlinedButton(
                onClick = { showUploadSheet = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (initialFocus == "upload") {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        Color.Transparent
                    },
                ),
                border = BorderStroke(
                    1.dp,
                    if (initialFocus == "upload") {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
            ) {
                Icon(Icons.Filled.CloudUpload, null)
                Spacer(Modifier.width(6.dp))
                Text("拍照 / 文件上传")
            }

            if (state.uploading) {
                Column(
                    Modifier.cardStyle(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            state.uploadStage.ifBlank { "正在上传…" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "上传完成后会转入后台识别，您可以随时离开此页。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.backgroundTaskHint?.let { hint ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            hint,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(
                            onClick = { vm.dismissBackgroundHint() },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "关闭提示",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUploadSheet) {
        AlertDialog(
            onDismissRequest = { showUploadSheet = false },
            title = { Text("选择上传类型") },
            text = { Text("") },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        showUploadSheet = false
                        vm.setUploadDocType("record")
                        filePicker.launch("*/*")
                    }) { Text("上传病例") }
                    TextButton(onClick = {
                        showUploadSheet = false
                        vm.setUploadDocType("exam")
                        filePicker.launch("*/*")
                    }) { Text("上传体检报告") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadSheet = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AiSummaryCard(state: HealthDataUiState, onGenerate: () -> Unit) {
    var expanded by rememberSaveable(state.summaryUpdatedAt) { mutableStateOf(false) }
    Column(
        modifier = Modifier.cardStyle(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Psychology, null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("AI 健康总结", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (state.generating) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onGenerate, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = if (state.summary.isBlank()) "生成" else "重新生成",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        when {
            state.generating -> {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(state.stage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.summary.isNotBlank() -> {
                val brief = remember(state.summary) { briefOf(state.summary) }
                val cleanedFull = remember(state.summary) {
                    state.summary.replace(Regex("\\s+"), " ").trim()
                }
                val hasMore = brief.replace(Regex("\\s+"), " ").length < cleanedFull.length - 4
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "简明版 · 核心摘要",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        MarkdownText(brief)
                    }
                }
                if (hasMore) {
                    Surface(
                        onClick = { expanded = !expanded },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (expanded) "收起详细分析" else "查看详细分析",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (expanded && hasMore) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        "详细版",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    MarkdownText(state.summary)
                }
                if (state.summaryUpdatedAt.isNotBlank()) {
                    Text("更新于 ${DateUtils.formatDateTime(state.summaryUpdatedAt)} · 仅本账号可见",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                Surface(
                    onClick = onGenerate,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("点击生成 AI 健康总结",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold)
                        Text("将综合您的所有病例和体检数据进行分析，生成后可重复查看。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun briefOf(full: String): String {
    val text = full.trim()
    if (text.isEmpty()) return ""

    // 1) 优先抽取 "## 核心摘要" 段落（到下一个 ## 标题为止），保留其内部加粗/列表的 Markdown 原样
    val coreRegex = Regex(
        pattern = "(?m)^##\\s*核心摘要\\s*\\n([\\s\\S]*?)(?=^##\\s|\\z)",
    )
    val coreMatch = coreRegex.find(text)
    if (coreMatch != null) {
        val body = coreMatch.groupValues[1].trim()
        if (body.isNotEmpty()) return body
    }

    // 2) 退回：取首段（到第一个空行为止），过长按整句截到 3 句
    fun cleanLine(s: String): String =
        s.trim()
            .removePrefix("###").removePrefix("##").removePrefix("#").trim()

    val paragraph = StringBuilder()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) {
            if (paragraph.isNotEmpty()) break else continue
        }
        if (line.startsWith("#") || line.startsWith("---") || line.startsWith("===")) {
            if (paragraph.isNotEmpty()) break else continue
        }
        if (paragraph.isNotEmpty()) paragraph.append(' ')
        paragraph.append(cleanLine(line))
    }
    val firstPara = paragraph.toString().replace(Regex("\\s+"), " ").trim()
    if (firstPara.isNotEmpty() && firstPara.length <= 220) return firstPara

    val source = firstPara.ifEmpty { text.replace(Regex("\\s+"), " ").trim() }
    val sentenceEnds = "。！？.!?"
    val sentences = mutableListOf<String>()
    val sb = StringBuilder()
    for (ch in source) {
        sb.append(ch)
        if (ch in sentenceEnds) {
            sentences.add(sb.toString().trim())
            sb.setLength(0)
            if (sentences.size >= 3) break
        }
    }
    if (sentences.isEmpty() && sb.isNotEmpty()) sentences.add(sb.toString().trim())
    val joined = sentences.joinToString(" ")
    return if (joined.length <= 220) joined else joined.substring(0, 220).trimEnd() + "…"
}

@Composable
private fun FocusWrapper(highlighted: Boolean, content: @Composable () -> Unit) {
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = if (highlighted) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        },
    ) {
        Box(Modifier.padding(if (highlighted) 2.dp else 0.dp)) {
            content()
        }
    }
}

@Composable
private fun FocusHintCard(focus: String) {
    val message = when (focus) {
        "records" -> "已为你定位到历史病例区域，可继续补充或核对病史来源。"
        "exams" -> "已为你定位到历史体检区域，可继续核对异常指标与来源。"
        "upload" -> "已为你定位到上传入口，缺少资料时可以从这里补充。"
        "indicator" -> "已为你定位到关注指标趋势，可继续查看关键数值。"
        else -> "已定位到相关健康资料区域。"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun SectionRow(
    icon: ImageVector,
    title: String,
    count: Int,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.cardStyle(),
        color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent,
        border = if (highlighted) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall)
                Text("$count 份记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text("可上传", color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
