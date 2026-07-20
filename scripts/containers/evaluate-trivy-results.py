#!/usr/bin/env python3
"""Apply OfferTrack's aggregate policy to Trivy JSON image reports.

Every expected scan must be supplied as a repeated ``--scan`` triple:

    --scan NAME JSON_PATH SCANNER_OUTCOME

The evaluator always inspects every triple before returning. Fixable HIGH or
CRITICAL vulnerabilities block, while vulnerabilities without an available
fix are reported as an explicit, non-blocking residual risk. A failed scanner
or an unusable report blocks because the policy cannot be evaluated safely.
"""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Sequence


EXPECTED_SCHEMA_VERSION = 2
BLOCKING_SEVERITIES = frozenset({"HIGH", "CRITICAL"})
MAX_REPORT_BYTES = 64 * 1024 * 1024
MAX_REPORTED_FINDINGS_PER_CLASS = 100
MAX_REPORTED_ERRORS = 100
MAX_DISPLAY_VALUE_LENGTH = 160
SCAN_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,63}\Z")
MARKDOWN_UNSAFE_CHARACTERS = frozenset(r"\`*_{}[]()!|@:")


class ReportError(ValueError):
    """Raised when a Trivy result cannot be trusted for policy evaluation."""


@dataclass(frozen=True)
class Finding:
    scan: str
    target: str
    vulnerability_id: str
    package: str
    installed_version: str
    fixed_version: str
    severity: str


@dataclass
class Evaluation:
    fixable: list[Finding] = field(default_factory=list)
    unfixed: list[Finding] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)

    @property
    def blocks(self) -> bool:
        return bool(self.fixable or self.errors)


def _safe_display(value: object, *, fallback: str = "unknown") -> str:
    """Return a bounded, single-line value safe for logs and Markdown tables."""

    if not isinstance(value, str) or not value:
        return fallback
    cleaned = "".join(character if character.isprintable() else "?" for character in value)
    if len(cleaned) > MAX_DISPLAY_VALUE_LENGTH:
        cleaned = cleaned[: MAX_DISPLAY_VALUE_LENGTH - 3] + "..."
    cleaned = html.escape(cleaned, quote=False)
    return "".join(
        f"&#{ord(character)};" if character in MARKDOWN_UNSAFE_CHARACTERS else character
        for character in cleaned
    )


def _require_string(
    vulnerability: dict[str, Any], field_name: str, location: str, *, allow_empty: bool = False
) -> str:
    value = vulnerability.get(field_name)
    if not isinstance(value, str) or (not value and not allow_empty):
        expected = "a string" if allow_empty else "a non-empty string"
        raise ReportError(f"{location}.{field_name} must be {expected}")
    return value


def _load_report(path: Path) -> dict[str, Any]:
    try:
        stat = path.stat()
    except FileNotFoundError as error:
        raise ReportError(f"result file is missing: {path}") from error
    except OSError as error:
        raise ReportError(f"result file cannot be inspected: {path}: {error}") from error

    if not path.is_file():
        raise ReportError(f"result path is not a regular file: {path}")
    if stat.st_size > MAX_REPORT_BYTES:
        raise ReportError(
            f"result file exceeds the {MAX_REPORT_BYTES // (1024 * 1024)} MiB safety limit: {path}"
        )

    try:
        with path.open("r", encoding="utf-8") as report_file:
            report = json.load(report_file)
    except UnicodeDecodeError as error:
        raise ReportError(f"result file is not UTF-8: {path}") from error
    except json.JSONDecodeError as error:
        raise ReportError(
            f"result file is malformed JSON at line {error.lineno}, column {error.colno}: {path}"
        ) from error
    except OSError as error:
        raise ReportError(f"result file cannot be read: {path}: {error}") from error

    if not isinstance(report, dict):
        raise ReportError("report root must be a JSON object")
    if report.get("SchemaVersion") != EXPECTED_SCHEMA_VERSION:
        raise ReportError(
            f"SchemaVersion must be {EXPECTED_SCHEMA_VERSION}, got "
            f"{_safe_display(str(report.get('SchemaVersion')))}"
        )
    if not isinstance(report.get("ArtifactName"), str) or not report["ArtifactName"]:
        raise ReportError("ArtifactName must be a non-empty string")
    if report.get("ArtifactType") != "container_image":
        raise ReportError("ArtifactType must be 'container_image'")

    results = report.get("Results", [])
    if results is None:
        # Trivy has emitted null for an empty result set in some versions.
        results = []
    if not isinstance(results, list):
        raise ReportError("Results must be an array, null, or omitted")
    return {**report, "Results": results}


