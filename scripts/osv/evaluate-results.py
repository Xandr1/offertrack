#!/usr/bin/env python3
"""Apply OfferTrack's blocking policy to OSV-Scanner JSON results.

OSV-Scanner 2.3.8 emits full advisory records in
``packages[].vulnerabilities[]`` and groups the affected advisory IDs in
``packages[].groups[]``.  A group's numeric ``max_severity`` is OSV-Scanner's
maximum CVSS score calculated from the advisory ``severity[].score`` data.

The scanner is expected to return 0 only with no findings and 1 only when it
reports one or more findings. All other scanner exit codes fail closed.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any

import tomllib

CRITICAL_CVSS = Decimal("9.0")
ADVISORY_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]*$")
SEVERITIES = ("Critical", "High", "Medium", "Low")


class PolicyError(Exception):
    """The scanner output or policy configuration cannot be trusted."""


@dataclass(frozen=True)
class Finding:
    advisory_ids: tuple[str, ...]
    score: Decimal | None

    @property
    def severity(self) -> str | None:
        if self.score is None:
            return None
        if self.score >= CRITICAL_CVSS:
            return "Critical"
        if self.score >= Decimal("7.0"):
            return "High"
        if self.score >= Decimal("4.0"):
            return "Medium"
        return "Low"


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--scanner-exit-code", type=int, required=True)
    parser.add_argument(
        "--summary",
        type=Path,
        default=os.environ.get("GITHUB_STEP_SUMMARY"),
        help="GitHub Actions step-summary file (defaults to GITHUB_STEP_SUMMARY).",
    )
    return parser.parse_args()


def load_results(path: Path) -> list[dict[str, Any]]:
    try:
        raw_results = path.read_text(encoding="utf-8")
    except OSError as error:
        raise PolicyError(
            f"OSV-Scanner did not produce a result file: {error}"
        ) from error

    if not raw_results.strip():
        raise PolicyError("OSV-Scanner produced an empty result file.")

    try:
        document = json.loads(raw_results)
    except json.JSONDecodeError as error:
        raise PolicyError(
            f"OSV-Scanner result file is not valid JSON: {error}"
        ) from error

    if not isinstance(document, dict) or not isinstance(document.get("results"), list):
        raise PolicyError(
            "OSV-Scanner result file must contain a top-level results array."
        )

    return document["results"]


def load_accepted_risks(path: Path) -> list[tuple[str, str]]:
    try:
        with path.open("rb") as config_file:
            document = tomllib.load(config_file)
    except (OSError, tomllib.TOMLDecodeError) as error:
        raise PolicyError(
            f"Unable to read OSV scanner configuration: {error}"
        ) from error

    risks = document.get("IgnoredVulns", [])
    if not isinstance(risks, list):
        raise PolicyError("IgnoredVulns must be an array when present.")

    accepted_risks = []
    for risk in risks:
        if not isinstance(risk, dict) or not is_advisory_id(risk.get("id")):
            raise PolicyError(
                "Each IgnoredVulns entry must contain a valid advisory id."
            )
        expiry = risk.get("ignoreUntil")
        if isinstance(expiry, (date, datetime)):
            expiry_text = expiry.isoformat()
        elif isinstance(expiry, str) and expiry:
            expiry_text = expiry
        else:
            raise PolicyError("Each IgnoredVulns entry must contain ignoreUntil.")
        accepted_risks.append((risk["id"], expiry_text))

    return sorted(accepted_risks)


def is_advisory_id(value: Any) -> bool:
    return isinstance(value, str) and bool(ADVISORY_ID.fullmatch(value))


def parse_score(value: Any) -> Decimal | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, (str, int, float, Decimal)):
        return None
    try:
        score = Decimal(str(value))
    except InvalidOperation:
        return None
    if not score.is_finite() or not Decimal("0.0") <= score <= Decimal("10.0"):
        return None
    return score


def extract_findings(results: list[dict[str, Any]]) -> list[Finding]:
    findings = []
    for result in results:
        if not isinstance(result, dict):
            raise PolicyError("Each OSV result must be an object.")
        source = result.get("source")
        if not isinstance(source, dict) or not all(
            isinstance(source.get(field), str) and source[field]
            for field in ("path", "type")
        ):
            raise PolicyError("Each OSV result must contain a source path and type.")
        packages = result.get("packages")
        if not isinstance(packages, list):
            raise PolicyError("Each OSV result packages value must be an array.")

        for package in packages:
            if not isinstance(package, dict):
                raise PolicyError("Each OSV package result must be an object.")
            if not isinstance(package.get("package"), dict):
                raise PolicyError(
                    "Each OSV package result must contain package metadata."
                )
            vulnerabilities = package.get("vulnerabilities")
            groups = package.get("groups")
            if not isinstance(vulnerabilities, list) or not isinstance(groups, list):
                raise PolicyError(
                    "Each OSV package result vulnerabilities and groups values must be arrays."
                )

            vulnerability_ids = set()
            for vulnerability in vulnerabilities:
                if not isinstance(vulnerability, dict) or not is_advisory_id(
                    vulnerability.get("id")
                ):
                    raise PolicyError(
                        "Each OSV vulnerability must contain a valid advisory id."
                    )
                vulnerability_ids.add(vulnerability["id"])
            if len(vulnerability_ids) != len(vulnerabilities):
                raise PolicyError(
                    "An OSV advisory must not appear more than once per package."
                )

            grouped_ids = set()
            for group in groups:
                if not isinstance(group, dict) or not isinstance(
                    group.get("ids"), list
                ):
                    raise PolicyError(
                        "Each OSV vulnerability group must contain an ids array."
                    )
                raw_ids = group["ids"]
                if not raw_ids or not all(is_advisory_id(item) for item in raw_ids):
                    raise PolicyError(
                        "Each OSV vulnerability group must contain valid advisory ids."
                    )
                advisory_ids = tuple(sorted(raw_ids))
                if len(set(advisory_ids)) != len(advisory_ids):
                    raise PolicyError("An OSV advisory must not repeat within a group.")
                if grouped_ids.intersection(advisory_ids):
                    raise PolicyError(
                        "An OSV advisory must not appear in multiple groups."
                    )
                grouped_ids.update(advisory_ids)
                findings.append(
                    Finding(advisory_ids, parse_score(group.get("max_severity")))
                )

            if grouped_ids != vulnerability_ids:
                raise PolicyError(
                    "OSV vulnerability groups must exactly cover the reported advisory ids."
                )

    return findings


def validate_scanner_execution(exit_code: int, findings: list[Finding]) -> None:
    if exit_code not in (0, 1):
        raise PolicyError(
            f"OSV-Scanner exited with {exit_code}; expected 0 for a clean scan or 1 for findings."
        )
    if exit_code == 0 and findings:
        raise PolicyError(
            "OSV-Scanner exited cleanly but reported vulnerability findings."
        )
    if exit_code == 1 and not findings:
        raise PolicyError(
            "OSV-Scanner reported a findings exit but no findings in its result file."
        )


def format_ids(advisory_ids: tuple[str, ...]) -> str:
    return ", ".join(f"`{advisory_id}`" for advisory_id in advisory_ids)


def write_summary(
    path: Path, findings: list[Finding], accepted_risks: list[tuple[str, str]]
) -> None:
    by_severity = {
        severity: sorted(
            (finding for finding in findings if finding.severity == severity),
            key=lambda finding: finding.advisory_ids,
        )
        for severity in SEVERITIES
    }
    unknown = sorted(
        (finding for finding in findings if finding.severity is None),
        key=lambda finding: finding.advisory_ids,
    )

    lines = ["## OSV dependency scan", ""]
    for severity in SEVERITIES:
        blocking = " (blocking)" if severity == "Critical" else ""
        lines.extend(
            (f"**{severity} findings{blocking}:** {len(by_severity[severity])}", "")
        )
        for finding in by_severity[severity]:
            lines.append(
                f"- {format_ids(finding.advisory_ids)} — CVSS `{finding.score}`"
            )
        if by_severity[severity]:
            lines.append("")

    lines.extend(
        [
            f"**Findings with missing or unusable CVSS severity (non-blocking):** {len(unknown)}",
            "",
        ]
    )
    for finding in unknown:
        lines.append(f"- {format_ids(finding.advisory_ids)}")
    if unknown:
        lines.append("")

    lines.extend((f"**Documented accepted risks:** {len(accepted_risks)}", ""))
    if accepted_risks:
        lines.append("**Accepted risk IDs and expiries:**")
        lines.extend(
            f"- `{advisory_id}` — expires `{expiry}`"
            for advisory_id, expiry in accepted_risks
        )
        lines.append("")

    try:
        with path.open("a", encoding="utf-8") as summary_file:
            summary_file.write("\n".join(lines))
            summary_file.write("\n")
    except OSError as error:
        raise PolicyError(
            f"Unable to write the GitHub Actions summary: {error}"
        ) from error


def main() -> int:
    arguments = parse_arguments()
    if arguments.summary is None:
        raise PolicyError("A GitHub Actions summary path is required.")

    results = load_results(arguments.results)
    accepted_risks = load_accepted_risks(arguments.config)
    findings = extract_findings(results)
    validate_scanner_execution(arguments.scanner_exit_code, findings)
    write_summary(arguments.summary, findings, accepted_risks)

    critical_count = sum(finding.severity == "Critical" for finding in findings)
    print(f"OSV-Scanner reported {len(findings)} unaccepted finding group(s).")
    print(f"Critical finding groups: {critical_count}.")
    if critical_count:
        print("Critical OSV findings block this job.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PolicyError as error:
        print(f"OSV policy evaluation failed closed: {error}", file=sys.stderr)
        raise SystemExit(1)
