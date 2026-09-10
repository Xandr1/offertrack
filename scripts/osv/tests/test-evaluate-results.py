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
    vulnerabilities = []
    for advisory_id, score in findings:
        group = {"ids": [advisory_id], "aliases": []}
        if score is not None:
            group["max_severity"] = score
        groups.append(group)
        vulnerabilities.append(
            {
                "id": advisory_id,
                "severity": [
                    {
                        "type": "CVSS_V3",
                        "score": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H",
                    }
                ],
            }
        )
    return {
        "results": [
            {
                "source": {"path": "fixture.lock", "type": "lockfile"},
                "packages": [
                    {
                        "package": {
                            "name": "fixture-package",
                            "version": "1.0.0",
                            "ecosystem": "npm",
                        },
                        "groups": groups,
                        "vulnerabilities": vulnerabilities,
                    }
                ],
            }
        ]
    }


class OsvPolicyEvaluatorTest(unittest.TestCase):
    def evaluate(
        self, document: object | None, config: str = "", scanner_exit_code: int = 0
    ) -> tuple[subprocess.CompletedProcess[str], str]:
        with tempfile.TemporaryDirectory(prefix="offertrack-osv-policy-") as directory:
            fixture = Path(directory)
            results_file = fixture / "osv-results.json"
            summary_file = fixture / "summary.md"
            config_file = fixture / "osv-scanner.toml"
            if document is not None:
                results_file.write_text(
                    document if isinstance(document, str) else json.dumps(document),
                    encoding="utf-8",
                )
            config_file.write_text(config, encoding="utf-8")
            result = subprocess.run(
                [
                    sys.executable,
                    str(EVALUATOR),
                    "--results",
                    str(results_file),
                    "--config",
                    str(config_file),
                    "--scanner-exit-code",
                    str(scanner_exit_code),
                    "--summary",
                    str(summary_file),
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            return result, summary_file.read_text(
                encoding="utf-8"
            ) if summary_file.exists() else ""

    def test_critical_finding_blocks(self) -> None:
        result, summary = self.evaluate(
            results_for(("GHSA-critical", "9.0")), scanner_exit_code=1
        )

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("**Critical findings (blocking):** 1", summary)
        self.assertIn("`GHSA-critical` — CVSS `9.0`", summary)

    def test_high_finding_does_not_block(self) -> None:
        result, summary = self.evaluate(
            results_for(("GHSA-high", "8.9")), scanner_exit_code=1
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("**High findings:** 1", summary)
        self.assertIn("`GHSA-high` — CVSS `8.9`", summary)

    def test_medium_and_low_findings_do_not_block(self) -> None:
        result, summary = self.evaluate(
            results_for(("GHSA-medium", "6.9"), ("GHSA-low", "3.9")),
            scanner_exit_code=1,
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("**Medium findings:** 1", summary)
        self.assertIn("**Low findings:** 1", summary)

    def test_mixed_critical_and_non_critical_findings_block(self) -> None:
        result, summary = self.evaluate(
            results_for(("GHSA-high", "7.0"), ("GHSA-critical", "9.8")),
            scanner_exit_code=1,
        )

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("**Critical findings (blocking):** 1", summary)
        self.assertIn("**High findings:** 1", summary)

    def test_no_findings_passes(self) -> None:
        result, summary = self.evaluate({"results": []})

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("**Critical findings (blocking):** 0", summary)
        self.assertIn("**High findings:** 0", summary)
        self.assertIn("**Medium findings:** 0", summary)
        self.assertIn("**Low findings:** 0", summary)

    def test_malformed_results_fail_closed(self) -> None:
        result, summary = self.evaluate('{"results": {}}')

        self.assertEqual(1, result.returncode)
        self.assertIn("failed closed", result.stderr)
        self.assertEqual("", summary)

    def test_missing_results_fail_closed(self) -> None:
        result, summary = self.evaluate(None)

        self.assertEqual(1, result.returncode)
        self.assertIn("failed closed", result.stderr)
        self.assertEqual("", summary)

    def test_scanner_operational_failure_fails_closed(self) -> None:
        result, summary = self.evaluate({"results": []}, scanner_exit_code=127)

        self.assertEqual(1, result.returncode)
        self.assertIn("exited with 127", result.stderr)
        self.assertEqual("", summary)

    def test_scanner_exit_code_must_match_result_findings(self) -> None:
        result, summary = self.evaluate(
            results_for(("GHSA-high", "8.9")), scanner_exit_code=0
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("exited cleanly but reported", result.stderr)
        self.assertEqual("", summary)

        result, summary = self.evaluate({"results": []}, scanner_exit_code=1)

        self.assertEqual(1, result.returncode)
        self.assertIn("findings exit but no findings", result.stderr)
        self.assertEqual("", summary)

    def test_missing_or_unusable_severity_is_reported_without_blocking(self) -> None:
        result, summary = self.evaluate(
            results_for(("GHSA-missing", None), ("GHSA-unusable", "not-a-score")),
            scanner_exit_code=1,
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(
            "**Findings with missing or unusable CVSS severity (non-blocking):** 2",
            summary,
        )
        self.assertIn("`GHSA-missing`", summary)
        self.assertIn("`GHSA-unusable`", summary)

    def test_documented_accepted_risks_remain_in_the_summary(self) -> None:
        result, summary = self.evaluate(
            {"results": []},
            '[[IgnoredVulns]]\nid = "GHSA-reviewed"\nignoreUntil = 2026-12-31\n',
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("**Documented accepted risks:** 1", summary)
        self.assertIn("`GHSA-reviewed` — expires `2026-12-31`", summary)


if __name__ == "__main__":
    unittest.main()
