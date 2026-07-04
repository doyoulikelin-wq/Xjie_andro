package com.xjie.app.feature.xage

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
import com.xjie.app.feature.healthdata.HealthDataViewModel
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class XAgeSection(val label: String) {
    Data("数据"),
    Chat("问答"),
    XAge("X年龄"),
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun XAgeMainScreen(
    onOpenPanelDestination: (String) -> Unit,
    syncVm: XAgeServerSyncViewModel = hiltViewModel(),
) {
    val sections = remember { XAgeSection.entries }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { sections.size })
    val syncState by syncVm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var chatHistorySignal by remember { mutableStateOf(0) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .xAgeLiquidBackground(),
    ) {
        val adaptive = XAgeAdaptiveMetrics.from(maxWidth, maxHeight)
        CompositionLocalProvider(LocalXAgeAdaptive provides adaptive) {
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
                            syncState = syncState,
                            onOpenPanelDestination = onOpenPanelDestination,
                        )
                        XAgeSection.Chat -> XAgeChatPage(historySignal = chatHistorySignal)
                        XAgeSection.XAge -> XAgeHealthspanPage()
                    }
                }
            }

            if (showMenu) {
                XAgeGlassDialog(title = "XAGE", onDismiss = { showMenu = false }) {
                    sections.forEach { section ->
                        val icon = when (section) {
                            XAgeSection.Data -> Icons.Filled.Sort
                            XAgeSection.Chat -> Icons.AutoMirrored.Filled.Send
                            XAgeSection.XAge -> Icons.Filled.Info
                        }
                        XAgeMenuRow(
                            title = section.label,
                            icon = icon,
                            selected = sections[pagerState.currentPage] == section,
                        ) {
                            showMenu = false
                            scope.launch { pagerState.animateScrollToPage(section.ordinal) }
                        }
                    }
                }
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
    val adaptive = LocalXAgeAdaptive.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = adaptive.topBarHorizontalPadding)
            .padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 8.dp else 10.dp),
    ) {
        Box {
            IconButton(
                onClick = onMenu,
                modifier = Modifier
                    .size(adaptive.topBarButtonSize)
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
                .height(adaptive.segmentHeight)
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
                    fontSize = adaptive.segmentFontSize,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(
            onClick = onTrailingAction,
            modifier = Modifier
                .size(if (selected == XAgeSection.Chat && !adaptive.compactWidth) 38.dp else adaptive.topBarButtonSize)
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
    syncState: XAgeServerSyncState,
    onOpenPanelDestination: (String) -> Unit,
) {
    val adaptive = LocalXAgeAdaptive.current
    var sortMode by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<XAgeDataKind?>(null) }
    var metrics by remember { mutableStateOf(XAgeMetric.defaults) }
    val serverMetrics = remember(syncState.metricCards) { syncState.metricCards.toXAgeMetrics() }
    var showMetricPicker by remember { mutableStateOf(false) }
    var healthSyncStatus by remember { mutableStateOf(XAgeAndroidHealthStatus.Idle) }
    var healthSyncCount by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showsTodayStatus by remember {
        derivedStateOf {
            !adaptive.shortHeight &&
                (sortMode || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 28))
        }
    }
    val availableMetrics = remember(metrics) {
        val currentIds = metrics.map { it.id }.toSet()
        XAgeMetric.androidHealthCandidates.filterNot { it.id in currentIds }
    }
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(serverMetrics) {
        if (serverMetrics.isNotEmpty()) {
            val localOnly = metrics.filter { metric ->
                !metric.id.startsWith("server-") && XAgeMetric.defaults.none { it.id == metric.id }
            }
            metrics = (serverMetrics + localOnly).distinctBy { it.id }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = adaptive.contentHorizontalPadding)
            .padding(bottom = 0.dp),
    ) {
        XAgeDataStickyHeader(
            sortMode = sortMode,
            showsTodayStatus = showsTodayStatus,
            caption = when {
                syncState.isLoading -> "正在同步历史数据"
                syncState.errorMessage != null -> "同步失败 · 显示本地样例"
                else -> syncState.snapshot.headerCaption
            },
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
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    top = 10.dp,
                    bottom = if (sortMode) navigationBottomPadding + 32.dp else adaptive.bottomPanelReserve + 28.dp,
                ),
            ) {
                if (!sortMode) {
                    item(key = "android-health-sync") {
                        XAgeAndroidHealthSyncCard(
                            status = healthSyncStatus,
                            syncedCount = healthSyncCount,
                            onSync = {
                                if (healthSyncStatus == XAgeAndroidHealthStatus.Syncing) return@XAgeAndroidHealthSyncCard
                                scope.launch {
                                    healthSyncStatus = XAgeAndroidHealthStatus.Syncing
                                    delay(420)
                                    val samples = XAgeMetric.androidHealthSamples
                                    metrics = metrics.mergeById(samples)
                                    healthSyncCount = samples.size
                                    healthSyncStatus = XAgeAndroidHealthStatus.Synced
                                }
                            },
                        )
                    }
                }

                itemsIndexed(metrics, key = { _, metric -> metric.id }) { index, metric ->
                    Box(
                        Modifier
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

                if (!sortMode) {
                    item(key = "add-metric") {
                        XAgeAddMetricCard(
                            availableCount = availableMetrics.size,
                            onClick = { if (availableMetrics.isNotEmpty()) showMetricPicker = true },
                        )
                    }
                }
            }

            if (!sortMode) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    XAgeBottomPanel(
                        onOpenCategory = { category -> onOpenPanelDestination(category.tagId) },
                    )
                    if (navigationBottomPadding > 0.dp) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(navigationBottomPadding)
                                .background(Color(0xFFFCFEFF)),
                        )
                    }
                }
            }
        }

        detail?.let { kind ->
            XAgeDataDetailDialog(kind = kind, onDismiss = { detail = null })
        }
        if (showMetricPicker) {
            XAgeMetricCandidateDialog(
                metrics = availableMetrics,
                onDismiss = { showMetricPicker = false },
                onSelect = { metric ->
                    val updatedMetrics = if (metrics.none { it.id == metric.id }) metrics + metric else metrics
                    metrics = updatedMetrics
                    showMetricPicker = false
                    scope.launch {
                        delay(120)
                        val listIndex = updatedMetrics.indexOfFirst { it.id == metric.id }
                        if (listIndex >= 0) {
                            listState.animateScrollToItem(listIndex + 1)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun XAgeDataStickyHeader(
    sortMode: Boolean,
    showsTodayStatus: Boolean,
    caption: String,
    onToggleSort: () -> Unit,
    onSelectDetail: (XAgeDataKind) -> Unit,
) {
    val adaptive = LocalXAgeAdaptive.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (adaptive.shortHeight) 10.dp else 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (adaptive.shortHeight) 8.dp else 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "今日健康数据",
                    color = XAgeTextPrimary,
                    fontSize = adaptive.dataTitleFontSize,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    caption,
                    color = XAgeTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            Surface(
                onClick = onToggleSort,
                modifier = Modifier
                    .width(if (adaptive.compactWidth) 50.dp else 54.dp)
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
            horizontalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 6.dp else 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .xAgeGlass(28.dp)
                .padding(
                    start = adaptive.scoreCardHorizontalPadding,
                    top = if (adaptive.shortHeight) 14.dp else 18.dp,
                    end = adaptive.scoreCardHorizontalPadding,
                    bottom = if (adaptive.shortHeight) 12.dp else 14.dp,
                ),
        ) {
            XAgeScoreRing(XAgeDataKind.Pressure, 68, Modifier.weight(1f)) { onSelectDetail(XAgeDataKind.Pressure) }
            XAgeScoreRing(XAgeDataKind.Recovery, 82, Modifier.weight(1f)) { onSelectDetail(XAgeDataKind.Recovery) }
            XAgeScoreRing(XAgeDataKind.Inflammation, 57, Modifier.weight(1f)) { onSelectDetail(XAgeDataKind.Inflammation) }
        }

        if (showsTodayStatus) {
            XAgeScoreSummaryCard()
        }
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
    val adaptive = LocalXAgeAdaptive.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag("xage.data.score.${kind.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 6.dp else 8.dp),
    ) {
        Box(Modifier.size(adaptive.scoreRingSize), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = adaptive.scoreRingStroke.toPx(), cap = StrokeCap.Round)
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
            Text("$score", color = XAgeTextDark, fontSize = adaptive.scoreNumberFontSize, fontWeight = FontWeight.Bold)
        }
        Text(kind.label, color = Color(0xFF43657F), fontSize = adaptive.scoreLabelFontSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun XAgeScoreSummaryCard() {
    val adaptive = LocalXAgeAdaptive.current
    Column(
        modifier = Modifier.xAgeGlass(24.dp).padding(if (adaptive.compactWidth) 14.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (adaptive.shortHeight) 8.dp else 10.dp),
    ) {
        Text("今日状态", color = XAgeTextPrimary, fontSize = if (adaptive.compactWidth) 16.sp else 17.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 6.dp else 8.dp)) {
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
                    fontSize = if (adaptive.compactWidth) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
private fun XAgeAndroidHealthSyncCard(
    status: XAgeAndroidHealthStatus,
    syncedCount: Int,
    onSync: () -> Unit,
) {
    val adaptive = LocalXAgeAdaptive.current
    val working = status == XAgeAndroidHealthStatus.Syncing
    Column(
        modifier = Modifier
            .xAgeGlass(24.dp)
            .padding(if (adaptive.shortHeight) 14.dp else 16.dp)
            .testTag("xage.androidHealth.sync"),
        verticalArrangement = Arrangement.spacedBy(if (adaptive.shortHeight) 8.dp else 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF238AD6), Color(0xFF20CDB1))))
                    .border(1.dp, Color.White.copy(alpha = 0.56f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Android 健康同步", color = Color(0xFF173F64), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(status.subtitle(syncedCount), color = Color(0xFF6C8194), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
            }
            Surface(
                onClick = onSync,
                enabled = !working,
                modifier = Modifier
                    .width(62.dp)
                    .height(34.dp)
                    .testTag("xage.androidHealth.sync.button"),
                shape = RoundedCornerShape(17.dp),
                color = Color.Transparent,
            ) {
                Box(
                    modifier = Modifier.background(Brush.linearGradient(listOf(Color(0xFF238AD6), Color(0xFF20CDB1)))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (working) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(status.buttonTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (adaptive.shortHeight) {
            Text(
                "${status.title} · ${if (status == XAgeAndroidHealthStatus.Synced) "$syncedCount 项已写入" else "只读授权"} · ${if (syncedCount > 0) syncedCount else XAgeMetric.androidHealthSamples.size} 项本地数据",
                color = Color(0xFF347FB7),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                XAgeSyncBadge(status.title)
                XAgeSyncBadge(if (status == XAgeAndroidHealthStatus.Synced) "$syncedCount 项已写入" else "只读授权")
                XAgeSyncBadge("${if (syncedCount > 0) syncedCount else XAgeMetric.androidHealthSamples.size} 项本地数据")
            }
        }
    }
}

@Composable
private fun RowScope.XAgeSyncBadge(title: String) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(28.dp)
            .xAgePill(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 6.dp),
            color = Color(0xFF347FB7),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
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
    val adaptive = LocalXAgeAdaptive.current
    Column(
        modifier = Modifier.xAgeGlass(24.dp).padding(horizontal = adaptive.metricCardHorizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(metric.accent, Color(0xFF20CDB1)))),
            )
            Text(
                metric.title,
                modifier = Modifier.weight(1f),
                color = metric.accent,
                fontSize = if (adaptive.compactWidth) 16.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(metric.time, color = Color(0xFF6A8198), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text("›", color = Color(0xFFA0B1C0), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                metric.value,
                color = Color(0xFF101C2F),
                fontSize = if (metric.value.length > 4) 27.sp else 31.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (metric.unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(metric.unit, color = Color(0xFF70879D), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
        }

        Text(
            metric.subtitle,
            color = Color(0xFF657E94),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = if (sortMode) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )

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
private fun XAgeAddMetricCard(
    availableCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = availableCount > 0,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .testTag("xage.data.metric.add"),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.88f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF238AD6), Color(0xFF20CDB1))))
                    .border(1.dp, Color.White.copy(alpha = 0.56f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (availableCount == 0) "全部指标已添加" else "添加指标",
                    color = Color(0xFF173F64),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    if (availableCount == 0) "候选列表暂无新项目" else "从 Android 健康候选项中选择",
                    color = Color(0xFF6C8194),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(30.dp)
                    .xAgePill(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (availableCount == 0) "完成" else "${availableCount}项",
                    color = Color(0xFF347FB7),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun XAgeMetricCandidateDialog(
    metrics: List<XAgeMetric>,
    onDismiss: () -> Unit,
    onSelect: (XAgeMetric) -> Unit,
) {
    XAgeGlassDialog(
        title = "添加指标",
        onDismiss = onDismiss,
        dismissOnClickOutside = false,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "参照 Android 健康可记录项目",
                color = Color(0xFF5D7890),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .height(32.dp)
                    .xAgePill(),
                contentAlignment = Alignment.Center,
            ) {
                Text("${metrics.size} 项", color = Color(0xFF347FB7), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (metrics.isEmpty()) {
            Column(
                modifier = Modifier.xAgeGlass(24.dp).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("已添加全部候选指标", color = Color(0xFF173F64), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("主界面下拉列表已经包含所有候选项。", color = Color(0xFF6C8194), fontSize = 13.sp)
            }
        } else {
            metrics.forEach { metric ->
                XAgeMetricCandidateRow(metric = metric) {
                    onSelect(metric)
                }
            }
        }
    }
}

@Composable
private fun XAgeMetricCandidateRow(
    metric: XAgeMetric,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag("xage.data.metric.candidate.${metric.id}"),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.84f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(metric.accent, Color(0xFF20CDB1)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(metric.title, color = Color(0xFF173F64), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(metric.time, color = metric.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Text(
                    metric.subtitle,
                    color = Color(0xFF6C8194),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    metric.value,
                    color = Color(0xFF12324F),
                    fontSize = if (metric.value.length > 4) 18.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (metric.unit.isNotEmpty()) {
                    Spacer(Modifier.width(2.dp))
                    Text(metric.unit, color = Color(0xFF6C8194), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF238AD6), Color(0xFF20CDB1))))
                    .border(1.dp, Color.White.copy(alpha = 0.72f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = "添加", tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun XAgeBottomPanel(
    onOpenCategory: (XAgePanelCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalXAgeAdaptive.current
    var selected by remember { mutableStateOf(XAgePanelCategory.Reports) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFFFCFEFF))
            .border(1.dp, Color.White.copy(alpha = 0.90f), RoundedCornerShape(30.dp))
            .padding(
                start = adaptive.bottomPanelHorizontalPadding,
                top = adaptive.bottomPanelTopPadding,
                end = adaptive.bottomPanelHorizontalPadding,
                bottom = adaptive.bottomPanelBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(if (adaptive.shortHeight || adaptive.compactWidth) 6.dp else 14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 6.dp else 8.dp)) {
            XAgePanelCategory.entries.forEach { category ->
                val active = selected == category
                Surface(
                    onClick = { selected = category },
                    modifier = Modifier
                        .weight(1f)
                        .height(adaptive.bottomPanelCategoryHeight),
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
                            modifier = Modifier.size(if (adaptive.compactWidth) 16.dp else 18.dp),
                        )
                        Spacer(Modifier.width(if (adaptive.compactWidth) 3.dp else 5.dp))
                        Text(
                            category.title,
                            color = if (active) Color(0xFF1268BD) else Color(0xFF5D7890),
                            fontSize = adaptive.bottomPanelCategoryFontSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.82f))
                .border(1.dp, Color.White.copy(alpha = 0.82f), RoundedCornerShape(24.dp))
                .clickable { onOpenCategory(selected) }
                .testTag(if (selected == XAgePanelCategory.Reports) "xage.data.upload" else "xage.data.panel.${selected.tagId}"),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 14.dp,
                    vertical = if (adaptive.shortHeight || adaptive.compactWidth) 6.dp else 12.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                XAgePanelHeroAsset(category = selected)
                Column(Modifier.weight(1f)) {
                    Text(selected.headline, color = XAgeTextPrimary, fontSize = if (adaptive.compactWidth) 16.sp else 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(selected.subtitle, color = Color(0xFF6C8194), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    modifier = Modifier
                        .width(adaptive.bottomPanelActionWidth)
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Brush.linearGradient(selected.gradient))
                        .clickable { onOpenCategory(selected) },
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
    );

    val detailSummary: String
        get() = when (this) {
            Reports -> "把体检、化验和影像资料先入库，小捷会在后台识别结构化字段，并提示缺失项。"
            Daily -> "聚合睡眠、步数、HRV 和训练负荷，用来解释当天压力、恢复和炎症评分变化。"
            Medical -> "把诊断、处方和随访整理成连续时间线，方便下一次问诊前快速回顾。"
            Profile -> "维护基础资料、慢病、过敏和长期用药，让问答和计划生成更贴近个人状态。"
        }

    val stats: List<XAgePanelStat>
        get() = when (this) {
            Reports -> listOf(
                XAgePanelStat("待识别", "3", "份"),
                XAgePanelStat("已结构化", "18", "项"),
                XAgePanelStat("完整度", "76", "%"),
            )
            Daily -> listOf(
                XAgePanelStat("睡眠", "7:18", ""),
                XAgePanelStat("步数", "8.2k", ""),
                XAgePanelStat("HRV", "43", "ms"),
            )
            Medical -> listOf(
                XAgePanelStat("诊断", "4", "条"),
                XAgePanelStat("处方", "2", "组"),
                XAgePanelStat("随访", "1", "次"),
            )
            Profile -> listOf(
                XAgePanelStat("基础", "92", "%"),
                XAgePanelStat("慢病", "2", "项"),
                XAgePanelStat("过敏", "1", "项"),
            )
        }

    val rows: List<XAgePanelRow>
        get() = when (this) {
            Reports -> listOf(
                XAgePanelRow(Icons.Filled.CameraAlt, "拍照上传", "体检报告、化验单、影像截图"),
                XAgePanelRow(Icons.Filled.Description, "AI 识别队列", "抽取指标、异常值和参考范围"),
                XAgePanelRow(Icons.Filled.Check, "需要确认", "核对姓名、日期和关键指标"),
            )
            Daily -> listOf(
                XAgePanelRow(Icons.Filled.Favorite, "Android 健康", "同步睡眠、步数、静息心率"),
                XAgePanelRow(Icons.Filled.Refresh, "恢复信号", "HRV、呼吸率和训练负荷"),
                XAgePanelRow(Icons.Filled.Sort, "趋势解释", "连接日常变化与三项评分"),
            )
            Medical -> listOf(
                XAgePanelRow(Icons.Filled.Description, "诊断摘要", "按科室和时间整理病程"),
                XAgePanelRow(Icons.Filled.MedicalServices, "处方核对", "剂量、频次和注意事项"),
                XAgePanelRow(Icons.Filled.Refresh, "随访提醒", "复诊、复查和报告回传"),
            )
            Profile -> listOf(
                XAgePanelRow(Icons.Filled.Person, "基础资料", "年龄、身高、体重和目标"),
                XAgePanelRow(Icons.Filled.CloudUpload, "长期标签", "慢病、家族史和风险因素"),
                XAgePanelRow(Icons.Filled.Info, "安全信息", "过敏、禁忌和长期用药"),
            )
        }

    companion object {
        fun fromTagId(id: String): XAgePanelCategory =
            entries.firstOrNull { it.tagId == id } ?: Reports
    }
}

private data class XAgePanelStat(
    val title: String,
    val value: String,
    val unit: String,
)

private fun XAgeServerSyncSnapshot.statsFor(category: XAgePanelCategory): List<XAgePanelStat> =
    when (category) {
        XAgePanelCategory.Reports -> listOf(
            XAgePanelStat("病历", "$recordCount", "份"),
            XAgePanelStat("体检", "$examCount", "份"),
            XAgePanelStat("指标", "$indicatorCount", "项"),
        )
        XAgePanelCategory.Daily -> listOf(
            XAgePanelStat("关注", "$watchedIndicatorCount", "项"),
            XAgePanelStat("趋势", "$trendPointCount", "点"),
            XAgePanelStat("目标", "$todayGoalCount", "条"),
        )
        XAgePanelCategory.Medical -> listOf(
            XAgePanelStat("计划", "$planCount", "个"),
            XAgePanelStat("问答", "$conversationCount", "次"),
            XAgePanelStat("反馈", "$feedbackCount", "条"),
        )
        XAgePanelCategory.Profile -> listOf(
            XAgePanelStat("基础", "$profileCompletion", "%"),
            XAgePanelStat("摘要", if (hasSummary) "有" else "待", ""),
            XAgePanelStat("评分", dashboardScore?.toString() ?: "--", ""),
        )
    }

private data class XAgePanelRow(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
) {
    val key: String get() = title.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "-")
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
fun XAgePanelDestinationScreen(
    categoryId: String,
    onBack: () -> Unit,
    syncVm: XAgeServerSyncViewModel = hiltViewModel(),
) {
    val category = remember(categoryId) { XAgePanelCategory.fromTagId(categoryId) }
    val syncState by syncVm.state.collectAsState()
    val snapshot = syncState.snapshot
    var selectedRow by remember(category) { mutableStateOf(category.rows.first()) }
    var completedActionIds by remember(category) { mutableStateOf(setOf<String>()) }
    var selectedTagIds by remember(category) { mutableStateOf(setOf<String>()) }
    var primaryActionCount by remember(category) { mutableStateOf(0) }
    var healthSyncStatus by remember(category) { mutableStateOf(XAgeAndroidHealthStatus.Idle) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .xAgeLiquidBackground(),
    ) {
        val adaptive = XAgeAdaptiveMetrics.from(maxWidth, maxHeight)
        CompositionLocalProvider(LocalXAgeAdaptive provides adaptive) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = adaptive.contentHorizontalPadding)
                    .padding(bottom = if (adaptive.shortHeight) 24.dp else 30.dp),
                verticalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 12.dp else 16.dp),
            ) {
                XAgePanelDestinationHeader(
                    category = category,
                    onBack = onBack,
                    modifier = Modifier.padding(top = if (adaptive.shortHeight) 12.dp else 18.dp),
                )

                Column(
                    modifier = Modifier
                        .xAgeGlass(28.dp)
                        .padding(if (adaptive.compactWidth) 16.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 12.dp else 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 10.dp else 14.dp),
                    ) {
                        Box(Modifier.size(if (adaptive.compactWidth) 56.dp else 62.dp), contentAlignment = Alignment.Center) {
                            XAgePanelHeroAsset(category = category)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                category.headline,
                                color = XAgeTextPrimary,
                                fontSize = if (adaptive.compactWidth) 24.sp else 27.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                category.subtitle,
                                color = Color(0xFF5D7890),
                                fontSize = if (adaptive.compactWidth) 13.sp else 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Text(
                        category.detailSummary,
                        color = Color(0xFF496A83),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 7.dp else 9.dp)) {
                    snapshot.statsFor(category).forEach { stat ->
                        XAgePanelStatCard(
                            stat = stat,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    category.rows.forEach { row ->
                        val selected = selectedRow.title == row.title
                        XAgePanelActionRow(
                            category = category,
                            row = row,
                            isSelected = selected,
                            trailingTitle = if (selected) "查看中" else null,
                            showsProgress = category == XAgePanelCategory.Daily &&
                                row.title == "Android 健康" &&
                                healthSyncStatus == XAgeAndroidHealthStatus.Syncing,
                            onClick = {
                                selectedRow = row
                                if (category == XAgePanelCategory.Daily && row.title == "Android 健康") {
                                    scope.launch {
                                        healthSyncStatus = XAgeAndroidHealthStatus.Syncing
                                        delay(420)
                                        healthSyncStatus = XAgeAndroidHealthStatus.Synced
                                    }
                                }
                            },
                        )
                    }
                }

                XAgePanelInteractiveDetail(
                    category = category,
                    row = selectedRow,
                    completedActionIds = completedActionIds,
                    selectedTagIds = selectedTagIds,
                    primaryActionCount = primaryActionCount,
                    healthSyncStatus = healthSyncStatus,
                    snapshot = snapshot,
                    onToggleAction = { key ->
                        completedActionIds = if (key in completedActionIds) completedActionIds - key else completedActionIds + key
                    },
                    onToggleTag = { key ->
                        selectedTagIds = if (key in selectedTagIds) selectedTagIds - key else selectedTagIds + key
                    },
                    onHealthSync = {
                        scope.launch {
                            healthSyncStatus = XAgeAndroidHealthStatus.Syncing
                            delay(420)
                            healthSyncStatus = XAgeAndroidHealthStatus.Synced
                        }
                    },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Brush.linearGradient(category.gradient))
                        .clickable {
                            primaryActionCount += 1
                            completedActionIds = completedActionIds + "primary-${category.tagId}-${selectedRow.key}-$primaryActionCount"
                            if (category == XAgePanelCategory.Daily && selectedRow.title == "Android 健康") {
                                scope.launch {
                                    healthSyncStatus = XAgeAndroidHealthStatus.Syncing
                                    delay(420)
                                    healthSyncStatus = XAgeAndroidHealthStatus.Synced
                                }
                            }
                        }
                        .testTag("xage.panel.destination.${category.tagId}.cta"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (primaryActionCount > 0) "已更新" else category.primaryButtonTitle(selectedRow),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun XAgePanelDestinationHeader(
    category: XAgePanelCategory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("xage.panel.destination.${category.tagId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(34.dp)
                .xAgePill()
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color(0xFF347FB7),
                modifier = Modifier.size(17.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .height(34.dp)
                .xAgePill()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                category.title,
                color = XAgeTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .width(42.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Brush.linearGradient(category.gradient))
                .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                category.heroIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun XAgePanelStatCard(
    stat: XAgePanelStat,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalXAgeAdaptive.current
    Column(
        modifier = modifier
            .height(if (adaptive.compactWidth) 66.dp else 70.dp)
            .xAgeGlass(22.dp)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stat.title,
            color = Color(0xFF6C8194),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                stat.value,
                color = Color(0xFF12324F),
                fontSize = if (adaptive.compactWidth) 18.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (stat.unit.isNotEmpty()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    stat.unit,
                    color = Color(0xFF6C8194),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun XAgePanelActionRow(
    category: XAgePanelCategory,
    row: XAgePanelRow,
    isSelected: Boolean,
    trailingTitle: String?,
    showsProgress: Boolean,
    onClick: () -> Unit,
) {
    val adaptive = LocalXAgeAdaptive.current
    Row(
        modifier = Modifier
            .height(if (adaptive.compactWidth) 62.dp else 66.dp)
            .xAgeGlass(22.dp)
            .border(
                1.2.dp,
                if (isSelected) (category.gradient.lastOrNull() ?: Color(0xFF20CDB1)).copy(alpha = 0.58f) else Color.Transparent,
                RoundedCornerShape(22.dp),
            )
            .clickable { onClick() }
            .testTag("xage.panel.${category.tagId}.row.${row.key}")
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 10.dp else 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (adaptive.compactWidth) 34.dp else 38.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(category.gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                row.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                row.title,
                color = Color(0xFF173F64),
                fontSize = if (adaptive.compactWidth) 15.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.subtitle,
                color = Color(0xFF6C8194),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            showsProgress -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color(0xFF347FB7),
                strokeWidth = 2.dp,
            )
            trailingTitle != null -> Box(
                modifier = Modifier
                    .width(if (adaptive.compactWidth) 64.dp else 72.dp)
                    .height(30.dp)
                    .xAgePill(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    trailingTitle,
                    color = Color(0xFF347FB7),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            else -> Text(
                "›",
                color = Color(0xFF7D9AB1),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun XAgePanelCategory.primaryButtonTitle(row: XAgePanelRow): String =
    when (this) {
        XAgePanelCategory.Reports -> if (row.title == "拍照上传") "开始入库" else "确认并入库"
        XAgePanelCategory.Daily -> if (row.title == "Android 健康") "同步日常数据" else "更新日常解释"
        XAgePanelCategory.Medical -> if (row.title == "随访提醒") "保存提醒" else "整理到时间线"
        XAgePanelCategory.Profile -> if (row.title == "安全信息") "保存安全信息" else "保存画像"
    }

@Composable
private fun XAgePanelInteractiveDetail(
    category: XAgePanelCategory,
    row: XAgePanelRow,
    completedActionIds: Set<String>,
    selectedTagIds: Set<String>,
    primaryActionCount: Int,
    healthSyncStatus: XAgeAndroidHealthStatus,
    snapshot: XAgeServerSyncSnapshot,
    onToggleAction: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onHealthSync: () -> Unit,
) {
    val adaptive = LocalXAgeAdaptive.current
    Column(
        modifier = Modifier
            .xAgeGlass(24.dp)
            .padding(if (adaptive.compactWidth) 14.dp else 16.dp)
            .testTag("xage.panel.${category.tagId}.detail.${row.key}"),
        verticalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 11.dp else 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.title, color = Color(0xFF173F64), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(category.detailSubtitle, color = Color(0xFF6C8194), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
            }
            Box(
                modifier = Modifier
                    .width(if (adaptive.compactWidth) 58.dp else 62.dp)
                    .height(28.dp)
                    .xAgePill(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (primaryActionCount > 0) "已更新" else "可编辑",
                    color = Color(0xFF347FB7),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        when (category) {
            XAgePanelCategory.Reports -> XAgePanelReportsDetail(category, row, snapshot, completedActionIds, selectedTagIds, onToggleAction, onToggleTag)
            XAgePanelCategory.Daily -> XAgePanelDailyDetail(category, row, snapshot, completedActionIds, selectedTagIds, healthSyncStatus, onToggleAction, onToggleTag, onHealthSync)
            XAgePanelCategory.Medical -> XAgePanelMedicalDetail(category, row, snapshot, completedActionIds, selectedTagIds, onToggleAction, onToggleTag)
            XAgePanelCategory.Profile -> XAgePanelProfileDetail(category, row, snapshot, completedActionIds, selectedTagIds, onToggleAction, onToggleTag)
        }
    }
}

private val XAgePanelCategory.detailSubtitle: String
    get() = when (this) {
        XAgePanelCategory.Reports -> "选择入口、检查识别队列，并确认关键报告字段。"
        XAgePanelCategory.Daily -> "把可穿戴与日常信号转成今天的压力、恢复、炎症解释。"
        XAgePanelCategory.Medical -> "把就医资料整理成时间线、处方核对和复查提醒。"
        XAgePanelCategory.Profile -> "维护画像信息，让问答、计划和风险提示更贴近个人状态。"
    }

@Composable
private fun XAgePanelReportsDetail(
    category: XAgePanelCategory,
    row: XAgePanelRow,
    snapshot: XAgeServerSyncSnapshot,
    completedActionIds: Set<String>,
    selectedTagIds: Set<String>,
    onToggleAction: (String) -> Unit,
    onToggleTag: (String) -> Unit,
) {
    when (row.title) {
        "拍照上传" -> {
            XAgePanelChipRow(category, row, listOf("拍照", "选 PDF", "相册"), selectedTagIds, onToggleTag)
            XAgePanelToggleRow(category, "姓名与报告一致", "未匹配时会进入人工确认", actionState(category, row, "name", completedActionIds), onToggleAction)
            XAgePanelToggleRow(category, "最近报告 ${snapshot.latestDocumentLabel}", "用于排列时间线和趋势", actionState(category, row, "date", completedActionIds), onToggleAction)
            XAgePanelToggleRow(category, "${snapshot.indicatorCount} 项指标已入库", "新增报告确认后会继续写入用户端数据", actionState(category, row, "indicators", completedActionIds), onToggleAction)
        }
        "AI 识别队列" -> {
            XAgePanelProgressLine(category, "病历资料", progress(snapshot.recordCount, cap = 20), "${snapshot.recordCount} 份")
            XAgePanelProgressLine(category, "体检化验", progress(snapshot.examCount, cap = 300), "${snapshot.examCount} 份")
            XAgePanelProgressLine(category, "指标趋势", progress(snapshot.indicatorCount, cap = 300), "${snapshot.indicatorCount} 项")
            XAgePanelChipRow(category, row, listOf("仅异常", "全部字段"), selectedTagIds, onToggleTag)
        }
        else -> {
            XAgePanelToggleRow(category, snapshot.primaryWatchedLabel, "${snapshot.trendPointCount} 个历史趋势点可用于复核", actionState(category, row, "watched", completedActionIds), onToggleAction)
            XAgePanelToggleRow(category, "健康摘要", if (snapshot.hasSummary) "已生成，可作为问答上下文" else "暂无摘要，建议生成后再问答", actionState(category, row, "summary", completedActionIds), onToggleAction)
            XAgePanelToggleRow(category, "报告日期 ${snapshot.latestDocumentLabel}", "确认后会用于排序", actionState(category, row, "report-date", completedActionIds), onToggleAction)
        }
    }
}

@Composable
private fun XAgePanelDailyDetail(
    category: XAgePanelCategory,
    row: XAgePanelRow,
    snapshot: XAgeServerSyncSnapshot,
    completedActionIds: Set<String>,
    selectedTagIds: Set<String>,
    healthSyncStatus: XAgeAndroidHealthStatus,
    onToggleAction: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onHealthSync: () -> Unit,
) {
    when (row.title) {
        "Android 健康" -> {
            Text(
                healthSyncStatus.subtitle(XAgeMetric.androidHealthSamples.size),
                color = Color(0xFF496A83),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XAgeSyncBadge(healthSyncStatus.title)
                XAgeSyncBadge("${XAgeMetric.androidHealthSamples.size} 项")
                XAgeSyncBadge("只读授权")
            }
            Surface(
                onClick = onHealthSync,
                enabled = healthSyncStatus != XAgeAndroidHealthStatus.Syncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("xage.panel.daily.detail.androidHealth.sync"),
                shape = RoundedCornerShape(19.dp),
                color = Color.Transparent,
            ) {
                Box(
                    modifier = Modifier.background(Brush.linearGradient(category.gradient)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (healthSyncStatus == XAgeAndroidHealthStatus.Syncing) "同步中" else "立即同步",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        "恢复信号" -> {
            XAgePanelProgressLine(category, "关注指标", progress(snapshot.watchedIndicatorCount, cap = 8), "${snapshot.watchedIndicatorCount} 项")
            XAgePanelProgressLine(category, "历史趋势", progress(snapshot.trendPointCount, cap = 60), "${snapshot.trendPointCount} 点")
            XAgePanelProgressLine(category, "今日目标", progress(snapshot.todayGoalCount, cap = 5), "${snapshot.todayGoalCount} 条")
            XAgePanelChipRow(category, row, listOf("用于恢复", "加入压力解释"), selectedTagIds, onToggleTag)
        }
        else -> {
            XAgePanelToggleRow(category, "关注 ${snapshot.primaryWatchedLabel}", "已同步服务端关注指标", actionState(category, row, "watched", completedActionIds), onToggleAction)
            XAgePanelToggleRow(category, "趋势点 ${snapshot.trendPointCount}", "用于解释日常变化与评分", actionState(category, row, "trend-points", completedActionIds), onToggleAction)
            XAgePanelToggleRow(category, "健康摘要", if (snapshot.hasSummary) "已接入问答上下文" else "等待生成摘要", actionState(category, row, "daily-summary", completedActionIds), onToggleAction)
        }
    }
}

@Composable
private fun XAgePanelMedicalDetail(
    category: XAgePanelCategory,
    row: XAgePanelRow,
    snapshot: XAgeServerSyncSnapshot,
    completedActionIds: Set<String>,
    selectedTagIds: Set<String>,
    onToggleAction: (String) -> Unit,
    onToggleTag: (String) -> Unit,
) {
    when (row.title) {
        "诊断摘要" -> {
            XAgePanelTimelineRow(category, snapshot.latestDocumentLabel, "最近报告", "已同步 ${snapshot.recordCount + snapshot.examCount} 份文档")
            XAgePanelTimelineRow(category, "问答记录", "历史咨询", "已同步 ${snapshot.conversationCount} 次对话")
            XAgePanelToggleRow(category, "生成问诊前摘要", if (snapshot.hasSummary) "可直接引用健康摘要" else "建议先生成健康摘要", actionState(category, row, "visit-summary", completedActionIds), onToggleAction)
        }
        "处方核对" -> {
            XAgePanelToggleRow(category, "健康计划 ${snapshot.planCount} 个", "可用于核对执行和提醒", actionState(category, row, "plans", completedActionIds), onToggleAction)
            XAgePanelToggleRow(category, "已入库指标 ${snapshot.indicatorCount} 项", "处方核对时结合关键检验值", actionState(category, row, "medicine-indicators", completedActionIds), onToggleAction)
            XAgePanelToggleRow(category, "提醒医生复核", "结合最新报告和健康摘要", actionState(category, row, "dose-check", completedActionIds), onToggleAction)
        }
        else -> {
            XAgePanelChipRow(category, row, listOf("下周", "一月内", "报告回传"), selectedTagIds, onToggleTag)
            XAgePanelToggleRow(category, "最近报告 ${snapshot.latestDocumentLabel}", "问诊前优先回看", actionState(category, row, "latest-report", completedActionIds), onToggleAction)
            XAgePanelToggleRow(category, "把新报告带到问诊", "上传后自动更新摘要和指标", actionState(category, row, "upload-next", completedActionIds), onToggleAction)
        }
    }
}

@Composable
private fun XAgePanelProfileDetail(
    category: XAgePanelCategory,
    row: XAgePanelRow,
    snapshot: XAgeServerSyncSnapshot,
    completedActionIds: Set<String>,
    selectedTagIds: Set<String>,
    onToggleAction: (String) -> Unit,
    onToggleTag: (String) -> Unit,
) {
    when (row.title) {
        "基础资料" -> {
            XAgePanelProgressLine(category, "资料完整度", snapshot.profileCompletion / 100f, "${snapshot.profileCompletion}%")
            XAgePanelChipRow(category, row, listOf("减脂", "控糖", "提升睡眠"), selectedTagIds, onToggleTag)
            XAgePanelToggleRow(category, "同步体重到画像", "来自 Android 健康或手动记录", actionState(category, row, "weight", completedActionIds), onToggleAction)
        }
        "长期标签" -> {
            XAgePanelChipRow(category, row, listOf("${snapshot.indicatorCount}项指标", "${snapshot.watchedIndicatorCount}项关注", "${snapshot.planCount}个计划"), selectedTagIds, onToggleTag)
            XAgePanelChipRow(category, row, listOf("历史报告", "问答上下文"), selectedTagIds, onToggleTag)
        }
        else -> {
            XAgePanelToggleRow(category, "健康摘要", if (snapshot.hasSummary) "已同步，可辅助风险提示" else "暂无摘要", actionState(category, row, "summary", completedActionIds), onToggleAction)
            XAgePanelToggleRow(category, "长期用药提示", "处方核对时避免冲突", actionState(category, row, "medicine", completedActionIds), onToggleAction)
            XAgePanelToggleRow(category, "家庭共享需单独授权", "默认不共享敏感健康资料", actionState(category, row, "family", completedActionIds), onToggleAction)
        }
    }
}

@Composable
private fun XAgePanelChipRow(
    category: XAgePanelCategory,
    row: XAgePanelRow,
    titles: List<String>,
    selectedTagIds: Set<String>,
    onToggleTag: (String) -> Unit,
) {
    val adaptive = LocalXAgeAdaptive.current
    Row(horizontalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 6.dp else 9.dp)) {
        titles.forEach { title ->
            val key = tagKey(category, row, title)
            val selected = key in selectedTagIds
            Surface(
                onClick = { onToggleTag(key) },
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp),
                shape = RoundedCornerShape(17.dp),
                color = if (selected) Color.Transparent else Color.White.copy(alpha = 0.62f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.78f)),
            ) {
                Box(
                    modifier = if (selected) Modifier.background(Brush.linearGradient(category.gradient)) else Modifier,
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        color = if (selected) Color.White else Color(0xFF347FB7),
                        fontSize = if (adaptive.compactWidth) 11.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun XAgePanelToggleRow(
    category: XAgePanelCategory,
    title: String,
    subtitle: String,
    state: Pair<String, Boolean>,
    onToggleAction: (String) -> Unit,
) {
    val (key, done) = state
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (done) 0.72f else 0.48f))
            .border(
                1.dp,
                if (done) (category.gradient.lastOrNull() ?: Color(0xFF20CDB1)).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.70f),
                RoundedCornerShape(18.dp),
            )
            .clickable { onToggleAction(key) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = if (done) (category.gradient.lastOrNull() ?: Color(0xFF20CDB1)) else Color(0xFF9BB6C9),
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color(0xFF173F64), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color(0xFF6C8194), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun XAgePanelProgressLine(
    category: XAgePanelCategory,
    title: String,
    value: Float,
    trailing: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.48f))
            .border(1.dp, Color.White.copy(alpha = 0.70f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row {
            Text(title, color = Color(0xFF173F64), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(trailing, color = Color(0xFF347FB7), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.54f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.linearGradient(category.gradient)),
            )
        }
    }
}

@Composable
private fun XAgePanelTimelineRow(
    category: XAgePanelCategory,
    date: String,
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.48f))
            .border(1.dp, Color.White.copy(alpha = 0.70f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(category.gradient)),
            )
            Box(
                Modifier
                    .width(2.dp)
                    .height(34.dp)
                    .background(Color(0xFFB9DDF2).copy(alpha = 0.60f)),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(date, color = Color(0xFF347FB7), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(title, color = Color(0xFF173F64), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, color = Color(0xFF6C8194), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2)
        }
    }
}

private fun actionState(
    category: XAgePanelCategory,
    row: XAgePanelRow,
    value: String,
    completedActionIds: Set<String>,
): Pair<String, Boolean> {
    val key = "${category.tagId}-${row.key}-action-$value"
    return key to (key in completedActionIds)
}

private fun tagKey(category: XAgePanelCategory, row: XAgePanelRow, value: String): String =
    "${category.tagId}-${row.key}-tag-$value"

private fun progress(value: Int, cap: Int): Float =
    if (cap <= 0) 0f else (value.toFloat() / cap.toFloat()).coerceIn(0f, 1f)

@Composable
private fun XAgeChatPage(
    historySignal: Int,
    vm: ChatViewModel = hiltViewModel(),
    uploadVm: HealthDataViewModel = hiltViewModel(),
) {
    val adaptive = LocalXAgeAdaptive.current
    val state by vm.state.collectAsState()
    val uploadState by uploadVm.state.collectAsState()
    val context = LocalContext.current
    var analysis by remember { mutableStateOf<ChatMessageItem?>(null) }
    var evidence by remember { mutableStateOf<ChatMessageItem?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var transientMessage by remember { mutableStateOf<String?>(null) }
    var pendingCameraUpload by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    fun sendReportPrompt(fileName: String) {
        val prompt = xAgeReportAnalysisPrompt(fileName)
        if (state.sending) {
            vm.setInput(prompt)
        } else {
            vm.sendText(prompt)
        }
    }

    fun uploadReport(uri: Uri, fileName: String) {
        uploadVm.setUploadDocType("exam")
        uploadVm.uploadFile(uri, fileName)
        sendReportPrompt(fileName)
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            uploadReport(it, displayNameFromUri(context, it))
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { uploadReport(it, displayNameFromUri(context, it).ifBlank { "xage_report_album.jpg" }) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val target = pendingCameraUpload
        if (success && target != null) {
            uploadReport(target.first, target.second)
        } else {
            transientMessage = "未完成拍照上传，可从相册或文件重新选择。"
        }
        pendingCameraUpload = null
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val target = pendingCameraUpload
        if (granted && target != null) {
            runCatching { cameraLauncher.launch(target.first) }
                .onFailure {
                    pendingCameraUpload = null
                    transientMessage = "无法打开相机，可从相册或文件选择报告。"
                }
        } else {
            pendingCameraUpload = null
            transientMessage = "请允许相机权限后再拍照上传报告。"
        }
    }
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                vm.setInput(text)
            } else {
                transientMessage = "未识别到语音内容，请再试一次或直接输入文字。"
            }
        } else {
            transientMessage = "语音输入已取消。"
        }
    }

    LaunchedEffect(Unit) { vm.loadConversations() }
    LaunchedEffect(historySignal) {
        if (historySignal > 0) vm.toggleHistory()
    }
    LaunchedEffect(transientMessage) {
        if (transientMessage != null) {
            delay(2600)
            transientMessage = null
        }
    }
    LaunchedEffect(uploadState.toast) {
        if (uploadState.toast != null) {
            delay(2600)
            uploadVm.clearToast()
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = adaptive.contentHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = adaptive.chatTopPadding, bottom = 12.dp),
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
            if (uploadState.uploading || uploadState.backgroundTaskHint != null || uploadState.toast != null || uploadState.error != null || transientMessage != null) {
                item {
                    XAgeChatUploadStatusCard(
                        uploading = uploadState.uploading,
                        title = when {
                            transientMessage != null -> "提示"
                            uploadState.uploading -> uploadState.uploadStage.ifBlank { "正在上传报告…" }
                            uploadState.error != null -> "上传失败"
                            uploadState.toast != null -> uploadState.toast ?: "上传成功"
                            else -> "报告已上传，AI 正在识别"
                        },
                        subtitle = transientMessage
                            ?: uploadState.error
                            ?: uploadState.backgroundTaskHint
                            ?: "完成后会继续进入问答解读。",
                    )
                }
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
            isUploading = uploadState.uploading,
            onMicTap = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要问小捷的问题")
                }
                runCatching { speechLauncher.launch(intent) }
                    .onFailure { transientMessage = "当前设备未安装语音识别服务，可直接输入文字。" }
            },
            onCameraTap = {
                val target = createXAgeReportImageUri(context)
                pendingCameraUpload = target
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    runCatching { cameraLauncher.launch(target.first) }
                        .onFailure {
                            pendingCameraUpload = null
                            transientMessage = "无法打开相机，可从相册或文件选择报告。"
                        }
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onPlusTap = { showAttachmentMenu = true },
            modifier = Modifier
                .padding(horizontal = adaptive.contentHorizontalPadding)
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
    if (showAttachmentMenu) {
        XAgeChatAttachmentMenu(
            onDismiss = { showAttachmentMenu = false },
            onPickDocument = {
                showAttachmentMenu = false
                documentPicker.launch(arrayOf("application/pdf", "image/*"))
            },
            onPickImage = {
                showAttachmentMenu = false
                imagePicker.launch("image/*")
            },
            onNewChat = {
                showAttachmentMenu = false
                vm.newChat()
            },
        )
    }
}

@Composable
private fun XAgeChatWelcome(vm: ChatViewModel) {
    val adaptive = LocalXAgeAdaptive.current
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
                    fontSize = if (adaptive.compactWidth) 22.sp else 25.sp,
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
        Spacer(Modifier.height(adaptive.chatWelcomeGap))
        Text("你可以这样问", color = Color(0xFF111827), fontSize = if (adaptive.compactWidth) 19.sp else 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(adaptive.chatStarterGap))
        XAgeStarterRow(
            iconKind = "document",
            title = "整理病史摘要",
            subtitle = "诊断、用药、过敏信息",
            primary = true,
        ) {}
        Spacer(Modifier.height(if (adaptive.shortHeight) 22.dp else 32.dp))
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
    val adaptive = LocalXAgeAdaptive.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (primary) {
                if (adaptive.shortHeight) 76.dp else 84.dp
            } else {
                if (adaptive.shortHeight) 60.dp else 66.dp
            })
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
    val adaptive = LocalXAgeAdaptive.current
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = if (isUser) {
                if (adaptive.compactWidth) 260.dp else 286.dp
            } else {
                if (adaptive.compactWidth) 288.dp else 320.dp
            }),
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
    isUploading: Boolean,
    onMicTap: () -> Unit,
    onCameraTap: () -> Unit,
    onPlusTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalXAgeAdaptive.current
    Row(
        modifier = modifier
            .height(adaptive.chatInputHeight)
            .xAgeGlass(29.dp)
            .padding(horizontal = if (adaptive.compactWidth) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(adaptive.chatInputSpacing),
    ) {
        Box(
            modifier = Modifier
                .size(adaptive.chatToolButtonSize)
                .clip(CircleShape)
                .clickable { onMicTap() }
                .testTag("xage.chat.mic"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Mic, contentDescription = "语音输入", tint = Color(0xFF172033), modifier = Modifier.size(24.dp))
        }
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
        Box(
            modifier = Modifier
                .size(if (adaptive.compactWidth) 28.dp else 30.dp)
                .clip(CircleShape)
                .clickable(enabled = !isUploading) { onCameraTap() }
                .testTag("xage.chat.camera"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.CameraAlt,
                contentDescription = "拍照上传报告",
                tint = Color(0xFF172033).copy(alpha = if (isUploading) 0.34f else 1f),
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(adaptive.chatToolButtonSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.58f))
                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                .clickable(enabled = !isUploading) { onPlusTap() }
                .testTag("xage.chat.plus"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "添加内容",
                tint = Color(0xFF172033).copy(alpha = if (isUploading) 0.34f else 1f),
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(if (adaptive.compactWidth) 34.dp else 36.dp)
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
private fun XAgeChatUploadStatusCard(
    uploading: Boolean,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .xAgeGlass(22.dp)
            .padding(14.dp)
            .testTag("xage.chat.upload.status"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.52f))
                .border(1.dp, Color.White.copy(alpha = 0.70f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (uploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color(0xFF159D8F),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF159D8F), modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color(0xFF173F64), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(subtitle, color = Color(0xFF5D7890), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3)
        }
    }
}

@Composable
private fun XAgeChatAttachmentMenu(
    onDismiss: () -> Unit,
    onPickDocument: () -> Unit,
    onPickImage: () -> Unit,
    onNewChat: () -> Unit,
) {
    XAgeGlassDialog(title = "添加内容", onDismiss = onDismiss) {
        XAgeAttachmentMenuRow(Icons.Filled.Description, "选择 PDF / 图片报告", "上传体检、化验或影像截图", onPickDocument)
        XAgeAttachmentMenuRow(Icons.Filled.CameraAlt, "从相册上传报告", "选择已拍好的报告图片", onPickImage)
        XAgeAttachmentMenuRow(Icons.Filled.Refresh, "新对话", "清空当前 XAGE 问答上下文", onNewChat)
    }
}

@Composable
private fun XAgeAttachmentMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.84f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF238AD6), Color(0xFF20CDB1)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color(0xFF173F64), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color(0xFF6C8194), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = Color(0xFF7D9AB1), fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C243A).copy(alpha = 0.16f)),
        ) {
            val constrainedWidth = maxWidth
            val constrainedHeight = maxHeight
            val compactDialog = constrainedWidth < 360.dp
            val horizontalPadding = if (compactDialog) 14.dp else 20.dp
            val verticalPadding = if (constrainedHeight < 720.dp) 20.dp else 34.dp
            val maxDialogHeight = if (constrainedHeight < 720.dp) constrainedHeight - 40.dp else 670.dp
            val dialogMaxWidth = if (constrainedWidth >= 600.dp) 460.dp else constrainedWidth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (constrainedWidth >= 600.dp) 0.6f else 1f)
                        .widthIn(max = dialogMaxWidth)
                        .heightIn(max = maxDialogHeight)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xFFF8FCFF))
                        .border(1.dp, Color.White.copy(alpha = 0.96f), RoundedCornerShape(30.dp))
                        .padding(if (compactDialog) 16.dp else 18.dp),
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
}

@Composable
private fun XAgeHealthspanPage() {
    val adaptive = LocalXAgeAdaptive.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = adaptive.contentHorizontalPadding)
            .navigationBarsPadding()
            .padding(bottom = if (adaptive.shortHeight) 36.dp else 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (adaptive.shortHeight) 9.dp else 12.dp),
    ) {
        Text("X年龄", modifier = Modifier.padding(top = if (adaptive.shortHeight) 10.dp else 16.dp), color = XAgeTextPrimary, fontSize = if (adaptive.compactWidth) 23.sp else 25.sp, fontWeight = FontWeight.Bold)
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
        Box(Modifier.size(adaptive.healthRingContainerSize), contentAlignment = Alignment.Center) {
            Box(Modifier.size(adaptive.healthRingContainerSize - 8.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF8EF7E6).copy(alpha = 0.44f), Color(0xFF21B5FF).copy(alpha = 0.16f), Color.Transparent))).blur(10.dp))
            Image(
                painter = painterResource(R.drawable.x_age_particle_ring_blue_green),
                contentDescription = "蓝绿色粒子圆环",
                modifier = Modifier.size(adaptive.healthRingImageSize).testTag("xage.particle.ring"),
            )
            Box(Modifier.size(adaptive.healthRingInnerSize).clip(CircleShape).background(Color.White.copy(alpha = 0.58f)).border(1.dp, Color.White.copy(alpha = 0.78f), CircleShape))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("29.9", color = Color(0xFF12324F), fontSize = adaptive.healthAgeFontSize, fontWeight = FontWeight.Bold)
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
private fun XAgeMenuRow(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, if (selected) Color(0xFF20CDB1).copy(alpha = 0.48f) else Color.White.copy(alpha = 0.86f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            if (selected) listOf(Color(0xFF238AD6), Color(0xFF20CDB1)) else listOf(Color(0xFF7ABBE7), Color(0xFF92DDCE)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Text(title, modifier = Modifier.weight(1f), color = Color(0xFF173F64), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF16A88E), modifier = Modifier.size(15.dp))
            } else {
                Text("›", color = Color(0xFF7D9AB1), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
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
    val time: String,
    val subtitle: String,
    val accent: Color,
) {
    companion object {
        val defaults = listOf(
            XAgeMetric("hrv", "心率变异性", "43", "ms", "07:10", "比 7 日均值低 8%，压力评分的主要贡献项。", Color(0xFF7B4DFF)),
            XAgeMetric("sleep", "睡眠", "7小时18分", "", "昨夜", "深睡和连续性良好，支持恢复评分保持绿色。", Color(0xFF14B887)),
            XAgeMetric("glucose", "血糖波动", "18", "%", "餐后", "餐后波动可控，建议继续核对晚餐碳水。", Color(0xFF11A7C8)),
            XAgeMetric("temp", "体温偏移", "+0.2", "°C", "夜间", "轻微偏高，结合炎症和睡眠信号观察。", Color(0xFFEF9A3D)),
        )

        val androidHealthCandidates = listOf(
            XAgeMetric("steps", "步数", "8,240", "步", "今日", "活动量基线，可解释压力和恢复的日内变化。", Color(0xFF238AD6)),
            XAgeMetric("distance", "步行+跑步距离", "5.6", "km", "今日", "补充步数之外的移动距离和通勤负荷。", Color(0xFF18B7D6)),
            XAgeMetric("activeEnergy", "活动能量", "486", "kcal", "今日", "运动和日常活动消耗，用于判断恢复压力。", Color(0xFFEF9A3D)),
            XAgeMetric("exerciseMinutes", "运动分钟", "42", "min", "今日", "中高强度活动时间，辅助解释训练负荷。", Color(0xFF14B887)),
            XAgeMetric("flights", "爬楼层数", "9", "层", "今日", "反映爬升活动，补足平地步数的盲区。", Color(0xFF4E8FE9)),
            XAgeMetric("restingHeartRate", "静息心率", "58", "bpm", "晨间", "静息心率偏移可提示恢复、压力和感染风险。", Color(0xFFF05B72)),
            XAgeMetric("respiratoryRate", "呼吸频率", "15.9", "次/分", "夜间", "夜间呼吸频率用于恢复和异常筛查。", Color(0xFF2A79C7)),
            XAgeMetric("bloodOxygen", "血氧", "97", "%", "夜间", "血氧变化可辅助睡眠和呼吸风险判断。", Color(0xFF7B4DFF)),
            XAgeMetric("bloodPressure", "血压", "118/76", "mmHg", "最近", "可手动记录或由设备同步，形成心血管基线。", Color(0xFFDB5B9B)),
            XAgeMetric("bodyWeight", "体重", "62.4", "kg", "今天", "体重趋势帮助解释代谢和计划执行效果。", Color(0xFF11A7C8)),
            XAgeMetric("bodyFat", "体脂率", "23", "%", "最近", "身体成分变化可补充长期健康画像。", Color(0xFFA47BEF)),
            XAgeMetric("mindfulMinutes", "正念分钟", "8", "min", "今天", "正念记录作为压力管理和恢复行为输入。", Color(0xFF20CDB1)),
            XAgeMetric("daylight", "日照时间", "36", "min", "今天", "户外日照可影响节律、睡眠和情绪状态。", Color(0xFFF3B349)),
        )

        val androidHealthSamples = listOf(
            androidHealthCandidates.first { it.id == "steps" }.copy(time = "Health Connect", subtitle = "今日步数已写入服务器指标趋势。"),
            androidHealthCandidates.first { it.id == "distance" }.copy(time = "Health Connect", subtitle = "今日步行和跑步距离已同步。"),
            androidHealthCandidates.first { it.id == "restingHeartRate" }.copy(time = "Health Connect", subtitle = "最近一次静息心率已更新到用户端趋势。"),
        )
    }
}

private enum class XAgeAndroidHealthStatus {
    Idle,
    Syncing,
    Synced;

    val title: String
        get() = when (this) {
            Idle -> "未授权同步"
            Syncing -> "正在读取"
            Synced -> "同步完成"
        }

    val buttonTitle: String
        get() = when (this) {
            Idle -> "授权"
            Syncing -> "同步中"
            Synced -> "同步"
        }

    fun subtitle(count: Int): String = when (this) {
        Idle -> "授权后读取 Health Connect 可用的步数、睡眠、HRV、静息心率等指标。"
        Syncing -> "正在汇总今日累计值和最近一次测量值。"
        Synced -> "用户端数据卡已刷新，$count 项本地健康数据已合并。"
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

private fun List<XAgeMetric>.mergeById(samples: List<XAgeMetric>): List<XAgeMetric> {
    val updated = toMutableList()
    samples.forEach { sample ->
        val index = updated.indexOfFirst { it.id == sample.id }
        if (index >= 0) {
            updated[index] = sample
        } else {
            updated += sample
        }
    }
    return updated
}

private fun List<XAgeServerMetric>.toXAgeMetrics(): List<XAgeMetric> =
    map { metric ->
        XAgeMetric(
            id = metric.id,
            title = metric.title,
            value = metric.value,
            unit = metric.unit,
            time = metric.time,
            subtitle = metric.subtitle,
            accent = Color(metric.accentArgb),
        )
    }

private fun xAgeReportAnalysisPrompt(fileName: String): String =
    "我刚上传了一份体检/化验报告（$fileName）。请结合我的健康档案和这份报告的识别结果，帮我总结关键指标、异常项、趋势变化和下一步建议。若后台识别仍在进行，请先说明正在识别，并告诉我完成后应该重点关注哪些项目。"

private fun displayNameFromUri(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            val name = cursor.getString(index)
            if (!name.isNullOrBlank()) return name
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "xage_report_upload.jpg"
}

private fun createXAgeReportImageUri(context: Context): Pair<Uri, String> {
    val dir = File(context.cacheDir, "xage_reports").apply { mkdirs() }
    val fileName = "xage_report_camera_${System.currentTimeMillis()}.jpg"
    val file = File(dir, fileName)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return uri to fileName
}

private val LocalXAgeAdaptive = compositionLocalOf { XAgeAdaptiveMetrics.Default }

private data class XAgeAdaptiveMetrics(
    val compactWidth: Boolean,
    val shortHeight: Boolean,
    val contentHorizontalPadding: Dp,
    val topBarHorizontalPadding: Dp,
    val topBarButtonSize: Dp,
    val segmentHeight: Dp,
    val segmentFontSize: TextUnit,
    val dataTitleFontSize: TextUnit,
    val scoreRingSize: Dp,
    val scoreRingStroke: Dp,
    val scoreNumberFontSize: TextUnit,
    val scoreLabelFontSize: TextUnit,
    val scoreCardHorizontalPadding: Dp,
    val bottomPanelReserve: Dp,
    val bottomPanelHorizontalPadding: Dp,
    val bottomPanelTopPadding: Dp,
    val bottomPanelBottomPadding: Dp,
    val bottomPanelCategoryHeight: Dp,
    val bottomPanelCategoryFontSize: TextUnit,
    val bottomPanelActionWidth: Dp,
    val metricCardHorizontalPadding: Dp,
    val chatTopPadding: Dp,
    val chatWelcomeGap: Dp,
    val chatStarterGap: Dp,
    val chatInputHeight: Dp,
    val chatInputSpacing: Dp,
    val chatToolButtonSize: Dp,
    val healthRingContainerSize: Dp,
    val healthRingImageSize: Dp,
    val healthRingInnerSize: Dp,
    val healthAgeFontSize: TextUnit,
) {
    companion object {
        val Default = from(390.dp, 844.dp)

        fun from(width: Dp, height: Dp): XAgeAdaptiveMetrics {
            val compact = width < 360.dp
            val short = height < 720.dp
            val wide = width >= 600.dp
            val contentPadding = when {
                compact -> 16.dp
                width < 420.dp -> 20.dp
                wide -> 32.dp
                else -> 24.dp
            }
            val ringContainer = when {
                compact -> 270.dp
                short -> 286.dp
                width < 390.dp -> 298.dp
                else -> 314.dp
            }
            return XAgeAdaptiveMetrics(
                compactWidth = compact,
                shortHeight = short,
                contentHorizontalPadding = contentPadding,
                topBarHorizontalPadding = if (compact) 14.dp else 20.dp,
                topBarButtonSize = if (compact) 32.dp else 34.dp,
                segmentHeight = if (compact) 44.dp else 48.dp,
                segmentFontSize = if (compact) 14.sp else 15.sp,
                dataTitleFontSize = if (compact) 24.sp else 27.sp,
                scoreRingSize = when {
                    compact -> 76.dp
                    width < 390.dp -> 84.dp
                    else -> 90.dp
                },
                scoreRingStroke = if (compact) 7.dp else 9.dp,
                scoreNumberFontSize = if (compact) 22.sp else 25.sp,
                scoreLabelFontSize = if (compact) 12.sp else 13.sp,
                scoreCardHorizontalPadding = if (compact) 8.dp else 12.dp,
                bottomPanelReserve = when {
                    compact -> 184.dp
                    short -> 194.dp
                    else -> 210.dp
                },
                bottomPanelHorizontalPadding = if (compact) 14.dp else 20.dp,
                bottomPanelTopPadding = if (compact || short) 10.dp else 22.dp,
                bottomPanelBottomPadding = if (compact || short) 8.dp else 34.dp,
                bottomPanelCategoryHeight = if (compact || short) 30.dp else 36.dp,
                bottomPanelCategoryFontSize = if (compact) 10.sp else 11.sp,
                bottomPanelActionWidth = if (compact) 56.dp else 62.dp,
                metricCardHorizontalPadding = if (compact) 16.dp else 20.dp,
                chatTopPadding = if (short) 24.dp else 34.dp,
                chatWelcomeGap = if (short) 34.dp else 50.dp,
                chatStarterGap = if (short) 20.dp else 28.dp,
                chatInputHeight = if (compact) 54.dp else 58.dp,
                chatInputSpacing = if (compact) 6.dp else 8.dp,
                chatToolButtonSize = if (compact) 30.dp else 32.dp,
                healthRingContainerSize = ringContainer,
                healthRingImageSize = ringContainer - if (compact) 24.dp else 20.dp,
                healthRingInnerSize = when {
                    compact -> 150.dp
                    short -> 162.dp
                    else -> 178.dp
                },
                healthAgeFontSize = if (compact || short) 44.sp else 50.sp,
            )
        }
    }
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
