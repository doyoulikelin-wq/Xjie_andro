from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
XAGE = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "xage" / "XAgeMainScreen.kt"
XAGE_SYNC = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "xage" / "XAgeServerSyncViewModel.kt"
CHAT = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "chat" / "ChatScreen.kt"
HEALTH_VM = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "healthdata" / "HealthDataViewModel.kt"
TREND = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "healthdata" / "IndicatorTrendSection.kt"
DOCUMENT_UI = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "healthdata" / "DocumentListScreen.kt"
MAIN_NAV = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "navigation" / "MainScaffold.kt"
ROUTES = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "navigation" / "Routes.kt"
SETTINGS_UI = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "settings" / "SettingsScreen.kt"
SETTINGS_VM = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "settings" / "SettingsViewModel.kt"
SUPPORT_POLICY = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "settings" / "XAgeSupportCompliancePolicy.kt"
MAIN_MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
USER_API = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "core" / "network" / "api" / "UserApi.kt"
PROFILE_UI = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "patienthistory" / "PatientHistoryScreen.kt"
PROFILE_VM = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "patienthistory" / "PatientHistoryViewModel.kt"
HEALTH_API = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "core" / "network" / "api" / "HealthDataApi.kt"
MEDICATION_EDIT = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "medication" / "MedicationEditPage.kt"
MEDICATION_UI = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "medication" / "MedicationListScreen.kt"
MEDICATION_VM = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "medication" / "MedicationViewModel.kt"
MEDICATION_API = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "core" / "network" / "api" / "MedicationApi.kt"
MEDICATION_POLICY = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "medication" / "MedicationTrustPolicy.kt"
MEDICATION_REMINDER_POLICY = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "medication" / "TrustedMedicationReminderPolicy.kt"
MEDICATION_ALARM_POLICY = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "medication" / "MedicationReminderPolicy.kt"
MEDICATION_SCHEDULER = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "medication" / "MedicationScheduler.kt"
MEDICATION_REMINDER_STORE = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "medication" / "MedicationReminderStore.kt"
NOTIFICATION_CHANNELS = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "core" / "push" / "NotificationChannels.kt"
MAIN_ACTIVITY = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "MainActivity.kt"
LOGIN_UI = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "login" / "LoginScreen.kt"
LOGIN_VM = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "login" / "LoginViewModel.kt"
LOGIN_LEGAL = ROOT / "app" / "src" / "main" / "java" / "com" / "xjie" / "app" / "feature" / "login" / "LoginLegalConsentPolicy.kt"
UI_FACTORY = ROOT / "app" / "src" / "androidTest" / "java" / "com" / "xjie" / "app" / "quality" / "DeterministicXjieUiTest.kt"


