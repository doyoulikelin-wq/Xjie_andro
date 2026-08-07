#!/usr/bin/env python3
"""Fail closed when Android JVM test source or executed results drift from inventory."""

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
EXPECTED_PATH = ROOT / "quality" / "expected_jvm_tests.json"
TEST_ROOT = ROOT / "app" / "src" / "test" / "java"


class InventoryError(RuntimeError):
    pass


def load_expected() -> list[str]:
    payload = json.loads(EXPECTED_PATH.read_text(encoding="utf-8"))
    if payload.get("schema_version") != 1:
        raise InventoryError("expected_jvm_tests.json schema_version must be 1")
    tests = payload.get("tests")
    if not isinstance(tests, list) or not all(isinstance(item, str) for item in tests):
        raise InventoryError("expected_jvm_tests.json tests must be a string list")
    if tests != sorted(set(tests)):
        raise InventoryError("expected JVM test inventory must be sorted and unique")
    return tests


def discover_source_tests() -> list[str]:
    discovered: list[str] = []
    files = sorted(TEST_ROOT.rglob("*.kt"))
    if not files:
        raise InventoryError(f"no Kotlin JVM tests found below {TEST_ROOT}")

    for path in files:
        source = path.read_text(encoding="utf-8")
        package_name, declarations = discover_kotlin_tests(source)
        if package_name is None:
            raise InventoryError(f"missing package declaration: {path.relative_to(ROOT)}")
        for declaration in declarations:
            discovered.append(f"{package_name}.{declaration.owner}.{declaration.method}")

    if len(discovered) != len(set(discovered)):
        raise InventoryError("duplicate Android JVM test identifiers detected in source")
    return sorted(discovered)


def discover_result_tests(results_dir: Path) -> list[str]:
    if not results_dir.is_dir():
        raise InventoryError(f"JVM result directory is missing: {results_dir}")
    result_files = sorted(results_dir.glob("TEST-*.xml"))
    if not result_files:
        raise InventoryError(f"no JUnit XML results found in {results_dir}")

    executed: list[str] = []
    for path in result_files:
        root = ET.parse(path).getroot()
        for case in root.iter("testcase"):
            class_name = case.get("classname")
            method = case.get("name")
            if not class_name or not method:
                raise InventoryError(f"malformed testcase in {path}")
            if case.find("skipped") is not None:
                raise InventoryError(f"skipped Android JVM test: {class_name}.{method}")
            if case.find("failure") is not None or case.find("error") is not None:
                raise InventoryError(f"failed Android JVM test: {class_name}.{method}")
            executed.append(f"{class_name}.{method}")

    if len(executed) != len(set(executed)):
        raise InventoryError("duplicate Android JVM test identifiers detected in results")
    return sorted(executed)


def require_exact(label: str, actual: list[str], expected: list[str]) -> None:
    if actual == expected:
        return
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    details = [f"{label} JVM inventory mismatch"]
    if missing:
        details.append("missing: " + ", ".join(missing))
    if extra:
        details.append("extra: " + ", ".join(extra))
    raise InventoryError("; ".join(details))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--results",
        type=Path,
        help="also require exact, passing, non-skipped JUnit XML results from this directory",
    )
    args = parser.parse_args()

    try:
        expected = load_expected()
        require_exact("source", discover_source_tests(), expected)
        if args.results is not None:
            require_exact("executed", discover_result_tests(args.results), expected)
    except (
        InventoryError,
        KotlinTestSourceError,
        OSError,
        ValueError,
        ET.ParseError,
        json.JSONDecodeError,
    ) as error:
        print(f"ANDROID JVM INVENTORY: FAIL: {error}", file=sys.stderr)
        return 1

    suffix = " source and results" if args.results is not None else " source"
    print(f"ANDROID JVM INVENTORY: PASS:{suffix} exact ({len(expected)} tests)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
