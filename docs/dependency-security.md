# Dependency security scanning

OfferTrack runs OSV-Scanner 2.3.8 weekly, on demand, and for pull requests that
change dependency manifests or locks. It scans the pnpm lock, the Maven POM,
and the committed Python `pylock.toml` without requiring GitHub Advanced
Security or SARIF upload.

The scan action currently has `continue-on-error: true` because this
milestone's baseline has not yet been verified clean on GitHub Actions. The
following step still requires a valid JSON result and prints all finding IDs;
findings must be fixed or documented here with an owner, rationale, and target
date. Once a full scheduled/manual run reports no findings, remove
`continue-on-error: true` from `.github/workflows/osv-scan.yml` in a reviewed
pull request. OSV's vulnerability exit code will then make the job blocking.
No vulnerability is currently recorded here as accepted risk.

The Python lock targets Python 3.11 on Linux. Generate it with the pinned tool:

```bash
cd apps/ai-service
bash scripts/lock-dependencies.sh
```

The script requires Linux/Python 3.11 and pins `pip==26.1.2` before running
`python -m pip lock --output pylock.toml ".[test]"`, normalizing the local
project entry for installation, and updating `pylock.toml.sha256`.

CI checks the checksum before installing from `pylock.toml`. Dependabot may
update `pyproject.toml`, but the resulting pull request requires manual lock
regeneration before it can pass CI.
