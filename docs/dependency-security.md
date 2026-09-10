# Dependency security baseline

This document covers the source dependency OSV policy. The independent
production-image Trivy policy, including the non-blocking accepted residual
risk for HIGH/CRITICAL findings without an upstream fix, is documented in
[Production container images](container-images.md#container-vulnerability-policy).

OfferTrack runs the pinned OSV-Scanner 2.3.8 on pull requests that change
dependency inputs, weekly, and on demand. Only Critical findings block the job.
High, Medium, and Low findings remain visible in the GitHub Actions summary and
the retained OSV JSON artifact, but do not block CI. Missing, malformed, or
unparsable scanner output fails the job closed.

The evaluator uses OSV-Scanner's `packages[].groups[].max_severity` number,
which the scanner calculates from the advisory CVSS vectors in
`packages[].vulnerabilities[].severity[].score`. A score of at least 9.0 is
Critical; 7.0--8.9 is High; 4.0--6.9 is Medium; and 0.0--3.9 is Low. A finding
whose group lacks a usable numeric CVSS score is reported separately as
non-blocking rather than being silently treated as Critical. The evaluator still
rejects a result whose structure is not the expected OSV result schema.

The workflow records the scanner's actual exit code. Only a clean `0` exit with
no findings or a `1` exit with one or more findings can reach severity
evaluation; every other code, result mismatch, pre-existing result file, or
missing/malformed output fails closed.

The scan covers:

- the complete pnpm resolution in `pnpm-lock.yaml`;
- a resolved Maven inventory, including test scope, generated from
  `dependency:tree` in OSV custom-lock format;
- the Python runtime lock, `apps/ai-service/pylock.toml`; and
- the Python runtime-plus-test lock, `apps/ai-service/pylock.test.toml`.

The pnpm 12 lockfile is split into environment and project documents by
`scripts/osv/split-pnpm-lockfile.sh`. Both documents are explicit scanner
inputs; an unexpected lockfile layout fails before scanning.

The baseline verified locally with OSV-Scanner 2.3.8 on 2026-08-01 is:

- unaccepted findings: **0**;
- accepted, documented findings: **0**; and
- raw findings: **0**.

The GitHub Actions job prints a Critical/High/Medium/Low breakdown and advisory
IDs, reports findings with missing or unusable severity separately, adds the
accepted-risk count and expiry to the job summary, and retains the raw JSON
result for three days. A GitHub run is still required before treating the remote
baseline as verified.

## Accepted risk

No dependency vulnerabilities are currently accepted. `osv-scanner.toml`
contains no ignored advisory IDs, but reviewed exceptions remain supported when
they have an advisory ID and `ignoreUntil` expiry. Exceptions are still removed
by OSV-Scanner before this evaluator runs and must not be extended merely to
keep CI green.

## Temporary JavaScript overrides

The root `packageManager` pins pnpm 12.3.4. `pnpm-workspace.yaml` explicitly
requires registry releases to be at least 1440 minutes (24 hours) old, sets
`minimumReleaseAgeStrict: true` to reject unsatisfied age constraints, and sets
`minimumReleaseAgeIgnoreMissingTime: false` to reject undated releases. There
are no freshness exclusions; frozen installs retain pnpm's lockfile policy
verification. `allowBuilds` is the build-script policy, with scripts for
`sharp` and `unrs-resolver` explicitly disabled.

The following overrides retain the reviewed fixed versions. Convergence
selectors (`package@`) pin only dependency edges whose declared semver ranges
accept the target. `@babel/core@`, `form-data@`, `nanoid@`, and `postcss@` use
this form; the two old PostCSS selectors converge on one target. The separate
`brace-expansion` and `js-yaml` selectors retain fixes for two major versions,
which a single convergence selector cannot express. `browserslist@<=4.28.6`
retains its explicit vulnerable range. The `sharp` pin remains unconditional as
a reviewed security baseline. Changing that reviewed target is a separate
dependency update.

These security overrides are temporary:

| Override | Advisory | Introduced by | Remove when |
| --- | --- | --- | --- |
| `@babel/core@` -> 7.29.6 | `GHSA-4x5r-pxfx-6jf8` | Jest/ts-jest | The supported upstream graph resolves 7.29.6 or later without the override. |
| `brace-expansion` 1.1.14 -> 1.1.18 and 5.0.6 -> 5.0.9 | `GHSA-mh99-v99m-4gvg`, `GHSA-rgw5-rvv9-x895` | `minimatch` 3.x and 10.x through ESLint and Jest | Both supported `minimatch` branches resolve their corresponding fixed `brace-expansion` release. |
| `form-data@` -> 4.0.6 | `GHSA-hmw2-7cc7-3qxx` | `jest-environment-jsdom` -> `jsdom` | jsdom's supported graph resolves 4.0.6 or later. |
| `baseline-browser-mapping` 2.10.32 -> 2.11.21 | `GHSA-w5vr-8v7q-w6rv` | Next/browserslist | The supported graph resolves 2.11.21 or later without the override. |
| `js-yaml` 3.14.2 -> 3.15.2 | `GHSA-h67p-54hq-rp68`, `GHSA-5p4m-2wfm-xmqj`, `GHSA-2883-xcg3-v3hh` | Jest coverage -> `@istanbuljs/load-nyc-config` | The Jest coverage graph resolves a fixed 3.x release or removes it. |
| `js-yaml` 4.1.1 -> 4.3.2 | `GHSA-h67p-54hq-rp68`, `GHSA-5p4m-2wfm-xmqj`, `GHSA-2883-xcg3-v3hh` | ESLint | The supported ESLint graph resolves 4.3.2 or later. |
| `nanoid@` -> 3.3.18 | `GHSA-28wg-ghj8-5hjv`, `GHSA-2v37-7h3g-55p8` | PostCSS | The supported PostCSS graph resolves 3.3.18 or later without the override. |
| `postcss@` -> 8.5.23 (replaces the 8.4.31 and 8.5.15 selectors) | `GHSA-qx2v-qp2m-jg93`, `GHSA-r28c-9q8g-f849`, `GHSA-fxqj-rqcc-2cmp` | Next and Tailwind CSS | Both supported requesters resolve 8.5.23 or later. |
| `sharp` -> 0.35.4 | `GHSA-f88m-g3jw-g9cj` | Next | The supported Next graph resolves a fixed Sharp release without the override. |

After regeneration, a frozen pnpm install resolved each fixed version. Frontend
lint, typecheck, all 42 Jest suites (270 tests), and the Next production build
passed in the CI Node/pnpm toolchain. Remove an override only after the same
checks and the Critical-only OSV policy passes without it.

## Temporary Maven dependency management

Spring Boot was updated from 3.5.14 to the latest compatible 3.5 patch,
3.5.16. Exact fixed versions are temporarily managed for Jackson, Logback,
Log4j, Netty, Apache Commons Lang, PostgreSQL JDBC, Spring Security, Tomcat, and
Apache Commons Compress. These dependencies enter through Spring Boot starters,
Lettuce/Netty, JJWT/Jackson, the PostgreSQL driver, and Testcontainers. In
particular, the PR #38 baseline pins Netty at `4.1.137.Final`; Spring Boot 3.5.16
otherwise resolves Log4j 2.24.3 through `spring-boot-starter-logging`, and
Testcontainers 1.21.4 otherwise resolves Commons Compress 1.24.0.

The same PR #38 baseline pins the Core runtime image's Alpine `libexpat` package
at `2.8.4-r0`. Its JavaScript overrides pin Sharp at `0.35.4`, `js-yaml` 3.x at
`3.15.2`, `js-yaml` 4.x at `4.3.2`, and `baseline-browser-mapping` at `2.11.21`.
These are documented here so dependency updates keep the source and runtime
baselines aligned.

Remove each temporary version property or management entry when a supported
Spring Boot/Testcontainers baseline manages at least the fixed version, and only
after the complete Maven suite and OSV scan pass. The resolved graph was checked
at the pinned versions. The complete 315-test backend suite passed with its
configured Docker/Testcontainers services.

## Python lock generation

The Python locks target Linux and Python 3.11 and are generated with pinned
`pip==26.1.2`:

```bash
cd apps/ai-service
bash scripts/lock-dependencies.sh
```

The script is the explicit developer dependency-maintenance command. It
regenerates both locks, removes only the local-project entry, and updates
separate SHA-256 manifests covering `pyproject.toml` and the corresponding
lock. A lock-regeneration change must commit both locks and both manifests.

Ordinary pull-request CI does not regenerate locks or resolve a new graph
against live PyPI. It verifies both committed manifests with `sha256sum
--check`, installs pinned `pip==26.1.2`, and installs third-party test
dependencies from the committed `pylock.test.toml`. Because each manifest also
covers `pyproject.toml`, changing the project dependency inputs without
regenerating the corresponding locks fails these checks. The production image
independently verifies and installs the committed production lock,
`pylock.toml`.

The test dependency range now requires pytest 9.0.3 or later within major
version 9, resolving `GHSA-6w46-j5rx-g56g`; the generated test lock currently
resolves pytest 9.1.1.

Normal service startup and tests do not install the local project. If packaging
verification requires an installation, first install all third-party build and
runtime dependencies from the applicable lock, then use:

```bash
python -m pip install --no-deps --no-build-isolation .
```

This prevents local-project installation from re-resolving third-party
dependencies.
