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
IMAGE_TAG = "test"
IMAGE_REPOSITORIES = {
    "web": "offertrack/web",
    "core-api": "offertrack/core-api",
    "ai-service": "offertrack/ai-service",
}
NOT_SUPPLIED = object()


def report(
    scan_name: str,
    *vulnerabilities: dict[str, object],
    artifact_name: str | None = None,
    results: object = NOT_SUPPLIED,
) -> dict[str, object]:
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
        "ArtifactName": artifact_name or f"{IMAGE_REPOSITORIES[scan_name]}:{IMAGE_TAG}",
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

    def complete_scans(
        self, **overrides: tuple[Path, str]
    ) -> list[tuple[str, Path, str]]:
        scans: list[tuple[str, Path, str]] = []
        for scan_name in IMAGE_REPOSITORIES:
            path, outcome = overrides.get(
                scan_name,
                (
                    self.write_json(
                        f"{scan_name}-clean.json", report(scan_name, results=[])
                    ),
                    "success",
                ),
            )
            scans.append((scan_name, path, outcome))
        return scans

    def run_evaluator(
        self,
        *scans: tuple[str, Path, str],
        image_tag: str = IMAGE_TAG,
        summary_path: Path | None = None,
    ) -> subprocess.CompletedProcess[str]:
        command = [sys.executable, str(EVALUATOR), "--image-tag", image_tag]
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

    def test_exact_valid_three_report_set_passes(self) -> None:
        result = self.run_evaluator(*self.complete_scans())

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Result: PASSED", result.stdout)
        self.assertIn("Evaluation errors (0)", result.stdout)

    def test_missing_expected_scan_blocks(self) -> None:
        scans = self.complete_scans()

        result = self.run_evaluator(*scans[:-1])

        self.assertEqual(1, result.returncode)
        self.assertIn("missing expected scan name&#58; ai-service", result.stdout)

    def test_unexpected_scan_name_blocks(self) -> None:
        scans = self.complete_scans()
        unexpected = self.write_json("unexpected.json", report("web", results=[]))

        result = self.run_evaluator(*scans, ("worker", unexpected, "success"))

        self.assertEqual(1, result.returncode)
        self.assertIn("unexpected scan name&#58; worker", result.stdout)

    def test_duplicate_scan_name_blocks_and_all_entries_are_inspected(self) -> None:
        scans = self.complete_scans()
        duplicate = self.write_json(
            "duplicate.json", report("web", vulnerability("CVE-DUPLICATE"))
        )

        result = self.run_evaluator(*scans, ("web", duplicate, "failure"))

        self.assertEqual(1, result.returncode)
        self.assertIn("duplicate scan name&#58; web &#40;2 entries&#41;", result.stdout)
        self.assertIn("web&#58; scanner outcome is 'failure'", result.stdout)
        self.assertIn("CVE-DUPLICATE", result.stdout)

    def test_invalid_image_tag_blocks(self) -> None:
        scans = self.complete_scans()
        for invalid_tag in ("bad/tag", "x" * 129):
            with self.subTest(invalid_tag=invalid_tag):
                result = self.run_evaluator(*scans, image_tag=invalid_tag)
                self.assertEqual(1, result.returncode)
                self.assertIn("invalid image tag", result.stdout)

    def test_artifact_name_mismatch_blocks(self) -> None:
        wrong = self.write_json(
            "wrong-artifact.json",
            report("web", artifact_name="registry.example/other/web:test", results=[]),
        )

        result = self.run_evaluator(*self.complete_scans(web=(wrong, "success")))

        self.assertEqual(1, result.returncode)
        self.assertIn("web&#58; ArtifactName is", result.stdout)
        self.assertIn("expected 'offertrack/web&#58;test'", result.stdout)

    def test_web_and_core_reports_swapped_blocks(self) -> None:
        web = self.write_json("web.json", report("web", results=[]))
        core = self.write_json("core.json", report("core-api", results=[]))

        result = self.run_evaluator(
            *self.complete_scans(web=(core, "success"), **{"core-api": (web, "success")})
        )

        self.assertEqual(1, result.returncode)
        self.assertEqual(2, result.stdout.count("ArtifactName is"))
        self.assertIn("expected 'offertrack/web&#58;test'", result.stdout)
        self.assertIn("expected 'offertrack/core-api&#58;test'", result.stdout)

    def test_one_report_reused_for_every_scan_blocks(self) -> None:
        web = self.write_json("web.json", report("web", results=[]))

        result = self.run_evaluator(
            ("web", web, "success"),
            ("core-api", web, "success"),
            ("ai-service", web, "success"),
        )

        self.assertEqual(1, result.returncode)
        self.assertEqual(2, result.stdout.count("ArtifactName is"))

    def test_fixable_high_or_critical_findings_block(self) -> None:
        path = self.write_json(
            "fixed.json",
            report(
                "web",
                vulnerability("CVE-2026-0001", fixed_version="1.0.1"),
                vulnerability("CVE-2026-0002", severity="CRITICAL", fixed_version="2.0.0"),
            ),
        )

        result = self.run_evaluator(*self.complete_scans(web=(path, "success")))

        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("Result: BLOCKED", result.stdout)
        self.assertIn("Fixable HIGH/CRITICAL findings (blocking) (2)", result.stdout)
        self.assertIn("CVE-2026-0001", result.stdout)
        self.assertIn("CVE-2026-0002", result.stdout)

    def test_unfixed_findings_are_reported_as_non_blocking_residual_risk(self) -> None:
        path = self.write_json(
            "unfixed.json",
            report(
                "core-api",
                vulnerability("CVE-2026-1001"),
                vulnerability("CVE-2026-1002", severity="CRITICAL", fixed_version=""),
            ),
        )

        result = self.run_evaluator(*self.complete_scans(**{"core-api": (path, "success")}))

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Result: PASSED", result.stdout)
        self.assertIn(
            "Unfixed HIGH/CRITICAL findings (non-blocking residual risk) (2)", result.stdout
        )
        self.assertIn("CVE-2026-1001", result.stdout)

    def test_mixed_reports_are_all_aggregated_before_blocking(self) -> None:
        web = self.write_json(
            "web.json", report("web", vulnerability("CVE-WEB", fixed_version="1.1.0"))
        )
        core = self.write_json(
            "core.json", report("core-api", vulnerability("CVE-CORE"))
        )

        result = self.run_evaluator(
            *self.complete_scans(web=(web, "success"), **{"core-api": (core, "success")})
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

        result = self.run_evaluator(*self.complete_scans(web=(path, "success")))

        self.assertEqual(1, result.returncode)
        self.assertIn("malformed JSON", result.stdout)
        self.assertIn("Result: BLOCKED", result.stdout)

    def test_missing_result_blocks(self) -> None:
        result = self.run_evaluator(
            *self.complete_scans(**{"ai-service": (self.temp_path / "missing.json", "success")})
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("result file is missing", result.stdout)

    def test_oversized_result_blocks(self) -> None:
        path = self.temp_path / "oversized.json"
        with path.open("wb") as report_file:
            report_file.truncate(64 * 1024 * 1024 + 1)

        result = self.run_evaluator(*self.complete_scans(web=(path, "success")))

        self.assertEqual(1, result.returncode)
        self.assertIn("exceeds the 64 MiB safety limit", result.stdout)

    def test_schema_invalid_results_are_all_reported(self) -> None:
        wrong_version = self.write_json(
            "wrong-version.json",
            {
                "SchemaVersion": 1,
                "ArtifactName": "offertrack/web:test",
                "ArtifactType": "container_image",
                "Results": [],
            },
        )
        malformed_vulnerability = self.write_json(
            "bad-vulnerability.json",
            report(
                "ai-service",
                results=[{"Target": "python", "Vulnerabilities": [{"Severity": "HIGH"}]}],
            ),
        )

        result = self.run_evaluator(
            *self.complete_scans(
                web=(wrong_version, "success"),
                **{"ai-service": (malformed_vulnerability, "success")},
            )
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("SchemaVersion must be 2", result.stdout)
        self.assertIn("VulnerabilityID must be a non-empty string", result.stdout)

    def test_failed_or_skipped_scanner_blocks_even_with_valid_reports(self) -> None:
        scans = self.complete_scans()
        scans[0] = (scans[0][0], scans[0][1], "failure")
        scans[1] = (scans[1][0], scans[1][1], "skipped")

        result = self.run_evaluator(*scans)

        self.assertEqual(1, result.returncode)
        self.assertIn("web&#58; scanner outcome is 'failure'", result.stdout)
        self.assertIn("core-api&#58; scanner outcome is 'skipped'", result.stdout)

    def test_null_results_and_null_vulnerabilities_are_valid_empty_reports(self) -> None:
        null_results = self.write_json("null-results.json", report("web", results=None))
        null_vulnerabilities = self.write_json(
            "null-vulnerabilities.json",
            report(
                "core-api", results=[{"Target": "node", "Vulnerabilities": None}]
            ),
        )

        result = self.run_evaluator(
            *self.complete_scans(
                web=(null_results, "success"),
                **{"core-api": (null_vulnerabilities, "success")},
            )
        )

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Result: PASSED", result.stdout)

    def test_lower_severity_findings_do_not_enter_high_critical_policy(self) -> None:
        path = self.write_json(
            "medium.json",
            report(
                "web", vulnerability("CVE-MEDIUM", severity="MEDIUM", fixed_version="1.0.1")
            ),
        )

        result = self.run_evaluator(*self.complete_scans(web=(path, "success")))

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("CVE-MEDIUM", result.stdout)

    def test_summary_is_appended_to_github_step_summary(self) -> None:
        summary_path = self.temp_path / "summary.md"

        result = self.run_evaluator(*self.complete_scans(), summary_path=summary_path)

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(result.stdout, summary_path.read_text(encoding="utf-8"))

    def test_summary_sanitizes_untrusted_finding_fields(self) -> None:
        unsafe = vulnerability("CVE-2026-2001", fixed_version="1.0.1")
        unsafe["PkgName"] = "<script>|package\nnext-line ![probe](https://example.invalid/x)"
        path = self.write_json(
            "unsafe-display.json",
            report(
                "web",
                results=[
                    {
                        "Target": "target|with-markdown",
                        "Vulnerabilities": [unsafe],
                    }
                ],
            ),
        )

        result = self.run_evaluator(*self.complete_scans(web=(path, "success")))

        self.assertEqual(1, result.returncode)
        self.assertNotIn("<script>", result.stdout)
        self.assertIn("&lt;script&gt;&#124;package?next-line", result.stdout)
        self.assertIn("target&#124;with-markdown", result.stdout)
        self.assertNotIn("![probe]", result.stdout)
        self.assertNotIn("https://example.invalid", result.stdout)
        self.assertIn("&#33;&#91;probe&#93;&#40;https&#58;//example.invalid/x&#41;", result.stdout)


if __name__ == "__main__":
    unittest.main()
