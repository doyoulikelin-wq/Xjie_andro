package com.xjie.app.feature.settings

/** Local, versioned support/compliance content shared by every Android entry point. */
data class XAgeComplianceSection(
    val title: String,
    val content: String,
)

data class XAgePermissionDisclosure(
    val id: String,
    val title: String,
    val badge: String,
    val timing: String,
    val purpose: String,
    val refusalImpact: String,
)

enum class XAgeDeviceManagementState {
    Loading,
    Unsupported,
}

object XAgeSupportCompliancePolicy {
    const val ACCOUNT_DESTINATION = "account"
    const val SUPPORT_DESTINATION = "support"
    const val DEVICE_DESTINATION = "device"
    const val DEVICE_UNSUPPORTED_TITLE = "设备绑定暂未开放"
    const val PRIVACY_POLICY_UPDATED_AT = "2026年7月26日"
    const val PRIVACY_POLICY_EFFECTIVE_AT = "2026年7月26日"
    const val PRIVACY_POLICY_VERSION = "2026.07"
    const val PRIVACY_POLICY_URL = "https://www.jianjieaitech.com/privacy"
    const val SUPPORT_EMAIL = "support@xjie-health.com"

    val destinationIds: List<String> =
        listOf("help", "version", "privacy", "permissions", "feedback")

    val privacyPolicyRequiredTopics: List<String> = listOf(
        "适用范围与重要提示",
        "我们如何收集和使用信息",
        "敏感个人信息与单独同意",
        "共享、委托与公开披露",
        "存储与保护",
        "你的权利",
        "未成年人",
        "联系我们",
    )

    val privacySections: List<XAgeComplianceSection> = listOf(
        XAgeComplianceSection(
            title = "适用范围与重要提示",
            content = "本政策适用于“小捷”App提供的账号、健康档案、报告上传与识别、健康趋势、用药提醒和 AI 健康助手等服务。健康信息属于敏感个人信息；请在充分理解后自主决定是否提供。健康管理建议不替代医生诊断、处方、急救或线下就医。",
        ),
        XAgeComplianceSection(
            title = "我们如何收集和使用信息",
            content = "为完成账号登录、身份核验和服务保障，我们处理手机号、账号状态和必要的安全记录。你填写的基础资料、健康记录、用药信息、健康目标，以及主动上传的报告、图片或病历，用于展示档案、趋势、提醒和你请求的分析结果。使用 AI 健康助手或报告识别时，你输入的问题、上传资料及为本次回答所需的健康上下文会被处理。",
        ),
        XAgeComplianceSection(
            title = "敏感个人信息与单独同意",
            content = "健康数据、医疗资料、生理记录和语音内容可能构成敏感个人信息。Health Connect 仅在你逐项授权后读取，当前版本不向 Health Connect 写入数据；相机、图片选择、麦克风、语音识别、通知和精确提醒仅在你主动触发相应功能时使用。拒绝可选权限不会影响账号、基础浏览和手动记录等不依赖该权限的功能。",
        ),
        XAgeComplianceSection(
            title = "共享、委托与公开披露",
            content = "除为实现你主动选择的功能、履行法定义务或取得你的单独同意外，我们不会向无关第三方出售或公开健康信息。使用 Health Connect、系统通知、系统图片选择器、相机或语音识别等系统能力时，相关处理还受 Android、设备厂商及系统服务的规则约束。涉及受托处理、第三方服务或用途变化时，我们会按适用要求另行告知并取得必要同意。",
        ),
        XAgeComplianceSection(
            title = "存储与保护",
            content = "我们在实现服务所必需的期限内保存信息，并采取访问权限控制、传输保护、审计和最小化处理等措施降低风险。互联网环境并非绝对安全；请妥善保管账号和验证码，避免在非私密环境展示报告、用药或健康状态。",
        ),
        XAgeComplianceSection(
            title = "你的权利",
            content = "你可以在应用内查看、更正或补充基础资料和健康信息，管理 Health Connect 与系统权限，撤回可选授权，提交反馈，退出登录或申请注销账号。关闭系统权限不会自动删除此前主动提交的数据；如需更正、删除或了解处理情况，可通过本政策的联系方式提出申请。注销账号为不可逆操作，请谨慎确认。",
        ),
        XAgeComplianceSection(
            title = "未成年人",
            content = "如你是未成年人，请在监护人同意和指导下使用本服务。监护人认为未成年人信息被不当处理的，可按本政策联系方式与我们联系。",
        ),
        XAgeComplianceSection(
            title = "联系我们与政策更新",
            content = "本政策版本为 $PRIVACY_POLICY_VERSION，生效日期为 $PRIVACY_POLICY_EFFECTIVE_AT。如服务、处理目的或权利行使方式发生实质变化，我们会通过应用内页面或其他合理方式更新并提示。隐私、数据更正、删除或账号问题请联系 $SUPPORT_EMAIL，我们将在核验身份后处理。",
        ),
    )

