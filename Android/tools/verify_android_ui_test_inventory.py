#!/usr/bin/env python3
"""Fail closed when Android UI test source or required device results drift."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

try:
    from tools.kotlin_test_source import KotlinTestSourceError, discover_kotlin_tests
except ModuleNotFoundError:  # Direct execution from the tools directory.
    from kotlin_test_source import KotlinTestSourceError, discover_kotlin_tests


ROOT = Path(__file__).resolve().parents[1]
EXPECTED_PATH = ROOT / "quality" / "expected_android_ui_tests.json"
TEST_ROOT = ROOT / "app" / "src" / "androidTest" / "java"


class InventoryError(RuntimeError):
    pass


def load_expected() -> tuple[list[str], list[str]]:
    payload = json.loads(EXPECTED_PATH.read_text(encoding="utf-8"))
    if payload.get("schema_version") != 1:
        raise InventoryError("expected_android_ui_tests.json schema_version must be 1")
    tests = payload.get("tests")
    result_sets = payload.get("required_result_sets")
    if not isinstance(tests, list) or not tests or not all(
        isinstance(item, str) for item in tests
    ):
        raise InventoryError("expected Android UI tests must be a non-empty string list")
    if not isinstance(result_sets, list) or not result_sets or not all(
        isinstance(item, str) and item for item in result_sets
    ):
        raise InventoryError("required_result_sets must be a non-empty string list")
    if tests != sorted(set(tests)):
        raise InventoryError("expected Android UI test inventory must be sorted and unique")
    if result_sets != sorted(set(result_sets)):
        raise InventoryError("required Android UI result sets must be sorted and unique")
    return tests, result_sets


def discover_source_tests() -> list[str]:
    discovered: list[str] = []
    files = sorted(TEST_ROOT.rglob("*.kt"))
    if not files:
        raise InventoryError(f"no Kotlin Android UI tests found below {TEST_ROOT}")

    for path in files:
        source = path.read_text(encoding="utf-8")
        package_name, declarations = discover_kotlin_tests(source)
        if not declarations:
            continue
        if package_name is None:
            raise InventoryError(f"missing package declaration: {path.relative_to(ROOT)}")
        for declaration in declarations:
            discovered.append(f"{package_name}.{declaration.owner}.{declaration.method}")

    if not discovered:
        raise InventoryError(f"no @Test methods found below {TEST_ROOT}")
    if len(discovered) != len(set(discovered)):
        raise InventoryError("duplicate Android UI test identifiers detected in source")
    return sorted(discovered)


def discover_result_tests(results_dir: Path) -> list[str]:
    if not results_dir.is_dir():
        raise InventoryError(f"Android UI result directory is missing: {results_dir}")
    result_files = sorted(results_dir.rglob("TEST-*.xml"))
    if not result_files:
        raise InventoryError(f"no Android UI JUnit XML results found in {results_dir}")

    executed: list[str] = []
    for path in result_files:
        root = ET.parse(path).getroot()
        for case in root.iter("testcase"):
            class_name = case.get("classname")
            method = case.get("name")
            if not class_name or not method:
                raise InventoryError(f"malformed testcase in {path}")
            identifier = f"{class_name}.{method}"
            if case.find("skipped") is not None:
                raise InventoryError(f"skipped Android UI test: {identifier}")
            if case.find("failure") is not None or case.find("error") is not None:
                raise InventoryError(f"failed Android UI test: {identifier}")
            executed.append(identifier)

    if len(executed) != len(set(executed)):
        raise InventoryError("duplicate Android UI test identifiers detected in results")
    return sorted(executed)


def parse_result_sets(raw_values: list[str]) -> dict[str, Path]:
    parsed: dict[str, Path] = {}
    for raw in raw_values:
        label, separator, path = raw.partition("=")
        if not separator or not label or not path:
            raise InventoryError("--result-set must use LABEL=RESULT_DIRECTORY")
        if label in parsed:
            raise InventoryError(f"duplicate Android UI result-set label: {label}")
        parsed[label] = Path(path)
    return parsed


def require_exact(label: str, actual: list[str], expected: list[str]) -> None:
    if actual == expected:
        return
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    details = [f"{label} Android UI inventory mismatch"]
    if missing:
        details.append("missing: " + ", ".join(missing))
    if extra:
        details.append("extra: " + ", ".join(extra))
    raise InventoryError("; ".join(details))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--result-set",
        action="append",
        default=[],
        metavar="LABEL=RESULT_DIRECTORY",
        help="require the exact inventory for every configured device/result set",
    )
    args = parser.parse_args()

    try:
        expected, required_result_sets = load_expected()
        require_exact("source", discover_source_tests(), expected)
        supplied = parse_result_sets(args.result_set)
        if supplied:
            require_exact(
                "result-set labels",
                sorted(supplied),
                required_result_sets,
            )
            for label in required_result_sets:
                require_exact(label, discover_result_tests(supplied[label]), expected)
    except (
        InventoryError,
        KotlinTestSourceError,
        OSError,
        ValueError,
        ET.ParseError,
        json.JSONDecodeError,
    ) as error:
        print(f"ANDROID UI INVENTORY: FAIL: {error}", file=sys.stderr)
        return 1

    suffix = " source and required device results" if args.result_set else " source"
    print(f"ANDROID UI INVENTORY: PASS:{suffix} exact ({len(expected)} tests)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
