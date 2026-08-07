package com.xjie.app.feature.medicalassistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.MedicalAssistantGenerationResult
import com.xjie.app.core.model.MedicalAssistantOverviewPolicy
import com.xjie.app.core.model.MedicalAssistantRecentDocument
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val InkBlue = Color(0xFF123E67)
private val BodyBlue = Color(0xFF173B59)
private val MutedBlue = Color(0xFF607B91)
private val BrandBlue = Color(0xFF1675DB)
private val BrandMint = Color(0xFF43D1B8)
private val WarningOrange = Color(0xFFE27A22)
private val SuccessMint = Color(0xFF1AAE96)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalAssistantScreen(
    onClose: () -> Unit,
    onOpenDocument: (String) -> Unit,
    viewModel: MedicalAssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { viewModel.load() }

    MedicalAssistantLiquidBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                GenerationBar(
                    generating = state.generating,
                    enabled = !state.loading && !state.generating,
                    onGenerate = viewModel::generate,
                )
            },
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = viewModel::load,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    NavigationActions(onClose = onClose, onRefresh = viewModel::load)
                    DashboardHeader()
                    OverviewCard(state = state, onRetry = viewModel::load)
                    GenerationStateBanner(state.lastGenerationResult)
                    TimingCard(
                        generatedAt = state.overview?.generated_at,
                        latestUploadAt = state.overview?.latest_report_uploaded_at,
                    )
                    RecentDocumentsCard(
                        countLastYear = state.overview?.report_count_last_year ?: 0,
                        documents = state.overview?.recent_documents.orEmpty(),
                        onOpenDocument = onOpenDocument,
                    )
                    SafetyNote()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    state.notice?.let { notice ->
        AlertDialog(
            modifier = Modifier.testTag("medicalAssistant.notice"),
            onDismissRequest = viewModel::clearNotice,
            title = { Text("就医助手") },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = viewModel::clearNotice) { Text("知道了") }
            },
        )
    }

    if (state.error != null && state.overview != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("无法完成") },
            text = { Text(state.error.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::load) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearError) { Text("取消") }
            },
        )
    }
}

@Composable
private fun NavigationActions(onClose: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(48.dp)
                .testTag("medicalAssistant.close"),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "关闭就医助手")
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新病人概况")
        }
    }
}

