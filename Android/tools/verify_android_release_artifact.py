#!/usr/bin/env python3
"""Fail-closed verifier for the exact signed APK or AAB selected for delivery.

AAB usage requires explicit trusted tool paths, for example:
`--bundletool /opt/bundletool.jar --java /path/to/java --jarsigner /path/to/jarsigner --keytool /path/to/keytool`.
This tool validates a supplied artifact; its unit fixtures are never real release evidence.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import subprocess
import zipfile
from pathlib import Path
from typing import Callable


FORBIDDEN_MARKERS = (
    b"xjie-ui-deterministic-v1",
    b"DebugUiAutomationTransport",
    b"10.0.2.2",
    b"localhost:8000",
    b"http://127.0.0.1",
)
SCAN_CHUNK_SIZE = 1024 * 1024


def _run(command: list[str]) -> str:
    completed = subprocess.run(command, check=False, capture_output=True, text=True)
    if completed.returncode != 0:
        raise ValueError(f"command failed ({completed.returncode}): {' '.join(command)}\n{completed.stdout}{completed.stderr}")
    return completed.stdout + completed.stderr


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(SCAN_CHUNK_SIZE):
            digest.update(chunk)
    return digest.hexdigest()


def _scan_archive_payload(archive: zipfile.ZipFile, expected_api_origin: str) -> None:
    """Scan every non-directory entry without size-based exemptions.

    A rolling tail detects values split across chunk boundaries, while keeping
    memory use bounded for large dex/resource entries.
    """

    expected_api = expected_api_origin.encode()
    needles = (expected_api, *FORBIDDEN_MARKERS)
    longest = max(map(len, needles))
    found_api = False
    found_markers: set[bytes] = set()
    for info in archive.infolist():
        if info.is_dir():
            continue
        tail = b""
        with archive.open(info, "r") as source:
            while chunk := source.read(SCAN_CHUNK_SIZE):
                window = tail + chunk
                found_api = found_api or expected_api in window
                found_markers.update(marker for marker in FORBIDDEN_MARKERS if marker in window)
                tail = window[-(longest - 1):]
    if not found_api:
        raise ValueError("release artifact does not contain the expected production API origin")
    if found_markers:
        found = sorted(marker.decode(errors="replace") for marker in found_markers)
        raise ValueError(f"release artifact contains Debug/cleartext fixture markers: {found}")


def _require_explicit_apk_cleartext_false(manifest: str) -> None:
    lines = [line for line in manifest.splitlines() if "usesCleartextTraffic" in line]
    false_value = re.compile(r"(?:\(type\s+0x12\)0x0\b|=\s*false\b)", re.IGNORECASE)
    if len(lines) != 1 or false_value.search(lines[0]) is None:
        raise ValueError("release APK manifest must explicitly set usesCleartextTraffic=false")


def verify_apk(
    artifact: Path,
    *,
    expected_sha256: str,
    expected_package: str,
    expected_version_code: int,
    expected_version_name: str,
    expected_api_origin: str,
    expected_signer_sha256: str,
    aapt: str = "aapt2",
    apksigner: str = "apksigner",
    runner: Callable[[list[str]], str] = _run,
) -> str:
    if not artifact.is_file() or artifact.suffix.lower() != ".apk":
        raise ValueError("release artifact must be one regular APK file")
    digest = _sha256_file(artifact)
    if digest != expected_sha256.lower():
        raise ValueError(f"APK SHA-256 mismatch: {digest}")
    if not expected_api_origin.startswith("https://") or expected_api_origin.endswith("/"):
        raise ValueError("expected API origin must be an explicit HTTPS origin without trailing slash")

    with zipfile.ZipFile(artifact) as archive:
        names = archive.namelist()
        if "AndroidManifest.xml" not in names or "classes.dex" not in names:
            raise ValueError("APK is missing manifest or primary dex")
        _scan_archive_payload(archive, expected_api_origin)

    badging = runner([aapt, "dump", "badging", str(artifact)])
    package_match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if package_match is None:
        raise ValueError("aapt did not return one package/version identity")
    package_name, version_code, version_name = package_match.groups()
    if (package_name, version_code, version_name) != (
        expected_package,
        str(expected_version_code),
        expected_version_name,
    ):
        raise ValueError(f"wrong package/version: {package_name} {version_code} {version_name}")

    manifest = runner([aapt, "dump", "xmltree", str(artifact), "AndroidManifest.xml"])
    _require_explicit_apk_cleartext_false(manifest)
    signing = runner([apksigner, "verify", "--verbose", "--print-certs", str(artifact)])
    signer_match = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-f:]+)", signing, re.IGNORECASE)
    signer = signer_match.group(1).replace(":", "").lower() if signer_match else ""
    if signer != expected_signer_sha256.replace(":", "").lower():
        raise ValueError(f"release signer SHA-256 mismatch: {signer or 'missing'}")
    if re.search(r"Verified using v[234] scheme[^\n]*:\s*true\b", signing, re.IGNORECASE) is None:
        raise ValueError("apksigner did not prove a modern APK signature scheme")
    return digest


def verify_aab(
    artifact: Path,
    *,
    expected_sha256: str,
    expected_package: str,
    expected_version_code: int,
    expected_version_name: str,
    expected_api_origin: str,
    expected_signer_sha256: str,
    bundletool: Path,
    java: Path,
    jarsigner: Path,
    keytool: Path,
    runner: Callable[[list[str]], str] = _run,
) -> str:
    tools = {"bundletool": bundletool, "java": java, "jarsigner": jarsigner, "keytool": keytool}
    if not artifact.is_file() or artifact.suffix.lower() != ".aab":
        raise ValueError("release artifact must be one regular AAB file")
    for label, path in tools.items():
        if not path.is_file():
            raise ValueError(f"explicit {label} path is missing or not a regular file")
        if label != "bundletool" and not os.access(path, os.X_OK):
            raise ValueError(f"explicit {label} path is not executable")
    digest = _sha256_file(artifact)
    if digest != expected_sha256.lower():
        raise ValueError(f"AAB SHA-256 mismatch: {digest}")
    if not expected_api_origin.startswith("https://") or expected_api_origin.endswith("/"):
        raise ValueError("expected API origin must be an explicit HTTPS origin without trailing slash")
    with zipfile.ZipFile(artifact) as archive:
        names = archive.namelist()
        if "base/manifest/AndroidManifest.xml" not in names or not any(name.endswith("/dex/classes.dex") for name in names):
            raise ValueError("AAB is missing base manifest or primary dex")
        _scan_archive_payload(archive, expected_api_origin)

    prefix = [str(java), "-jar", str(bundletool), "dump", "manifest", f"--bundle={artifact}"]
    def xpath(expression: str) -> str:
        return runner(prefix + [f"--xpath={expression}"]).strip()
    actual = (
        xpath("/manifest/@package"),
        xpath("/manifest/@android:versionCode"),
        xpath("/manifest/@android:versionName"),
    )
    expected = (expected_package, str(expected_version_code), expected_version_name)
    if actual != expected:
        raise ValueError(f"wrong AAB package/version: {' '.join(actual)}")
    cleartext = xpath("/manifest/application/@android:usesCleartextTraffic").lower()
    if cleartext != "false":
        raise ValueError("release AAB manifest must explicitly set usesCleartextTraffic=false")

    runner([str(jarsigner), "-verify", "-strict", "-verbose", "-certs", str(artifact)])
    certificate = runner([str(keytool), "-printcert", "-jarfile", str(artifact)])
    match = re.search(r"SHA(?:-)?256:\s*([0-9a-f:]+)", certificate, re.IGNORECASE)
    signer = match.group(1).replace(":", "").lower() if match else ""
    if signer != expected_signer_sha256.replace(":", "").lower():
        raise ValueError(f"release signer SHA-256 mismatch: {signer or 'missing'}")
    return digest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("artifact", type=Path)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--package", default="com.xjie.app")
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--api-origin", required=True)
    parser.add_argument("--signer-sha256", required=True)
    parser.add_argument("--aapt", default="aapt2")
    parser.add_argument("--apksigner", default="apksigner")
    parser.add_argument("--bundletool", type=Path)
    parser.add_argument("--java", type=Path)
    parser.add_argument("--jarsigner", type=Path)
    parser.add_argument("--keytool", type=Path)
    args = parser.parse_args()
    common = dict(
        expected_sha256=args.sha256,
        expected_package=args.package,
        expected_version_code=args.version_code,
        expected_version_name=args.version_name,
        expected_api_origin=args.api_origin,
        expected_signer_sha256=args.signer_sha256,
    )
    if args.artifact.suffix.lower() == ".aab":
        if None in (args.bundletool, args.java, args.jarsigner, args.keytool):
            parser.error("AAB verification requires explicit --bundletool, --java, --jarsigner, and --keytool paths")
        digest = verify_aab(
            args.artifact,
            **common,
            bundletool=args.bundletool,
            java=args.java,
            jarsigner=args.jarsigner,
            keytool=args.keytool,
        )
    else:
        digest = verify_apk(
            args.artifact,
            **common,
            aapt=args.aapt,
            apksigner=args.apksigner,
        )
    print(f"ANDROID RELEASE ARTIFACT VERIFIED: sha256={digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
