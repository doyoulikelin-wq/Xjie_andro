package com.xjie.app.feature.xage

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.ChatConversation
import com.xjie.app.R
import com.xjie.app.core.model.Citation
import com.xjie.app.core.ui.components.MarkdownText
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.feature.chat.ChatDeliveryStatus
import com.xjie.app.feature.chat.ChatMessageItem
import com.xjie.app.feature.chat.ChatViewModel
import kotlinx.coroutines.launch

private enum class XAgeSection(val label: String) {
    Data("数据"),
    Chat("问答"),
    XAge("X年龄"),
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun XAgeMainScreen(
    onOpenUpload: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLegacyHome: () -> Unit,
    onOpenHealthPlan: () -> Unit,
    onOpenPatientHistory: () -> Unit,
    onOpenOmics: () -> Unit,
) {
    val sections = remember { XAgeSection.entries }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { sections.size })
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var chatHistorySignal by remember { mutableStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .xAgeLiquidBackground(),
    ) {
        Column(Modifier.fillMaxSize()) {
            XAgeTopBar(
                selected = sections[pagerState.currentPage],
                onSelect = { section ->
                    scope.launch { pagerState.animateScrollToPage(section.ordinal) }
                },
                onMenu = { showMenu = true },
                onTrailingAction = {
                    if (sections[pagerState.currentPage] == XAgeSection.Chat) {
                        chatHistorySignal += 1
                    }
                },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (sections[page]) {
                    XAgeSection.Data -> XAgeDataPage(
                        onOpenUpload = onOpenUpload,
                        onOpenSettings = onOpenSettings,
                        onOpenHealthPlan = onOpenHealthPlan,
                        onOpenPatientHistory = onOpenPatientHistory,
                    )
                    XAgeSection.Chat -> XAgeChatPage(historySignal = chatHistorySignal)
                    XAgeSection.XAge -> XAgeHealthspanPage()
                }
            }
        }
    }

