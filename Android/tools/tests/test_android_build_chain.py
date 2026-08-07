import hashlib
import pathlib
import re
import unittest


ANDROID_ROOT = pathlib.Path(__file__).resolve().parents[2]


class AndroidBuildChainContractTests(unittest.TestCase):
    def test_health_connect_stable_version_has_required_unsuppressed_build_chain(self) -> None:
        versions = (ANDROID_ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        app_gradle = (ANDROID_ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        manifest = (ANDROID_ROOT / "app/src/main/AndroidManifest.xml").read_text(
            encoding="utf-8"
        )
        wrapper = (
            ANDROID_ROOT / "gradle/wrapper/gradle-wrapper.properties"
        ).read_text(encoding="utf-8")
        wrapper_jar = ANDROID_ROOT / "gradle/wrapper/gradle-wrapper.jar"
        network_module = (
            ANDROID_ROOT
            / "app/src/main/java/com/xjie/app/core/network/HealthConnectNetworkModule.kt"
        ).read_text(encoding="utf-8")
        health_connect_sources = "\n".join(
            path.read_text(encoding="utf-8")
            for path in sorted(
                (
                    ANDROID_ROOT
                    / "app/src/main/java/com/xjie/app/feature/healthconnect"
                ).glob("*.kt")
            )
        )
        health_connect_view_model = (
            ANDROID_ROOT
            / "app/src/main/java/com/xjie/app/feature/healthconnect/HealthConnectSyncViewModel.kt"
        ).read_text(encoding="utf-8")
        ci_workflow = (ANDROID_ROOT.parent / ".github/workflows/ci.yml").read_text(
            encoding="utf-8"
        )

        self.assertRegex(versions, r'(?m)^agp = "8\.11\.1"$')
        self.assertRegex(versions, r'(?m)^health-connect = "1\.1\.0"$')
        self.assertRegex(app_gradle, r'(?m)^\s*compileSdk = 36$')
        self.assertRegex(app_gradle, r'(?m)^\s*targetSdk = 35$')
        self.assertRegex(app_gradle, r'(?m)^\s*minSdk = 28$')
        self.assertEqual(2, app_gradle.count("JavaVersion.VERSION_17"))
        self.assertIn('jvmTarget = "17"', app_gradle)
        self.assertIn("implementation(libs.androidx.health.connect.client)", app_gradle)
        self.assertIn("gradle-8.13-bin.zip", wrapper)
        self.assertIn(
            "distributionSha256Sum="
            "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78",
            wrapper,
        )
        self.assertEqual(
            "81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f",
            hashlib.sha256(wrapper_jar.read_bytes()).hexdigest(),
        )

        combined = "\n".join((versions, app_gradle, wrapper))
        forbidden = (
            "android.suppressUnsupportedCompileSdk",
            "android.enableAarMetadataCompatibilityCheck=false",
            "android.disableAutomaticComponentCreation",
        )
        for token in forbidden:
            self.assertNotIn(token, combined)

        expected_read_permissions = {
            "READ_STEPS",
            "READ_DISTANCE",
            "READ_SLEEP",
            "READ_HEART_RATE_VARIABILITY",
            "READ_RESTING_HEART_RATE",
            "READ_WEIGHT",
        }
        declared_read_permissions = set(
            re.findall(r"android\.permission\.health\.(READ_[A-Z_]+)", manifest)
        )
        self.assertEqual(expected_read_permissions, declared_read_permissions)
        self.assertNotIn("android.permission.health.WRITE_", manifest)
        self.assertIn("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE", manifest)
        self.assertIn("android.intent.action.VIEW_PERMISSION_USAGE", manifest)
        self.assertIn("android.permission.START_VIEW_PERMISSION_USAGE", manifest)

        self.assertIn("OkHttpClient.Builder()", network_module)
        self.assertNotIn("AuthInterceptor", network_module)
        self.assertNotIn("TokenAuthenticator", network_module)
        self.assertNotIn("HttpLoggingInterceptor", network_module)
        self.assertNotIn("delay(", health_connect_sources)
        self.assertNotIn("androidHealthSamples", health_connect_sources)
        self.assertGreaterEqual(
            health_connect_view_model.count(
                "availability = HealthConnectAvailability.Available"
            ),
            2,
            "permission-ready and direct-sync paths must both confirm current availability",
        )
        self.assertIn(
            "showBlocked(blocked.reason, blocked.message)", health_connect_view_model
        )
        self.assertIn('java-version: "17"', ci_workflow)
        self.assertIn("android-actions/setup-android@v3", ci_workflow)
        self.assertIn(
            'sdkmanager "platforms;android-36" "build-tools;35.0.0"',
            ci_workflow,
        )
        self.assertNotRegex(ci_workflow, r"(?m)^\s*(yes|echo\s+[yY])\s*\|")


if __name__ == "__main__":
    unittest.main()