    val permissionDisclosures: List<XAgePermissionDisclosure> = listOf(
        XAgePermissionDisclosure(
            id = "health",
            title = "Health Connect",
            badge = "敏感信息",
            timing = "你主动点击“授权并同步 Health Connect”时",
            purpose = "读取你逐项允许的步数、距离、睡眠、心率变异性、静息心率和体重记录，并用于当前账号的健康趋势与同步；当前版本不写入 Health Connect。",
            refusalImpact = "拒绝后不能自动同步这些健康记录；仍可手动记录和查看其他功能。",
        ),
        XAgePermissionDisclosure(
            id = "notifications",
            title = "通知",
            badge = "可选",
            timing = "你主动开启用药、关怀或报告完成提醒时",
            purpose = "发送你主动设置的本机提醒，或接收报告完成等服务状态通知。",
            refusalImpact = "拒绝后不会收到系统通知，其他功能可继续使用。",
        ),
        XAgePermissionDisclosure(
            id = "exact-alarm",
            title = "闹钟和提醒",
            badge = "特殊权限",
            timing = "你明确开启需要准时投递的用药提醒并进入系统授权页时",
            purpose = "在系统允许时按你确认的服药时间安排本机精确提醒；提醒不等于已服药。",
            refusalImpact = "拒绝后不会冒充精确提醒已安排；可继续查看计划并在应用内主动确认。",
        ),
        XAgePermissionDisclosure(
            id = "camera",
            title = "相机",
            badge = "可选",
            timing = "你选择拍摄膳食、体检报告或其他健康资料时",
            purpose = "拍摄你主动选择上传的图片，用于记录或分析。",
            refusalImpact = "拒绝后不能拍摄上传；可改用系统图片或文件选择器。",
        ),
        XAgePermissionDisclosure(
            id = "photos",
            title = "图片与文件选择",
            badge = "可选",
            timing = "你主动从系统选择器挑选报告、膳食或其他健康资料时",
            purpose = "只读取系统授予本次访问权的所选项目，用于上传、记录或分析；不因打开 App 扫描整个媒体库。",
            refusalImpact = "取消选择后不会读取任何项目，仍可使用拍摄或文字录入。",
        ),
        XAgePermissionDisclosure(
            id = "photo-save",
            title = "保存到相册",
            badge = "当前未申请",
            timing = "当前版本不申请相册写入权限",
            purpose = "拍摄的待上传原件保存在应用私有目录，不会静默写入系统相册。",
            refusalImpact = "不影响拍摄和上传；未来如提供保存动作，会在你主动触发时另行说明。",
        ),
        XAgePermissionDisclosure(
            id = "microphone",
            title = "麦克风",
            badge = "可选",
            timing = "你主动点击 AI 助手的语音输入时",
            purpose = "采集本次语音，以完成你请求的语音输入。",
            refusalImpact = "拒绝后可继续用键盘输入。",
        ),
        XAgePermissionDisclosure(
            id = "speech",
            title = "语音识别服务",
            badge = "可选",
            timing = "你主动使用语音输入且系统需要转换文字时",
            purpose = "把本次语音交给设备提供的语音识别服务转换为文字，供你确认并发送。",
            refusalImpact = "系统无可用识别服务或你取消时，可继续键盘输入。",
        ),
        XAgePermissionDisclosure(
            id = "package-installs",
            title = "安装应用更新",
            badge = "特殊权限",
            timing = "仅当你确认安装来自小捷更新服务的 APK，且系统要求允许此来源时",
            purpose = "把你确认下载的安装包交给 Android 系统安装器；应用不能绕过系统确认安装。",
            refusalImpact = "拒绝后不能通过应用内安装 APK，可继续使用当前版本或改用受支持的应用商店更新。",
        ),
        XAgePermissionDisclosure(
            id = "boot-recovery",
            title = "开机后恢复提醒",
            badge = "服务所需",
            timing = "设备重启或应用完成系统更新后",
            purpose = "仅按当前账号已明确开启的本机用药提醒恢复排期；不会借此自动确认服药或启动健康分析。",
            refusalImpact = "该能力不弹出单独授权框；你关闭提醒后不会恢复对应排期。",
        ),
        XAgePermissionDisclosure(
            id = "network",
            title = "网络连接",
            badge = "服务所需",
            timing = "你登录、同步、上传、调用 AI、检查更新或提交反馈时",
            purpose = "与服务端建立连接，完成账号、数据同步、文件上传、分析结果、更新信息和服务状态的传输；该项不弹出 Android 运行时权限框。",
            refusalImpact = "断开网络后，依赖服务端的功能不可用或无法更新；本地已显示内容不因此被删除。",
        ),
        XAgePermissionDisclosure(
            id = "not-used",
            title = "当前未申请的权限",
            badge = "当前版本",
            timing = "不适用",
            purpose = "当前版本不申请定位、通讯录、日历、蓝牙、附近设备或 NFC 权限。",
            refusalImpact = "若未来新增相关能力，我们会在实际使用场景中单独说明用途并按要求取得授权。",
        ),
    )

    fun isFeedbackValid(content: String): Boolean = content.trim().length in 2..2_000

    fun hasFeedbackDraft(content: String, contact: String): Boolean =
        content.isNotBlank() || contact.isNotBlank()

    fun deviceManagementState(isLoading: Boolean): XAgeDeviceManagementState =
        if (isLoading) XAgeDeviceManagementState.Loading else XAgeDeviceManagementState.Unsupported
}
