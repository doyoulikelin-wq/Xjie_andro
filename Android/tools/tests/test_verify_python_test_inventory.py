from __future__ import annotations

import importlib.util
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).resolve().parents[1] / "verify_python_test_inventory.py"
SPEC = importlib.util.spec_from_file_location("verify_python_test_inventory", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
inventory = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inventory)


class AndroidPythonInventoryTests(unittest.TestCase):
    def fixture(self) -> tuple[tempfile.TemporaryDirectory[str], Path, str]:
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        expected_id = "test_sample.SampleTests.test_named_behavior"
        (root / "quality").mkdir(parents=True)
        (root / "quality" / "expected_python_tests.json").write_text(
            json.dumps({"schema_version": 1, "tests": [expected_id]}),
            encoding="utf-8",
        )
        tests = root / "tools" / "tests"
        tests.mkdir(parents=True)
        (tests / "test_sample.py").write_text(
            """import unittest
class SampleTests(unittest.TestCase):
    def test_named_behavior(self):
        self.assertTrue(True)
""",
            encoding="utf-8",
        )
        return temporary, root, expected_id

    def patched(self, root: Path):
        return mock.patch.multiple(
            inventory,
            ROOT=root,
            EXPECTED_PATH=root / "quality" / "expected_python_tests.json",
            TEST_ROOT=root / "tools" / "tests",
        )

    def test_source_inventory_rejects_removed_or_extra_test(self) -> None:
        temporary, root, expected_id = self.fixture()
        self.addCleanup(temporary.cleanup)
        with self.patched(root):
            self.assertEqual([expected_id], inventory.discover_source_tests())
            test_file = next(inventory.TEST_ROOT.glob("test_*.py"))
            test_file.write_text(
                test_file.read_text(encoding="utf-8").replace(
                    "test_named_behavior", "helper_named_behavior"
                ),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(inventory.InventoryError, "no Python test methods"):
                inventory.discover_source_tests()

    def test_execution_inventory_rejects_skipped_test(self) -> None:
        temporary, root, expected_id = self.fixture()
        self.addCleanup(temporary.cleanup)
        with self.patched(root):
            suite, identifiers = inventory.load_execution_suite()
            self.assertEqual([expected_id], identifiers)
            result = unittest.TextTestRunner(stream=io.StringIO()).run(suite)
            inventory.require_clean_result(result, 1)

            class SkippedProof(unittest.TestCase):
                @unittest.skip("proof")
                def test_skipped(self) -> None:
                    pass

            skipped_suite = unittest.TestSuite([SkippedProof("test_skipped")])
            skipped = unittest.TextTestRunner(stream=io.StringIO()).run(skipped_suite)
            with self.assertRaisesRegex(inventory.InventoryError, "skipped"):
                inventory.require_clean_result(skipped, 1)

    def test_expected_inventory_rejects_unsorted_or_duplicate_entries(self) -> None:
        temporary, root, expected_id = self.fixture()
        self.addCleanup(temporary.cleanup)
        expected_path = root / "quality" / "expected_python_tests.json"
        expected_path.write_text(
            json.dumps({"schema_version": 1, "tests": [expected_id, expected_id]}),
            encoding="utf-8",
        )
        with self.patched(root), self.assertRaisesRegex(inventory.InventoryError, "sorted and unique"):
            inventory.load_expected()


if __name__ == "__main__":
    unittest.main()
