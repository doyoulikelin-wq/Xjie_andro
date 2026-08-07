package com.xjie.app.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.BuildConfig
import com.xjie.app.core.model.GlucoseUnit
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialSection: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenAdmin: () -> Unit = {},
    onOpenElderlyHistory: () -> Unit = {},
    onOpenFamily: () -> Unit = {},
    onOpenMedications: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val unit by vm.glucoseUnit.collectAsState()
    val demo by vm.omicsDemo.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showChangePwd by remember { mutableStateOf(false) }
    val isDirectFeedback = initialSection == "support_feedback"
    var showFeedback by remember(initialSection) { mutableStateOf(isDirectFeedback) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var supportDetail by remember(initialSection) {
        mutableStateOf(SupportDestination.fromInitialSection(initialSection))
    }
    var deviceSupportLoading by remember(initialSection) {
        mutableStateOf(initialSection == XAgeSupportCompliancePolicy.DEVICE_DESTINATION)
    }

    val requiresAccountSettings = XAgeSettingsLoadPolicy.requiresAccountSettings(initialSection)
    LaunchedEffect(requiresAccountSettings) {
        if (requiresAccountSettings) vm.load()
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(initialSection) {
        if (initialSection == XAgeSupportCompliancePolicy.DEVICE_DESTINATION) {
            kotlinx.coroutines.yield()
            deviceSupportLoading = false
        }
    }

    supportDetail?.let { detail ->
        val isDirect = initialSection == "support_${detail.id}"
        val closeDetail = {
            if (isDirect && onBack != null) onBack() else supportDetail = null
        }
        BackHandler(onBack != null || !isDirect) { closeDetail() }
        SupportDetailScreen(
            destination = detail,
            onBack = closeDetail,
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (initialSection) {
                            XAgeSupportCompliancePolicy.ACCOUNT_DESTINATION -> "账号与安全"
                            XAgeSupportCompliancePolicy.SUPPORT_DESTINATION -> "关于与支持"
                            XAgeSupportCompliancePolicy.DEVICE_DESTINATION -> "设备管理"
                            "support_feedback" -> "意见反馈"
                            else -> "设置"
                        }
                    )
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
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
                .testTag("xage.settings.${initialSection ?: "root"}")
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (initialSection) {
                XAgeSupportCompliancePolicy.ACCOUNT_DESTINATION -> {
                    AccountSecuritySummaryCard(user = state.user, isLoading = state.loading)
                    OutlinedButton(
                        onClick = { showChangePwd = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("xage.account.changePassword"),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Filled.Lock, null)
                        Spacer(Modifier.width(6.dp))
                        Text("修改密码")
                    }
                    OutlinedButton(
                        onClick = { vm.showLogoutAlert(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("xage.account.logout"),
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("退出登录") }
                    TextButton(
                        onClick = { showDeleteAccount = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("xage.account.delete"),
                    ) {
                        Text(
                            "注销账号",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                XAgeSupportCompliancePolicy.SUPPORT_DESTINATION -> {
                    SupportHub(
                        onOpen = { supportDetail = it },
                        onOpenFeedback = { showFeedback = true },
                    )
                }

                XAgeSupportCompliancePolicy.DEVICE_DESTINATION -> {
                    DeviceManagementUnsupportedCard(
                        state = XAgeSupportCompliancePolicy.deviceManagementState(deviceSupportLoading),
                    )
                }

                "support_feedback" -> Unit

                else -> {
            AccountCard(
                user = state.user,
                onEdit = { vm.showProfileEdit(true) },
            )
            FeedbackEntryCard(onOpen = { showFeedback = true })
            FamilyEntryCard(onOpen = onOpenFamily)
            InterventionCard(state.settings?.intervention_level, vm::updateLevel)
            GlucoseUnitCard(unit, vm::updateGlucoseUnit)
            Surface(
                onClick = onOpenMedications,
                modifier = Modifier.cardStyle(),
                color = Color.Transparent,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MedicalServices, null, tint = XjiePalette.Primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("我的用药", fontWeight = FontWeight.SemiBold)
                        Text(
                            "拍照识别 / 手动添加，按疗程定时提醒",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ElderlyModeCard(
                enabled = state.settings?.elderly_mode == true,
                intervalMin = state.settings?.elderly_checkin_interval_min ?: 180,
                onToggle = vm::updateElderlyMode,
                onIntervalChange = vm::updateElderlyInterval,
                onOpenHistory = onOpenElderlyHistory,
            )
            DemoModeCard(demo, vm::toggleOmicsDemo)
            ConsentCard(
                aiChat = state.user?.consent?.allow_ai_chat ?: false,
                dataUpload = state.user?.consent?.allow_data_upload ?: false,
                onAiChat = { vm.toggleAiChat() },
                onDataUpload = { vm.toggleDataUpload() },
            )
            if (state.user?.is_admin == true) {
                Surface(
                    onClick = onOpenAdmin,
                    modifier = Modifier.cardStyle(),
                    color = Color.Transparent,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AdminPanelSettings, null, tint = XjiePalette.Warning)
                        Spacer(Modifier.width(8.dp))
                        Text("管理后台", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(
                onClick = { showChangePwd = true },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Filled.Lock, null)
                Spacer(Modifier.width(6.dp))
                Text("修改密码")
            }
            OutlinedButton(
                onClick = { vm.showLogoutAlert(true) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
            ) { Text("退出登录") }
                }
            }
        }
    }

    if (showChangePwd) {
        ChangePasswordDialog(onDismiss = { showChangePwd = false })
    }

    if (showFeedback) {
        FeedbackDialog(
            onDismiss = {
                showFeedback = false
                if (isDirectFeedback) onBack?.invoke()
            },
            isSubmitting = state.feedbackSubmitting,
            onSubmit = { category, content, contact ->
                vm.submitFeedback(category, content, contact) {
                    showFeedback = false
                    if (isDirectFeedback) onBack?.invoke()
                }
            },
        )
    }

    if (showDeleteAccount) {
        DeleteAccountDialog(
            isSubmitting = state.deleteSubmitting,
            onDismiss = { showDeleteAccount = false },
            onConfirm = {
                vm.deleteAccount { showDeleteAccount = false }
            },
        )
    }

    if (state.showLogoutAlert) {        AlertDialog(
            onDismissRequest = { vm.showLogoutAlert(false) },
            title = { Text("确认退出") },
            text = { Text("确定要退出登录吗？") },
            confirmButton = {
                TextButton(onClick = { vm.showLogoutAlert(false); vm.confirmLogout() }) {
                    Text("退出", color = XjiePalette.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.showLogoutAlert(false) }) { Text("取消") }
            },
        )
    }

    if (state.showProfileEdit) {
        ProfileEditDialog(
            current = state.user?.profile,
            onDismiss = { vm.showProfileEdit(false) },
            onSave = { sex, age, height, weight ->
                vm.updateProfile(sex, age, height.toDouble(), weight.toDouble())
            },
        )
    }
}

private enum class SupportDestination(val id: String, val title: String) {
    Help("help", "使用帮助"),
    Version("version", "版本信息"),
    Privacy("privacy", "隐私政策"),
    Permissions("permissions", "权限申请与使用情况说明");

    companion object {
        fun fromInitialSection(initialSection: String?): SupportDestination? =
            entries.firstOrNull { initialSection == "support_${it.id}" }
    }
}

@Composable
private fun SupportHub(
    onOpen: (SupportDestination) -> Unit,
    onOpenFeedback: () -> Unit,
) {
    SupportEntryCard(
        tag = "xage.support.help",
        icon = Icons.AutoMirrored.Filled.Help,
        title = "使用帮助",
        subtitle = "查看报告、指标和健康同步操作",
        onOpen = { onOpen(SupportDestination.Help) },
    )
    SupportEntryCard(
        tag = "xage.support.version",
        icon = Icons.Filled.Info,
        title = "版本信息",
        subtitle = "查看当前版本与备案信息",
        onOpen = { onOpen(SupportDestination.Version) },
    )
    SupportEntryCard(
        tag = "xage.support.privacy",
        icon = Icons.Filled.PrivacyTip,
        title = "隐私政策",
        subtitle = "了解数据处理方式和你的权利",
        onOpen = { onOpen(SupportDestination.Privacy) },
    )
    SupportEntryCard(
        tag = "xage.support.permissions",
        icon = Icons.AutoMirrored.Filled.ListAlt,
        title = "权限申请与使用情况说明",
        subtitle = "查看系统权限的申请时机、用途和拒绝影响",
        onOpen = { onOpen(SupportDestination.Permissions) },
    )
    SupportEntryCard(
        tag = "xage.support.feedback",
        icon = Icons.Filled.Edit,
        title = "意见反馈",
        subtitle = "提交问题、建议或数据异常",
        onOpen = onOpenFeedback,
    )
}

@Composable
private fun SupportEntryCard(
    tag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onOpen: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier
            .cardStyle()
            .testTag(tag),
        color = Color.Transparent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = XjiePalette.Primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportDetailScreen(
    destination: SupportDestination,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(destination.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .testTag("xage.support.${destination.id}.page")
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (destination) {
                SupportDestination.Help -> {
                    SupportTextSection(
                        "上传或查看报告",
                        "回到 XAGE 首页，点“报告”。上传后可查看识别状态；识别结果需要你确认后才会进入正式健康档案。",
                    )
                    SupportTextSection(
                        "补录健康指标",
                        "回到 XAGE 首页，点“管理”进入数据卡片管理；也可以进入健康数据页补录指标。请同时确认测量时间和单位。",
                    )
                    SupportTextSection(
                        "同步健康数据",
                        "在数据管理页点“连接健康数据”并按系统提示授权。拒绝授权不会影响手动记录；小捷只读取你单独允许的项目。",
                    )
                    SupportTextSection(
                        "AI 回答怎么看",
                        "回答中的来源和数据时间用于解释依据。内容仅供健康管理参考，不构成诊断或治疗建议；急症或明显不适请及时联系医疗机构。",
                    )
                }

                SupportDestination.Version -> {
                    SupportTextSection("当前版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    SupportTextSection("应用名称", "小捷")
                    SupportTextSection("备案信息", "皖ICP备2026008853号-2")
                    SupportTextSection(
                        "版本说明",
                        "本版本聚焦 XAGE 数据、问答和 X年龄体验。健康数据按来源与测量时间处理；数据不足时明确显示待评估。",
                    )
                }

                SupportDestination.Privacy -> {
                    Text(
                        "版本 ${XAgeSupportCompliancePolicy.PRIVACY_POLICY_VERSION} · 更新于 ${XAgeSupportCompliancePolicy.PRIVACY_POLICY_UPDATED_AT}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SupportComplianceHero(
                        eyebrow = "请在使用前阅读",
                        title = "你的健康信息，由你决定",
                        message = "健康信息属于敏感个人信息。我们仅在提供你主动选择的服务所需范围内处理，并通过独立权限说明告诉你何时、为何需要系统能力。",
                    )
                    XAgeSupportCompliancePolicy.privacySections.forEach { section ->
                        SupportTextSection(section.title, section.content)
                    }
                    OutlinedButton(
                        onClick = { uriHandler.openUri(XAgeSupportCompliancePolicy.PRIVACY_POLICY_URL) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("查看官网政策原文")
                    }
                    Text(
                        "官网原文需要网络连接；本页核心说明可离线查看。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SupportDestination.Permissions -> {
                    SupportComplianceHero(
                        eyebrow = "先说明，再申请",
                        title = "不因打开 App 而索取权限",
                        message = "只有在你主动使用同步、上传、语音、提醒或安装更新等功能时，才会进入对应系统授权流程。你可以拒绝或稍后在 Android 系统设置中修改。",
                    )
                    SupportTextSection(
                        "如何阅读本页",
                        "每一项均写明申请时机、用途和拒绝后的影响。“服务所需”不代表运行时弹框；“当前未申请”表示此版本不会调用该能力。",
                    )
                    XAgeSupportCompliancePolicy.permissionDisclosures.forEach { disclosure ->
                        PermissionDisclosureCard(disclosure)
                    }
                    SupportTextSection(
                        "权限管理方式",
                        "你可以随时前往 Android“设置”>“应用”>“小捷”管理系统权限；Health Connect 权限在 Health Connect 或系统健康权限页管理。关闭权限不会删除已经主动提交的数据。",
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportComplianceHero(
    eyebrow: String,
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(XjiePalette.Primary.copy(alpha = 0.08f))
            .border(1.dp, XjiePalette.Primary.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = XjiePalette.Primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionDisclosureCard(disclosure: XAgePermissionDisclosure) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardStyle()
            .testTag("xage.permission.${disclosure.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(disclosure.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            SuggestionChip(onClick = {}, enabled = false, label = { Text(disclosure.badge) })
        }
        PermissionDisclosureLine("申请时机", disclosure.timing)
        PermissionDisclosureLine("使用目的", disclosure.purpose)
        PermissionDisclosureLine("拒绝影响", disclosure.refusalImpact)
    }
}

@Composable
private fun PermissionDisclosureLine(label: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AccountSecuritySummaryCard(
    user: com.xjie.app.core.model.UserInfo?,
    isLoading: Boolean,
) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("当前账号", fontWeight = FontWeight.Bold)
        if (isLoading && user == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("正在读取账号信息", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            InfoRow("手机号", user?.phone ?: user?.email ?: "--")
            Text(
                "账号画像在“更多 > 画像”维护；本页只处理密码、退出和不可逆注销。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceManagementUnsupportedCard(state: XAgeDeviceManagementState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardStyle()
            .testTag("xage.device.${state.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            XAgeDeviceManagementState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("正在检查设备支持状态")
                }
            }

            XAgeDeviceManagementState.Unsupported -> {
                Text(XAgeSupportCompliancePolicy.DEVICE_UNSUPPORTED_TITLE, fontWeight = FontWeight.Bold)
                Text(
                    "蓝牙与 NFC 绑定将在首批型号、协议、鉴权和凭证撤销规则完成后开放。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "序列号、电量和保修信息只会展示厂商权威来源；当前不会生成占位设备。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "当前没有可执行的添加、查看或解绑操作。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SupportTextSection(title: String, content: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .cardStyle(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
            content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeleteAccountDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var confirmation by remember { mutableStateOf("") }
    val canConfirm = confirmation.trim() == "注销" && !isSubmitting

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("注销账号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("账号停用后会清除本机登录态，此操作不可撤销。为避免误触，请输入“注销”后再确认。")
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text("输入：注销") },
                    enabled = !isSubmitting,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = onConfirm) {
                if (isSubmitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text("确认注销", color = if (canConfirm) XjiePalette.Danger else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun FamilyEntryCard(onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.cardStyle(),
        color = Color.Transparent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Person, null, tint = XjiePalette.Primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("家庭模式", fontWeight = FontWeight.SemiBold)
                Text(
                    "邀请家人协作照护，敏感数据需单独授权，计划只读不可修改",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileEditDialog(
    current: com.xjie.app.core.model.UserProfile?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int) -> Unit,
) {
    val sexOptions = remember { listOf("female" to "女", "male" to "男", "other" to "其他") }
    var sex by remember(current) {
        mutableStateOf(current?.sex?.lowercase()?.takeIf { sexOptions.any { p -> p.first == it } } ?: "female")
    }
    var age by remember(current) { mutableStateOf(current?.age ?: 30) }
    var heightCm by remember(current) { mutableStateOf(current?.height_cm?.toInt() ?: 165) }
    var weightKg by remember(current) { mutableStateOf(current?.weight_kg?.toInt() ?: 55) }

    val ageItems = remember { (1..100).map { "$it" } }
    val heightItems = remember { (120..220).map { "$it" } }
    val weightItems = remember { (30..200).map { "$it" } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改个人资料") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "上下滚动选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                com.xjie.app.core.ui.components.WheelPickerRow(
                    columns = listOf(
                        com.xjie.app.core.ui.components.WheelColumn(
                            label = "性别",
                            items = sexOptions.map { it.second },
                            selectedIndex = sexOptions.indexOfFirst { it.first == sex }.coerceAtLeast(0),
                            onSelected = { sex = sexOptions[it].first },
                        ),
                        com.xjie.app.core.ui.components.WheelColumn(
                            label = "年龄",
                            items = ageItems,
                            selectedIndex = (age - 1).coerceIn(0, ageItems.size - 1),
                            onSelected = { age = it + 1 },
                        ),
                        com.xjie.app.core.ui.components.WheelColumn(
                            label = "身高(cm)",
                            items = heightItems,
                            selectedIndex = (heightCm - 120).coerceIn(0, heightItems.size - 1),
                            onSelected = { heightCm = it + 120 },
                        ),
                        com.xjie.app.core.ui.components.WheelColumn(
                            label = "体重(kg)",
                            items = weightItems,
                            selectedIndex = (weightKg - 30).coerceIn(0, weightItems.size - 1),
                            onSelected = { weightKg = it + 30 },
                        ),
                    ),
                    itemHeight = 36.dp,
                    visibleItemCount = 5,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sex, age, heightCm, weightKg) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AccountCard(
    user: com.xjie.app.core.model.UserInfo?,
    onEdit: () -> Unit,
) {
    val profile = user?.profile
    Surface(
        onClick = onEdit,
        color = Color.Transparent,
        modifier = Modifier.cardStyle(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(Icons.Filled.Person, "账户信息")
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            InfoRow("手机号", user?.phone ?: user?.email ?: "--")
            InfoRow("用户名", user?.username ?: "--")
            InfoRow("性别", sexLabel(profile?.sex))
            InfoRow("年龄", profile?.age?.let { "$it 岁" } ?: "--")
            InfoRow("身高", profile?.height_cm?.let { "${it.toInt()} cm" } ?: "--")
            InfoRow("体重", profile?.weight_kg?.let { "${it.toInt()} kg" } ?: "--")
            InfoRow("注册时间", user?.created_at ?: "--")
            Text(
                "点击在线修改性别/年龄/身高/体重",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun sexLabel(raw: String?): String = when (raw?.lowercase()) {
    "female", "f", "女" -> "女"
    "male", "m", "男" -> "男"
    null, "" -> "--"
    else -> "其他"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InterventionCard(currentLevel: String?, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple("L1", "温和", "仅高风险提醒，每天最多 1 条"),
        Triple("L2", "标准", "中风险提醒，每天最多 2 条（默认）"),
        Triple("L3", "积极", "主动提醒，每天最多 4 条"),
        Triple("L4", "强化", "餐后复查+运动提醒，每天最多 6 条"),
        Triple("L5", "全场景", "错餐推送+夜间安眠+服药提醒，每天最多 10 条"),
    )
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(Icons.Filled.Bolt, "干预级别")
        items.forEach { (key, label, desc) ->
            val active = currentLevel == key
            val borderColor = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            Surface(
                onClick = { onSelect(key) },
                shape = RoundedCornerShape(8.dp),
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(if (active) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp)),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(key, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        if (active) Icon(Icons.Filled.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(desc, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GlucoseUnitCard(unit: GlucoseUnit, onSelect: (GlucoseUnit) -> Unit) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(Icons.Filled.WaterDrop, "血糖单位")
        Text("中国临床惯用 mmol/L，欧美多用 mg/dL。1 mmol/L = 18.018 mg/dL。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GlucoseUnit.entries.forEachIndexed { i, u ->
                SegmentedButton(
                    selected = unit == u,
                    onClick = { onSelect(u) },
                    shape = SegmentedButtonDefaults.itemShape(i, GlucoseUnit.entries.size),
                ) { Text(u.label) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElderlyModeCard(
    enabled: Boolean,
    intervalMin: Int,
    onToggle: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(Icons.Filled.Person, "关怀模式")
        Text(
            "开启后，应用会定期主动询问您的活动、身体感觉与心情，并保存为历史记录。相关设置和记录只在设置中查看。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("启用关怀模式", Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled) {
            Text("主动询问间隔", style = MaterialTheme.typography.labelMedium)
            val options = listOf(60 to "1 小时", 120 to "2 小时", 180 to "3 小时", 240 to "4 小时", 360 to "6 小时")
            var expanded by remember { mutableStateOf(false) }
            val currentLabel = options.firstOrNull { it.first == intervalMin }?.second ?: "$intervalMin 分钟"
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = currentLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { (min, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { onIntervalChange(min); expanded = false },
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = onOpenHistory,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("查看签到历史")
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, null)
            }
        }
    }
}

@Composable
private fun DemoModeCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(Icons.Filled.Bolt, "多组学演示模式")
        Text("在尚无真实组学数据时，用合成的示例数据展示代谢指纹、蛋白炎症、基因风险与菌群画像。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("启用演示模式", Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ConsentCard(
    aiChat: Boolean,
    dataUpload: Boolean,
    onAiChat: () -> Unit,
    onDataUpload: () -> Unit,
) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(Icons.Filled.Lock, "隐私与同意")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("允许 AI 聊天", Modifier.weight(1f))
            Switch(checked = aiChat, onCheckedChange = { onAiChat() })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("允许数据上传", Modifier.weight(1f))
            Switch(checked = dataUpload, onCheckedChange = { onDataUpload() })
        }
    }
}

@Composable
private fun FeedbackEntryCard(onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.cardStyle(),
        color = Color.Transparent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Edit, null, tint = XjiePalette.Primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("意见反馈", fontWeight = FontWeight.SemiBold)
                Text(
                    "提交问题、建议或异常现象，开发者会在 Dashboard 中查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeedbackDialog(
    onDismiss: () -> Unit,
    isSubmitting: Boolean,
    onSubmit: (String, String, String?) -> Unit,
) {
    val categories = remember {
        listOf("general" to "建议", "bug" to "问题", "data" to "数据异常", "ui" to "界面体验")
    }
    var category by remember { mutableStateOf("general") }
    var content by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val trimmed = content.trim()

    fun requestDismiss() {
        if (isSubmitting) return
        if (XAgeSupportCompliancePolicy.hasFeedbackDraft(content, contact)) {
            showDiscardConfirmation = true
        } else {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = ::requestDismiss,
        modifier = Modifier.testTag("xage.feedback.dialog"),
        title = { Text("意见反馈") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                categories.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { item ->
                            FilterChip(
                                selected = category == item.first,
                                onClick = { category = item.first },
                                label = { Text(item.second, maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { if (it.length <= 2000) content = it },
                    label = { Text("反馈内容") },
                    minLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("xage.feedback.content"),
                )
                Text(
                    "${trimmed.length}/2000",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { if (it.length <= 128) contact = it },
                    label = { Text("联系方式（可选）") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("xage.feedback.contact"),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = XAgeSupportCompliancePolicy.isFeedbackValid(content) && !isSubmitting,
                modifier = Modifier.testTag("xage.feedback.submit"),
                onClick = {
                    onSubmit(category, trimmed, contact.trim().ifBlank { null })
                },
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (isSubmitting) "正在提交" else "提交")
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = ::requestDismiss) { Text("取消") }
        },
    )

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("放弃这次反馈？") },
            text = { Text("已输入的内容不会保存。") },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("放弃反馈", color = XjiePalette.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) { Text("继续编辑") }
            },
        )
    }
}
