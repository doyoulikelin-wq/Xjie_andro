from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).resolve().parents[1] / "verify_android_ui_test_inventory.py"
SPEC = importlib.util.spec_from_file_location("verify_android_ui_test_inventory", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
inventory = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inventory)


class AndroidUiInventoryTests(unittest.TestCase):
    def fixture(self) -> tuple[tempfile.TemporaryDirectory[str], Path, str]:
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        expected_id = "com.xjie.SampleUiTest.namedBehavior"
        (root / "quality").mkdir(parents=True)
        (root / "quality" / "expected_android_ui_tests.json").write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "required_result_sets": ["compact_api35", "standard_api35"],
                    "tests": [expected_id],
                }
            ),
            encoding="utf-8",
        )
        source_dir = root / "app" / "src" / "androidTest" / "java" / "com" / "xjie"
        source_dir.mkdir(parents=True)
        (source_dir / "SampleUiTest.kt").write_text(
            """package com.xjie
import org.junit.Test
class SampleUiTest {
    @Test
    fun namedBehavior() = Unit
}
""",
            encoding="utf-8",
        )
        return temporary, root, expected_id

    def patched(self, root: Path):
        return mock.patch.multiple(
            inventory,
            ROOT=root,
            EXPECTED_PATH=root / "quality" / "expected_android_ui_tests.json",
            TEST_ROOT=root / "app" / "src" / "androidTest" / "java",
        )

    @staticmethod
    def write_result(results: Path, method: str, body: str = "") -> None:
        results.mkdir(parents=True, exist_ok=True)
        (results / "TEST-com.xjie.SampleUiTest.xml").write_text(
            f"""<testsuite tests="1" skipped="0" failures="0" errors="0">
  <testcase classname="com.xjie.SampleUiTest" name="{method}">{body}</testcase>
</testsuite>
""",
            encoding="utf-8",
        )

    def test_source_inventory_rejects_removed_named_ui_test(self) -> None:
        temporary, root, expected_id = self.fixture()
        self.addCleanup(temporary.cleanup)
        with self.patched(root):
            expected, result_sets = inventory.load_expected()
            self.assertEqual([expected_id], inventory.discover_source_tests())
            self.assertEqual(["compact_api35", "standard_api35"], result_sets)
            test_file = next(inventory.TEST_ROOT.rglob("*.kt"))
            test_file.write_text(
                test_file.read_text(encoding="utf-8").replace("@Test", ""),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(inventory.InventoryError, "no @Test methods"):
                inventory.require_exact("source", inventory.discover_source_tests(), expected)

    def test_result_inventory_rejects_skipped_failed_or_extra_test(self) -> None:
        temporary, root, expected_id = self.fixture()
        self.addCleanup(temporary.cleanup)
        results = root / "results"
        self.write_result(results, "namedBehavior")
        with self.patched(root):
            self.assertEqual([expected_id], inventory.discover_result_tests(results))
            self.write_result(results, "namedBehavior", "<skipped />")
            with self.assertRaisesRegex(inventory.InventoryError, "skipped"):
                inventory.discover_result_tests(results)
            self.write_result(results, "namedBehavior", "<failure />")
            with self.assertRaisesRegex(inventory.InventoryError, "failed"):
                inventory.discover_result_tests(results)

    def test_required_result_sets_reject_missing_duplicate_or_unexpected_labels(self) -> None:
        temporary, root, _ = self.fixture()
        self.addCleanup(temporary.cleanup)
        with self.patched(root):
            _, expected_sets = inventory.load_expected()
            supplied = inventory.parse_result_sets(
                ["standard_api35=/tmp/standard", "compact_api35=/tmp/compact"]
            )
            inventory.require_exact("result-set labels", sorted(supplied), expected_sets)
            with self.assertRaisesRegex(inventory.InventoryError, "duplicate"):
                inventory.parse_result_sets(
                    ["standard_api35=/tmp/a", "standard_api35=/tmp/b"]
                )
            with self.assertRaisesRegex(inventory.InventoryError, "missing"):
                inventory.require_exact(
                    "result-set labels",
                    ["standard_api35"],
                    expected_sets,
                )


if __name__ == "__main__":
    unittest.main()
