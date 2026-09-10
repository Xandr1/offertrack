from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
EVALUATOR = REPOSITORY_ROOT / "scripts" / "osv" / "evaluate-results.py"


def results_for(*findings: tuple[str, object]) -> dict[str, object]:
    groups = []
    for advisory_id, score in findings:
        group = {"ids": [advisory_id]}
        if score is not None:
            group["max_severity"] = score
        groups.append(group)
    return {"results": [{"packages": [{"groups": groups}]}]}


class OsvPolicyEvaluatorTest(unittest.TestCase):
    def evaluate(
        self, document: object | None, scanner_exit_code: int = 0
    ) -> tuple[subprocess.CompletedProcess[str], str]:
        with tempfile.TemporaryDirectory(prefix="offertrack-osv-policy-") as directory:
            fixture = Path(directory)
            results_file = fixture / "osv-results.json"
            summary_file = fixture / "summary.md"
            if document is not None:
                results_file.write_text(
                    document if isinstance(document, str) else json.dumps(document),
                    encoding="utf-8",
                )
            result = subprocess.run(
                [
                    sys.executable,
                    str(EVALUATOR),
                    "--results",
                    str(results_file),
                    "--scanner-exit-code",
                    str(scanner_exit_code),
                    "--summary",
                    str(summary_file),
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            summary = (
                summary_file.read_text(encoding="utf-8")
                if summary_file.exists()
                else ""
            )
            return result, summary

    def test_clean_scan_passes(self) -> None:
        result, summary = self.evaluate({"results": []})

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("**Critical findings (blocking):** 0", summary)

    def test_critical_finding_blocks(self) -> None:
        result, summary = self.evaluate(results_for(("GHSA-critical", "9.0")), 1)

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("`GHSA-critical` — CVSS `9.0`", summary)

    def test_high_finding_does_not_block(self) -> None:
        result, summary = self.evaluate(results_for(("GHSA-high", "8.9")), 1)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("**High findings:** 1", summary)

    def test_medium_and_low_findings_do_not_block(self) -> None:
        result, summary = self.evaluate(
            results_for(("GHSA-medium", 6.9), ("GHSA-low", 3.9)), 1
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("**Medium findings:** 1", summary)
        self.assertIn("**Low findings:** 1", summary)

    def test_mixed_critical_and_non_critical_findings_block(self) -> None:
        result, summary = self.evaluate(
            results_for(("GHSA-high", 7.0), ("GHSA-critical", 9.8)), 1
        )

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("**Critical findings (blocking):** 1", summary)
        self.assertIn("**High findings:** 1", summary)

    def test_unknown_severity_is_reported_without_blocking(self) -> None:
        result, summary = self.evaluate(
            results_for(("GHSA-missing", None), ("GHSA-unusable", "not-a-score")), 1
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("missing or unusable CVSS severity (non-blocking):** 2", summary)
        self.assertIn("`GHSA-missing`", summary)
        self.assertIn("`GHSA-unusable`", summary)

    def test_malformed_or_missing_results_fail_closed(self) -> None:
        for document in (
            None,
            "",
            '{"results": {}}',
            {"results": [{"packages": [{}]}]},
        ):
            with self.subTest(document=document):
                result, summary = self.evaluate(document)

                self.assertEqual(1, result.returncode)
                self.assertIn("failed closed", result.stderr)
                self.assertEqual("", summary)

    def test_unexpected_scanner_exit_code_fails_closed(self) -> None:
        result, summary = self.evaluate({"results": []}, 127)

        self.assertEqual(1, result.returncode)
        self.assertIn("Unexpected OSV-Scanner exit code: 127", result.stderr)
        self.assertEqual("", summary)

    def test_scanner_exit_code_must_match_findings(self) -> None:
        for document, exit_code in (
            (results_for(("GHSA-high", 8.9)), 0),
            ({"results": []}, 1),
        ):
            with self.subTest(exit_code=exit_code):
                result, summary = self.evaluate(document, exit_code)

                self.assertEqual(1, result.returncode)
                self.assertIn("exit code and findings disagree", result.stderr)
                self.assertEqual("", summary)


if __name__ == "__main__":
    unittest.main()