def _findings(scan_name: str, report: dict[str, Any]) -> tuple[list[Finding], list[Finding]]:
    fixable: list[Finding] = []
    unfixed: list[Finding] = []

    for result_index, result in enumerate(report["Results"]):
        result_location = f"Results[{result_index}]"
        if not isinstance(result, dict):
            raise ReportError(f"{result_location} must be an object")
        target = result.get("Target")
        if not isinstance(target, str) or not target:
            raise ReportError(f"{result_location}.Target must be a non-empty string")

        vulnerabilities = result.get("Vulnerabilities", [])
        if vulnerabilities is None:
            vulnerabilities = []
        if not isinstance(vulnerabilities, list):
            raise ReportError(f"{result_location}.Vulnerabilities must be an array, null, or omitted")

        for vulnerability_index, vulnerability in enumerate(vulnerabilities):
            location = f"{result_location}.Vulnerabilities[{vulnerability_index}]"
            if not isinstance(vulnerability, dict):
                raise ReportError(f"{location} must be an object")

            vulnerability_id = _require_string(vulnerability, "VulnerabilityID", location)
            package = _require_string(vulnerability, "PkgName", location)
            severity = _require_string(vulnerability, "Severity", location).upper()
            if severity not in {"UNKNOWN", "LOW", "MEDIUM", "HIGH", "CRITICAL"}:
                raise ReportError(f"{location}.Severity has an unsupported value")
            if severity not in BLOCKING_SEVERITIES:
                continue

            installed_version = vulnerability.get("InstalledVersion", "")
            if not isinstance(installed_version, str):
                raise ReportError(f"{location}.InstalledVersion must be a string when present")
            fixed_version = vulnerability.get("FixedVersion", "")
            if fixed_version is None:
                fixed_version = ""
            if not isinstance(fixed_version, str):
                raise ReportError(f"{location}.FixedVersion must be a string, null, or omitted")

            finding = Finding(
                scan=scan_name,
                target=target,
                vulnerability_id=vulnerability_id,
                package=package,
                installed_version=installed_version,
                fixed_version=fixed_version,
                severity=severity,
            )
            if fixed_version.strip():
                fixable.append(finding)
            else:
                unfixed.append(finding)

    return fixable, unfixed


def evaluate(scans: Sequence[tuple[str, Path, str]]) -> Evaluation:
    evaluation = Evaluation()
    seen_names: set[str] = set()

    for scan_name, result_path, scanner_outcome in scans:
        if not SCAN_NAME.fullmatch(scan_name):
            evaluation.errors.append(
                f"invalid scan name {_safe_display(scan_name)!r}; use 1-64 letters, digits, '.', '_' or '-'"
            )
            continue
        if scan_name in seen_names:
            evaluation.errors.append(f"duplicate scan name: {scan_name}")
            continue
        seen_names.add(scan_name)

        if scanner_outcome != "success":
            evaluation.errors.append(
                f"{scan_name}: scanner outcome is {_safe_display(scanner_outcome)!r}, expected 'success'"
            )

        try:
            report = _load_report(result_path)
            fixable, unfixed = _findings(scan_name, report)
        except ReportError as error:
            evaluation.errors.append(f"{scan_name}: {error}")
            continue

        evaluation.fixable.extend(fixable)
        evaluation.unfixed.extend(unfixed)

    return evaluation


def _finding_lines(title: str, findings: Sequence[Finding]) -> list[str]:
    lines = [f"### {title} ({len(findings)})", ""]
    if not findings:
        lines.extend(["None.", ""])
        return lines

    lines.extend(
        [
            "| Scan | Severity | Vulnerability | Package | Installed | Fixed version | Target |",
            "| --- | --- | --- | --- | --- | --- | --- |",
        ]
    )
    for finding in findings[:MAX_REPORTED_FINDINGS_PER_CLASS]:
        lines.append(
            "| "
            + " | ".join(
                (
                    _safe_display(finding.scan),
                    _safe_display(finding.severity),
                    _safe_display(finding.vulnerability_id),
                    _safe_display(finding.package),
                    _safe_display(finding.installed_version, fallback="not reported"),
                    _safe_display(finding.fixed_version, fallback="unavailable"),
                    _safe_display(finding.target),
                )
            )
            + " |"
        )
    omitted = len(findings) - MAX_REPORTED_FINDINGS_PER_CLASS
    if omitted > 0:
        lines.extend(["", f"{omitted} additional finding(s) omitted from this bounded summary."])
    lines.append("")
    return lines


def render(evaluation: Evaluation) -> str:
    verdict = "BLOCKED" if evaluation.blocks else "PASSED"
    lines = [
        "## Trivy aggregate policy",
        "",
        f"**Result: {verdict}**",
        "",
        (
            "Fixable HIGH/CRITICAL findings block. Unfixed HIGH/CRITICAL findings are "
            "reported as a non-blocking residual risk."
        ),
        "",
        f"### Evaluation errors ({len(evaluation.errors)})",
        "",
    ]
    if evaluation.errors:
        lines.extend(
            f"- {_safe_display(error)}" for error in evaluation.errors[:MAX_REPORTED_ERRORS]
        )
        omitted_errors = len(evaluation.errors) - MAX_REPORTED_ERRORS
        if omitted_errors > 0:
            lines.append(
                f"- {omitted_errors} additional evaluation error(s) omitted from this bounded summary."
            )
        lines.append("")
    else:
        lines.extend(["None.", ""])

    lines.extend(_finding_lines("Fixable HIGH/CRITICAL findings (blocking)", evaluation.fixable))
    lines.extend(
        _finding_lines(
            "Unfixed HIGH/CRITICAL findings (non-blocking residual risk)", evaluation.unfixed
        )
    )
    return "\n".join(lines).rstrip() + "\n"


def _append_github_summary(summary: str) -> None:
    summary_path_value = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path_value:
        return
    try:
        with Path(summary_path_value).open("a", encoding="utf-8") as summary_file:
            summary_file.write(summary)
    except OSError as error:
        print(f"Unable to write GITHUB_STEP_SUMMARY: {error}", file=sys.stderr)


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--scan",
        action="append",
        nargs=3,
        metavar=("NAME", "JSON_PATH", "SCANNER_OUTCOME"),
        required=True,
        help="add an expected Trivy scan result; repeat once per image",
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    scans = [(name, Path(path), outcome) for name, path, outcome in args.scan]
    evaluation = evaluate(scans)
    summary = render(evaluation)
    print(summary, end="")
    _append_github_summary(summary)
    return 1 if evaluation.blocks else 0


if __name__ == "__main__":
    raise SystemExit(main())
