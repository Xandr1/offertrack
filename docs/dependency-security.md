# Dependency security baseline

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

The baseline verified locally with OSV-Scanner 2.3.8 on 2026-07-13 is:

- unaccepted findings: **0**;
- accepted, documented findings: **1**; and
- raw findings before applying the reviewed exception: **1**.

The GitHub Actions job prints the unaccepted finding count and advisory IDs,
adds the accepted-risk count and expiry to the job summary, and retains the JSON
result for three days. A blocking GitHub run is still required before treating
the remote baseline as verified.

## Accepted risk

### GHSA-5jmj-h7xm-6q6v

- **Package and version:** `com.fasterxml.jackson.core:jackson-databind:2.21.4`.
- **Fix availability:** OSV identifies 2.21.5 as the patched Jackson 2.x release,
  but that version is not published. Moving to the separately fixed Jackson 3.x
  line requires Spring Boot 4 and is not a compatible patch-level dependency
  update for this PR.
- **Reachability:** the vulnerable behavior requires both a property-level
  `@JsonIgnoreProperties` annotation and
  `MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES`. Repository-wide searches
  found neither construct in OfferTrack.
- **Actual OfferTrack impact:** none identified in the current code. If that
  annotation/configuration combination were introduced, an ignored property
  could be accepted through case-variant input, creating a mass-assignment risk.
- **Mitigation:** keep explicit request DTOs and validation; do not enable the
  vulnerable combination; keep the blocking OSV scan; and upgrade to Jackson
  2.21.5 (or a supported later line) as soon as it is released and compatibility
  tests pass.
- **Owner:** OfferTrack backend maintainers.
- **Target resolution date:** 2026-08-31.

The exception expires automatically through `ignoreUntil`. It must not be
extended merely to keep CI green.

## Temporary JavaScript overrides

The following exact-version overrides remediate vulnerable transitive releases.
They are intentionally narrow and temporary:

| Override | Advisory | Introduced by | Remove when |
| --- | --- | --- | --- |
| `@babel/core` 7.29.0 -> 7.29.6 | `GHSA-4x5r-pxfx-6jf8` | Next/styled-jsx peers and Jest/ts-jest | The supported upstream graph resolves 7.29.6 or later without the override. |
| `form-data` 4.0.5 -> 4.0.6 | `GHSA-hmw2-7cc7-3qxx` | `jest-environment-jsdom` -> `jsdom` | jsdom's supported graph resolves 4.0.6 or later. |
| `js-yaml` 3.14.2 -> 3.15.0 | `GHSA-h67p-54hq-rp68` | Jest coverage -> `@istanbuljs/load-nyc-config` | The Jest coverage graph resolves a fixed 3.x release or removes it. |
| `js-yaml` 4.1.1 -> 4.2.0 | `GHSA-h67p-54hq-rp68` | ESLint | The supported ESLint graph resolves 4.2.0 or later. |
| `postcss` 8.4.31 -> 8.5.15 | `GHSA-qx2v-qp2m-jg93` | Next 16.2.6 | The supported Next graph resolves 8.5.15 or later. |

After regeneration, a frozen pnpm install resolved each fixed version. Frontend
lint, typecheck, all 39 Jest suites (233 tests), and the Next production build
passed in the CI Node/pnpm toolchain. Remove an override only after the same
checks and the blocking OSV scan pass without it.

## Temporary Maven dependency management

Spring Boot was updated from 3.5.14 to the latest compatible 3.5 patch,
3.5.16. Exact fixed versions are temporarily managed for Jackson, Logback,
Netty, Apache Commons Lang, PostgreSQL JDBC, Spring Security, Tomcat, and Apache
Commons Compress. These dependencies enter through Spring Boot starters,
Lettuce/Netty, JJWT/Jackson, the PostgreSQL driver, and Testcontainers. In
particular, Testcontainers 1.21.4 otherwise resolves Commons Compress 1.24.0.

Remove each temporary version property or management entry when a supported
Spring Boot/Testcontainers baseline manages at least the fixed version, and only
after the complete Maven suite and OSV scan pass. The resolved graph was checked
at the pinned versions. All 313 backend tests were compatibility-tested: 312 ran
with Docker/Testcontainers, and the context-load test ran separately against an
isolated PostgreSQL service.

## Python lock generation

The Python locks target Linux and Python 3.11 and are generated with pinned
`pip==26.1.2`:

```bash
cd apps/ai-service
bash scripts/lock-dependencies.sh
```

The script regenerates both locks, removes only the local-project entry, and
updates separate SHA-256 manifests covering `pyproject.toml` and the
corresponding lock. CI regenerates all four generated files and requires a clean
diff before installing third-party test dependencies from `pylock.test.toml`.
The production image verifies and installs only `pylock.toml`.

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
