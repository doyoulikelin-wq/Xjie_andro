import re
import unittest
from pathlib import Path


ANDROID_ROOT = Path(__file__).resolve().parents[2]


def without_kotlin_comments(source: str) -> str:
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", source)


class DeterministicAndroidUiTransportTest(unittest.TestCase):
    def test_transport_is_debug_only_exact_opt_in_and_unknown_requests_fail_closed(self) -> None:
        runtime = (ANDROID_ROOT / "app/src/main/java/com/xjie/app/core/quality/UiAutomationRuntime.kt").read_text()
        debug = (ANDROID_ROOT / "app/src/debug/java/com/xjie/app/core/quality/DebugUiAutomationTransport.kt").read_text()
        main_activity = (ANDROID_ROOT / "app/src/main/java/com/xjie/app/MainActivity.kt").read_text()

        self.assertIn('EXACT_DEBUG_TOKEN = "xjie-ui-deterministic-v1"', runtime)
        self.assertIn("if (!BuildConfig.DEBUG || rawValue != EXACT_DEBUG_TOKEN) return", runtime)
        self.assertIn("UiAutomationRuntime.activateIfExplicit", main_activity)
        self.assertLess(
            main_activity.index("UiAutomationRuntime.activateIfExplicit"),
            main_activity.index("setContent {"),
        )
        self.assertIn("if (fixture == null) 418 else 200", debug)
        self.assertIn("unknownRequests += key", debug)
        self.assertNotIn("chain.proceed", debug)
        self.assertIn('"POST" to "/api/chat/stream" -> chatStream(request)', debug)
        self.assertIn('request.headers.values("Accept")', debug)
        self.assertIn('payload["client_message_id"]', debug)
        self.assertIn('threadId != null && threadId != "ui-thread"', debug)
        self.assertIn('"text/event-stream; charset=utf-8"', debug)
        self.assertIn('"GET" to "/api/users/settings" -> userSettings(request)', debug)
        self.assertIn('"intervention_level":"balanced"', debug)
        self.assertIn('"type":"route"', debug)
        self.assertIn('"type":"done"', debug)
        self.assertFalse((ANDROID_ROOT / "app/src/main/java/com/xjie/app/core/quality/DebugUiAutomationTransport.kt").exists())

    def test_zero_evidence_score_waits_for_loaded_empty_state_before_assertion(self) -> None:
        factory = without_kotlin_comments(
            (
                ANDROID_ROOT
                / "app/src/androidTest/java/com/xjie/app/quality/DeterministicXjieUiTest.kt"
            ).read_text()
        )

        helper = factory.split("protected fun waitForLoadedXAgeData()", 1)[1].split(
            "/**", 1
        )[0]
        self.assertIn('waitFor(hasTestTag("xage.data.metrics.loaded"))', helper)

        readiness = "waitForLoadedXAgeData()"
        score_tag = re.compile(
            r'"xage\.data\.score\.(?:pressure|recovery|inflammation)(?:\.[^"]+)?"'
        )
        guarded_methods: list[str] = []
        test_root = ANDROID_ROOT / "app/src/androidTest/java"
        for path in sorted(test_root.rglob("*.kt")):
            source = without_kotlin_comments(path.read_text())
            for segment in source.split("\n    @Test"):
                first_score_access = score_tag.search(segment)
                if first_score_access is None:
                    continue
                method_name = re.search(r"fun\s+([A-Za-z0-9_]+)\s*\(", segment)
                self.assertIsNotNone(method_name, str(path))
                guarded_methods.append(f"{path.name}::{method_name.group(1)}")
                self.assertEqual(segment.count(readiness), 1, guarded_methods[-1])
                self.assertLess(
                    segment.index(readiness),
                    first_score_access.start(),
                    guarded_methods[-1],
                )
                self.assertIsNone(
                    re.search(
                        r'waitFor\s*\(\s*hasTestTag\s*\(\s*"xage\.data\.score\.',
                        segment,
                    ),
                    guarded_methods[-1],
                )
        self.assertEqual(
            guarded_methods,
            ["XAgeShellSwipeUiTest.kt::zeroEvidenceShowsNeutralDailyScoreAndIndependentWarning"],
        )

    def test_app_owned_modals_close_through_focused_semantic_owner(self) -> None:
        factory = without_kotlin_comments(
            (
                ANDROID_ROOT
                / "app/src/androidTest/java/com/xjie/app/quality/DeterministicXjieUiTest.kt"
            ).read_text()
        )
        helper = factory.split("protected fun closeAppOwnedModal(closeTag: String)", 1)[1].split(
            "/**", 1
        )[0]
        self.assertIn("waitFor(hasTestTag(closeTag))", helper)
        self.assertIn(
            "compose.onNodeWithTag(closeTag, useUnmergedTree = true)",
            helper,
        )
        self.assertIn(".assertWidthIsAtLeast(48.dp)", helper)
        self.assertIn(".assertHeightIsAtLeast(48.dp)", helper)
        self.assertIn(".performClick()", helper)
        self.assertIn("waitForAbsent(hasTestTag(closeTag))", helper)

        xage_source = without_kotlin_comments(
            (
                ANDROID_ROOT
                / "app/src/androidTest/java/com/xjie/app/feature/xage/XAgeShellSwipeUiTest.kt"
            ).read_text()
        )
        weight_source = without_kotlin_comments(
            (
                ANDROID_ROOT
                / "app/src/androidTest/java/com/xjie/app/feature/weight/WeightDashboardUiTest.kt"
            ).read_text()
        )
        xage_screen = without_kotlin_comments(
            (
                ANDROID_ROOT
                / "app/src/main/java/com/xjie/app/feature/xage/XAgeMainScreen.kt"
            ).read_text()
        )
        weight_screen = without_kotlin_comments(
            (
                ANDROID_ROOT
                / "app/src/main/java/com/xjie/app/feature/weight/WeightScreen.kt"
            ).read_text()
        )

        def test_method(source: str, name: str) -> str:
            segment = source.split(f"fun {name}()", 1)[1]
            return segment.split("\n    @Test", 1)[0]

        dialog_methods = {
            "zeroEvidenceShowsNeutralDailyScoreAndIndependentWarning": 2,
            "swipingFocusedChatInputUsesStubbedAnswerAndClearsEditorPage": 2,
        }
        for method_name, expected_closes in dialog_methods.items():
            method = test_method(xage_source, method_name)
            self.assertEqual(
                method.count('closeAppOwnedModal("xage.dialog.close")'),
                expected_closes,
                method_name,
            )
            self.assertNotIn("pressBack()", method, method_name)
        self.assertEqual(
            xage_source.count('closeAppOwnedModal("xage.dialog.close")'),
            4,
        )

        weight_method = test_method(
            weight_source,
            "quickActionOpensTrustedWeightDetailAndInputSheetsReturnSafely",
        )
        height_close = 'closeAppOwnedModal("weight.height.close")'
        guidance_close = 'closeAppOwnedModal("weight.guidance.close")'
        self.assertEqual(weight_method.count(height_close), 1)
        self.assertEqual(weight_method.count(guidance_close), 1)
        self.assertEqual(weight_method.count("pressBack()"), 1)
        self.assertLess(weight_method.index(height_close), weight_method.index(guidance_close))
        self.assertLess(weight_method.index(guidance_close), weight_method.index("pressBack()"))

        self.assertRegex(
            xage_screen,
            r'\.size\(48\.dp\)\s*\.testTag\("xage\.dialog\.close"\)',
        )
        for close_tag in ("weight.height.close", "weight.guidance.close"):
            self.assertIn(
                f'Modifier.size(48.dp).testTag("{close_tag}")',
                weight_screen,
            )

        allowed_raw_backs: dict[str, int] = {}
        test_root = ANDROID_ROOT / "app/src/androidTest/java"
        for path in sorted(test_root.rglob("*.kt")):
            source = without_kotlin_comments(path.read_text())
            for segment in source.split("\n    @Test"):
                count = segment.count("pressBack()")
                if count == 0:
                    continue
                method_name = re.search(r"fun\s+([A-Za-z0-9_]+)\s*\(", segment)
                self.assertIsNotNone(method_name, str(path))
                relative_path = path.relative_to(test_root).as_posix()
                key = f"{relative_path}::{method_name.group(1)}"
                self.assertNotIn(key, allowed_raw_backs, key)
                allowed_raw_backs[key] = count
        self.assertEqual(
            allowed_raw_backs,
            {
                "com/xjie/app/feature/medication/MedicationDashboardUiTest.kt::secondaryRowsOpenRealDestinationsAndBackReturnsToDashboard": 1,
                "com/xjie/app/feature/weight/WeightDashboardUiTest.kt::quickActionOpensTrustedWeightDetailAndInputSheetsReturnSafely": 1,
                "com/xjie/app/feature/xage/XAgeShellSwipeUiTest.kt::mealsAndProfileUseAllowlistedEmptyStatesAndReturnToDataPage": 2,
                "com/xjie/app/feature/xage/XAgeShellSwipeUiTest.kt::moreMenuRoutesDeviceAndSupportWithoutDeadAffordances": 2,
            },
        )

    def test_every_app_owned_okhttp_builder_installs_the_shared_transport(self) -> None:
        network = (ANDROID_ROOT / "app/src/main/java/com/xjie/app/core/network/NetworkModule.kt").read_text()
        health_connect = (
            ANDROID_ROOT / "app/src/main/java/com/xjie/app/core/network/HealthConnectNetworkModule.kt"
        ).read_text()

        self.assertEqual(network.count("OkHttpClient.Builder()"), network.count("UiAutomationRuntime.installOn("))
        self.assertEqual(
            health_connect.count("OkHttpClient.Builder()"),
            health_connect.count("UiAutomationRuntime.installOn("),
        )
        self.assertIn("if (UiAutomationRuntime.isActive) return", (
            ANDROID_ROOT / "app/src/main/java/com/xjie/app/feature/healthconnect/HealthConnectPermissionRequester.kt"
        ).read_text())

    def test_every_network_entry_point_uses_the_single_api_prefix_policy(self) -> None:
        network = (ANDROID_ROOT / "app/src/main/java/com/xjie/app/core/network/NetworkModule.kt").read_text()
        health_connect = (
            ANDROID_ROOT / "app/src/main/java/com/xjie/app/core/network/HealthConnectNetworkModule.kt"
        ).read_text()
        authenticator = (
            ANDROID_ROOT / "app/src/main/java/com/xjie/app/core/network/TokenAuthenticator.kt"
        ).read_text()
        policy = (
            ANDROID_ROOT / "app/src/main/java/com/xjie/app/core/network/ApiEndpointPolicy.kt"
        ).read_text()

        self.assertEqual(
            network.count(".baseUrl("),
            network.count("ApiEndpointPolicy.retrofitBaseUrl(BuildConfig.API_BASE_URL)"),
        )
        self.assertEqual(
            health_connect.count(".baseUrl("),
            health_connect.count("ApiEndpointPolicy.retrofitBaseUrl(BuildConfig.API_BASE_URL)"),
        )
        self.assertIn(
            'ApiEndpointPolicy.endpoint(BuildConfig.API_BASE_URL, "api/auth/refresh")',
            authenticator,
        )
        self.assertNotIn("BuildConfig.API_BASE_URL.trimEnd", network + health_connect + authenticator)
        self.assertIn('pathSegments.all { it == "api" }', policy)

    def test_connected_tests_share_real_app_factory_profile_gate_and_runtime_ledger(self) -> None:
        build = (ANDROID_ROOT / "app/build.gradle.kts").read_text()
        factory = (
            ANDROID_ROOT
            / "app/src/androidTest/java/com/xjie/app/quality/DeterministicXjieUiTest.kt"
        ).read_text()
        xage_ui = (
            ANDROID_ROOT
            / "app/src/androidTest/java/com/xjie/app/feature/xage/XAgeShellSwipeUiTest.kt"
        ).read_text()
        medication_ui = (
            ANDROID_ROOT
            / "app/src/androidTest/java/com/xjie/app/feature/medication/MedicationDashboardUiTest.kt"
        ).read_text()
        weight_ui = (
            ANDROID_ROOT
            / "app/src/androidTest/java/com/xjie/app/feature/weight/WeightDashboardUiTest.kt"
        ).read_text()
        xage_screen = (
            ANDROID_ROOT
            / "app/src/main/java/com/xjie/app/feature/xage/XAgeMainScreen.kt"
        ).read_text()
        medication_screen = (
            ANDROID_ROOT
            / "app/src/main/java/com/xjie/app/feature/medication/MedicationListScreen.kt"
        ).read_text()
        active_xage_screen = without_kotlin_comments(xage_screen)
        active_medication_screen = without_kotlin_comments(medication_screen)
        active_xage_ui = without_kotlin_comments(xage_ui)
        active_medication_ui = without_kotlin_comments(medication_ui)
        active_weight_ui = without_kotlin_comments(weight_ui)
        runtime = (
            ANDROID_ROOT / "app/src/main/java/com/xjie/app/core/quality/UiAutomationRuntime.kt"
        ).read_text()
        debug = (
            ANDROID_ROOT / "app/src/debug/java/com/xjie/app/core/quality/DebugUiAutomationTransport.kt"
        ).read_text()

        self.assertIn(
            'testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"',
            build,
        )
        self.assertNotIn("HiltTestApplication", factory)
        self.assertIn("UiAutomationRuntime.EXACT_DEBUG_TOKEN", factory)
        self.assertIn("UiAutomationRuntime.INTENT_AUTHENTICATED_EXTRA", factory)
        self.assertIn("protected open val launchAuthenticated: Boolean = true", factory)
        self.assertIn('INTENT_AUTHENTICATED_EXTRA = "xjie.ui.authenticated"', runtime)
        self.assertIn("if (!authenticated)", debug)
        self.assertIn("authManager.logout()", debug)
        self.assertIn("DebugUiAutomationTransport.assertNoRequestEscapedStub()", factory)
        self.assertIn('const val PROFILE_ARGUMENT = "xjie.ui.profile"', factory)
        self.assertIn('"standard_api35"', factory)
        self.assertIn('"compact_api35"', factory)
        self.assertIn('"large_text_api35"', factory)
        self.assertIn("protected fun waitForAndScrollToText(text: String)", factory)
        helper = factory.split("protected fun waitForAndScrollToText(text: String)", 1)[1].split(
            "/**", 1
        )[0]
        self.assertLess(helper.index("waitFor(hasText(text))"), helper.index("performScrollTo()"))
        self.assertIn(
            "compose.onNodeWithText(text)\n"
            "            .performScrollTo()\n"
            "            .assertIsDisplayed()",
            helper,
        )
        self.assertNotIn("useUnmergedTree = true", helper)
        for loaded_text in (
            "本日暂无已确认餐食；识别草稿不会自动进入这里",
            "暂无服务端已确认的长期用药摘要。",
        ):
            call_pattern = rf'^\s{{8}}waitForAndScrollToText\("{re.escape(loaded_text)}"\)\s*$'
            self.assertEqual(
                len(re.findall(call_pattern, active_xage_ui, flags=re.MULTILINE)),
                1,
            )
            self.assertNotIn(f'onNodeWithText("{loaded_text}"', active_xage_ui)

        self.assertIn(
            "protected fun waitForAndScrollToTag(\n"
            "        rootTag: String,\n"
            "        readinessTag: String,\n"
            "        targetTag: String,\n"
            "    )",
            factory,
        )
        tag_helper = factory.split(
            "protected fun waitForAndScrollToTag(", 1
        )[1].split("/**", 1)[0]
        self.assertIn("waitFor(hasTestTag(rootTag))", tag_helper)
        self.assertLess(
            tag_helper.index("waitFor(hasTestTag(readinessTag))"),
            tag_helper.index("waitFor(hasTestTag(rootTag))"),
        )
        self.assertLess(
            tag_helper.index("waitFor(hasTestTag(rootTag))"),
            tag_helper.index("performScrollToNode(hasTestTag(targetTag))"),
        )
        self.assertIn(
            "compose.onNodeWithTag(rootTag, useUnmergedTree = true)\n"
            "            .performScrollToNode(hasTestTag(targetTag))",
            tag_helper,
        )
        self.assertIn(
            "compose.onNodeWithTag(targetTag, useUnmergedTree = true)\n"
            "            .assertIsDisplayed()",
            tag_helper,
        )
        self.assertEqual(active_xage_screen.count('"xage.data.metrics.loaded"'), 1)
        self.assertRegex(
            active_xage_screen,
            r'\.then\s*\(\s*if\s*\(syncState\.snapshot\.isLoaded\)\s*'
            r'Modifier\.testTag\("xage\.data\.metrics\.loaded"\)',
        )
        self.assertLess(
            active_xage_screen.index('"xage.data.metrics.loaded"'),
            active_xage_screen.index('.testTag("xage.data.scroll")'),
        )
        self.assertNotIn('item(key = "metrics-loaded-sentinel")', active_xage_screen)
        self.assertEqual(active_medication_screen.count('"xage.medication.loaded"'), 1)
        self.assertRegex(
            active_medication_screen,
            r'Scaffold\s*\(\s*modifier\s*=\s*if\s*'
            r'\(editor\s*==\s*null\s*&&\s*!state\.loading\s*&&\s*'
            r'state\.today\s*!=\s*null\)\s*\{\s*'
            r'Modifier\.testTag\("xage\.medication\.loaded"\)',
        )
        self.assertLess(
            active_medication_screen.index('"xage.medication.loaded"'),
            active_medication_screen.index('snackbarHost = { SnackbarHost(snackbar) }'),
        )
        self.assertNotIn('item(key = "loaded-state")', active_medication_screen)
        for source, root_tag, readiness_tag, target_tag in (
            (
                active_weight_ui,
                "xage.data.scroll",
                "xage.data.metrics.loaded",
                "xage.data.metric.bodyWeight",
            ),
            (
                active_medication_ui,
                "xage.medication.root",
                "xage.medication.loaded",
                "xage.medication.destination.plans",
            ),
            (
                active_medication_ui,
                "xage.medication.root",
                "xage.medication.loaded",
                "xage.medication.destination.reactions",
            ),
        ):
            expected_call = (
                f'waitForAndScrollToTag(\n'
                f'            rootTag = "{root_tag}",\n'
                f'            readinessTag = "{readiness_tag}",\n'
                f'            targetTag = "{target_tag}",\n'
                "        )"
            )
            self.assertEqual(source.count(expected_call), 1)
            self.assertEqual(source.count(f'"{target_tag}"'), 2)
            self.assertIsNone(
                re.search(
                    rf'performScrollToNode\s*\(\s*hasTestTag\s*\(\s*"{re.escape(target_tag)}"',
                    source,
                ),
            )

    def test_ci_runs_backend_and_all_ui_profiles_with_exact_fail_closed_gates(self) -> None:
        workflow = (ANDROID_ROOT.parent / ".github/workflows/ci.yml").read_text()
        runner = (ANDROID_ROOT / "tools/run_connected_ui_profiles.sh").read_text()

        self.assertIn("backend/**", workflow)
        self.assertIn("verify_backend_python_test_inventory.py", workflow)
        self.assertIn("--network none", workflow)
        self.assertIn("reactivecircus/android-emulator-runner@v2", workflow)
        self.assertIn("bash tools/run_connected_ui_profiles.sh", workflow)
        self.assertNotIn("|| true", workflow + runner)
        self.assertIn("trap finalize_run EXIT", runner)
        self.assertIn('> "$EVIDENCE_ROOT/run-status.txt"', runner)
        self.assertIn('"$EVIDENCE_ROOT/_diagnostics"', runner)
        for profile in ("standard_api35", "compact_api35", "large_text_api35"):
            self.assertIn(f"    {profile})", runner)
            self.assertIn(f"run_profile {profile}", runner)
            self.assertIn(f'--result-set "{profile}=', runner)
        self.assertIn(":app:connectedDebugAndroidTest", runner)
        self.assertIn("verify_android_ui_test_inventory.py", runner)

    def test_ci_requires_kvm_before_emulator_and_keeps_evidence_upload_fail_closed(self) -> None:
        workflow = (ANDROID_ROOT.parent / ".github/workflows/ci.yml").read_text()
        runner = (ANDROID_ROOT / "tools/run_connected_ui_profiles.sh").read_text()
        ui_job = workflow.split("  android-connected-ui:\n", 1)[1]

        evidence_step = ui_job.index("- name: Initialize connected-test evidence")
        kvm_step = ui_job.index("- name: Require KVM acceleration")
        emulator_step = ui_job.index("reactivecircus/android-emulator-runner@v2")
        self.assertLess(evidence_step, kvm_step)
        self.assertLess(kvm_step, emulator_step)

        kvm_contract = ui_job[kvm_step:emulator_step]
        self.assertIn("test -c /dev/kvm", kvm_contract)
        self.assertIn('KERNEL=="kvm", GROUP="kvm", MODE="0666"', kvm_contract)
        self.assertIn("sudo udevadm control --reload-rules", kvm_contract)
        self.assertIn("sudo udevadm trigger --name-match=kvm", kvm_contract)
        self.assertIn("sudo udevadm settle", kvm_contract)
        self.assertIn("test -r /dev/kvm", kvm_contract)
        self.assertIn("test -w /dev/kvm", kvm_contract)
        self.assertIn("disable-linux-hw-accel: false", ui_job)
        self.assertNotIn("disable-linux-hw-accel: true", ui_job)
        self.assertIn("-no-metrics", ui_job)
        self.assertNotIn("continue-on-error", ui_job)
        self.assertNotIn("|| true", ui_job + runner)

        upload_step = ui_job.split("- name: Upload connected-test evidence", 1)[1]
        self.assertIn("if: always()", upload_step)
        self.assertIn("path: Android/build/quality/android-ui", upload_step)
        self.assertIn("if-no-files-found: error", upload_step)
        clear_results = 'rm -rf "$RESULT_ROOT" "$REPORT_ROOT"'
        self.assertIn(clear_results, runner)
        self.assertLess(
            runner.index(clear_results),
            runner.index('"$ANDROID_ROOT/gradlew" --no-daemon :app:connectedDebugAndroidTest'),
        )

    def test_ci_pins_deterministic_origin_without_local_properties_dependency(self) -> None:
        workflow = (ANDROID_ROOT.parent / ".github/workflows/ci.yml").read_text()
        build = (ANDROID_ROOT / "app/build.gradle.kts").read_text()
        debug = (
            ANDROID_ROOT / "app/src/debug/java/com/xjie/app/core/quality/DebugUiAutomationTransport.kt"
        ).read_text()
        ui_job = workflow.split("  android-connected-ui:\n", 1)[1]

        pinned_origin = "API_BASE_URL_DEBUG: https://www.jianjieaitech.com/api"
        self.assertEqual(ui_job.count("API_BASE_URL_DEBUG"), 1)
        self.assertEqual(ui_job.count(pinned_origin), 1)
        self.assertIn(
            "    env:\n      " + pinned_origin + "\n    defaults:\n",
            ui_job,
        )
        self.assertLess(ui_job.index(pinned_origin), ui_job.index("    steps:\n"))
        self.assertNotIn("local.properties", ui_job)
        self.assertNotIn("10.0.2.2", ui_job)
        self.assertNotIn("continue-on-error", ui_job)
        self.assertNotIn("|| true", ui_job)

        self.assertIn('buildInput("API_BASE_URL_DEBUG")', build)
        self.assertIn('?: "http://10.0.2.2:8000"', build)
        self.assertIn('request.url.scheme == "https"', debug)
        self.assertIn('request.url.host == "www.jianjieaitech.com"', debug)
        self.assertIn("request.url.port == 443", debug)
        self.assertIn("if (fixture == null) 418 else 200", debug)
        self.assertIn("DebugUiAutomationTransport.assertNoRequestEscapedStub()", (
            ANDROID_ROOT
            / "app/src/androidTest/java/com/xjie/app/quality/DeterministicXjieUiTest.kt"
        ).read_text())


if __name__ == "__main__":
    unittest.main()
