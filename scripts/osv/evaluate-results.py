#!/usr/bin/env python3
"""Apply OfferTrack's Critical-only policy to OSV-Scanner JSON results."""

import argparse
import json
import math
import os
import sys
from pathlib import Path

CUTOFFS = ((9, "Critical"), (7, "High"), (4, "Medium"), (0, "Low"))


class PolicyError(Exception):
    pass


def parse_score(value):
    if isinstance(value, bool):
        return None
    try:
        score = float(value)
    except (TypeError, ValueError, OverflowError):
        return None
    return score if math.isfinite(score) and 0 <= score <= 10 else None


def load_findings(path: Path):
    try:
        contents = path.read_text(encoding="utf-8")
    except OSError as error:
        raise PolicyError("Missing OSV-Scanner result file.") from error
    if not contents.strip():
        raise PolicyError("OSV-Scanner produced an empty result file.")
    try:
        document = json.loads(contents)
    except json.JSONDecodeError as error:
        raise PolicyError("Invalid OSV-Scanner JSON.") from error
    if not isinstance(document, dict) or not isinstance(document.get("results"), list):
        raise PolicyError("OSV-Scanner JSON must contain a results array.")

    findings = []
    for result in document["results"]:
        packages = result.get("packages", []) if isinstance(result, dict) else None
        if not isinstance(packages, list):
            raise PolicyError("OSV result packages must be an array.")
        for package in packages:
            groups = package.get("groups") if isinstance(package, dict) else None
            if not isinstance(groups, list):
                raise PolicyError("OSV package groups must be an array.")
            for group in groups:
                ids = group.get("ids") if isinstance(group, dict) else None
                if not isinstance(ids, list) or not ids:
                    raise PolicyError("OSV group IDs must be a non-empty array.")
                findings.append((ids, parse_score(group.get("max_severity"))))
    return findings


def severity(score):
    return (
        "Unknown"
        if score is None
        else next(name for minimum, name in CUTOFFS if score >= minimum)
    )


def write_summary(path: Path, findings):
    buckets = {name: [] for name in ("Critical", "High", "Medium", "Low", "Unknown")}
    for ids, score in findings:
        buckets[severity(score)].append((ids, score))

    lines = ["## OSV dependency scan", ""]
    for name in ("Critical", "High", "Medium", "Low"):
        suffix = " (blocking)" if name == "Critical" else ""
        lines.append(f"**{name} findings{suffix}:** {len(buckets[name])}")
        for ids, score in buckets[name]:
            lines.append(
                f"- {', '.join(f'`{advisory}`' for advisory in ids)} — CVSS `{score}`"
            )
        lines.append("")
    lines.append(
        "**Findings with missing or unusable CVSS severity (non-blocking):** "
        f"{len(buckets['Unknown'])}"
    )
    for ids, _ in buckets["Unknown"]:
        lines.append(f"- {', '.join(f'`{advisory}`' for advisory in ids)}")
    with path.open("a", encoding="utf-8") as summary:
        summary.write("\n".join(lines) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path, required=True)
    parser.add_argument("--scanner-exit-code", type=int, required=True)
    parser.add_argument(
        "--summary", type=Path, default=os.environ.get("GITHUB_STEP_SUMMARY")
    )
    args = parser.parse_args()
    if args.summary is None:
        raise PolicyError("No GitHub Actions summary path was provided.")
    findings = load_findings(args.results)
    if args.scanner_exit_code not in (0, 1):
        raise PolicyError(
            f"Unexpected OSV-Scanner exit code: {args.scanner_exit_code}."
        )
    if bool(findings) != (args.scanner_exit_code == 1):
        raise PolicyError("OSV-Scanner exit code and findings disagree.")
    write_summary(args.summary, findings)
    critical_count = sum(severity(score) == "Critical" for _, score in findings)
    print(f"OSV findings: {len(findings)}; Critical findings: {critical_count}.")
    return int(critical_count > 0)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PolicyError as error:
        print(f"OSV policy evaluation failed closed: {error}", file=sys.stderr)
        raise SystemExit(1) from error
