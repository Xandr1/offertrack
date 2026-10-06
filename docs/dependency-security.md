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
which the scanner calculates from advisory CVSS data. A score of at least 9.0
is Critical; 7.0--8.9 is High; 4.0--6.9 is Medium; and 0.0--3.9 is Low. A
finding whose group lacks a usable numeric CVSS score is reported separately as
non-blocking rather than being silently treated as Critical. The evaluator
validates only the result structure needed to apply this policy.

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
IDs, reports findings with missing or unusable severity separately, and retains
the raw JSON result for three days. A GitHub run is still required before
treating the remote baseline as verified.

## Accepted risk

No dependency vulnerabilities are currently accepted. `osv-scanner.toml`
contains no ignored advisory IDs, but reviewed exceptions remain supported when
they have an advisory ID and `ignoreUntil` expiry. Exceptions are still removed
by OSV-Scanner before this evaluator runs; the evaluator does not reimplement
ignore or expiry enforcement. Exceptions must not be extended merely to keep CI
green.

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
| `sharp` -> 0.35.5 | `GHSA-f88m-g3jw-g9cj`, `GHSA-wq5f-xc86-pv6w` | Next | The supported Next graph resolves a fixed Sharp release without the override. |

After regeneration, a frozen pnpm install resolved each fixed version. Frontend
lint, typecheck, all 42 Jest suites (270 tests), and the Next production build
passed in the CI Node/pnpm toolchain. Remove an override only after the same
checks and the Critical-only OSV policy passes without it.

## Maven dependency management

Core uses Java 25 and Spring Boot 4.0.8 with its managed Spring Framework 7.0.9,
Spring Security 7.0.7. Jackson uses the security patches 3.1.7 and 2.21.7;
Tomcat uses 11.0.26 / Servlet 6.1, as described below.
The MVC, OAuth client, Flyway, MVC test, security test, and jOOQ test dependencies
use Boot 4's modular starters. Application JSON uses Jackson 3; Jackson annotations
retain their upstream `com.fasterxml.jackson.annotation` package. Flyway and JJWT
still require Jackson 2 internally, without a Jackson 2 Spring mapper
or compatibility auto-configuration.

Java 25 requires explicit configuration of the existing Boot configuration
annotation processor. Formatting uses Spotless 3.10.3 and google-java-format
1.30.0: the previous formatter fails against Java 25's javac APIs, and
[Spotless's migration notes](https://github.com/diffplug/spotless/blob/main/plugin-maven/CHANGES.md)
require google-java-format 1.30.0 or later on Java 25. Existing formatting checks
and unused-import removal remain enabled.

The previous Boot 3 security overrides have been reassessed:

| Previous override | Boot 4.0.8 baseline | Reason for removal |
| --- | --- | --- |
| Jackson BOM 2.21.5 | Jackson 3.1.5; isolated Jackson 2.21.5 | Replaced by the justified security patches for both generations below. |
| Spring Security 6.5.11 | 7.0.7 | Use the supported Boot 4 security stack. |
| Tomcat 10.1.59 | 11.0.24 | Replaced by the justified 11.0.26 security override below. |
| Logback 1.5.35 | 1.5.38 | Managed version exceeds the previous security baseline. |
| Log4j 2.25.5 | 2.25.5 | Managed version matches the previous security baseline. |
| Netty 4.1.137.Final | 4.2.17.Final | Boot manages the current generation; Core has no Netty runtime dependency. |
| Commons Lang 3.18.0 | 3.19.0 | Managed version exceeds the previous security baseline. |
| PostgreSQL JDBC 42.7.13 | 42.7.13 | Managed version matches the previous security baseline. |
| Commons Compress 1.28.0 | Testcontainers 2.0.5 resolves 1.28.0 | The previous Testcontainers 1.x vulnerable transitive version is gone. |

The retained security version overrides are:

- `jackson-bom.version=3.1.7` and `jackson-2-bom.version=2.21.7`. OSV and
  Trivy flag Boot's managed patches for parser and databind denial-of-service
  advisories, including `CVE-2026-89407`, `CVE-2026-89425`, `CVE-2026-68497`,
  `CVE-2026-91776`, and `CVE-2026-91777`. The upstream
  [Jackson 3.1.7](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.1.7)
  and [Jackson 2.21.7](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.7)
  releases contain the fixes. Jackson 2 remains confined to third-party use.
- `tomcat.version=11.0.26`.

The resolved OSV scan flags Boot's 11.0.24 for Critical advisories
`GHSA-9xv2-5v5q-p794` / `CVE-2026-65905`, `GHSA-gcx9-497g-6cp6` /
`CVE-2026-65182`, and `GHSA-h3x4-894j-xpx5` / `CVE-2026-68525`.
[Apache's Tomcat 11 security notes](https://tomcat.apache.org/security-11.html)
document fixes in 11.0.25; 11.0.26 also addresses the subsequent HTTP/2 regression
`CVE-2026-86350`. Remove each override when a supported Boot 4.0 patch manages
at least its fixed baseline and the Maven, smoke, and scan checks pass without it.

The Core runtime image also pins Alpine's `libexpat=2.8.5-r0`, replacing
2.8.4-r0 because Trivy reports the fixable HIGH `CVE-2026-93990`. This retains
the existing policy that blocks fixable HIGH and CRITICAL image findings.
OpenSSL, libcrypto3, and libssl3 move together from 3.5.8-r0 to 3.5.9-r0,
the available [Alpine 3.23 package](https://pkgs.alpinelinux.org/package/v3.23/main/x86_64/openssl)
and [upstream security patch](https://openssl-library.org/news/openssl-3.5-notes/);
the repository no longer serves the previous exact package pin. All runtime
package and base-image pins remain exact.

JJWT 0.13.0 and Google Auth
1.51.0 remain explicit application dependencies not managed by Boot. Source OSV
and production container Trivy gates retain their existing policies; no advisory
ignore or severity-threshold change accompanies this migration.

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
