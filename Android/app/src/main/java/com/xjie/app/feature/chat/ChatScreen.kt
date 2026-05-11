package com.xjie.app.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.Citation
import com.xjie.app.core.ui.components.MarkdownText
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onBack: (() -> Unit)? = null,
    onOpenPatientHistory: () -> Unit = {},
    vm: ChatViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) { vm.loadConversations() }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    com.xjie.app.core.ui.components.BrandTitle(
                        if (state.isViewingHistory) "历史对话" else "助手小捷",
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    } else if (state.isViewingHistory) {
                        TextButton(onClick = vm::backToCurrentChat) { Text("当前对话") }
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .clip(RoundedCornerShape(50))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(50),
                                )
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { vm.newChat() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "+ 新对话",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::toggleHistory) {
                        Icon(
                            Icons.Filled.History,
                            "历史",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        bottomBar = {
            InputBar(
                value = state.input,
                sending = state.sending,
                onValueChange = vm::setInput,
                onSend = { focus.clearFocus(); vm.send() },
            )
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (state.messages.isEmpty()) {
                item { WelcomeMessage(onOpenPatientHistory = onOpenPatientHistory) }
            }
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(msg, onFollowup = vm::useFollowup)
            }
            if (state.sending) {
                item { ThinkingIndicator(hint = state.thinkingHint) }
            }
        }
    }

    if (state.showHistory) {
        ConversationHistorySheet(
            conversations = state.conversations,
            onPick = vm::loadConversation,
            onDelete = vm::deleteConversation,
            onLoadMore = vm::loadMoreConversations,
            onDismiss = vm::toggleHistory,
        )
    }
}

@Composable
private fun WelcomeMessage(onOpenPatientHistory: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(id = com.xjie.app.R.drawable.ic_logo),
            contentDescription = "助手小捷",
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp)),
        )
        Text(
            "你好！我是助手小捷。",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "可以问我关于血糖、膳食、健康管理的问题。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            onClick = onOpenPatientHistory,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "病史整理",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "把过往诊断、用药、过敏和关键异常检查整理成给医生看的摘要。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "可点击关键数值跳到健康数据页继续核对来源。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessageItem, onFollowup: (String) -> Unit) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isUser) {
                Text(msg.content, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                MarkdownText(msg.content)
                msg.analysis?.takeIf { it.isNotBlank() }?.let { a ->
                    var expanded by remember { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(0.dp)) {
                        Text(if (expanded) "收起详细分析" else "查看详细分析",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    if (expanded) MarkdownText(a)
                }
                if (msg.citations.isNotEmpty()) {
                    CitationSection(msg.citations)
                }
                msg.followups?.takeIf { it.isNotEmpty() }?.let { fups ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("延伸追问",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        fups.forEach { q ->
                            Surface(
                                onClick = { onFollowup(q) },
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(q,
                                    modifier = Modifier.padding(horizontal = 10.dp,
                                        vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                msg.confidence?.let { c ->
                    Text("置信度 ${(c * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator(hint: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BouncingDots()
            Spacer(Modifier.width(10.dp))
            Text(
                hint.ifEmpty { "正在为您精准智能分析…" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BouncingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = LinearEasing,
                        delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(7.dp)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun CitationSection(citations: List<Citation>) {
    var selected by remember { mutableStateOf<Citation?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "参考文献",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        citations.forEachIndexed { idx, c ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { selected = c }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "[${idx + 1}]",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = evidenceColor(c.evidence_level).copy(alpha = 0.15f),
                ) {
                    Text(
                        c.evidence_level,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = evidenceColor(c.evidence_level),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    c.short_ref,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
    selected?.let { CitationDetailDialog(citation = it, onDismiss = { selected = null }) }
}

@Composable
private fun CitationDetailDialog(citation: Citation, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = evidenceColor(citation.evidence_level).copy(alpha = 0.15f),
                ) {
                    Text(
                        citation.evidence_level,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = evidenceColor(citation.evidence_level),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    citation.short_ref,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(citation.claim_text, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                CitationMetaRow("证据等级", evidenceLabel(citation.evidence_level))
                CitationMetaRow("期刊", citation.journal ?: "—")
                CitationMetaRow("年份", citation.year?.toString() ?: "—")
                CitationMetaRow("样本量", citation.sample_size?.let { "n = $it" } ?: "—")
                CitationMetaRow("可信度", citation.confidence)
                Text(
                    "仅作健康管理参考，不构成诊断或治疗建议。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun CitationMetaRow(label: String, value: String) {
    Row {
        Text(
            label,
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

private fun evidenceLabel(level: String): String = when (level.uppercase()) {
    "L1" -> "L1（Meta 分析 / 系统综述 / RCT）"
    "L2" -> "L2（队列 / 小型 RCT）"
    "L3" -> "L3（横断 / 个案）"
    "L4" -> "L4（专家共识 / 动物实验）"
    else -> level
}

private fun evidenceColor(level: String): Color = when (level.uppercase()) {
    "L1" -> XjiePalette.Success
    "L2" -> XjiePalette.Primary
    "L3" -> XjiePalette.Warning
    "L4" -> XjiePalette.Danger
    // 兼容旧版 A/B/C/D
    "A" -> XjiePalette.Success
    "B" -> XjiePalette.Primary
    "C" -> XjiePalette.Warning
    "D" -> XjiePalette.Danger
    else -> XjiePalette.Primary
}

@Composable
private fun InputBar(
    value: String,
    sending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息...") },
                maxLines = 4,
                enabled = !sending,
                shape = RoundedCornerShape(20.dp),
                singleLine = false,
            )
            Spacer(Modifier.width(8.dp))
            val canSend = !sending && value.isNotBlank()
            TextButton(
                onClick = onSend,
                enabled = canSend,
            ) {
                Text(
                    "发送",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationHistorySheet(
    conversations: List<com.xjie.app.core.model.ChatConversation>,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<com.xjie.app.core.model.ChatConversation?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("历史对话", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null) }
            }
            Spacer(Modifier.height(8.dp))
            if (conversations.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center) {
                    Text("暂无历史对话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(conversations, key = { it.id }) { conv ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                onClick = { onPick(conv.id) },
                                color = Color.Transparent,
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(Modifier.padding(vertical = 8.dp)) {
                                    Text(conv.title ?: "未命名对话",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1)
                                    Row {
                                        conv.message_count?.let {
                                            Text("$it 条消息",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        conv.updated_at?.let {
                                            Text(" · ${it.take(10)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            IconButton(onClick = { pendingDelete = conv }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                    item {
                        TextButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("加载更多") }
                    }
                }
            }
        }
    }

    pendingDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除对话") },
            text = { Text("确定删除「${conv.title ?: "未命名对话"}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(conv.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}
