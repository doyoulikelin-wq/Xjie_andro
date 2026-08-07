#!/usr/bin/env python3
"""Require exact Android quality-tool Python tests in source and execution."""

from __future__ import annotations

import argparse
import ast
import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EXPECTED_PATH = ROOT / "quality" / "expected_python_tests.json"
TEST_ROOT = ROOT / "tools" / "tests"


class InventoryError(RuntimeError):
    pass


def load_expected() -> list[str]:
    payload = json.loads(EXPECTED_PATH.read_text(encoding="utf-8"))
    if payload.get("schema_version") != 1:
        raise InventoryError("expected_python_tests.json schema_version must be 1")
    tests = payload.get("tests")
    if not isinstance(tests, list) or not tests or not all(
        isinstance(item, str) for item in tests
    ):
        raise InventoryError("expected Python tests must be a non-empty string list")
    if tests != sorted(set(tests)):
        raise InventoryError("expected Python test inventory must be sorted and unique")
    return tests


def discover_source_tests() -> list[str]:
    discovered: list[str] = []
    files = sorted(TEST_ROOT.glob("test_*.py"))
    if not files:
        raise InventoryError(f"no Python test modules found below {TEST_ROOT}")
    for path in files:
        tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        for node in tree.body:
            if not isinstance(node, ast.ClassDef):
                continue
            for member in node.body:
                if isinstance(member, (ast.FunctionDef, ast.AsyncFunctionDef)) and member.name.startswith(
                    "test_"
                ):
                    discovered.append(f"{path.stem}.{node.name}.{member.name}")
    if not discovered:
        raise InventoryError(f"no Python test methods found below {TEST_ROOT}")
    if len(discovered) != len(set(discovered)):
        raise InventoryError("duplicate Python test identifiers detected in source")
    return sorted(discovered)


def flatten_suite(suite: unittest.TestSuite) -> list[unittest.TestCase]:
    cases: list[unittest.TestCase] = []
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            cases.extend(flatten_suite(item))
        else:
            cases.append(item)
    return cases


def load_execution_suite() -> tuple[unittest.TestSuite, list[str]]:
    suite = unittest.defaultTestLoader.discover(
        str(TEST_ROOT),
        pattern="test_*.py",
        top_level_dir=str(TEST_ROOT),
    )
    identifiers = [case.id() for case in flatten_suite(suite)]
    if not identifiers:
        raise InventoryError("unittest discovered no Python tests")
    if len(identifiers) != len(set(identifiers)):
        raise InventoryError("duplicate Python test identifiers detected at execution")
    return suite, sorted(identifiers)


def require_exact(label: str, actual: list[str], expected: list[str]) -> None:
    if actual == expected:
        return
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    details = [f"{label} Python inventory mismatch"]
    if missing:
        details.append("missing: " + ", ".join(missing))
    if extra:
        details.append("extra: " + ", ".join(extra))
    raise InventoryError("; ".join(details))


def require_clean_result(result: unittest.TestResult, expected_count: int) -> None:
    if result.testsRun != expected_count:
        raise InventoryError(
            f"Python execution count mismatch: expected {expected_count}, ran {result.testsRun}"
        )
    if result.failures or result.errors:
        raise InventoryError(
            f"Python execution failed: {len(result.failures)} failures, {len(result.errors)} errors"
        )
    if result.skipped:
        raise InventoryError(f"skipped Python tests: {len(result.skipped)}")
    if result.expectedFailures:
        raise InventoryError(f"expected-failure Python tests: {len(result.expectedFailures)}")
    if result.unexpectedSuccesses:
        raise InventoryError(f"unexpected-success Python tests: {len(result.unexpectedSuccesses)}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--run",
        action="store_true",
        help="execute the exact suite and reject failures, errors, skips, or expected failures",
    )
    args = parser.parse_args()

    try:
        expected = load_expected()
        require_exact("source", discover_source_tests(), expected)
        if args.run:
            suite, identifiers = load_execution_suite()
            require_exact("execution", identifiers, expected)
            result = unittest.TextTestRunner(verbosity=2).run(suite)
            require_clean_result(result, len(expected))
    except (InventoryError, OSError, SyntaxError, ValueError, json.JSONDecodeError) as error:
        print(f"ANDROID PYTHON INVENTORY: FAIL: {error}", file=sys.stderr)
        return 1

    suffix = " source and execution" if args.run else " source"
    print(f"ANDROID PYTHON INVENTORY: PASS:{suffix} exact ({len(expected)} tests)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
