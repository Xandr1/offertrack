from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
EVALUATOR = REPO_ROOT / "scripts" / "containers" / "evaluate-trivy-results.py"
NOT_SUPPLIED = object()


def report(*vulnerabilities: dict[str, object], results: object = NOT_SUPPLIED) -> dict[str, object]:
    if results is NOT_SUPPLIED:
        results = [
            {
                "Target": "debian 13",
                "Class": "os-pkgs",
                "Type": "debian",
                "Vulnerabilities": list(vulnerabilities),
            }
        ]
    return {
        "SchemaVersion": 2,
        "ArtifactName": "offertrack/example:test",
        "ArtifactType": "container_image",
        "Results": results,
    }


def vulnerability(
    vulnerability_id: str,
    *,
    severity: str = "HIGH",
    fixed_version: str | None = None,
) -> dict[str, object]:
    value: dict[str, object] = {
        "VulnerabilityID": vulnerability_id,
        "PkgName": "example-package",
        "InstalledVersion": "1.0.0",
        "Severity": severity,
    }
    if fixed_version is not None:
        value["FixedVersion"] = fixed_version
    return value


class EvaluateTrivyResultsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_directory.cleanup)
        self.temp_path = Path(self.temp_directory.name)

    def write_json(self, name: str, value: object) -> Path:
        path = self.temp_path / name
        path.write_text(json.dumps(value), encoding="utf-8")
        return path

    def run_evaluator(
        self, *scans: tuple[str, Path, str], summary_path: Path | None = None
    ) -> subprocess.CompletedProcess[str]:
        command = [sys.executable, str(EVALUATOR)]
        for name, path, outcome in scans:
            command.extend(("--scan", name, str(path), outcome))
        environment = os.environ.copy()
        environment.pop("GITHUB_STEP_SUMMARY", None)
        if summary_path is not None:
            environment["GITHUB_STEP_SUMMARY"] = str(summary_path)
        return subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            env=environment,
        )

    def test_fixable_high_or_critical_findings_block(self) -> None:
        path = self.write_json(
            "fixed.json",
            report(
                vulnerability("CVE-2026-0001", fixed_version="1.0.1"),
                vulnerability("CVE-2026-0002", severity="CRITICAL", fixed_version="2.0.0"),
            ),
        )

        result = self.run_evaluator(("web", path, "success"))

        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("Result: BLOCKED", result.stdout)
        self.assertIn("Fixable HIGH/CRITICAL findings (blocking) (2)", result.stdout)
        self.assertIn("CVE-2026-0001", result.stdout)
        self.assertIn("CVE-2026-0002", result.stdout)

    def test_unfixed_findings_are_reported_as_non_blocking_residual_risk(self) -> None:
        path = self.write_json(
            "unfixed.json",
            report(
                vulnerability("CVE-2026-1001"),
                vulnerability("CVE-2026-1002", severity="CRITICAL", fixed_version=""),
            ),
        )

        result = self.run_evaluator(("core", path, "success"))

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Result: PASSED", result.stdout)
        self.assertIn(
            "Unfixed HIGH/CRITICAL findings (non-blocking residual risk) (2)", result.stdout
        )
        self.assertIn("CVE-2026-1001", result.stdout)

    def test_mixed_reports_are_all_aggregated_before_blocking(self) -> None:
        web = self.write_json(
            "web.json", report(vulnerability("CVE-WEB", fixed_version="1.1.0"))
        )
        core = self.write_json("core.json", report(vulnerability("CVE-CORE")))
        ai = self.write_json("ai.json", report(results=[]))

        result = self.run_evaluator(
            ("web", web, "success"),
            ("core", core, "success"),
            ("ai", ai, "success"),
        )

        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("CVE-WEB", result.stdout)
        self.assertIn("CVE-CORE", result.stdout)
        self.assertIn("Fixable HIGH/CRITICAL findings (blocking) (1)", result.stdout)
        self.assertIn(
            "Unfixed HIGH/CRITICAL findings (non-blocking residual risk) (1)", result.stdout
        )

    def test_malformed_json_blocks(self) -> None:
        path = self.temp_path / "malformed.json"
        path.write_text('{"SchemaVersion": 2,', encoding="utf-8")

        result = self.run_evaluator(("web", path, "success"))

        self.assertEqual(1, result.returncode)
        self.assertIn("malformed JSON", result.stdout)
        self.assertIn("Result: BLOCKED", result.stdout)

    def test_missing_result_blocks(self) -> None:
        result = self.run_evaluator(("ai", self.temp_path / "missing.json", "success"))

        self.assertEqual(1, result.returncode)
        self.assertIn("result file is missing", result.stdout)

    def test_schema_invalid_result_blocks(self) -> None:
        wrong_version = self.write_json(
            "wrong-version.json",
            {
                "SchemaVersion": 1,
                "ArtifactName": "offertrack/example:test",
                "ArtifactType": "container_image",
                "Results": [],
            },
        )
        malformed_vulnerability = self.write_json(
            "bad-vulnerability.json",
            report(results=[{"Target": "python", "Vulnerabilities": [{"Severity": "HIGH"}]}]),
        )

        result = self.run_evaluator(
            ("web", wrong_version, "success"),
            ("ai", malformed_vulnerability, "success"),
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("SchemaVersion must be 2", result.stdout)
        self.assertIn("VulnerabilityID must be a non-empty string", result.stdout)

    def test_failed_or_skipped_scanner_blocks_even_with_valid_report(self) -> None:
        path = self.write_json("clean.json", report(results=[]))

        result = self.run_evaluator(
            ("web", path, "failure"),
            ("core", path, "skipped"),
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("web&#58; scanner outcome is 'failure'", result.stdout)
        self.assertIn("core&#58; scanner outcome is 'skipped'", result.stdout)

    def test_null_results_and_null_vulnerabilities_are_valid_empty_reports(self) -> None:
        null_results = self.write_json("null-results.json", report(results=None))
        null_vulnerabilities = self.write_json(
            "null-vulnerabilities.json",
            report(results=[{"Target": "node", "Vulnerabilities": None}]),
        )

        result = self.run_evaluator(
            ("web", null_results, "success"),
            ("core", null_vulnerabilities, "success"),
        )

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Result: PASSED", result.stdout)

    def test_lower_severity_findings_do_not_enter_high_critical_policy(self) -> None:
        path = self.write_json(
            "medium.json",
            report(vulnerability("CVE-MEDIUM", severity="MEDIUM", fixed_version="1.0.1")),
        )

        result = self.run_evaluator(("web", path, "success"))

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("CVE-MEDIUM", result.stdout)

    def test_summary_is_appended_to_github_step_summary(self) -> None:
        path = self.write_json("clean.json", report(results=[]))
        summary_path = self.temp_path / "summary.md"

        result = self.run_evaluator(("web", path, "success"), summary_path=summary_path)

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(result.stdout, summary_path.read_text(encoding="utf-8"))

    def test_summary_sanitizes_untrusted_finding_fields(self) -> None:
        unsafe = vulnerability("CVE-2026-2001", fixed_version="1.0.1")
        unsafe["PkgName"] = "<script>|package\nnext-line ![probe](https://example.invalid/x)"
        path = self.write_json(
            "unsafe-display.json",
            report(
                results=[
                    {
                        "Target": "target|with-markdown",
                        "Vulnerabilities": [unsafe],
                    }
                ]
            ),
        )

        result = self.run_evaluator(("web", path, "success"))

        self.assertEqual(1, result.returncode)
        self.assertNotIn("<script>", result.stdout)
        self.assertIn("&lt;script&gt;&#124;package?next-line", result.stdout)
        self.assertIn("target&#124;with-markdown", result.stdout)
        self.assertNotIn("![probe]", result.stdout)
        self.assertNotIn("https://example.invalid", result.stdout)
        self.assertIn("&#33;&#91;probe&#93;&#40;https&#58;//example.invalid/x&#41;", result.stdout)


if __name__ == "__main__":
    unittest.main()
