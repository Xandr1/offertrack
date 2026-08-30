# Dependency security baseline

This document covers the source dependency OSV policy. The independent
production-image Trivy policy, including the non-blocking accepted residual
risk for HIGH/CRITICAL findings without an upstream fix, is documented in
[Production container images](container-images.md#container-vulnerability-policy).

OfferTrack runs the pinned OSV-Scanner 2.3.8 on pull requests that change
dependency inputs, weekly, and on demand. The workflow is blocking: malformed or
missing scanner output fails the job, as does any vulnerability not listed in
`osv-scanner.toml` as a reviewed, time-bounded exception.

The scan covers:

- the complete pnpm resolution in `pnpm-lock.yaml`;
- a resolved Maven inventory, including test scope, generated from
  `dependency:tree` in OSV custom-lock format;
- the Python runtime lock, `apps/ai-service/pylock.toml`; and
- the Python runtime-plus-test lock, `apps/ai-service/pylock.test.toml`.

The baseline verified locally with OSV-Scanner 2.3.8 on 2026-08-01 is:

- unaccepted findings: **0**;
- accepted, documented findings: **0**; and
- raw findings: **0**.

The GitHub Actions job prints the unaccepted finding count and advisory IDs,
adds the accepted-risk count and expiry to the job summary, and retains the JSON
result for three days. A blocking GitHub run is still required before treating
the remote baseline as verified.

## Accepted risk

No dependency vulnerabilities are currently accepted. `osv-scanner.toml`
contains no ignored advisory IDs, so every reported vulnerability blocks CI.

## Temporary JavaScript overrides

The following exact-version overrides remediate vulnerable transitive releases.
They are intentionally narrow and temporary:

| Override | Advisory | Introduced by | Remove when |
| --- | --- | --- | --- |
| `@babel/core` 7.29.0 -> 7.29.6 | `GHSA-4x5r-pxfx-6jf8` | Next/styled-jsx peers and Jest/ts-jest | The supported upstream graph resolves 7.29.6 or later without the override. |
| `brace-expansion` 1.1.14 -> 1.1.18 and 5.0.6 -> 5.0.9 | `GHSA-mh99-v99m-4gvg`, `GHSA-rgw5-rvv9-x895` | `minimatch` 3.x and 10.x through ESLint and Jest | Both supported `minimatch` branches resolve their corresponding fixed `brace-expansion` release. |
| `form-data` 4.0.5 -> 4.0.6 | `GHSA-hmw2-7cc7-3qxx` | `jest-environment-jsdom` -> `jsdom` | jsdom's supported graph resolves 4.0.6 or later. |
| `js-yaml` 3.14.2 -> 3.15.1 | `GHSA-h67p-54hq-rp68`, `GHSA-5p4m-2wfm-xmqj` | Jest coverage -> `@istanbuljs/load-nyc-config` | The Jest coverage graph resolves a fixed 3.x release or removes it. |
| `js-yaml` 4.1.1 -> 4.3.1 | `GHSA-h67p-54hq-rp68`, `GHSA-5p4m-2wfm-xmqj` | ESLint | The supported ESLint graph resolves 4.3.1 or later. |
| `nanoid` 3.3.12 -> 3.3.18 | `GHSA-28wg-ghj8-5hjv`, `GHSA-2v37-7h3g-55p8` | PostCSS | The supported PostCSS graph resolves 3.3.18 or later without the override. |
| `postcss` 8.4.31 and 8.5.15 -> 8.5.23 | `GHSA-qx2v-qp2m-jg93`, `GHSA-r28c-9q8g-f849`, `GHSA-fxqj-rqcc-2cmp` | Next 16.2.11 and Tailwind CSS | Both supported requesters resolve 8.5.23 or later. |
| `sharp` -> 0.35.0 | `GHSA-f88m-g3jw-g9cj` | Next 16.2.11 | The supported Next graph resolves a fixed Sharp release without the override. |

After regeneration, a frozen pnpm install resolved each fixed version. Frontend
lint, typecheck, all 42 Jest suites (270 tests), and the Next production build
passed in the CI Node/pnpm toolchain. Remove an override only after the same
checks and the blocking OSV scan pass without it.

## Temporary Maven dependency management

Spring Boot was updated from 3.5.14 to the latest compatible 3.5 patch,
3.5.16. Exact fixed versions are temporarily managed for Jackson, Logback,
Log4j, Netty, Apache Commons Lang, PostgreSQL JDBC, Spring Security, Tomcat, and
Apache Commons Compress. These dependencies enter through Spring Boot starters,
Lettuce/Netty, JJWT/Jackson, the PostgreSQL driver, and Testcontainers. In
particular, Spring Boot 3.5.16 otherwise resolves Log4j 2.24.3 through
`spring-boot-starter-logging`, and Testcontainers 1.21.4 otherwise resolves
Commons Compress 1.24.0.

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