@Composable
private fun DashboardHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(BrandBlue, Color(0xFF50D4C1))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MedicalServices,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(31.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "就医助手",
                color = InkBlue,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "整理资料，生成给医生看的病人概况",
                color = Color(0xFF5D7890),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun OverviewCard(state: MedicalAssistantUiState, onRetry: () -> Unit) {
    GlassCard(cornerRadius = 28) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = InkBlue)
                Spacer(Modifier.width(7.dp))
                Text(
                    "病人概况",
                    color = InkBlue,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            state.overview?.let { overview -> StatusBadge(overview.hasNewerUpload, overview.hasSummary) }
        }
        Spacer(Modifier.height(14.dp))

        when {
            state.loading && state.overview == null -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在读取最新概况…", color = MutedBlue)
                }
            }
            state.error != null && state.overview == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        state.error,
                        color = MutedBlue,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("重试")
                    }
                }
            }
            state.overview?.hasSummary == true -> {
                SelectionContainer {
                    Text(
                        state.overview.summary,
                        color = BodyBlue,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 27.sp,
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        tint = Color(0xFF3D91DD),
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "还没有生成过概况，请点击下方按钮生成。",
                        color = Color(0xFF456982),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(hasNewerUpload: Boolean, hasSummary: Boolean) {
    val label = if (hasNewerUpload) "有新资料" else if (hasSummary) "已生成" else "未生成"
    val color = if (hasNewerUpload) WarningOrange else SuccessMint
    Surface(shape = CircleShape, color = color.copy(alpha = 0.12f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun GenerationStateBanner(result: MedicalAssistantGenerationResult?) {
    val (message, color) = when (result) {
        MedicalAssistantGenerationResult.NoInformationUpdate ->
            "无信息更新：当前概况已包含最新入库资料" to SuccessMint
        MedicalAssistantGenerationResult.ReportProcessing ->
            "最新报告正在处理，确认并入库后可再次生成" to WarningOrange
        else -> return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f)),
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = BodyBlue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TimingCard(generatedAt: String?, latestUploadAt: String?) {
    GlassCard(cornerRadius = 26) {
        Text(
            "更新时间",
            color = InkBlue,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints {
            val stackCells = maxWidth < 360.dp || fontScale > 1.3f
            if (stackCells) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeCell(Icons.Filled.AutoAwesome, "概况生成时间", formattedTime(generatedAt))
                    TimeCell(Icons.Filled.UploadFile, "最近上传报告", formattedTime(latestUploadAt))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeCell(
                        Icons.Filled.AutoAwesome,
                        "概况生成时间",
                        formattedTime(generatedAt),
                        Modifier.weight(1f),
                    )
                    TimeCell(
                        Icons.Filled.UploadFile,
                        "最近上传报告",
                        formattedTime(latestUploadAt),
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeCell(icon: ImageVector, title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 64.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(BrandBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color(0xFF71879A),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    value,
                    color = BodyBlue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RecentDocumentsCard(
    countLastYear: Int,
    documents: List<MedicalAssistantRecentDocument>,
    onOpenDocument: (String) -> Unit,
) {
    GlassCard(cornerRadius = 26) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "最近就医资料",
                modifier = Modifier.weight(1f),
                color = InkBlue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "近一年 $countLastYear 份已入库",
                color = Color(0xFF6B8195),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无已上传的病历、就诊单或检查报告",
                    color = Color(0xFF71879A),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            documents.forEachIndexed { index, document ->
                RecentDocumentRow(document, onClick = { onOpenDocument(document.document_id) })
                if (index < documents.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            }
        }
    }
}

@Composable
private fun RecentDocumentRow(document: MedicalAssistantRecentDocument, onClick: () -> Unit) {
    val admitted = document.status == "admitted"
    val statusColor = if (admitted) SuccessMint else Color(0xFFD57826)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                contentDescription = "${document.title}，${documentStatus(document.status)}，打开资料详情"
            },
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (admitted) Icons.Filled.Description else Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = if (admitted) BrandBlue else WarningOrange,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    document.title,
                    color = BodyBlue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    documentMetadata(document),
                    color = Color(0xFF71879A),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                documentStatus(document.status),
                color = statusColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF91A7BA),
            )
        }
    }
}

@Composable
private fun SafetyNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Filled.VerifiedUser,
            contentDescription = null,
            tint = MutedBlue,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "概况仅整理本人已确认并入库的资料，供就诊沟通参考；请同时向医生出示原件。",
            modifier = Modifier.weight(1f),
            color = MutedBlue,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun GenerationBar(generating: Boolean, enabled: Boolean, onGenerate: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 8.dp,
    ) {
        Button(
            onClick = onGenerate,
            enabled = enabled,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) {
                        Brush.horizontalGradient(listOf(Color(0xFF176FE0), BrandMint))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0xFF9BAFC2), Color(0xFF91BDB7)))
                    },
                ),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.8f),
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            if (generating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                )
            } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            }
            Spacer(Modifier.width(9.dp))
            Text(
                if (generating) "正在生成病人概况…" else "生成病人概况",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun GlassCard(cornerRadius: Int, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(cornerRadius.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.58f)),
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun MedicalAssistantLiquidBackground(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val top = if (dark) Color(0xFF0B1D30) else Color(0xFFF2FBFF)
    val bottom = if (dark) Color(0xFF10283A) else Color(0xFFE7F5FA)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = BrandBlue.copy(alpha = if (dark) 0.16f else 0.10f),
                radius = size.minDimension * 0.58f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.94f, size.height * 0.08f),
            )
            drawCircle(
                color = BrandMint.copy(alpha = if (dark) 0.12f else 0.12f),
                radius = size.minDimension * 0.46f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.02f, size.height * 0.70f),
            )
        }
        content()
    }
}

private fun formattedTime(raw: String?): String {
    val instant = MedicalAssistantOverviewPolicy.parseInstant(raw) ?: return "暂无记录"
    return DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.SIMPLIFIED_CHINESE)
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

private fun documentMetadata(document: MedicalAssistantRecentDocument): String =
    listOfNotNull(
        formattedTime(document.document_date ?: document.uploaded_at),
        document.hospital?.trim()?.takeIf(String::isNotEmpty),
    ).joinToString(" · ")

private fun documentStatus(status: String): String = when (status) {
    "admitted" -> "已入库"
    "failed" -> "处理失败"
    else -> "处理中"
}
