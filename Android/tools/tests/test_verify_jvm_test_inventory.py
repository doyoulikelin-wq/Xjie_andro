from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).resolve().parents[1] / "verify_jvm_test_inventory.py"
SPEC = importlib.util.spec_from_file_location("verify_jvm_test_inventory", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
inventory = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inventory)


class AndroidJvmInventoryTests(unittest.TestCase):
    def fixture(self) -> tuple[tempfile.TemporaryDirectory[str], Path, str]:
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        expected_id = "com.xjie.SampleTest.namedBehavior"
        (root / "quality").mkdir(parents=True)
        (root / "quality" / "expected_jvm_tests.json").write_text(
            json.dumps({"schema_version": 1, "tests": [expected_id]}),
            encoding="utf-8",
        )
        source_dir = root / "app" / "src" / "test" / "java" / "com" / "xjie"
        source_dir.mkdir(parents=True)
        (source_dir / "SampleTest.kt").write_text(
            """package com.xjie
import org.junit.Test
class SampleTest {
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
            EXPECTED_PATH=root / "quality" / "expected_jvm_tests.json",
            TEST_ROOT=root / "app" / "src" / "test" / "java",
        )

    def test_source_inventory_rejects_removed_named_test(self) -> None:
        temporary, root, expected_id = self.fixture()
        self.addCleanup(temporary.cleanup)
        with self.patched(root):
            expected = inventory.load_expected()
            self.assertEqual([expected_id], inventory.discover_source_tests())
            test_file = next(inventory.TEST_ROOT.rglob("*.kt"))
            test_file.write_text(
                test_file.read_text(encoding="utf-8").replace("@Test", ""),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(inventory.InventoryError, "missing"):
                inventory.require_exact("source", inventory.discover_source_tests(), expected)

    def test_result_inventory_rejects_skipped_or_extra_test(self) -> None:
        temporary, root, expected_id = self.fixture()
        self.addCleanup(temporary.cleanup)
        results = root / "results"
        results.mkdir()
        result_file = results / "TEST-com.xjie.SampleTest.xml"
        result_file.write_text(
            """<testsuite tests="1" skipped="0" failures="0" errors="0">
  <testcase classname="com.xjie.SampleTest" name="namedBehavior" />
</testsuite>
""",
            encoding="utf-8",
        )
        with self.patched(root):
            self.assertEqual([expected_id], inventory.discover_result_tests(results))
            result_file.write_text(
                """<testsuite tests="1" skipped="1" failures="0" errors="0">
  <testcase classname="com.xjie.SampleTest" name="namedBehavior"><skipped /></testcase>
</testsuite>
""",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(inventory.InventoryError, "skipped"):
                inventory.discover_result_tests(results)

    def test_nested_helper_class_cannot_steal_outer_test_owner(self) -> None:
        temporary, root, expected_id = self.fixture()
        self.addCleanup(temporary.cleanup)
        test_file = next((root / "app" / "src" / "test" / "java").rglob("*.kt"))
        test_file.write_text(
            """package com.xjie
import org.junit.Test
class SampleTest {
    private data class Payload(val value: String)
    private class ClosedHelper { val value = \"@Test fun fake() { }\" }
    @Test
    fun namedBehavior() = Unit
}
""",
            encoding="utf-8",
        )
        with self.patched(root):
            self.assertEqual([expected_id], inventory.discover_source_tests())


if __name__ == "__main__":
    unittest.main()
