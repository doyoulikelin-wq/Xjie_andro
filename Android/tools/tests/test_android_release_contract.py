import unittest
from pathlib import Path


ANDROID_ROOT = Path(__file__).resolve().parents[2]


class ReleaseConfigContractTest(unittest.TestCase):
    def test_release_api_has_no_placeholder_and_requires_explicit_https(self) -> None:
        build = (ANDROID_ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

        self.assertNotIn("https://api.example.com", build)
        self.assertIn("API_BASE_URL_RELEASE", build)
        self.assertIn("verifyReleaseConfiguration", build)
        self.assertIn('startsWith("https://")', build)
        self.assertIn('contains("example.com",', build)

    def test_release_signing_requires_external_credentials(self) -> None:
        build = (ANDROID_ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

        for key in (
            "XJIE_RELEASE_STORE_FILE",
            "XJIE_RELEASE_STORE_PASSWORD",
            "XJIE_RELEASE_KEY_ALIAS",
            "XJIE_RELEASE_KEY_PASSWORD",
        ):
            self.assertIn(key, build)
        self.assertIn("releaseSigningConfigured", build)
        self.assertIn("Release signing inputs are incomplete", build)

    def test_cleartext_hosts_exist_only_in_debug_resources(self) -> None:
        main_manifest = (ANDROID_ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        debug_manifest = (ANDROID_ROOT / "app/src/debug/AndroidManifest.xml").read_text(encoding="utf-8")
        main_network = (ANDROID_ROOT / "app/src/main/res/xml/network_security_config.xml").read_text(
            encoding="utf-8"
        )
        debug_network = (ANDROID_ROOT / "app/src/debug/res/xml/network_security_config.xml").read_text(
            encoding="utf-8"
        )

        self.assertIn('android:usesCleartextTraffic="false"', main_manifest)
        self.assertIn('android:usesCleartextTraffic="true"', debug_manifest)
        self.assertNotIn('cleartextTrafficPermitted="true"', main_network)
        self.assertIn('cleartextTrafficPermitted="true"', debug_network)
        for development_host in ("10.0.2.2", "localhost", "127.0.0.1", "192.168.1.21"):
            self.assertNotIn(development_host, main_network)
            self.assertIn(development_host, debug_network)


if __name__ == "__main__":
    unittest.main()
