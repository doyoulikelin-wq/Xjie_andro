import hashlib
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from verify_android_release_artifact import main, verify_aab, verify_apk


API_ORIGIN = "https://www.jianjieaitech.com"
LARGE_ENTRY_CHUNKS = 65


class ReleaseArtifactVerifierContractTest(unittest.TestCase):
    def _apk(self, root: Path, *, marker: bytes = b"", large_marker: bytes = b"") -> Path:
        apk = root / "release.apk"
        with zipfile.ZipFile(apk, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("AndroidManifest.xml", b"binary-manifest")
            archive.writestr("classes.dex", API_ORIGIN.encode() + marker)
            if large_marker:
                with archive.open("assets/large-release-payload.bin", "w", force_zip64=True) as output:
                    for _ in range(LARGE_ENTRY_CHUNKS):
                        output.write(b"A" * (1024 * 1024))
                    output.write(large_marker)
        return apk

    def _aab(self, root: Path, *, large_marker: bytes = b"") -> Path:
        aab = root / "release.aab"
        with zipfile.ZipFile(aab, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("base/manifest/AndroidManifest.xml", b"binary")
            archive.writestr("base/dex/classes.dex", API_ORIGIN.encode())
            if large_marker:
                with archive.open("base/assets/large-release-payload.bin", "w", force_zip64=True) as output:
                    for _ in range(LARGE_ENTRY_CHUNKS):
                        output.write(b"A" * (1024 * 1024))
                    output.write(large_marker)
        return aab

    def _tools(self, root: Path) -> tuple[Path, Path, Path, Path]:
        tools = tuple(root / name for name in ("bundletool.jar", "java", "jarsigner", "keytool"))
        for index, tool in enumerate(tools):
            tool.write_bytes(b"trusted-test-tool-placeholder")
            if index:
                tool.chmod(0o700)
        return tools

    def _common(self, artifact: Path) -> dict[str, object]:
        return {
            "expected_sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
            "expected_package": "com.xjie.app",
            "expected_version_code": 2,
            "expected_version_name": "1.0",
            "expected_api_origin": API_ORIGIN,
            "expected_signer_sha256": "aabb",
        }

    def test_apk_requires_exact_identity_modern_signature_api_and_digest(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            apk = self._apk(Path(raw))
            outputs = iter(
                (
                    "package: name='com.xjie.app' versionCode='2' versionName='1.0'",
                    "A: android:usesCleartextTraffic(0x0101052c)=(type 0x12)0x0",
                    "Verified using v2 scheme (APK Signature Scheme v2): true\n"
                    "Signer #1 certificate SHA-256 digest: aa:bb",
                )
            )
            self.assertEqual(
                self._common(apk)["expected_sha256"],
                verify_apk(apk, **self._common(apk), runner=lambda command: next(outputs)),
            )

    def test_apk_digest_debug_marker_and_missing_cleartext_false_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            apk = self._apk(root, marker=b"xjie-ui-deterministic-v1")
            common = self._common(apk)
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                verify_apk(apk, **{**common, "expected_sha256": "0" * 64})
            with self.assertRaisesRegex(ValueError, "fixture markers"):
                verify_apk(apk, **common)

            clean = self._apk(root)
            outputs = iter(
                (
                    "package: name='com.xjie.app' versionCode='2' versionName='1.0'",
                    "E: application",
                )
            )
            with self.assertRaisesRegex(ValueError, "explicitly set usesCleartextTraffic=false"):
                verify_apk(clean, **self._common(clean), runner=lambda command: next(outputs))

    def test_apk_scans_forbidden_marker_inside_entry_larger_than_64_mib(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            apk = self._apk(Path(raw), large_marker=b"DebugUiAutomationTransport")
            with self.assertRaisesRegex(ValueError, "fixture markers"):
                verify_apk(apk, **self._common(apk))

    def test_aab_requires_exact_manifest_signature_certificate_and_explicit_tools(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            aab = self._aab(root)
            bundletool, java, jarsigner, keytool = self._tools(root)
            outputs = iter(("com.xjie.app", "2", "1.0", "false", "signature details", "SHA256: AA:BB"))
            self.assertEqual(
                self._common(aab)["expected_sha256"],
                verify_aab(
                    aab,
                    **self._common(aab),
                    bundletool=bundletool,
                    java=java,
                    jarsigner=jarsigner,
                    keytool=keytool,
                    runner=lambda command: next(outputs),
                ),
            )
            with self.assertRaisesRegex(ValueError, "explicit bundletool path"):
                verify_aab(
                    aab,
                    **self._common(aab),
                    bundletool=root / "missing.jar",
                    java=java,
                    jarsigner=jarsigner,
                    keytool=keytool,
                )
            java.chmod(0o600)
            with self.assertRaisesRegex(ValueError, "explicit java path is not executable"):
                verify_aab(
                    aab,
                    **self._common(aab),
                    bundletool=bundletool,
                    java=java,
                    jarsigner=jarsigner,
                    keytool=keytool,
                )

    def test_aab_cli_rejects_omitted_explicit_tool_paths(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            aab = self._aab(Path(raw))
            arguments = [
                "verify_android_release_artifact.py",
                str(aab),
                "--sha256",
                str(self._common(aab)["expected_sha256"]),
                "--version-code",
                "2",
                "--version-name",
                "1.0",
                "--api-origin",
                API_ORIGIN,
                "--signer-sha256",
                "aabb",
            ]
            with patch("sys.argv", arguments), self.assertRaises(SystemExit) as raised:
                main()
            self.assertEqual(2, raised.exception.code)

    def test_aab_missing_cleartext_false_and_wrong_certificate_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            aab = self._aab(root)
            bundletool, java, jarsigner, keytool = self._tools(root)
            arguments = {
                **self._common(aab),
                "bundletool": bundletool,
                "java": java,
                "jarsigner": jarsigner,
                "keytool": keytool,
            }
            missing_cleartext = iter(("com.xjie.app", "2", "1.0", ""))
            with self.assertRaisesRegex(ValueError, "explicitly set usesCleartextTraffic=false"):
                verify_aab(aab, **arguments, runner=lambda command: next(missing_cleartext))

            wrong_certificate = iter(("com.xjie.app", "2", "1.0", "false", "signature details", "SHA256: CC:DD"))
            with self.assertRaisesRegex(ValueError, "signer SHA-256 mismatch"):
                verify_aab(aab, **arguments, runner=lambda command: next(wrong_certificate))

    def test_aab_scans_forbidden_marker_inside_entry_larger_than_64_mib(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            aab = self._aab(root, large_marker=b"xjie-ui-deterministic-v1")
            bundletool, java, jarsigner, keytool = self._tools(root)
            with self.assertRaisesRegex(ValueError, "fixture markers"):
                verify_aab(
                    aab,
                    **self._common(aab),
                    bundletool=bundletool,
                    java=java,
                    jarsigner=jarsigner,
                    keytool=keytool,
                )


if __name__ == "__main__":
    unittest.main()