class HealthTrustConsumerTests(unittest.TestCase):
    def test_report_upload_does_not_send_ai_prompt_before_confirmation(self) -> None:
        source = XAGE.read_text(encoding="utf-8")
        for forbidden in (
            "sendReportPrompt",
            "xAgeReportAnalysisPrompt",
            "上传后自动更新摘要和指标",
            "完成后会继续进入问答解读",
        ):
            self.assertNotIn(forbidden, source)
        self.assertIn("确认前不会进入 AI 问答", source)

    def test_upload_and_trend_copy_require_admission_after_confirmation(self) -> None:
        view_model = HEALTH_VM.read_text(encoding="utf-8")
        trend = TREND.read_text(encoding="utf-8")
        self.assertGreaterEqual(view_model.count("ReportTrustPresentation.title"), 3)
        self.assertNotIn("自动出现在", view_model)
        self.assertNotIn("自动出现在", trend)
        self.assertIn("确认入库", trend)
        for required in (
            "IndicatorTrendInteractionContract",
            "horizontalScroll(horizontalScrollState)",
            "detectDragGesturesAfterLongPress",
            'testTag("indicator.trend.scroll")',
            ".semantics {",
            "CustomAccessibilityAction(\"上一个数据点\")",
            "CustomAccessibilityAction(\"下一个数据点\")",
            "validReferenceRange",
            "candidateRefLow <= candidateRefHigh",
            "轻点选择；长按拖动查看连续数据；左右滑动查看历史",
        ):
            self.assertIn(required, trend)
        xage = XAGE.read_text(encoding="utf-8")
        self.assertIn("XAgeMetricDetailDialog", xage)
        self.assertIn("IndicatorTrendChart(trend = trend)", xage)
        self.assertIn("trend = metric.trend", xage)
        self.assertIn("trend = trend", XAGE_SYNC.read_text(encoding="utf-8"))

    def test_xage_home_has_real_information_architecture_and_no_simulated_health_success(self) -> None:
        source = XAGE.read_text(encoding="utf-8")
        navigation = MAIN_NAV.read_text(encoding="utf-8")
        routes = ROUTES.read_text(encoding="utf-8")
        settings = SETTINGS_UI.read_text(encoding="utf-8")
        settings_vm = SETTINGS_VM.read_text(encoding="utf-8")
        for required in (
            "XAgeInformationArchitecture.DATA_MANAGER_TITLE",
            "XAgeQuickActionStrip",
            "XAgeDataManagerPage",
            'Text("数据卡片管理"',
            'testTag("xage.data.manager.back")',
            "HealthConnectPermissionRequester(healthConnectVm)",
            "HealthConnectSyncViewModel",
            "onHealthConnectSync = healthConnectVm::requestSync",
            "XAgeHealthConnectSyncCard",
            "if (healthConnectState.phase == HealthConnectSyncPhase.Success) syncVm.refresh()",
            "尚未启用计算",
            "XAgeInformationArchitecture.ACCOUNT_DESTINATION",
            "XAgeInformationArchitecture.SUPPORT_HELP_DESTINATION",
            "XAgeInformationArchitecture.SUPPORT_VERSION_DESTINATION",
            "XAgeInformationArchitecture.SUPPORT_PRIVACY_DESTINATION",
            "XAgeInformationArchitecture.SUPPORT_PERMISSIONS_DESTINATION",
            "XAgeInformationArchitecture.SUPPORT_FEEDBACK_DESTINATION",
        ):
            self.assertIn(required, source)
        for forbidden in (
            "dataSortMode",
            "androidHealthSamples",
            'age = "29.9"',
            '"压力", 68',
            '"恢复", 82',
            '"炎症", 57',
            "XAgeDataManagerDialog",
            'XAgeGlassDialog(title = "数据卡片管理"',
            "XAgeAndroidHealthStatus",
            "XAgeAndroidHealthSyncCard",
            "var completedActionIds",
            "var selectedTagIds",
            "markUpdated(",
            '"已更新"',
            "XAgePanelDestinationScreen",
            "XAgePanelCategory",
            "primaryActionCount",
            "XAgeInformationArchitecture.SUPPORT_DESTINATION",
        ):
            self.assertNotIn(forbidden, source)
        self.assertNotIn("Route.XAgePanelDestination", navigation)
        self.assertNotIn("XAgePanelDestination", routes)
        self.assertIn('title = "就医助手"', navigation)
        document_ui = DOCUMENT_UI.read_text(encoding="utf-8")
        for required in (
            'Text("就医助手"',
            '"选择就医资料（文件）"',
            '"拍照添加就医资料"',
            '"暂无就医资料"',
            "不替代医生诊断、审方或安排随访",
        ):
            self.assertIn(required, document_ui)
        for forbidden in ("诊断摘要", "处方核对", "随访提醒"):
            self.assertNotIn(forbidden, source)
        for destination, route in (
            ("meals", "Route.Meals.path"),
            ("mood", "Route.Mood.path"),
            ("weight", "Route.Weight.path"),
            ("reports", "Route.ExamReports.path"),
            ("medications", "Route.Medications.path"),
            ("health_plan", "Route.HealthPlan.path"),
            ("medical", "Route.MedicalAssistant.path"),
            ("profile", "Route.PatientHistory.path"),
            ("device", 'Route.SettingsFocus("device").path'),
            ("account", 'Route.SettingsFocus("account").path'),
            ("family", "Route.FamilyMode.path"),
            ("support", 'Route.SettingsFocus("support").path'),
        ):
            self.assertIn(f'"{destination}" -> {route}', navigation)
        self.assertIn('data class SettingsFocus(val focus: String)', routes)
        for required in (
            "SupportHub(",
            'title = "权限申请与使用情况说明"',
            'title = "意见反馈"',
            "isSubmitting = state.feedbackSubmitting",
            "XAgeSupportCompliancePolicy.hasFeedbackDraft(content, contact)",
            'Text("放弃这次反馈？")',
            "DeleteAccountDialog(",
        ):
            self.assertIn(required, settings)
        self.assertIn("onSuccess()", settings_vm)
        self.assertIn('@DELETE("api/users/me")', USER_API.read_text(encoding="utf-8"))
        self.assertNotIn("后续版本会把反馈入口", settings)

    def test_support_policy_covers_current_privacy_and_every_android_capability(self) -> None:
        policy = SUPPORT_POLICY.read_text(encoding="utf-8")
        manifest = MAIN_MANIFEST.read_text(encoding="utf-8")

        for required in (
            'const val PRIVACY_POLICY_VERSION = "2026.07"',
            'const val PRIVACY_POLICY_UPDATED_AT = "2026年7月26日"',
            'listOf("help", "version", "privacy", "permissions", "feedback")',
            'title = "适用范围与重要提示"',
            'title = "我们如何收集和使用信息"',
            'title = "敏感个人信息与单独同意"',
            'title = "共享、委托与公开披露"',
            'title = "存储与保护"',
            'title = "你的权利"',
            'title = "未成年人"',
            'title = "联系我们与政策更新"',
        ):
            self.assertIn(required, policy)

        permission_to_disclosure = {
            "android.permission.INTERNET": 'id = "network"',
            "android.permission.ACCESS_NETWORK_STATE": 'id = "network"',
            "android.permission.CAMERA": 'id = "camera"',
            "android.permission.RECORD_AUDIO": 'id = "microphone"',
            "android.permission.POST_NOTIFICATIONS": 'id = "notifications"',
            "android.permission.REQUEST_INSTALL_PACKAGES": 'id = "package-installs"',
            "android.permission.SCHEDULE_EXACT_ALARM": 'id = "exact-alarm"',
            "android.permission.USE_EXACT_ALARM": 'id = "exact-alarm"',
            "android.permission.RECEIVE_BOOT_COMPLETED": 'id = "boot-recovery"',
        }
        for permission, disclosure in permission_to_disclosure.items():
            self.assertIn(permission, manifest)
            self.assertIn(disclosure, policy)
        self.assertIn("android.permission.health.READ_STEPS", manifest)
        self.assertIn('id = "health"', policy)
        self.assertNotIn("android.permission.READ_MEDIA_IMAGES", manifest)
        self.assertNotIn("android.permission.BLUETOOTH", manifest)
        self.assertNotIn("android.permission.NFC", manifest)
        self.assertIn('id = "photos"', policy)
        self.assertIn('id = "photo-save"', policy)
        self.assertIn('id = "speech"', policy)
        self.assertIn('id = "not-used"', policy)

    def test_registration_uses_shared_dual_legal_consent_and_hides_research_launcher(self) -> None:
        ui = LOGIN_UI.read_text(encoding="utf-8")
        view_model = LOGIN_VM.read_text(encoding="utf-8")
        legal = LOGIN_LEGAL.read_text(encoding="utf-8")
        factory = UI_FACTORY.read_text(encoding="utf-8")

        self.assertIn('Text("小捷"', ui)
        self.assertNotIn('Text("Xjie"', ui)
        self.assertEqual(1, ui.count("ModeSwitch("), "research mode switch must have no UI caller")
        for required in (
            'testTag("login.legal.consents")',
            'tag = "login.legal.userAgreement"',
            'tag = "login.legal.privacyPolicy"',
            "modifier = Modifier.testTag(tag)",
            'Text("确认同意并注册")',
            "vm.acceptRequiredLegalAgreements()",
            "LoginLegalConsentPolicy.privacyPolicySections",
        ):
            self.assertIn(required, ui)
        self.assertIn("LoginLegalConsentPolicy.canSubmit(", view_model)
        self.assertLess(
            view_model.index("LoginLegalConsentPolicy.canSubmit("),
            view_model.index("repo.loginOrSignupPhone("),
        )
        self.assertIn(
            "get() = XAgeSupportCompliancePolicy.privacySections",
            legal,
        )
        self.assertIn("protected open val launchAuthenticated: Boolean = true", factory)
        self.assertIn("UiAutomationRuntime.INTENT_AUTHENTICATED_EXTRA", factory)

    def test_chat_input_is_multiline_and_keyboard_dismisses_on_scroll_or_navigation(self) -> None:
        source = XAGE.read_text(encoding="utf-8")
        for required in (
            "LocalFocusManager.current",
            "LocalSoftwareKeyboardController.current",
            "focusManager.clearFocus(force = true)",
            "keyboardController?.hide()",
            ".nestedScroll(dismissKeyboardOnScroll)",
            ".heightIn(min = adaptive.chatInputHeight, max = 156.dp)",
            "singleLine = false",
            "minLines = 1",
            "maxLines = 5",
        ):
            self.assertIn(required, source)
        self.assertNotIn("singleLine = true", source)

        legacy_chat = CHAT.read_text(encoding="utf-8")
        for required in (
            "LocalSoftwareKeyboardController.current",
            "focus.clearFocus(force = true)",
            "keyboardController?.hide()",
            ".nestedScroll(dismissKeyboardOnScroll)",
            "maxLines = 4",
            "singleLine = false",
        ):
            self.assertIn(required, legacy_chat)

    def test_report_review_ui_requires_field_decisions_and_report_level_confirmation(self) -> None:
        source = DOCUMENT_UI.read_text(encoding="utf-8")
        for required in (
            "ReportReviewPolicy.canSubmit",
            "确认整份报告并入库",
            "修正后的值",
            "不入库",
            "低置信度",
            "reportConflictLabel",
            "ReportReviewPolicy.hasUnsavedDrafts",
            "BackHandler { requestBack() }",
            "放弃未提交的复核？",
            "snapshotFlow { scrollState.isScrollInProgress }",
            "手动补录未识别字段",
            "failure_recovery",
            "仍需逐项复核并确认整份报告",
        ):
            self.assertIn(required, source)
        self.assertIn("ReportReviewPolicy.isSameRevision", HEALTH_VM.read_text(encoding="utf-8"))
        self.assertIn(
            'api/health-data/report-workflows/{workflowId}/manual-candidates',
            HEALTH_API.read_text(encoding="utf-8"),
        )

    def test_health_profile_uses_server_authority_and_explicit_confirmation(self) -> None:
        ui = PROFILE_UI.read_text(encoding="utf-8")
        view_model = PROFILE_VM.read_text(encoding="utf-8")
        api = HEALTH_API.read_text(encoding="utf-8")
        for required in (
            "profile.overview",
            "healthProfile.primaryAction",
            "healthProfile.basic.derivedBMI",
            "healthProfile.goal.editor.name",
            "healthProfile.goal.editor.startedOn",
            "healthProfile.goal.editor.metrics",
            "healthProfile.medication.open",
            "待确认更新",
            "确认到画像",
            "目标只能由你主动创建；支持同时管理多个目标。AI 和报告候选不能自动替你设定。",
            "healthProfile.history.revision.",
            "这里只展示服务端已确认摘要",
            "HealthProfileConfirmation.SaveSafety",
            "X年龄暂不消费健康画像",
            "完整度只表示资料是否已处理",
            "BackHandler(enabled = true, onBack = ::requestBack)",
            "state.hasUnsavedEditor",
            "放弃未保存的画像修改？",
            "继续编辑",
        ):
            self.assertIn(required, ui)
        for required in (
            "reviewHealthProfileCandidate",
            "upsertHealthProfileFact",
            "retractHealthProfileFact",
            "HealthProfileRequestToken",
            "PendingMutation",
            "HealthProfileConfirmation.RetractFact",
        ):
            self.assertIn(required, view_model)
        for endpoint in (
            'api/health-data/profile-trust")',
            'api/health-data/profile-trust/candidates/{candidateId}/review")',
            'api/health-data/profile-trust/facts")',
            'api/health-data/profile-trust/facts/{factId}/retract")',
        ):
            self.assertIn(endpoint, api)
        self.assertIn(
            "onOpenMedications = { navController.navigate(Route.Medications.path) }",
            MAIN_NAV.read_text(encoding="utf-8"),
        )
        self.assertNotIn("PatientHistoryUpdateBody", view_model)
        self.assertNotIn("Text(if (state.saving) \"保存中\" else \"保存\")", ui)
        self.assertNotIn("永久保存", ui)

    def test_medication_editor_is_a_page_with_keyboard_and_unsaved_change_guards(self) -> None:
        source = MEDICATION_EDIT.read_text(encoding="utf-8")
        for required in (
            "fun MedicationEditPage(",
            "TopAppBar(",
            ".nestedScroll(dismissKeyboardOnScroll)",
            "focus.clearFocus(force = true)",
            "keyboard?.hide()",
            "放弃未保存修改？",
            "MedicationFormPolicy.validate",
            "提醒默认关闭",
        ):
            self.assertIn(required, source)
        self.assertNotIn("androidx.compose.ui.window.Dialog", source)
        self.assertNotIn("fun MedicationEditDialog(", source)

    def test_trusted_medication_screen_never_promotes_schedule_or_ocr_to_confirmation(self) -> None:
        ui = MEDICATION_UI.read_text(encoding="utf-8")
        view_model = MEDICATION_VM.read_text(encoding="utf-8")
        api = MEDICATION_API.read_text(encoding="utf-8")
        policy = MEDICATION_POLICY.read_text(encoding="utf-8")
        reminder_policy = MEDICATION_REMINDER_POLICY.read_text(encoding="utf-8")
        alarm_policy = MEDICATION_ALARM_POLICY.read_text(encoding="utf-8")
        scheduler = MEDICATION_SCHEDULER.read_text(encoding="utf-8")
        reminder_store = MEDICATION_REMINDER_STORE.read_text(encoding="utf-8")
        notification_channels = NOTIFICATION_CHANNELS.read_text(encoding="utf-8")
        main_activity = MAIN_ACTIVITY.read_text(encoding="utf-8")
        for required in (
            "MedicationTrustPolicy.primaryAction",
            "MedicationDashboardPresentation.hero",
            "提醒时间已过，仍需你确认",
            "确认本次服药",
            "确认已经服用？",
            '"xage.medication.hero.next"',
            '"xage.medication.bottomAction"',
            "MedicationPlansPage",
            "MedicationReactionsPage",
            "MedicationCoursePage",
            "提醒默认关闭",
            "当前版本没有接入相机",
            "低置信度",
            "确认并创建计划",
            "预计剩余",
            "不能据此认定由药物导致",
            "BackHandler(enabled = true, onBack = ::requestBack)",
            ".nestedScroll(dismissKeyboardOnScroll)",
            "放弃未保存修改？",
            "MedicationReminderEditor",
            "MedicationRecordsPage",
            "ActivityResultContracts.RequestPermission",
            "ActivityResultContracts.StartActivityForResult",
            "Settings.ACTION_APP_NOTIFICATION_SETTINGS",
            "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM",
            "TrustedMedicationReminderPolicy.addCapabilities",
            "vm::correctDose",
            "疗程已确认率",
        ):
            self.assertIn(required, ui)
        for forbidden in (
            "拍下药品说明书自动识别",
            "漏服已确认",
            "准确库存",
            "MedicationEditPage(",
            "state.items",
            "本机提醒已按你的设置开启",
        ):
            self.assertNotIn(forbidden, ui)
        for required in (
            "repo.trustedToday",
            "repo.trustedPlans",
            "repo.trustedPrefills",
            "repo.trustedReactions",
            "pendingDoseBodies.getOrPut",
            "subject_user_id = subjectUserId",
            "MedicationTrustPolicy.isTrustedSnapshot",
            "scheduler.scheduleTrustedSnooze",
            "scheduler.reconcileTrustedPlans",
            "scheduler.saveTrustedReminder",
            "NotificationPermissionRequired",
            "ExactAlarmPermissionRequired",
            "scheduledReminderCountByPlan",
            "captureMutationOwner",
            "authManager.isCurrent",
        ):
            self.assertIn(required, view_model)
        for required in (
            "expected_plan_version = task.plan_version",
            "expected_occurrence_version = task.occurrence_version",
            '"possibly_missed" -> "可能漏服（待确认）"',
            'inventory.basis != "user_confirmed_taken_events_only"',
        ):
            self.assertIn(required, policy)
        for required in (
            "已确认率",
            "续配资格：暂不可用",
            "疗程已确认率：暂不可用",
        ):
            self.assertIn(required, reminder_policy)
        self.assertIn("trustedPlanRequestCode", alarm_policy)
        for required in (
            "saveTrustedReminder",
            "reconcileTrustedPlans",
            "rescheduleTrustedSnoozes",
            "REMINDER_KIND_TRUSTED_PLAN",
            "MedicationNotificationActivity",
            "scheduledCount",
            "exactAlarmAccessState",
            "requireExact = true",
        ):
            self.assertIn(required, scheduler)
        self.assertIn("saveTrustedReminderSettings", reminder_store)
        self.assertIn("MEDICATION_SILENT", notification_channels)
        self.assertNotIn("ensureNotificationPermission", main_activity)
        self.assertNotIn("ActivityResultContracts.RequestPermission", main_activity)
        for endpoint in (
            'api/medications/trust/today',
            'api/medications/trust/plans',
            'api/medications/trust/prefill-candidates',
            'api/medications/trust/dose-events',
            'api/medications/trust/reactions',
            'api/medications/trust/plans/confirm',
        ):
            self.assertIn(endpoint, api)


if __name__ == "__main__":
    unittest.main()
