package com.xjie.app.feature.xage

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.ChatConversation
import com.xjie.app.R
import com.xjie.app.core.model.IndicatorTrend
import com.xjie.app.core.ui.components.MarkdownText
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.feature.chat.ChatDeliveryStatus
import com.xjie.app.feature.chat.ChatCitationReference
import com.xjie.app.feature.chat.ChatMessageItem
import com.xjie.app.feature.chat.ChatPresentationPolicy
import com.xjie.app.feature.chat.ChatViewModel
import com.xjie.app.feature.chat.hasDistinctAnalysis
import com.xjie.app.feature.chat.relevantCitationReferences
import com.xjie.app.feature.healthconnect.HealthConnectCardPresentationPolicy
import com.xjie.app.feature.healthconnect.HealthConnectCardSurface
import com.xjie.app.feature.healthconnect.HealthConnectPermissionRequester
import com.xjie.app.feature.healthconnect.HealthConnectSyncPhase
import com.xjie.app.feature.healthconnect.HealthConnectSyncUiState
import com.xjie.app.feature.healthconnect.HealthConnectSyncViewModel
import com.xjie.app.feature.healthdata.HealthDataViewModel
import com.xjie.app.feature.healthdata.IndicatorTrendChart
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class XAgeReportUploadAction {
    Camera,
    Document,
    PhotoLibrary,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XAgeMainScreen(
    onOpenPanelDestination: (String) -> Unit,
    syncVm: XAgeServerSyncViewModel = hiltViewModel(),
    healthConnectVm: HealthConnectSyncViewModel = hiltViewModel(),
) {
    val syncState by syncVm.state.collectAsState()
    val healthConnectState by healthConnectVm.state.collectAsState()
    val dailyScores = remember(syncState.snapshot) {
        XAgeDailyScorePresentationPolicy.presentation(syncState.snapshot)
    }
    val pagerState = rememberPagerState(
        initialPage = XAgeShellState().normalizedPage,
        pageCount = { XAgeShellState.pageCount },
    )
    val selectedSection = XAgeShellState.sectionForPage(pagerState.currentPage)
    val shellScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var dataManagerSignal by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var chatHistorySignal by remember { mutableStateOf(0) }
    var xAgeInfoSignal by remember { mutableStateOf(0) }

    fun dismissKeyboard() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) dismissKeyboard()
        }
    }

    HealthConnectPermissionRequester(healthConnectVm)
    LaunchedEffect(healthConnectState.phase, healthConnectState.syncedCount) {
        if (healthConnectState.phase == HealthConnectSyncPhase.Success) syncVm.refresh()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .xAgeLiquidBackground(),
    ) {
        val adaptive = XAgeAdaptiveMetrics.from(maxWidth, maxHeight)
        CompositionLocalProvider(LocalXAgeAdaptive provides adaptive) {
            Column(Modifier.fillMaxSize()) {
                XAgeTopBar(
                    selected = selectedSection,
                    onSelect = { section ->
                        dismissKeyboard()
                        shellScope.launch {
                            pagerState.animateScrollToPage(section.ordinal)
                        }
                    },
                    onMenu = {
                        dismissKeyboard()
                        showMenu = true
                    },
                    onTrailingAction = {
                        dismissKeyboard()
                        when (selectedSection) {
                            XAgeSection.Data -> dataManagerSignal += 1
                            XAgeSection.Chat -> chatHistorySignal += 1
                            XAgeSection.XAge -> xAgeInfoSignal += 1
                        }
                    },
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { stateDescription = "当前页：${selectedSection.label}" }
                        .testTag("xage.shell.pager"),
                    beyondViewportPageCount = 0,
                    key = { page -> XAgeSection.entries[page].name },
                ) { page ->
                    when (XAgeShellState.sectionForPage(page)) {
                        XAgeSection.Data -> XAgeDataPage(
                            syncState = syncState,
                            scores = dailyScores,
                            accountScope = syncState.accountScope,
                            healthConnectState = healthConnectState,
                            onHealthConnectSync = healthConnectVm::requestSync,
                            managerSignal = dataManagerSignal,
                            onOpenDestination = { destination ->
                                dismissKeyboard()
                                onOpenPanelDestination(destination)
                            },
                        )
                        XAgeSection.Chat -> XAgeChatPage(historySignal = chatHistorySignal)
                        XAgeSection.XAge -> XAgeHealthspanPage(infoSignal = xAgeInfoSignal)
                    }
                }
            }

            if (showMenu) {
                XAgeGlassDialog(title = "更多", onDismiss = { showMenu = false }) {
                    Text("资料与设备", color = XAgeTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    XAgeMenuRow(
                        title = "画像",
                        subtitle = "查看和维护已确认的健康画像",
                        icon = Icons.Filled.Person,
                        selected = false,
                        tag = "xage.more.profile",
                    ) {
                        showMenu = false
                        dismissKeyboard()
                        onOpenPanelDestination(XAgeInformationArchitecture.PROFILE_DESTINATION)
                    }
                    XAgeMenuRow(
                        title = "设备管理",
                        subtitle = "当前设备绑定能力与支持状态",
                        icon = Icons.Filled.DevicesOther,
                        selected = false,
                        tag = "xage.more.device",
                    ) {
                        showMenu = false
                        dismissKeyboard()
                        onOpenPanelDestination(XAgeInformationArchitecture.DEVICE_DESTINATION)
                    }
                    Text("账号管理", color = XAgeTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    XAgeMenuRow(
                        title = "账号与安全",
                        subtitle = "查看手机号、修改密码或注销账号",
                        icon = Icons.Filled.Person,
                        selected = false,
                        tag = "xage.more.account",
                    ) {
                        showMenu = false
                        dismissKeyboard()
                        onOpenPanelDestination(XAgeInformationArchitecture.ACCOUNT_DESTINATION)
                    }
                    XAgeMenuRow(
                        title = "关联用户",
                        subtitle = "家庭模式、邀请和单独授权",
                        icon = Icons.Filled.Group,
                        selected = false,
                        tag = "xage.more.family",
                    ) {
                        showMenu = false
                        dismissKeyboard()
                        onOpenPanelDestination(XAgeInformationArchitecture.FAMILY_DESTINATION)
                    }
                    Text("帮助与关于", color = XAgeTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    XAgeMenuRow(
                        title = "使用帮助",
                        subtitle = "查看报告、指标和健康同步操作",
                        icon = Icons.AutoMirrored.Filled.Help,
                        selected = false,
                        tag = "xage.support.help",
                    ) {
                        showMenu = false
                        dismissKeyboard()
                        onOpenPanelDestination(XAgeInformationArchitecture.SUPPORT_HELP_DESTINATION)
                    }
                    XAgeMenuRow(
                        title = "版本信息",
                        subtitle = "查看当前版本与备案信息",
                        icon = Icons.Filled.Info,
                        selected = false,
                        tag = "xage.support.version",
                    ) {
                        showMenu = false
                        dismissKeyboard()
                        onOpenPanelDestination(XAgeInformationArchitecture.SUPPORT_VERSION_DESTINATION)
                    }
                    XAgeMenuRow(
                        title = "隐私政策",
                        subtitle = "了解数据处理方式和你的权利",
                        icon = Icons.Filled.PrivacyTip,
                        selected = false,
                        tag = "xage.support.privacy",
                    ) {
                        showMenu = false
                        dismissKeyboard()
                        onOpenPanelDestination(XAgeInformationArchitecture.SUPPORT_PRIVACY_DESTINATION)
                    }
                    XAgeMenuRow(
                        title = "权限申请与使用情况说明",
                        subtitle = "查看权限申请时机、用途和拒绝影响",
                        icon = Icons.AutoMirrored.Filled.ListAlt,
                        selected = false,
                        tag = "xage.support.permissions",
                    ) {
                        showMenu = false
                        dismissKeyboard()
                        onOpenPanelDestination(XAgeInformationArchitecture.SUPPORT_PERMISSIONS_DESTINATION)
                    }
                    XAgeMenuRow(
                        title = "意见反馈",
                        subtitle = "提交问题、建议或数据异常",
                        icon = Icons.Filled.Edit,
                        selected = false,
                        tag = "xage.support.feedback",
                    ) {
                        showMenu = false
                        dismissKeyboard()
                        onOpenPanelDestination(XAgeInformationArchitecture.SUPPORT_FEEDBACK_DESTINATION)
                    }
                    Text(
                        "备案号：皖ICP备2026008853号-2",
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF7D9AB1),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
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
        IconButton(
            onClick = onMenu,
            modifier = Modifier
                .size(48.dp)
                .testTag("xage.more"),
        ) {
            Icon(Icons.Filled.Menu, contentDescription = "更多", tint = XAgeTextPrimary)
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

        val trailingTouchModifier = if (selected == XAgeSection.Data) {
            Modifier
                .width(if (adaptive.compactWidth) 50.dp else 54.dp)
                .height(48.dp)
                .testTag("xage.data.manage")
        } else {
            Modifier.size(48.dp)
        }
        val trailingSurfaceModifier = if (selected == XAgeSection.Data) {
            Modifier
                .width(if (adaptive.compactWidth) 50.dp else 54.dp)
                .height(34.dp)
        } else {
            Modifier.size(if (selected == XAgeSection.Chat && !adaptive.compactWidth) 38.dp else adaptive.topBarButtonSize)
        }

        IconButton(
            onClick = onTrailingAction,
            modifier = trailingTouchModifier,
        ) {
            Box(
                modifier = trailingSurfaceModifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.48f))
                    .border(1.dp, Color.White.copy(alpha = 0.86f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected == XAgeSection.Data) {
                    Text(
                        XAgeInformationArchitecture.DATA_MANAGER_TITLE,
                        color = Color(0xFF1268BD),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                } else {
                    Icon(
                        if (selected == XAgeSection.Chat) Icons.Filled.Refresh else Icons.Filled.Info,
                        contentDescription = if (selected == XAgeSection.Chat) "历史" else "说明",
                        tint = if (selected == XAgeSection.Chat) XAgeTextPrimary else Color(0xFF2A79BB),
                        modifier = Modifier.size(if (selected == XAgeSection.Chat) 20.dp else 17.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun XAgeDataPage(
    syncState: XAgeServerSyncState,
    scores: XAgeCompositeScores,
    accountScope: String?,
    healthConnectState: HealthConnectSyncUiState,
    onHealthConnectSync: () -> Unit,
    managerSignal: Int,
    onOpenDestination: (String) -> Unit,
) {
    val adaptive = LocalXAgeAdaptive.current
    val context = LocalContext.current
    val cardLayoutStore = remember(context) { XAgeCardLayoutStore(context) }
    val quickActionOrderStore = remember(context) { XAgeQuickActionOrderStore(context) }
    var cardLayout by remember(accountScope) {
        mutableStateOf(cardLayoutStore.load(accountScope))
    }
    var quickActionOrder by remember(accountScope) {
        mutableStateOf(quickActionOrderStore.load(accountScope))
    }
    var detail by remember { mutableStateOf<XAgeDataKind?>(null) }
    var scoreInfoDetail by remember { mutableStateOf<XAgeDataKind?>(null) }
    var confidenceDetail by remember { mutableStateOf<XAgeDataKind?>(null) }
    var selectedMetric by remember { mutableStateOf<XAgeMetric?>(null) }
    val serverMetrics = remember(syncState.metricCards) { syncState.metricCards.toXAgeMetrics() }
    val candidateMetrics = XAgeMetric.androidHealthCandidates
    val metricCatalog = remember(serverMetrics) {
        (serverMetrics + candidateMetrics).distinctBy { it.id }.associateBy { it.id }
    }
    val metrics = remember(serverMetrics, cardLayout) {
        cardLayout.visibleIds(
            serverIds = serverMetrics.map { it.id },
            candidateIds = candidateMetrics.map { it.id },
        ).mapNotNull(metricCatalog::get)
    }
    var showDataManager by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val showsTodayStatus by remember {
        derivedStateOf {
            !adaptive.shortHeight &&
                (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 28)
        }
    }
    val availableMetrics = remember(metricCatalog, metrics) {
        val currentIds = metrics.map { it.id }.toSet()
        metricCatalog.values.filterNot { it.id in currentIds }
    }
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val orderedQuickActions = remember(quickActionOrder) {
        quickActionOrder.resolvedIds().mapNotNull(XAgeQuickActionRegistry::action)
    }

    fun saveCardLayout(next: XAgeCardLayoutState) {
        cardLayout = next
        cardLayoutStore.save(accountScope, next)
    }

    LaunchedEffect(managerSignal) {
        if (managerSignal > 0) showDataManager = true
    }

    if (showDataManager) {
        XAgeDataManagerPage(
            metrics = metrics,
            availableMetrics = availableMetrics,
            healthConnectState = healthConnectState,
            onHealthConnectSync = onHealthConnectSync,
            onBack = { showDataManager = false },
            onMoveUp = { index ->
                if (index > 0) saveCardLayout(cardLayout.withVisibleOrder(metrics.swap(index, index - 1).map { it.id }))
            },
            onMoveDown = { index ->
                if (index < metrics.lastIndex) saveCardLayout(cardLayout.withVisibleOrder(metrics.swap(index, index + 1).map { it.id }))
            },
            onRemove = { metric ->
                saveCardLayout(
                    cardLayout.removing(
                        id = metric.id,
                        isServer = metric.id.startsWith("server-"),
                        visibleIds = metrics.map { it.id },
                    ),
                )
            },
            onAdd = { metric ->
                if (metrics.none { it.id == metric.id }) {
                    saveCardLayout(
                        cardLayout.adding(
                            id = metric.id,
                            isServer = metric.id.startsWith("server-"),
                            visibleIds = metrics.map { it.id },
                        ),
                    )
                }
            },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = adaptive.contentHorizontalPadding)
            .padding(bottom = 0.dp)
            .then(
                if (syncState.snapshot.isLoaded) Modifier.testTag("xage.data.metrics.loaded")
                else Modifier,
            ),
    ) {
        XAgeDataStickyHeader(
            showsTodayStatus = showsTodayStatus,
            scores = scores,
            caption = when {
                syncState.isLoading -> "正在同步历史数据"
                syncState.errorMessage != null -> "同步失败 · 未显示模拟数据"
                else -> syncState.snapshot.headerCaption
            },
            onSelectDetail = { detail = it },
            onSelectInfo = { scoreInfoDetail = it },
            onSelectConfidence = { confidenceDetail = it },
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
                    .testTag("xage.data.scroll"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    top = 10.dp,
                    bottom = navigationBottomPadding + 32.dp,
                ),
            ) {
                item(key = "quick-actions") {
                    XAgeQuickActionStrip(
                        actions = orderedQuickActions,
                        onOpenDestination = onOpenDestination,
                        onMove = { id, targetIndex ->
                            val next = quickActionOrder.moving(id, targetIndex)
                            quickActionOrder = next
                            quickActionOrderStore.save(accountScope, next)
                        },
                    )
                }

                if (!healthConnectState.hasSuccessfulSync) {
                    item(key = "health-connect-entry") {
                        XAgeHealthConnectSyncCard(
                            state = healthConnectState,
                            onSync = onHealthConnectSync,
                            surface = com.xjie.app.feature.healthconnect.HealthConnectCardSurface.CompactHome,
                        )
                    }
                }

                items(metrics, key = { metric -> metric.id }) { metric ->
                    Box(
                        Modifier
                            .testTag("xage.data.metric.${metric.id}"),
                    ) {
                        XAgeMetricCard(
                            metric = metric,
                            sortMode = false,
                            onClick = {
                                XAgeInformationArchitecture.destinationForMetric(metric.id)
                                    ?.let(onOpenDestination)
                                    ?: run { selectedMetric = metric }
                            },
                            onMoveUp = {},
                            onMoveDown = {},
                        )
                    }
                }

                if (metrics.isEmpty()) {
                    item(key = "empty-metrics") {
                        XAgeMetricEmptyRow()
                    }
                }
            }
        }

        detail?.let { kind ->
            XAgeDataDetailDialog(
                kind = kind,
                metric = scores.score(kind),
                onDismiss = { detail = null },
            )
        }
        scoreInfoDetail?.let { kind ->
            XAgeScoreInfoDialog(
                kind = kind,
                metric = scores.score(kind),
                onDismiss = { scoreInfoDetail = null },
            )
        }
        confidenceDetail?.let { kind ->
            XAgeScoreConfidenceDialog(
                kind = kind,
                metric = scores.score(kind),
                onDismiss = { confidenceDetail = null },
            )
        }
        selectedMetric?.let { metric ->
            XAgeMetricDetailDialog(metric = metric, onDismiss = { selectedMetric = null })
        }
    }
}

@Composable
private fun XAgeDataStickyHeader(
    showsTodayStatus: Boolean,
    scores: XAgeCompositeScores,
    caption: String,
    onSelectDetail: (XAgeDataKind) -> Unit,
    onSelectInfo: (XAgeDataKind) -> Unit,
    onSelectConfidence: (XAgeDataKind) -> Unit,
) {
    val adaptive = LocalXAgeAdaptive.current
    val chrome = XAgeHomeChromePresentationPolicy.header(caption)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("xage.data.header")
            .semantics { stateDescription = chrome.semanticStatus }
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
                chrome.visibleCaption?.let { visibleCaption ->
                    Text(visibleCaption, color = XAgeTextSecondary, fontSize = 13.sp)
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
            XAgeDataKind.entries.forEach { kind ->
                XAgeScoreRing(
                    kind = kind,
                    metric = scores.score(kind),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectDetail(kind) },
                    onInfoClick = { onSelectInfo(kind) },
                    onConfidenceClick = { onSelectConfidence(kind) },
                )
            }
        }

        if (showsTodayStatus) {
            XAgeScoreSummaryCard(scores)
        }
    }
}

@Composable
private fun XAgeDataDetailDialog(
    kind: XAgeDataKind,
    metric: XAgeMetricScore,
    onDismiss: () -> Unit,
) {
    XAgeGlassDialog(title = "${kind.label}详情", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("今日", color = XAgeTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            XAgeLargeScoreRing(kind = kind, metric = metric)
            Text(
                metric.badgeLabel,
                color = kind.color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(metric.summary, color = Color(0xFF496A83), fontSize = 14.sp, lineHeight = 20.sp)
            Text(metric.simpleExplanation, color = XAgeTextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            Column(
                modifier = Modifier.xAgeGlass(22.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("本次输入", color = XAgeTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (metric.fields.isEmpty()) {
                    Text("暂无已验证的评分输入", color = XAgeTextSecondary, fontSize = 13.sp)
                } else metric.fields.forEach { row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(row.title, color = XAgeTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            row.value,
                            color = XAgeTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (metric.drivers.isNotEmpty()) {
                Column(
                    modifier = Modifier.xAgeGlass(22.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("主要影响", color = XAgeTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    metric.drivers.forEach { driver ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(driver.title, color = XAgeTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(driver.value, color = XAgeTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(driver.note, color = XAgeTextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }
            if (metric.isProxy) {
                Text(
                    "当前为代理参考分，不是炎症诊断。",
                    color = Color(0xFFB46A18),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(metric.nextAction, color = Color(0xFF496A83), fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun XAgeScoreConfidenceDialog(
    kind: XAgeDataKind,
    metric: XAgeMetricScore,
    onDismiss: () -> Unit,
) {
    XAgeGlassDialog(title = "${kind.label}数据完整度", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${XAgeScoreStatusPresentation.confidenceBand(metric)} · ${metric.confidence.coerceIn(0, 100)}%",
                color = if (XAgeScoreStatusPresentation.requiresConfidenceAttention(metric)) {
                    Color(0xFFB96E19)
                } else {
                    kind.color
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                XAgeScoreStatusPresentation.confidenceExplanation(kind, metric),
                color = Color(0xFF496A83),
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            Text(
                "评分弧表示 0–100 的每日参考分；外环表示本次输入的数据支撑程度。两者含义不同。",
                color = XAgeTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text(metric.nextAction, color = XAgeTextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun XAgeScoreInfoDialog(
    kind: XAgeDataKind,
    metric: XAgeMetricScore,
    onDismiss: () -> Unit,
) {
    val presentation = XAgeScoreInfoPresentationPolicy.presentation(kind, metric)
    XAgeGlassDialog(title = presentation.title, onDismiss = onDismiss) {
        Text(
            presentation.scoreTypeAndConfidence,
            color = XAgeTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Column(
            modifier = Modifier.xAgeGlass(22.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("先看结论", color = XAgeTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(presentation.conclusion, color = Color(0xFF496A83), fontSize = 14.sp, lineHeight = 20.sp)
            Text("专业依据", color = kind.color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(presentation.professionalBasis, color = XAgeTextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Column(
            modifier = Modifier.xAgeGlass(22.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("评分依据", color = XAgeTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (metric.drivers.isEmpty()) {
                Text(
                    presentation.emptyEvidenceMessage,
                    color = XAgeTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            } else {
                metric.drivers.take(3).forEach { driver ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(driver.title, color = XAgeTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(driver.value, color = kind.color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(driver.note, color = XAgeTextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
        Column(
            modifier = Modifier.xAgeGlass(20.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("提高置信度", color = XAgeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(presentation.nextAction, color = kind.color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun XAgeLargeScoreRing(kind: XAgeDataKind, metric: XAgeMetricScore) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(174.dp),
        contentAlignment = Alignment.Center,
    ) {
        XAgeScoreRingGraphic(
            kind = kind,
            metric = metric,
            ringSize = 154.dp,
            numberFontSize = 38.sp,
            showLabel = true,
        )
    }
}

@Composable
private fun XAgeScoreRing(
    kind: XAgeDataKind,
    metric: XAgeMetricScore,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    onConfidenceClick: () -> Unit,
) {
    val adaptive = LocalXAgeAdaptive.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (adaptive.compactWidth) 6.dp else 8.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(adaptive.scoreRingSize),
            contentAlignment = Alignment.Center,
        ) {
            XAgeScoreRingGraphic(
                kind = kind,
                metric = metric,
                ringSize = adaptive.scoreRingSize,
                numberFontSize = adaptive.scoreNumberFontSize,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClick)
                    .testTag(XAgeScoreActionContract.testTag(kind, XAgeScoreAction.Detail))
                    .semantics {
                        contentDescription = XAgeScoreStatusPresentation.accessibilitySummary(kind, metric)
                    },
            )
            if (
                metric.hasDisplayableScore &&
                XAgeScoreStatusPresentation.requiresConfidenceAttention(metric)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onConfidenceClick)
                        .testTag(XAgeScoreActionContract.testTag(kind, XAgeScoreAction.Completeness))
                        .semantics {
                            contentDescription = "${kind.label}评分置信度较低，查看数据完整度说明"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFF0D8))
                            .border(1.dp, Color(0xFFE78A20), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "!",
                            color = Color(0xFFB96E19),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                kind.label,
                color = Color(0xFF43657F),
                fontSize = adaptive.scoreLabelFontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.clickable(onClick = onClick),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onInfoClick)
                    .testTag(XAgeScoreActionContract.testTag(kind, XAgeScoreAction.Explanation))
                    .semantics { contentDescription = "${kind.label}评分说明" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = kind.color,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
private fun XAgeScoreRingGraphic(
    kind: XAgeDataKind,
    metric: XAgeMetricScore,
    ringSize: Dp,
    numberFontSize: TextUnit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
) {
    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            val confidenceStroke = XAgeScoreRingGeometry.confidenceLineWidth(diameter.toDouble()).toFloat()
            val confidenceInset = confidenceStroke / 2f + 1.dp.toPx()
            val confidenceArcSize = Size(
                diameter - confidenceInset * 2f,
                diameter - confidenceInset * 2f,
            )
            val confidenceTopLeft = Offset(
                (size.width - diameter) / 2f + confidenceInset,
                (size.height - diameter) / 2f + confidenceInset,
            )
            drawArc(
                color = Color.White.copy(alpha = 0.52f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = confidenceTopLeft,
                size = confidenceArcSize,
                style = Stroke(width = confidenceStroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = if (XAgeScoreStatusPresentation.requiresConfidenceAttention(metric)) {
                    Color(0xFFE78A20)
                } else {
                    kind.color
                },
                startAngle = -90f,
                sweepAngle = 360f * XAgeScoreStatusPresentation.confidenceProgress(metric),
                useCenter = false,
                topLeft = confidenceTopLeft,
                size = confidenceArcSize,
                style = Stroke(width = confidenceStroke, cap = StrokeCap.Round),
            )

            val scoreDiameter = XAgeScoreRingGeometry.scoreRingSize(diameter.toDouble()).toFloat()
            val scoreStroke = XAgeScoreRingGeometry.scoreLineWidth(diameter.toDouble()).toFloat()
            val scoreInset = scoreStroke / 2f + 1.dp.toPx()
            val scoreArcSize = Size(
                scoreDiameter - scoreInset * 2f,
                scoreDiameter - scoreInset * 2f,
            )
            val scoreTopLeft = Offset(
                (size.width - scoreDiameter) / 2f + scoreInset,
                (size.height - scoreDiameter) / 2f + scoreInset,
            )
            drawArc(
                color = Color.White.copy(alpha = 0.54f),
                startAngle = XAgeScoreRingGeometry.SCORE_ARC_START_DEGREES,
                sweepAngle = XAgeScoreRingGeometry.SCORE_ARC_SWEEP_DEGREES,
                useCenter = false,
                topLeft = scoreTopLeft,
                size = scoreArcSize,
                style = Stroke(width = scoreStroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(kind.color.copy(alpha = 0.42f), kind.color, XjiePalette.Accent, kind.color),
                ),
                startAngle = XAgeScoreRingGeometry.SCORE_ARC_START_DEGREES,
                sweepAngle = XAgeScoreRingGeometry.SCORE_ARC_SWEEP_DEGREES *
                    (if (metric.hasDisplayableScore) metric.value.coerceIn(0, 100) else 0) / 100f,
                useCenter = false,
                topLeft = scoreTopLeft,
                size = scoreArcSize,
                style = Stroke(width = scoreStroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(metric.dailyDisplayValue, color = XAgeTextDark, fontSize = numberFontSize, fontWeight = FontWeight.Bold)
            if (showLabel) {
                Text(kind.label, color = Color(0xFF43657F), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun XAgeScoreSummaryCard(scores: XAgeCompositeScores) {
    val adaptive = LocalXAgeAdaptive.current
    val presentation = XAgeScoreStatusPresentation.summaryPresentation(scores)
    Column(
        modifier = Modifier.xAgeGlass(24.dp).padding(if (adaptive.compactWidth) 14.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (adaptive.shortHeight) 8.dp else 10.dp),
    ) {
        if (presentation.mode == XAgeScoreStatusPresentation.SummaryMode.Ready) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    presentation.title,
                    modifier = Modifier.weight(1f),
                    color = XAgeTextPrimary,
                    fontSize = if (adaptive.compactWidth) 16.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                XAgeScoreBadge(scores.pressure.badgeLabel, XAgeDataKind.Pressure.color)
                XAgeScoreBadge(scores.recovery.badgeLabel, XAgeDataKind.Recovery.color)
                XAgeScoreBadge(scores.inflammation.badgeLabel, XAgeDataKind.Inflammation.color)
            }
            Text(
                presentation.message,
                modifier = Modifier.testTag("xage.score.trust.notice"),
                color = Color(0xFF496A83),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
            )
        } else {
            XAgeScoreDataPrompt(presentation)
        }
    }
}

@Composable
private fun XAgeScoreDataPrompt(
    presentation: XAgeScoreStatusPresentation.SummaryPresentation,
) {
    val icon = when (presentation.mode) {
        XAgeScoreStatusPresentation.SummaryMode.Unavailable -> Icons.Filled.Refresh
        XAgeScoreStatusPresentation.SummaryMode.FirstUse -> Icons.Filled.Favorite
        XAgeScoreStatusPresentation.SummaryMode.NeedsData -> Icons.Filled.CloudUpload
        XAgeScoreStatusPresentation.SummaryMode.Ready -> Icons.Filled.Info
    }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFFDCF4FF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF237FC4), modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(presentation.title, color = XAgeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                presentation.message,
                modifier = Modifier.testTag("xage.score.trust.notice"),
                color = Color(0xFF496A83),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun XAgeScoreBadge(label: String, color: Color) {
    Row(
        modifier = Modifier
            .widthIn(max = 60.dp)
            .height(22.dp)
            .xAgePill()
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun XAgeQuickActionStrip(
    actions: List<XAgeQuickActionSpec>,
    onOpenDestination: (String) -> Unit,
    onMove: (id: String, targetIndex: Int) -> Unit,
) {
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val latestIds by rememberUpdatedState(actions.map { it.id })
    val reorderThresholdPx = with(LocalDensity.current) { 40.dp.toPx() }
    val horizontalStripContainment = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): Offset = Offset(x = available.x, y = 0f)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity(x = available.x, y = 0f)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "快捷功能",
                color = XAgeTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            XAgeHomeChromePresentationPolicy.visibleQuickActionReorderHint()?.let { hint ->
                Text(hint, color = XAgeTextSecondary, fontSize = 11.sp)
            }
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(horizontalStripContainment)
                .testTag("xage.quickActions"),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items(actions, key = { action -> action.id }) { action ->
                val index = actions.indexOfFirst { it.id == action.id }
                val isDragging = draggingId == action.id
                Surface(
                    onClick = {
                        action.destination?.let(onOpenDestination)
                    },
                    modifier = Modifier
                        .width(72.dp)
                        .height(72.dp)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationX = if (isDragging) dragOffsetPx else 0f
                            scaleX = if (isDragging) 0.97f else 1f
                            scaleY = if (isDragging) 0.97f else 1f
                        }
                        .pointerInput(action.id, reorderThresholdPx) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = action.id
                                    dragOffsetPx = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffsetPx = 0f
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffsetPx = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggingId == action.id) {
                                        dragOffsetPx = (dragOffsetPx + dragAmount.x)
                                            .coerceIn(-reorderThresholdPx, reorderThresholdPx)
                                        val direction = when {
                                            dragOffsetPx >= reorderThresholdPx -> 1
                                            dragOffsetPx <= -reorderThresholdPx -> -1
                                            else -> 0
                                        }
                                        if (direction != 0) {
                                            val currentIndex = latestIds.indexOf(action.id)
                                            val targetIndex = currentIndex + direction
                                            if (currentIndex >= 0 && targetIndex in latestIds.indices) {
                                                onMove(action.id, targetIndex)
                                                dragOffsetPx = 0f
                                            }
                                        }
                                    }
                                },
                            )
                        }
                        .semantics {
                            contentDescription = "${action.title}快捷功能"
                            stateDescription = "第 ${index + 1} 项，共 ${actions.size} 项；长按拖动可排序"
                            customActions = buildList {
                                if (index > 0) {
                                    add(
                                        CustomAccessibilityAction("向左移动") {
                                            onMove(action.id, index - 1)
                                            true
                                        },
                                    )
                                }
                                if (index in 0 until actions.lastIndex) {
                                    add(
                                        CustomAccessibilityAction("向右移动") {
                                            onMove(action.id, index + 1)
                                            true
                                        },
                                    )
                                }
                            }
                        }
                        .testTag("xage.quickAction.${action.id}"),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.48f),
                    border = BorderStroke(
                        if (isDragging) 2.dp else 1.dp,
                        if (isDragging) Color(0xFF277EBB) else Color.White.copy(alpha = 0.86f),
                    ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            action.quickActionIcon(),
                            contentDescription = null,
                            tint = Color(0xFF277EBB),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            action.title,
                            color = Color(0xFF173F64),
                            fontSize = if (action.title.length > 3) 11.sp else 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun XAgeQuickActionSpec.quickActionIcon(): ImageVector = when (id) {
    "meals" -> Icons.Filled.Restaurant
    "weight" -> Icons.Filled.MonitorWeight
    "reports" -> Icons.Filled.Description
    "medications" -> Icons.Filled.Medication
    "medical" -> Icons.Filled.MedicalServices
    else -> Icons.Filled.Info
}

@Composable
private fun XAgeHealthConnectSyncCard(
    state: HealthConnectSyncUiState,
    onSync: () -> Unit,
    surface: HealthConnectCardSurface,
) {
    val adaptive = LocalXAgeAdaptive.current
    val presentation = HealthConnectCardPresentationPolicy.presentation(surface, state)
    if (!presentation.isVisible) return
    val working = state.phase == HealthConnectSyncPhase.Syncing
    Column(
        modifier = Modifier
            .xAgeGlass(24.dp)
            .padding(if (adaptive.shortHeight) 14.dp else 16.dp)
            .testTag("xage.healthConnect.sync"),
        verticalArrangement = Arrangement.spacedBy(if (adaptive.shortHeight) 8.dp else 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (surface == HealthConnectCardSurface.CompactHome) 44.dp else 48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF238AD6), Color(0xFF20CDB1))))
                    .border(1.dp, Color.White.copy(alpha = 0.56f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    presentation.title,
                    color = Color(0xFF173F64),
                    fontSize = if (surface == HealthConnectCardSurface.CompactHome) 16.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    presentation.subtitle,
                    color = Color(0xFF6C8194),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = if (presentation.showsDetailedStatus) 3 else 2,
                )
            }
            Surface(
                onClick = onSync,
                enabled = !working,
                modifier = Modifier
                    .width(62.dp)
                    .height(34.dp)
                    .testTag("xage.healthConnect.sync.button"),
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
                        Text(presentation.buttonTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (presentation.showsDetailedStatus && adaptive.shortHeight) {
            Text(
                presentation.detailBadges.joinToString(" · "),
                color = Color(0xFF347FB7),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (presentation.showsDetailedStatus) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                presentation.detailBadges.forEach { badge -> XAgeSyncBadge(badge) }
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
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val adaptive = LocalXAgeAdaptive.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .xAgeGlass(24.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = adaptive.metricCardHorizontalPadding, vertical = 16.dp),
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
private fun XAgeMetricDetailDialog(
    metric: XAgeMetric,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFF3FAFE),
            tonalElevation = 0.dp,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            metric.title,
                            color = XAgeTextPrimary,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "健康指标详情",
                            color = XAgeTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭${metric.title}详情", tint = Color(0xFF2A79BB))
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .xAgeGlass(22.dp)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(metric.value, color = XAgeTextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        if (metric.unit.isNotBlank()) {
                            Spacer(Modifier.width(5.dp))
                            Text(metric.unit, color = XAgeTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(metric.subtitle, color = XAgeTextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .xAgeGlass(22.dp)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("历史趋势", color = XAgeTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    val trend = metric.trend
                    if (trend == null) {
                        Box(
                            Modifier.fillMaxWidth().height(112.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "暂无可用历史趋势；同步、手动记录或确认报告后再查看。",
                                color = XAgeTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        IndicatorTrendChart(trend = trend)
                    }
                }

                Text(
                    "趋势只展示服务端返回的真实记录，不补造缺失数据。",
                    color = XAgeTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun XAgeMetricEmptyRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .xAgeGlass(24.dp)
            .padding(horizontal = 18.dp, vertical = 22.dp)
            .testTag("xage.data.metric.empty"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "首页暂无数据卡片",
            color = Color(0xFF173F64),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "打开数据卡片管理，添加需要长期关注的指标。",
            color = Color(0xFF6C8194),
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun XAgeDataManagerPage(
    metrics: List<XAgeMetric>,
    availableMetrics: List<XAgeMetric>,
    healthConnectState: HealthConnectSyncUiState,
    onHealthConnectSync: () -> Unit,
    onBack: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (XAgeMetric) -> Unit,
    onAdd: (XAgeMetric) -> Unit,
) {
    val adaptive = LocalXAgeAdaptive.current
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = adaptive.contentHorizontalPadding)
            .padding(top = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("xage.data.manager.back"),
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回数据页", tint = XAgeTextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("数据卡片管理", color = XAgeTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("调整会即时保存", color = XAgeTextSecondary, fontSize = 12.sp)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 14.dp, bottom = 28.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "这是独立管理页面。使用返回按钮回到数据页，不需要在首页进入“排序模式”。",
                color = Color(0xFF5D7890),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .xAgeGlass(22.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("已显示卡片", color = XAgeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (metrics.isEmpty()) {
                    Text("暂无真实指标数据。你可以先添加占位卡片，连接并同步后才会显示数值。", color = XAgeTextSecondary, fontSize = 12.sp)
                }
                metrics.forEachIndexed { index, metric ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.54f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(metric.title, modifier = Modifier.weight(1f), color = XAgeTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(metric.time, color = XAgeTextSecondary, fontSize = 11.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            XAgeSmallButton("上移", { onMoveUp(index) }, Modifier.weight(1f))
                            XAgeSmallButton("下移", { onMoveDown(index) }, Modifier.weight(1f))
                            XAgeSmallButton("移除", { onRemove(metric) }, Modifier.weight(1f))
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .xAgeGlass(22.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("可添加卡片", color = XAgeTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (availableMetrics.isEmpty()) {
                    Text("没有更多候选卡片。", color = XAgeTextSecondary, fontSize = 12.sp)
                } else {
                    availableMetrics.forEach { metric ->
                        Surface(
                            onClick = { onAdd(metric) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("xage.data.manager.add.${metric.id}"),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.54f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(metric.title, modifier = Modifier.weight(1f), color = XAgeTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("添加", color = Color(0xFF287DBB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            XAgeHealthConnectSyncCard(
                state = healthConnectState,
                onSync = onHealthConnectSync,
                surface = com.xjie.app.feature.healthconnect.HealthConnectCardSurface.FullManager,
            )
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
private fun XAgeChatPage(
    historySignal: Int,
    vm: ChatViewModel = hiltViewModel(),
    uploadVm: HealthDataViewModel = hiltViewModel(),
) {
    val adaptive = LocalXAgeAdaptive.current
    val state by vm.state.collectAsState()
    val uploadState by uploadVm.state.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var analysis by remember { mutableStateOf<ChatMessageItem?>(null) }
    var evidence by remember { mutableStateOf<ChatMessageItem?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var transientMessage by remember { mutableStateOf<String?>(null) }
    var pendingCameraUpload by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val chatListState = rememberLazyListState()
    val showUploadStatus = uploadState.uploading ||
        uploadState.backgroundTaskHint != null ||
        uploadState.toast != null ||
        uploadState.error != null ||
        transientMessage != null ||
        state.error != null

    fun dismissKeyboard() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    val dismissKeyboardOnScroll = remember(focusManager, keyboardController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (available.y != 0f) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
                return Offset.Zero
            }
        }
    }

    fun uploadReport(uri: Uri, fileName: String) {
        uploadVm.setUploadDocType("exam")
        uploadVm.uploadFile(uri, fileName)
        transientMessage = "报告开始上传。识别完成后请先检查并确认，确认前不会进入 AI 问答。"
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
    LaunchedEffect(state.messages.size, state.sending, state.thinkingHint, showUploadStatus) {
        val bottomIndex = (if (state.messages.isEmpty()) 1 else state.messages.size) +
            (if (showUploadStatus) 1 else 0) +
            (if (state.sending) 1 else 0)
        chatListState.scrollToItem(bottomIndex)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = chatListState,
            modifier = Modifier
                .weight(1f)
                .nestedScroll(dismissKeyboardOnScroll)
                .padding(horizontal = adaptive.contentHorizontalPadding),
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
            if (showUploadStatus) {
                item {
                    XAgeChatUploadStatusCard(
                        uploading = uploadState.uploading,
                        title = when {
                            transientMessage != null -> "提示"
                            state.error != null -> "回答提示"
                            uploadState.uploading -> uploadState.uploadStage.ifBlank { "正在上传报告…" }
                            uploadState.error != null -> "上传失败"
                            uploadState.toast != null -> uploadState.toast ?: "上传成功"
                            else -> "报告已上传，AI 正在识别"
                        },
                        subtitle = transientMessage
                            ?: state.error
                            ?: uploadState.error
                            ?: uploadState.backgroundTaskHint
                            ?: "识别完成后请先检查并确认，再进入问答解读。",
                    )
                }
            }
            if (state.sending) {
                item {
                    Text(
                        state.thinkingHint.ifBlank { "仍在等待回答完成…" },
                        modifier = Modifier.xAgeGlass(18.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                        color = Color(0xFF5D7890),
                        fontSize = 14.sp,
                    )
                }
            }
            item(key = "xage-chat-bottom-anchor") { Spacer(Modifier.height(1.dp)) }
        }
        XAgeChatInput(
            value = state.input,
            sending = state.sending,
            onValueChange = vm::setInput,
            onSend = {
                dismissKeyboard()
                vm.send()
            },
            isUploading = uploadState.uploading,
            onMicTap = {
                dismissKeyboard()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要问小捷的问题")
                }
                runCatching { speechLauncher.launch(intent) }
                    .onFailure { transientMessage = "当前设备未安装语音识别服务，可直接输入文字。" }
            },
            onPlusTap = {
                dismissKeyboard()
                showAttachmentMenu = true
            },
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
            XAgeEvidenceList(msg.relevantCitationReferences)
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
            isNewChatEnabled = !state.sending,
            onCamera = {
                showAttachmentMenu = false
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
    if (state.showAiConsentPrompt) {
        AlertDialog(
            onDismissRequest = vm::declineAiConsent,
            title = { Text("开启 AI 健康问答") },
            text = {
                Text("小捷需要读取你已授权的健康档案和当前会话来生成个性化回答。只有你明确同意后才会继续处理这条消息。")
            },
            dismissButton = {
                TextButton(onClick = vm::declineAiConsent) { Text("暂不开启") }
            },
            confirmButton = {
                TextButton(onClick = {
                    dismissKeyboard()
                    vm.grantAiConsentAndRetry()
                }) { Text("同意并继续") }
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
                    XAgeGreeting.currentHeadline(),
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(if (isUser) 32.dp else 20.dp))
                    .background(
                        if (isUser) Brush.linearGradient(listOf(Color(0xFF238AD6), Color(0xFF20CDB1)))
                        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.58f), Color.White.copy(alpha = 0.46f)))
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(if (isUser) 32.dp else 20.dp))
                    .padding(horizontal = if (isUser) 17.dp else 15.dp, vertical = if (isUser) 12.dp else 14.dp),
            ) {
                if (isUser) {
                    Text(
                        msg.content,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 24.sp,
                    )
                } else {
                    MarkdownText(msg.content, color = Color(0xFF244E6D))
                }
            }
            msg.status?.let { status ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(status.label, color = Color(0xFF45677F), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (status == ChatDeliveryStatus.Failed) {
                        Text("重试", modifier = Modifier.clickable { onRetry() }, color = Color(0xFF1268BD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!isUser) {
                val relevantCitations = msg.relevantCitationReferences
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (msg.hasDistinctAnalysis) XAgeSmallButton("查看分析", onAnalysis)
                    if (relevantCitations.isNotEmpty()) XAgeSmallButton("证据展示", onEvidence)
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
    onPlusTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalXAgeAdaptive.current
    Row(
        modifier = modifier
            .heightIn(min = adaptive.chatInputHeight, max = 156.dp)
            .xAgeGlass(29.dp)
            .padding(
                horizontal = if (adaptive.compactWidth) 8.dp else 10.dp,
                vertical = 4.dp,
            ),
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
            placeholder = { Text("输入消息", fontSize = 14.sp) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = adaptive.chatInputHeight - 8.dp, max = 148.dp)
                .testTag("xage.chat.input"),
            singleLine = false,
            minLines = 1,
            maxLines = 5,
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
    testTag: String = "xage.chat.upload.status",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .xAgeGlass(22.dp)
            .padding(14.dp)
            .testTag(testTag),
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
    isNewChatEnabled: Boolean,
    onCamera: () -> Unit,
    onPickDocument: () -> Unit,
    onPickImage: () -> Unit,
    onNewChat: () -> Unit,
) {
    XAgeGlassDialog(title = "添加内容", onDismiss = onDismiss) {
        XAgeAttachmentMenuRow(Icons.Filled.CameraAlt, "拍照采集报告", "拍摄纸质报告或检查单", "xage.chat.attachment.camera", onCamera)
        XAgeAttachmentMenuRow(Icons.Filled.Description, "数据上传 PDF / 图片", "上传体检、化验或影像截图", "xage.chat.attachment.documents", onPickDocument)
        XAgeAttachmentMenuRow(Icons.Filled.CameraAlt, "从相册上传报告", "选择已拍好的报告图片", "xage.chat.attachment.photos", onPickImage)
        XAgeAttachmentMenuRow(
            Icons.Filled.Refresh,
            "新对话",
            "清空当前 XAGE 问答上下文",
            "xage.chat.attachment.new",
            onNewChat,
            enabled = isNewChatEnabled,
        )
    }
}

@Composable
private fun XAgeAttachmentMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .testTag(testTag),
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
private fun XAgeEvidenceList(references: List<ChatCitationReference>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (references.isEmpty()) {
            Text("当前回答暂无文献引用。", color = XAgeTextSecondary)
        }
        references.forEach { reference ->
            val citation = reference.citation
            Column(Modifier.xAgeGlass(18.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row {
                    Text("[${reference.number}]", color = XjiePalette.Primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(citation.evidence_level, color = XjiePalette.Accent, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(citation.confidence, color = XAgeTextSecondary, fontSize = 12.sp)
                }
                Text(citation.claim_text, color = Color(0xFF244E6D), fontSize = 14.sp)
                Text("${citation.short_ref} · ${citation.journal ?: "source"} · ${citation.year ?: "year"}", color = XAgeTextSecondary, fontSize = 12.sp, maxLines = 1)
                citation.population?.takeIf(String::isNotBlank)?.let {
                    Text("研究人群：$it", color = XAgeTextSecondary, fontSize = 12.sp)
                }
                ChatPresentationPolicy.studyDesignDisplayText(citation.study_design)?.let {
                    Text("研究设计：$it", color = XAgeTextSecondary, fontSize = 12.sp)
                }
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
                                .size(48.dp)
                                .testTag("xage.dialog.close"),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.56f))
                                    .border(1.dp, Color.White.copy(alpha = 0.84f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = XAgeTextPrimary, modifier = Modifier.size(18.dp))
                            }
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
private fun XAgeHealthspanPage(infoSignal: Int) {
    val adaptive = LocalXAgeAdaptive.current
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(infoSignal) {
        if (infoSignal > 0) showInfo = true
    }

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
        Text("尚未启用计算", color = XAgeTextSecondary, fontSize = 13.sp)
        Box(Modifier.size(adaptive.healthRingContainerSize), contentAlignment = Alignment.Center) {
            Box(Modifier.size(adaptive.healthRingContainerSize - 8.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF8EF7E6).copy(alpha = 0.44f), Color(0xFF21B5FF).copy(alpha = 0.16f), Color.Transparent))).blur(10.dp))
            Image(
                painter = painterResource(R.drawable.x_age_particle_ring_blue_green),
                contentDescription = "蓝绿色粒子圆环",
                modifier = Modifier.size(adaptive.healthRingImageSize).testTag("xage.particle.ring"),
            )
            Box(Modifier.size(adaptive.healthRingInnerSize).clip(CircleShape).background(Color.White.copy(alpha = 0.58f)).border(1.dp, Color.White.copy(alpha = 0.78f), CircleShape))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("--", color = Color(0xFF12324F), fontSize = adaptive.healthAgeFontSize, fontWeight = FontWeight.Bold)
                Text("X年龄", color = Color(0xFF45677F), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("等待验证", color = Color(0xFF347FB7), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.xAgeGlass(26.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("为什么暂不显示数值", color = XAgeTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "X年龄尚未完成字段映射、权重、方向、验证集、阈值和回滚方案的独立版本验证。为避免把样例或未确认报告伪装成健康结论，当前保持关闭。",
                color = Color(0xFF496A83),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }

    if (showInfo) {
        XAgeInfoDialog(onDismiss = { showInfo = false })
    }
}

@Composable
private fun XAgeInfoDialog(onDismiss: () -> Unit) {
    XAgeGlassDialog(title = "X年龄说明", onDismiss = onDismiss) {
        Text(
            "当前版本不会计算或展示 X年龄。只有独立版本的字段映射、算法方向、验证集、阈值、失败行为和回滚合同全部通过后，才允许启用真实消费。",
            color = Color(0xFF496A83),
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Text(
            "未确认报告、画像候选、样例数据和评分待更新状态都不能作为 X年龄输入。",
            modifier = Modifier.xAgePill().padding(14.dp),
            color = XAgeTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun XAgeMenuRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    tag: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(tag?.let { Modifier.testTag(it) } ?: Modifier),
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color(0xFF173F64), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(subtitle, color = Color(0xFF6C8194), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
    val trend: IndicatorTrend? = null,
) {
    companion object {
        val defaultCards = listOf(
            XAgeMetric("hrv", "心率变异性", "无", "", "待同步", "同步 Health Connect 后显示最近一次 HRV。", Color(0xFF7B4DFF)),
            XAgeMetric("sleep", "睡眠", "无", "", "待同步", "同步 Health Connect 后显示最近一晚睡眠。", Color(0xFF14B887)),
            XAgeMetric("glucose", "血糖波动", "待上传", "", "待上传", "上传血糖、CGM 或报告后显示波动趋势。", Color(0xFF11A7C8)),
            XAgeMetric("temp", "体温偏移", "无", "", "待上传", "上传或记录体温后显示最近体温偏移。", Color(0xFFEF9A3D)),
        )

        val androidHealthCandidates = (defaultCards + listOf(
            XAgeMetric("steps", "步数", "--", "步", "未同步", "连接 Health Connect 后显示真实累计值。", Color(0xFF238AD6)),
            XAgeMetric("distance", "步行+跑步距离", "--", "km", "未同步", "连接 Health Connect 后显示真实累计值。", Color(0xFF18B7D6)),
            XAgeMetric("restingHeartRate", "静息心率", "--", "bpm", "未同步", "读取最近一次真实静息心率记录。", Color(0xFFF05B72)),
            XAgeMetric("bodyWeight", "体重", "--", "kg", "未同步", "读取最近一次真实体重记录。", Color(0xFF11A7C8)),
        )).distinctBy { it.id }
    }
}

private val XAgeDataKind.color: Color
    get() = when (this) {
        XAgeDataKind.Pressure -> Color(0xFF2789D8)
        XAgeDataKind.Recovery -> Color(0xFF14B887)
        XAgeDataKind.Inflammation -> Color(0xFFEF9A3D)
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
            trend = metric.trend,
        )
    }

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

private fun validateXAgeReportUpload(context: Context, uri: Uri, fileName: String): String? {
    val lower = fileName.lowercase(Locale.ROOT)
    val mimeType = runCatching { context.contentResolver.getType(uri).orEmpty() }.getOrDefault("")
    val isImage = mimeType.startsWith("image/") || listOf(
        ".jpg",
        ".jpeg",
        ".png",
        ".heic",
        ".heif",
        ".webp",
        ".tif",
        ".tiff",
    ).any { lower.endsWith(it) }
    if (!isImage) return null

    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return "未能读取图片数据，请重新拍摄或选择 PDF。"
    if (bytes.size < 30 * 1024) {
        return "图片过小（小于 30KB），可能不是完整报告。请重新拍摄。"
    }

    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val shortEdge = minOf(options.outWidth, options.outHeight)
    if (shortEdge <= 0) return "未能读取图片数据，请重新拍摄或选择 PDF。"
    if (shortEdge < 600) {
        return "图片分辨率过低（短边 ${shortEdge}px），识别可能失败。请重新拍摄。"
    }
    return null
}

private fun createXAgeReportImageUri(context: Context, prefix: String = "xage_report_camera"): Pair<Uri, String> {
    val dir = File(context.cacheDir, "xage_reports").apply { mkdirs() }
    val fileName = "${prefix}_${System.currentTimeMillis()}.jpg"
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
