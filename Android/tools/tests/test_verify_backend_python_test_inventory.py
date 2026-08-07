from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[1] / "verify_backend_python_test_inventory.py"
SPEC = importlib.util.spec_from_file_location("verify_backend_python_test_inventory", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
inventory = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inventory)


class BackendPythonInventoryTests(unittest.TestCase):
    def test_collection_normalizes_unit_nodes_and_rejects_duplicates(self) -> None:
        node = "tests/unit/test_sample.py::test_named[value]"
        expected = "tests.unit.test_sample::test_named[value]"
        self.assertEqual([expected], inventory.parse_collection(node))
        with self.assertRaisesRegex(inventory.BackendInventoryError, "duplicate"):
            inventory.parse_collection(f"{node}\n{node}")

    def test_exact_inventory_rejects_missing_or_extra_test(self) -> None:
        expected = ["tests.unit.test_sample::test_named"]
        inventory.require_exact("source", expected, expected)
        with self.assertRaisesRegex(inventory.BackendInventoryError, "missing"):
            inventory.require_exact("source", [], expected)
        with self.assertRaisesRegex(inventory.BackendInventoryError, "extra"):
            inventory.require_exact("source", expected + ["tests.unit.test_sample::test_extra"], expected)

    def test_junit_rejects_skip_failure_and_duplicate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.xml"
            path.write_text(
                """<testsuite>
<testcase classname="tests.unit.test_sample" name="test_ok" />
<testcase classname="tests.unit.test_sample" name="test_skip"><skipped /></testcase>
</testsuite>""",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(inventory.BackendInventoryError, "skipped"):
                inventory.parse_clean_junit(path)
            path.write_text(
                """<testsuite>
<testcase classname="tests.unit.test_sample" name="test_same" />
<testcase classname="tests.unit.test_sample" name="test_same" />
</testsuite>""",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(inventory.BackendInventoryError, "duplicate"):
                inventory.parse_clean_junit(path)

    def test_expected_contract_requires_reference_sha_sorted_unique_unit_ids(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "expected.json"
            payload = {
                "schema_version": 1,
                "reference_ios_sha": "d" * 40,
                "tests": ["tests.unit.test_sample::test_named"],
            }
            path.write_text(json.dumps(payload), encoding="utf-8")
            self.assertEqual(payload["tests"], inventory.load_expected(path))
            payload["tests"] *= 2
            path.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaisesRegex(inventory.BackendInventoryError, "sorted"):
                inventory.load_expected(path)


if __name__ == "__main__":
    unittest.main()
