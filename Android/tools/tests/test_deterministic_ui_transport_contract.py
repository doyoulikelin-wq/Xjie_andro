import unittest
from pathlib import Path


ANDROID_ROOT = Path(__file__).resolve().parents[2]


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


if __name__ == "__main__":
    unittest.main()