    if (showMenu) {
        ModalBottomSheet(onDismissRequest = { showMenu = false }) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("更多", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                XAgeMenuRow("旧首页", onOpenLegacyHome)
                XAgeMenuRow("计划", onOpenHealthPlan)
                XAgeMenuRow("多组学", onOpenOmics)
                XAgeMenuRow("设置", onOpenSettings)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun XAgeTopBar(
    selected: XAgeSection,
    onSelect: (XAgeSection) -> Unit,
    onMenu: () -> Unit,
    onTrailingAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box {
            IconButton(
                onClick = onMenu,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("xage.more"),
            ) {
                Icon(Icons.Filled.Menu, contentDescription = "更多", tint = XAgeTextPrimary)
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .align(Alignment.TopEnd)
                    .background(Color(0xFFFF5B63), CircleShape),
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.46f))
                .border(1.dp, Color.White.copy(alpha = 0.86f), RoundedCornerShape(24.dp))
                .padding(5.dp),
        ) {
            XAgeSection.entries.forEach { section ->
                val active = selected == section
                val textColor by animateColorAsState(
                    if (active) Color(0xFF1268BD) else Color(0xFF4E718E),
                    label = "sectionColor",
                )
                Text(
                    text = section.label,
                    modifier = Modifier
                        .weight(if (section == XAgeSection.XAge) 1.12f else 1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(19.dp))
                        .background(if (active) Color.White.copy(alpha = 0.74f) else Color.Transparent)
                        .clickable { onSelect(section) }
                        .wrapContentHeight(Alignment.CenterVertically)
                        .testTag("xage.segment.${section.label}"),
                    textAlign = TextAlign.Center,
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }

        IconButton(
            onClick = onTrailingAction,
            modifier = Modifier
                .size(if (selected == XAgeSection.Chat) 38.dp else 34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.48f))
                .border(1.dp, Color.White.copy(alpha = 0.86f), CircleShape),
        ) {
            Icon(
                if (selected == XAgeSection.Chat) Icons.Filled.Refresh else Icons.Filled.Info,
                contentDescription = if (selected == XAgeSection.Chat) "历史" else "说明",
                tint = if (selected == XAgeSection.Chat) XAgeTextPrimary else Color(0xFF2A79BB),
                modifier = Modifier.size(if (selected == XAgeSection.Chat) 20.dp else 17.dp),
            )
        }
    }
}

@Composable
private fun XAgeDataPage(
    onOpenUpload: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHealthPlan: () -> Unit,
    onOpenPatientHistory: () -> Unit,
) {
    var sortMode by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<XAgeDataKind?>(null) }
    var metrics by remember { mutableStateOf(XAgeMetric.defaults) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val peelDistancePx = with(density) { 126.dp.toPx() }
    val peelTranslationPx = with(density) { 34.dp.toPx() }
    val titleCollapseDistancePx = with(density) { 92.dp.toPx() }
    val firstMetricIndex = listState.firstVisibleItemIndex
    val firstMetricOffset = listState.firstVisibleItemScrollOffset.toFloat()
    val headerCollapse = if (sortMode) {
        0f
    } else if (firstMetricIndex > 0) {
        1f
    } else {
        (firstMetricOffset / titleCollapseDistancePx).coerceIn(0f, 1f)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 0.dp),
    ) {
        XAgeDataStickyHeader(
            sortMode = sortMode,
            collapse = headerCollapse,
            onToggleSort = {
                if (!sortMode) {
                    scope.launch { listState.scrollToItem(0) }
                }
                sortMode = !sortMode
            },
            onSelectDetail = { detail = it },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (sortMode) 0.dp else 190.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = if (sortMode) 24.dp else 12.dp),
            ) {
                itemsIndexed(metrics, key = { _, metric -> metric.id }) { index, metric ->
                    val progress = if (!sortMode && index == firstMetricIndex) {
                        (firstMetricOffset / peelDistancePx).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Box(
                        Modifier
                            .graphicsLayer {
                                alpha = 1f - 0.78f * progress
                                translationY = -peelTranslationPx * progress
                                rotationX = -5f * progress
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            }
                            .testTag("xage.data.metric.${metric.id}"),
                    ) {
                        XAgeMetricCard(
                            metric = metric,
                            sortMode = sortMode,
                            onMoveUp = { if (index > 0) metrics = metrics.swap(index, index - 1) },
                            onMoveDown = { if (index < metrics.lastIndex) metrics = metrics.swap(index, index + 1) },
                        )
                    }
                }
            }

            if (!sortMode) {
                XAgeBottomPanel(
                    onOpenUpload = onOpenUpload,
                    onOpenSettings = onOpenSettings,
                    onOpenHealthPlan = onOpenHealthPlan,
                    onOpenPatientHistory = onOpenPatientHistory,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        detail?.let { kind ->
            XAgeDataDetailDialog(kind = kind, onDismiss = { detail = null })
        }
    }
}

@Composable
private fun XAgeDataStickyHeader(
    sortMode: Boolean,
    collapse: Float,
    onToggleSort: () -> Unit,
    onSelectDetail: (XAgeDataKind) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp - 3.dp * collapse),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "今日健康数据",
                    color = XAgeTextPrimary,
                    fontSize = (27f - 4f * collapse).sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "6月29日 · 自动同步",
                    modifier = Modifier
                        .height(17.dp * (1f - collapse))
                        .graphicsLayer { alpha = 1f - collapse },
                    color = XAgeTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            Surface(
                onClick = onToggleSort,
                modifier = Modifier
                    .width(54.dp)
                    .height(34.dp)
                    .testTag(if (sortMode) "xage.data.sort.done" else "xage.data.sort"),
                shape = RoundedCornerShape(17.dp),
                color = Color.White.copy(alpha = 0.58f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.88f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (sortMode) "完成" else "排序", color = Color(0xFF1268BD), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .xAgeGlass(28.dp)
                .padding(start = 12.dp, top = 18.dp, end = 12.dp, bottom = 14.dp),
        ) {
            XAgeScoreRing(XAgeDataKind.Pressure, 68, Modifier.weight(1f)) { onSelectDetail(XAgeDataKind.Pressure) }
            XAgeScoreRing(XAgeDataKind.Recovery, 82, Modifier.weight(1f)) { onSelectDetail(XAgeDataKind.Recovery) }
            XAgeScoreRing(XAgeDataKind.Inflammation, 57, Modifier.weight(1f)) { onSelectDetail(XAgeDataKind.Inflammation) }
        }

        XAgeScoreSummaryCard()
    }
}

@Composable
private fun XAgeDataDetailDialog(kind: XAgeDataKind, onDismiss: () -> Unit) {
    XAgeGlassDialog(title = "${kind.label}详情", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("今日", color = XAgeTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            XAgeLargeScoreRing(kind = kind, score = kind.score)
            Text(kind.summary, color = Color(0xFF496A83), fontSize = 14.sp, lineHeight = 20.sp)
            Column(
                modifier = Modifier.xAgeGlass(22.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                kind.fields.forEach { row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(row.first, color = XAgeTextSecondary, fontSize = 13.sp, maxLines = 1)
                        Spacer(Modifier.width(12.dp))
                        Spacer(Modifier.weight(1f))
                        Text(
                            row.second,
                            color = XAgeTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun XAgeLargeScoreRing(kind: XAgeDataKind, score: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(174.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(154.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2f + 1.dp.toPx()
                val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                drawArc(
                    color = Color.White.copy(alpha = 0.54f),
                    startAngle = 112f,
                    sweepAngle = 310f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(kind.color.copy(alpha = 0.42f), kind.color, XjiePalette.Accent, kind.color),
                    ),
                    startAngle = 112f,
                    sweepAngle = 310f * score / 100f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$score", color = XAgeTextDark, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text(kind.label, color = Color(0xFF43657F), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun XAgeScoreRing(kind: XAgeDataKind, score: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag("xage.data.score.${kind.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(90.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2f + 1.dp.toPx()
                val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                drawArc(
                    color = Color.White.copy(alpha = 0.52f),
                    startAngle = 112f,
                    sweepAngle = 310f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(kind.color.copy(alpha = 0.42f), kind.color, XjiePalette.Accent, kind.color)),
                    startAngle = 112f,
                    sweepAngle = 310f * score / 100f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
            Text("$score", color = XAgeTextDark, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Text(kind.label, color = Color(0xFF43657F), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun XAgeScoreSummaryCard() {
    Column(
        modifier = Modifier.xAgeGlass(24.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("今日状态", color = XAgeTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "压力中等" to XAgeDataKind.Pressure.color,
                "恢复良好" to XAgeDataKind.Recovery.color,
                "炎症关注" to XAgeDataKind.Inflammation.color,
            ).forEach { item ->
                Text(
                    item.first,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.46f))
                        .wrapContentHeight(Alignment.CenterVertically),
                    color = item.second,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Text(
            "今天先保持低波动饮食和轻中等活动，晚间观察 HRV 与静息心率是否回到个人基线。",
            color = Color(0xFF496A83),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun XAgeMetricCard(
    metric: XAgeMetric,
    sortMode: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Column(
        modifier = Modifier.xAgeGlass(24.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(metric.title, color = XAgeTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(metric.subtitle, color = Color(0xFF657E94), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(metric.value, color = Color(0xFF101C2F), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Text(metric.unit, color = Color(0xFF70879D), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (sortMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                XAgeSmallButton("上移", onMoveUp)
                XAgeSmallButton("下移", onMoveDown)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.SwapVert, null, tint = Color(0xFF6C8194))
            }
        }
    }
}

@Composable
private fun XAgeBottomPanel(
    onOpenUpload: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHealthPlan: () -> Unit,
    onOpenPatientHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(XAgePanelCategory.Reports) }
    val action = when (selected) {
        XAgePanelCategory.Reports -> onOpenUpload
        XAgePanelCategory.Daily -> onOpenHealthPlan
        XAgePanelCategory.Medical -> onOpenPatientHistory
        XAgePanelCategory.Profile -> onOpenSettings
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.90f), RoundedCornerShape(30.dp))
            .padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XAgePanelCategory.entries.forEach { category ->
                val active = selected == category
                Surface(
                    onClick = { selected = category },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (active) Color.White.copy(alpha = 0.76f) else Color.White.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = if (active) 0.88f else 0.46f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        XAgePanelCategoryGlyph(
                            category = category,
                            selected = active,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            category.title,
                            color = if (active) Color(0xFF1268BD) else Color(0xFF5D7890),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.58f))
                .border(1.dp, Color.White.copy(alpha = 0.82f), RoundedCornerShape(24.dp))
                .clickable { action() }
                .testTag(if (selected == XAgePanelCategory.Reports) "xage.data.upload" else "xage.data.panel.${selected.tagId}"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                XAgePanelHeroAsset(category = selected)
                Column(Modifier.weight(1f)) {
                    Text(selected.headline, color = XAgeTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(selected.subtitle, color = Color(0xFF6C8194), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    modifier = Modifier
                        .width(62.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Brush.linearGradient(selected.gradient))
                        .clickable { action() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(selected.actionTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

private enum class XAgePanelCategory(
    val title: String,
    val headline: String,
    val subtitle: String,
    val actionTitle: String,
    val tagId: String,
    val gradient: List<Color>,
    val heroIcon: ImageVector,
) {
    Reports(
        "报告",
        "报告入库",
        "体检、化验、影像",
        "上传",
        "reports",
        listOf(Color(0xFF238AD6), Color(0xFF20CDB1)),
        Icons.Filled.Description,
    ),
    Daily(
        "日常",
        "日常同步",
        "睡眠、步数、HRV",
        "查看",
        "daily",
        listOf(Color(0xFF18B7D6), Color(0xFF34D6A6)),
        Icons.Filled.Favorite,
    ),
    Medical(
        "就医",
        "就医整理",
        "诊断、处方、随访",
        "整理",
        "medical",
        listOf(Color(0xFF4E8FE9), Color(0xFF7BD5F1)),
        Icons.Filled.MedicalServices,
    ),
    Profile(
        "画像",
        "健康画像",
        "基础、慢病、过敏",
        "完善",
        "profile",
        listOf(Color(0xFF2A79C7), Color(0xFF6EE4C6)),
        Icons.Filled.Person,
    ),
}

@Composable
private fun XAgePanelCategoryGlyph(
    category: XAgePanelCategory,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    Brush.linearGradient(category.gradient)
                } else {
                    Brush.linearGradient(listOf(Color(0xFFB8DFF5).copy(alpha = 0.30f), Color(0xFFB8DFF5).copy(alpha = 0.30f)))
                },
            )
            .border(0.8.dp, Color.White.copy(alpha = if (selected) 0.84f else 0.58f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val glyphColor = if (selected) Color.White else Color(0xFF347FB7)
        when (category) {
            XAgePanelCategory.Reports -> Column(
                verticalArrangement = Arrangement.spacedBy(1.6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                listOf(8.dp, 5.8.dp, 7.2.dp).forEach { width ->
                    Box(
                        Modifier
                            .width(width)
                            .height(1.8.dp)
                            .clip(RoundedCornerShape(1.2.dp))
                            .background(glyphColor),
                    )
                }
            }

            XAgePanelCategory.Daily -> Row(
                modifier = Modifier.height(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            ) {
                listOf(5.dp, 9.dp, 6.dp, 11.dp).forEach { height ->
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(height)
                            .clip(RoundedCornerShape(1.2.dp))
                            .background(glyphColor),
                    )
                }
            }

            XAgePanelCategory.Medical -> Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(9.dp)
                        .height(2.2.dp)
                        .clip(RoundedCornerShape(1.1.dp))
                        .background(glyphColor),
                )
                Box(
                    Modifier
                        .width(2.2.dp)
                        .height(9.dp)
                        .clip(RoundedCornerShape(1.1.dp))
                        .background(glyphColor),
                )
            }

            XAgePanelCategory.Profile -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = glyphColor,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

@Composable
private fun XAgePanelHeroAsset(category: XAgePanelCategory) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(category.gradient)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape),
        )
        XAgePanelCategoryGlyph(
            category = category,
            selected = true,
            modifier = Modifier.size(24.dp),
        )
        Icon(
            category.heroIcon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 7.dp, end = 7.dp)
                .size(9.dp),
        )
    }
}

@Composable
private fun XAgeChatPage(
    historySignal: Int,
    vm: ChatViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var analysis by remember { mutableStateOf<ChatMessageItem?>(null) }
    var evidence by remember { mutableStateOf<ChatMessageItem?>(null) }

    LaunchedEffect(Unit) { vm.loadConversations() }
    LaunchedEffect(historySignal) {
        if (historySignal > 0) vm.toggleHistory()
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 34.dp, bottom = 12.dp),
        ) {
            if (state.messages.isEmpty()) {
                item { XAgeChatWelcome(vm) }
            }
            items(state.messages, key = { it.id }) { msg ->
                XAgeChatBubble(
                    msg = msg,
                    onRetry = { vm.retry(msg.id) },
                    onAnalysis = { analysis = msg },
                    onEvidence = { evidence = msg },
                )
            }
            if (state.sending) {
                item {
                    Text(
                        state.thinkingHint.ifBlank { "正在思考…" },
                        modifier = Modifier.xAgeGlass(18.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                        color = Color(0xFF5D7890),
                        fontSize = 14.sp,
                    )
                }
            }
        }
        XAgeChatInput(
            value = state.input,
            sending = state.sending,
            onValueChange = vm::setInput,
            onSend = vm::send,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
        )
    }

    analysis?.let { msg ->
        XAgeGlassDialog(title = "详细分析", onDismiss = { analysis = null }) {
            MarkdownText(msg.analysis ?: "当前回答没有额外分析。")
        }
    }
    evidence?.let { msg ->
        XAgeGlassDialog(title = "证据展示", onDismiss = { evidence = null }) {
            XAgeEvidenceList(msg.citations)
        }
    }
    if (state.showHistory) {
        XAgeHistoryDialog(
            conversations = state.conversations,
            onPick = vm::loadConversation,
            onLoadMore = vm::loadMoreConversations,
            onDismiss = vm::toggleHistory,
        )
    }
}

@Composable
private fun XAgeChatWelcome(vm: ChatViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            XAgeAssistantOrb()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "下午好，想问什么？",
                    color = Color(0xFF111827),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "小捷先帮你问清关键问题。",
                    color = Color(0xFF637083),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(50.dp))
        Text("你可以这样问", color = Color(0xFF111827), fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(28.dp))
        XAgeStarterRow(
            iconKind = "document",
            title = "整理病史摘要",
            subtitle = "诊断、用药、过敏信息",
            primary = true,
        ) {}
        Spacer(Modifier.height(32.dp))
        XAgeStarterRow(
            iconKind = "chart",
            title = "分析报告趋势",
            subtitle = null,
            primary = false,
        ) {
            vm.setInput("帮我分析最近报告趋势")
            vm.send()
        }
    }
}

@Composable
private fun XAgeAssistantOrb() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF00C9A7), Color(0xFF1565C0)))),
        )
        Box(
            Modifier
                .offset(x = 8.dp, y = (-4).dp)
                .width(10.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.26f))
                .blur(1.dp),
        )
    }
}

@Composable
private fun XAgeStarterRow(
    iconKind: String,
    title: String,
    subtitle: String?,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (primary) 84.dp else 66.dp)
            .xAgeGlass(if (primary) 34.dp else 33.dp)
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFE7FAFF).copy(alpha = 0.46f))
                .border(1.dp, Color.White.copy(alpha = 0.62f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            XAgePromptGlyph(iconKind)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color(0xFF111827), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, color = Color(0xFF637083), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("›", color = Color(0xFF6F7F91).copy(alpha = 0.72f), fontSize = 34.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun XAgePromptGlyph(kind: String) {
    if (kind == "chart") {
        Row(
            modifier = Modifier.size(22.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(9.dp, 15.dp, 6.dp).forEach { h ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height(h)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF1565C0), Color(0xFF00C9A7)))),
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .width(17.dp)
                .height(21.dp)
                .clip(RoundedCornerShape(3.dp))
                .border(2.dp, Color(0xFF1565C0), RoundedCornerShape(3.dp)),
        ) {
            Box(Modifier.align(Alignment.TopEnd).size(6.dp).background(Color(0xFF1565C0).copy(alpha = 0.18f)))
            Box(Modifier.offset(x = 4.dp, y = 8.dp).width(9.dp).height(2.dp).background(Color(0xFF1565C0), RoundedCornerShape(1.dp)))
            Box(Modifier.offset(x = 4.dp, y = 13.dp).width(7.dp).height(2.dp).background(Color(0xFF00C9A7), RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
private fun XAgeChatBubble(
    msg: ChatMessageItem,
    onRetry: () -> Unit,
    onAnalysis: () -> Unit,
    onEvidence: () -> Unit,
) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = if (isUser) 286.dp else 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                msg.content,
                modifier = Modifier
                    .clip(RoundedCornerShape(if (isUser) 32.dp else 20.dp))
                    .background(
                        if (isUser) Brush.linearGradient(listOf(Color(0xFF238AD6), Color(0xFF20CDB1)))
                        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.58f), Color.White.copy(alpha = 0.46f)))
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(if (isUser) 32.dp else 20.dp))
                    .padding(horizontal = if (isUser) 17.dp else 15.dp, vertical = if (isUser) 12.dp else 14.dp),
                color = if (isUser) Color.White else Color(0xFF244E6D),
                fontSize = if (isUser) 19.sp else 15.sp,
                fontWeight = if (isUser) FontWeight.Bold else FontWeight.Normal,
                lineHeight = if (isUser) 24.sp else 21.sp,
            )
            msg.status?.let { status ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(status.label, color = Color(0xFF45677F), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (status == ChatDeliveryStatus.Failed) {
                        Text("重试", modifier = Modifier.clickable { onRetry() }, color = Color(0xFF1268BD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!isUser) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!msg.analysis.isNullOrBlank()) XAgeSmallButton("查看分析", onAnalysis)
                    if (msg.citations.isNotEmpty()) XAgeSmallButton("证据展示", onEvidence)
                }
            }
        }
    }
}

@Composable
private fun XAgeChatInput(
    value: String,
    sending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(58.dp)
            .xAgeGlass(29.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Mic, null, tint = Color(0xFF172033), modifier = Modifier.size(24.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("输入或长按说话", fontSize = 14.sp) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Icon(Icons.Filled.CameraAlt, null, tint = Color(0xFF172033), modifier = Modifier.size(22.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.58f))
                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, null, tint = Color(0xFF172033), modifier = Modifier.size(22.dp))
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF228DD8), Color(0xFF1DC8AE))))
                .clickable(enabled = value.isNotBlank() && !sending) { onSend() }
                .testTag("xage.chat.send"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun XAgeEvidenceList(citations: List<Citation>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (citations.isEmpty()) {
            Text("当前回答暂无文献引用。", color = XAgeTextSecondary)
        }
        citations.forEachIndexed { index, citation ->
            Column(Modifier.xAgeGlass(18.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row {
                    Text("[${index + 1}]", color = XjiePalette.Primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(citation.evidence_level, color = XjiePalette.Accent, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(citation.confidence, color = XAgeTextSecondary, fontSize = 12.sp)
                }
                Text(citation.claim_text, color = Color(0xFF244E6D), fontSize = 14.sp)
                Text("${citation.short_ref} · ${citation.journal ?: "source"} · ${citation.year ?: "year"}", color = XAgeTextSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun XAgeHistoryDialog(
    conversations: List<ChatConversation>,
    onPick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    XAgeGlassDialog(title = "历史对话", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .xAgeGlass(22.dp)
                        .padding(horizontal = 18.dp, vertical = 26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无历史对话", color = XAgeTextSecondary, fontSize = 14.sp)
                }
            } else {
                conversations.forEach { conv ->
                    Surface(
                        onClick = {
                            onPick(conv.id)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.54f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.84f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                conv.title ?: "未命名对话",
                                color = XAgeTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row {
                                Text("${conv.message_count ?: 0} 条消息", color = XAgeTextSecondary, fontSize = 12.sp)
                                conv.updated_at?.let {
                                    Text(" · ${it.take(10)}", color = XAgeTextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                XAgeSmallButton("加载更多", onLoadMore, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun XAgeGlassDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C243A).copy(alpha = 0.16f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 670.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFFF8FCFF))
                    .border(1.dp, Color.White.copy(alpha = 0.96f), RoundedCornerShape(30.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        color = XAgeTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.56f))
                            .border(1.dp, Color.White.copy(alpha = 0.84f), CircleShape),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = XAgeTextPrimary, modifier = Modifier.size(18.dp))
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun XAgeHealthspanPage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("X年龄", modifier = Modifier.padding(top = 16.dp), color = XAgeTextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("下次更新：6天后", color = XAgeTextSecondary, fontSize = 13.sp)
        Row(
            modifier = Modifier
                .width(194.dp)
                .height(34.dp)
                .xAgePill(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("‹  6月24日 - 6月30日  ›", color = Color(0xFF347FB7), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(314.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(306.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF8EF7E6).copy(alpha = 0.44f), Color(0xFF21B5FF).copy(alpha = 0.16f), Color.Transparent))).blur(10.dp))
            Image(
                painter = painterResource(R.drawable.x_age_particle_ring_blue_green),
                contentDescription = "蓝绿色粒子圆环",
                modifier = Modifier.size(294.dp).testTag("xage.particle.ring"),
            )
            Box(Modifier.size(178.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.58f)).border(1.dp, Color.White.copy(alpha = 0.78f), CircleShape))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("29.9", color = Color(0xFF12324F), fontSize = 50.sp, fontWeight = FontWeight.Bold)
                Text("X年龄", color = Color(0xFF45677F), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("年轻 4.7 岁", color = Color(0xFF10A88E), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        XAgePaceCard()
        Column(Modifier.xAgeGlass(26.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("稳定且健康", color = XAgeTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "炎症信号较低会减轻生物负担；压力升高会推快衰老进度；恢复因子（HRV、睡眠、静息心率）改善会拉慢进度。",
                color = Color(0xFF496A83),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun XAgePaceCard() {
    Column(Modifier.xAgeGlass(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("衰老进度", color = XAgeTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("0.8x", color = XAgeTextDark, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Row {
            Text("慢", color = Color(0xFF6A8197), fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text("快", color = Color(0xFF6A8197), fontSize = 14.sp)
        }
        Box(Modifier.height(44.dp).horizontalScroll(rememberScrollState()), contentAlignment = Alignment.CenterStart) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(44) { i ->
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(if (i % 10 == 0) 31.dp else 22.dp)
                            .background(Color(0xFF577990).copy(alpha = if (i % 10 == 0) 0.52f else 0.28f), RoundedCornerShape(1.dp)),
                    )
                }
            }
            Box(Modifier.offset(x = 146.dp).width(4.dp).height(40.dp).background(Brush.verticalGradient(listOf(Color.White, Color(0xFF18C3B6))), RoundedCornerShape(2.dp)))
        }
        Row {
            Text("-1.0x", color = Color(0xFF6C8194), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("1.0x", color = Color(0xFF6C8194), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("3.0x", color = Color(0xFF6C8194), fontSize = 12.sp)
        }
    }
}

@Composable
private fun XAgeMenuRow(title: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.86f)),
    ) {
        Text(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), color = XAgeTextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun XAgeSmallButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.widthIn(min = 70.dp).height(34.dp),
        shape = RoundedCornerShape(17.dp),
        color = Color.White.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.88f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                title,
                modifier = Modifier.padding(horizontal = 10.dp),
                color = Color(0xFF365F80),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private data class XAgeMetric(
    val id: String,
    val title: String,
    val value: String,
    val unit: String,
    val subtitle: String,
) {
    companion object {
        val defaults = listOf(
            XAgeMetric("hrv", "心率变异性", "43", "ms", "比 7 日均值低 8%，压力评分的主要贡献项。"),
            XAgeMetric("sleep", "睡眠恢复", "7.2", "h", "深睡和连续性良好，支持恢复评分保持绿色。"),
            XAgeMetric("glucose", "血糖波动", "18", "%", "餐后波动可控，建议继续核对晚餐碳水。"),
            XAgeMetric("temp", "体温偏移", "+0.2", "°C", "轻微偏高，结合炎症和睡眠信号观察。"),
        )
    }
}

private enum class XAgeDataKind(val label: String, val score: Int, val color: Color, val summary: String, val fields: List<Pair<String, String>>) {
    Pressure(
        "压力", 68, Color(0xFF2789D8), "压力处于中等区间，夜间恢复质量和白天负荷是主要变量。",
        listOf("HR残差" to "+6 bpm", "HRV下降" to "-12%", "RHR" to "62 bpm", "呼吸率" to "16.8", "体温" to "+0.2°C", "睡眠债" to "1.4h", "活动负荷" to "中等", "EMA" to "紧张"),
    ),
    Recovery(
        "恢复", 82, Color(0xFF14B887), "恢复状态良好，HRV、睡眠连续性和静息心率共同支持今天的行动能力。",
        listOf("夜间HRV" to "43 ms", "RHR" to "58 bpm", "睡眠指标" to "86%", "呼吸率" to "15.9", "SpO2" to "97%", "体温" to "稳定", "前日负荷" to "适中"),
    ),
    Inflammation(
        "炎症", 57, Color(0xFFEF9A3D), "炎症需要关注，体温、RHR、呼吸率和实验室指标需要持续交叉确认。",
        listOf("hsCRP/IL-6" to "待补充", "CBC/NLR" to "2.1", "体温异常" to "轻微", "RHR异常" to "+3 bpm", "HRV异常" to "-8%", "呼吸异常" to "否", "SpO2异常" to "否", "多组学" to "需复核"),
    ),
}

private fun List<XAgeMetric>.swap(from: Int, to: Int): List<XAgeMetric> = toMutableList().apply {
    val item = removeAt(from)
    add(to, item)
}

private val XAgeTextPrimary = Color(0xFF123E67)
private val XAgeTextDark = Color(0xFF17324E)
private val XAgeTextSecondary = Color(0xFF5D7B95)

private fun Modifier.xAgeGlass(radius: androidx.compose.ui.unit.Dp): Modifier =
    this
        .fillMaxWidth()
        .clip(RoundedCornerShape(radius))
        .background(Color.White.copy(alpha = 0.56f))
        .border(1.dp, Color.White.copy(alpha = 0.84f), RoundedCornerShape(radius))

private fun Modifier.xAgePill(): Modifier =
    this
        .clip(RoundedCornerShape(999.dp))
        .background(Color.White.copy(alpha = 0.58f))
        .border(1.dp, Color.White.copy(alpha = 0.88f), RoundedCornerShape(999.dp))

private fun Modifier.xAgeLiquidBackground(): Modifier =
    background(
        Brush.linearGradient(
            listOf(Color(0xFFE8F7FF), Color(0xFFD5ECFF), Color(0xFFF7FCFF)),
        ),
    )
