#!/usr/bin/env python3
"""Require the exact Android-repository backend unit suite in source and execution."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


ANDROID_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ANDROID_ROOT.parent
BACKEND_ROOT = REPO_ROOT / "backend"
EXPECTED_PATH = ANDROID_ROOT / "quality" / "expected_backend_python_tests.json"
DEFAULT_JUNIT = ANDROID_ROOT / "build" / "quality" / "backend-unit-results.xml"


class BackendInventoryError(RuntimeError):
    pass


def load_expected(path: Path = EXPECTED_PATH) -> list[str]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if tuple(payload) != ("schema_version", "reference_ios_sha", "tests"):
        raise BackendInventoryError(
            "backend inventory keys must be schema_version, reference_ios_sha, tests"
        )
    if payload.get("schema_version") != 1:
        raise BackendInventoryError("backend inventory schema_version must be 1")
    reference = payload.get("reference_ios_sha")
    if not isinstance(reference, str) or len(reference) != 40:
        raise BackendInventoryError("reference_ios_sha must be a full commit SHA")
    tests = payload.get("tests")
    if not isinstance(tests, list) or not tests or not all(
        isinstance(item, str) and item.startswith("tests.unit.") for item in tests
    ):
        raise BackendInventoryError("backend tests must be a non-empty unit-test ID list")
    if tests != sorted(set(tests)):
        raise BackendInventoryError("backend inventory must be sorted and duplicate-free")
    return tests


def normalize_collected_node(node: str) -> str:
    path, separator, name = node.partition("::")
    if not separator or not path.startswith("tests/unit/") or not path.endswith(".py"):
        raise BackendInventoryError(f"unexpected pytest collection node: {node}")
    module = path[:-3].replace("/", ".")
    return f"{module}::{name}"


def parse_collection(stdout: str) -> list[str]:
    nodes = [
        normalize_collected_node(line.strip())
        for line in stdout.splitlines()
        if line.strip().startswith("tests/unit/") and "::" in line
    ]
    if not nodes:
        raise BackendInventoryError("pytest collected zero backend unit tests")
    duplicates = sorted(item for item, count in Counter(nodes).items() if count > 1)
    if duplicates:
        raise BackendInventoryError("duplicate collected tests: " + ", ".join(duplicates))
    return sorted(nodes)


def parse_clean_junit(path: Path) -> list[str]:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as error:
        raise BackendInventoryError(f"cannot parse backend JUnit: {error}") from error
    tests: list[str] = []
    dirty: list[str] = []
    for case in root.iter("testcase"):
        module = (case.get("classname") or "").strip()
        name = (case.get("name") or "").strip()
        if not module or not name:
            raise BackendInventoryError("backend JUnit contains an unnamed testcase")
        identifier = f"{module}::{name}"
        tests.append(identifier)
        if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
            dirty.append(identifier)
    if not tests:
        raise BackendInventoryError("backend JUnit contains zero tests")
    duplicates = sorted(item for item, count in Counter(tests).items() if count > 1)
    if duplicates:
        raise BackendInventoryError("duplicate executed tests: " + ", ".join(duplicates))
    if dirty:
        raise BackendInventoryError("failed, errored, or skipped tests: " + ", ".join(dirty))
    return sorted(tests)


def require_exact(label: str, actual: list[str], expected: list[str]) -> None:
    if actual == expected:
        return
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    details = [f"{label} backend inventory mismatch"]
    if missing:
        details.append("missing=" + ",".join(missing))
    if extra:
        details.append("extra=" + ",".join(extra))
    raise BackendInventoryError("; ".join(details))


def collect(python: str) -> list[str]:
    completed = subprocess.run(
        [python, "-I", "-m", "pytest", "--collect-only", "-q", "tests/unit"],
        cwd=BACKEND_ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if completed.returncode != 0:
        raise BackendInventoryError(
            f"pytest collection failed with exit {completed.returncode}:\n{completed.stdout}"
        )
    return parse_collection(completed.stdout)


def execute(python: str, junit: Path) -> list[str]:
    junit.parent.mkdir(parents=True, exist_ok=True)
    junit.unlink(missing_ok=True)
    completed = subprocess.run(
        [
            python,
            "-I",
            "-m",
            "pytest",
            "-q",
            "tests/unit",
            f"--junitxml={junit}",
        ],
        cwd=BACKEND_ROOT,
        check=False,
    )
    parsed_error: BackendInventoryError | None = None
    try:
        tests = parse_clean_junit(junit)
    except BackendInventoryError as error:
        parsed_error = error
        tests = []
    if completed.returncode != 0:
        raise BackendInventoryError(f"pytest execution failed with exit {completed.returncode}")
    if parsed_error is not None:
        raise parsed_error
    return tests


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--python", default=sys.executable)
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--junit", type=Path, default=DEFAULT_JUNIT)
    args = parser.parse_args()
    try:
        expected = load_expected()
        require_exact("source", collect(args.python), expected)
        if args.run:
            require_exact("execution", execute(args.python, args.junit), expected)
    except (BackendInventoryError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"ANDROID BACKEND PYTHON INVENTORY: FAIL: {error}", file=sys.stderr)
        return 1
    suffix = " source and execution" if args.run else " source"
    print(f"ANDROID BACKEND PYTHON INVENTORY: PASS:{suffix} exact ({len(expected)} tests)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
