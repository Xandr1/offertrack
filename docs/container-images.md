# Production container images

Checkpoint 1 provides three repeatable production images and a bounded,
production-like container smoke suite. It does not define a production
deployment topology or production secrets.

## Prerequisites and platform

Run the scripts from the repository root in Bash. On Windows, use Git Bash.
The build requires:

- Docker Engine with Docker Compose v2 and Buildx;
- a Java 21 JDK on the host, including the `jar` tool;
- Node.js for Web build-input validation; and
- network access for the Maven wrapper and the pinned container bases when
  they are not already cached.

The smoke suite additionally requires Python 3 and `curl` on the host.

All application images and the PostgreSQL and Redis smoke services target
`linux/amd64`. Builds use `docker buildx build --platform linux/amd64 --load`,
and the scripts reject a loaded image whose inspected platform is not
`linux/amd64`. An ARM host therefore needs Docker's amd64 emulation; these
images are not multi-architecture images.

## Build commands

Build and load all three images with explicit Web build inputs:

```bash
bash scripts/containers/build-images.sh \
  --app-env production \
  --next-public-api-url https://api.example.com \
  --tag local
```

The result is:

```text
offertrack/web:local
offertrack/core-api:local
offertrack/ai-service:local
```

`--tag` defaults to `local`. `--app-env` is required and accepts exactly
`local`, `development`, `test`, `e2e`, `staging`, or `production`.
`--next-public-api-url` is also required. The validator rejects credentials,
queries, fragments, non-HTTP(S) schemes, and unsafe protected-environment
hosts. Staging and production require HTTPS and a non-local, non-loopback,
non-unspecified host. Invalid or duplicate arguments are rejected before any
Docker command runs.

`APP_ENV` and `NEXT_PUBLIC_API_URL` are public build inputs, not secrets. The
API URL is compiled into browser JavaScript. Neither variable is retained in
the final Web image environment or supplied to its smoke container.

### Core host packaging

The build script starts the pinned PostgreSQL codegen service under a unique
Compose project. Docker assigns its loopback host port, which the script reads
with `docker compose port`. A temporary three-entry Compose env file contains
only the deterministic codegen database name, user, and password; it is passed
with `--env-file`, is never sourced, and the repository `.env` is not used.
Caller `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` values are removed
from codegen Compose invocations so they cannot override that file.

With explicit process-scoped database variables, the script runs:

```text
mvnw clean flyway:migrate package -DskipTests
```

The Maven lifecycle performs jOOQ generation. The script then requires exactly
one application JAR, verifies its Spring Boot launcher, start class, application
class, and embedded libraries, and stages only that JAR into the runtime-only
Java image. A Java 21 JDK is required on the host even though the resulting
image contains only the pinned Java 21 JRE.

## Runtime contracts

The images have these non-root defaults:

| Image | User | Bind and port defaults | PID 1 |
| --- | --- | --- | --- |
| Web | `1000:1000` | `HOSTNAME=0.0.0.0`, `PORT=3000` | `node server.js` |
| Core | `10001:10001` | `server.port=${PORT:${SERVER_PORT:8080}}`, with `SERVER_PORT=8080` in server mode | `java -jar /app/app.jar` |
| AI | `10001:10001` | `HOST=0.0.0.0`, `PORT=8000` | Python launching `uvicorn app.main:app` |

Web also defaults to `NODE_ENV=production` and disables Next telemetry. Its
standalone root is `/app`, with runtime assets at
`/app/apps/web/public` and `/app/apps/web/.next/static`; its working directory
is `/app/apps/web`. The runtime stage does not contain the builder dependency
store.

The same Core image supports `OFFERTRACK_RUN_MODE=server` (the default) and the
one-shot `OFFERTRACK_RUN_MODE=migrate` path. The entrypoint and non-root user do
not change between modes. Core server mode enables graceful shutdown. A shutdown
phase defaults to 10 seconds via
`SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE`; `PORT` overrides
`SERVER_PORT`, which overrides port 8080. The E2E profile uses the same fallback
shape with 18080 as its final default.

Local and smoke containers set `AI_SERVICE_AUTH_MODE=internal-key` with an empty
`AI_SERVICE_AUDIENCE`. A future protected Cloud Run container will use
`AI_SERVICE_AUTH_MODE=google-id-token` and matching root HTTPS values for
`AI_SERVICE_BASE_URL` and `AI_SERVICE_AUDIENCE`. ADC supplies the Google
identity at request time, while `AI_SERVICE_INTERNAL_API_KEY` remains required.
No static GCP key file belongs in the image or runtime configuration. Cloud Run
IAM remains part of the later infrastructure milestone.

Migration mode accepts only `OFFERTRACK_RUN_MODE=migrate`, `DATABASE_URL`,
`DB_USER`, and `DB_PASSWORD`. It runs embedded Flyway migrations and exits,
without starting Spring or an HTTP listener. `SPRING_FLYWAY_ENABLED` controls
only automatic server-mode migration; direct migration mode ignores it.

AI installs production dependencies into `/opt/venv` and copies only that
virtual environment and application code into its runtime stage. The builder's
`/usr/local` is not copied.

## Container smoke suite

Build and run the complete suite once:

```bash
bash scripts/containers/run-smoke.sh --tag local
```

To reuse an already loaded set of exact images:

```bash
bash scripts/containers/run-smoke.sh --no-build --tag local
```

Normal mode supplies `APP_ENV=e2e` and
`NEXT_PUBLIC_API_URL=http://127.0.0.1:18081` to the image build. `--no-build`
does not build or pull application images: it requires all three exact local
application image references and verifies their `linux/amd64` architecture
before Compose starts. Docker may still pull the PostgreSQL and Redis images by
their committed immutable digests when those infrastructure images are absent.

The deterministic smoke endpoints are:

| Service | Container port | Loopback host endpoint |
| --- | ---: | --- |
| Web | 13001 | `http://127.0.0.1:13001` |
| Core | 18081 | `http://127.0.0.1:18081` |
| AI | 18001 | `http://127.0.0.1:18001` |
| PostgreSQL | 5432 | `127.0.0.1:15433` |
| Redis | 6379 | `127.0.0.1:56380` |

The Compose file contains fixed smoke-only database credentials, cryptographic
placeholders, URLs, CORS and cookie settings, AI limits and internal key, Redis
timeouts, and non-routable mail settings. They must never be reused as
production credentials. Application root filesystems are read-only, Linux
capabilities are dropped, `no-new-privileges` is enabled, and bounded tmpfs
mounts provide `/tmp` plus the writable Next.js cache.

The suite waits for infrastructure and application readiness, verifies Flyway,
and seeds one dedicated verified user. Before starting the application services,
it starts a fresh PostgreSQL container, confirms that Flyway history is absent,
and runs the exact Core image in migration mode twice. The first run verifies
representative schema objects and captures ordered immutable
`flyway_schema_history` fields; the second must exit successfully with byte-for-byte
identical history and schema snapshots. Migration logs are retained only as
sanitized failure diagnostics and are not parsed to establish idempotency. The
one-shot container has no published port and must be removed before the normal
smoke services start.

The full suite then performs bounded CORS, CSRF and
login, Core-to-AI SSRF, PostgreSQL create/restart/read persistence, health,
non-root, PID 1, runtime-content, and SIGTERM checks. The Core readiness view
that exposes database and Redis components exists only in the
`container-smoke` profile.

Web production-output checks require:

- `/login` to return the expected page;
- a dynamically discovered JavaScript asset to contain the baked Core URL;
- `/offertrack-logo.png` to return a non-empty PNG;
- `/_next/image` to return a non-empty image while exercising the writable
  cache; and
- `/login` to remain healthy after the Web container restarts.

The successful Core-to-AI request shows that the Redis-backed limiter path,
internal AI authentication and networking, and SSRF rejection are operational.
It does not prove Redis fail-closed behavior during an outage; that behavior
remains covered by the existing focused tests.

Before its first Compose mutation, each script installs cleanup traps scoped to
its validated, unique Compose project and temporary directory. Cleanup removes
only that project's containers, named volumes, and orphans, plus its exact
validated temporary directory. CI repeats exact-project cleanup in an
`if: always()` step. It does not enumerate projects, prune Docker, or delete a
broader temporary tree. On smoke failure, raw bounded log tails stay only in
the temporary root; only sanitized diagnostics are written under
`test-results/container-smoke` for upload.

## Standalone E2E startup

The existing E2E flow builds the same Next.js standalone form. After `next
build`, it removes only stale copies at the two asset destinations, then copies:

```text
apps/web/public/.       -> apps/web/.next/standalone/apps/web/public/
apps/web/.next/static/. -> apps/web/.next/standalone/apps/web/.next/static/
```

It starts `server.js` from `apps/web/.next/standalone/apps/web` with
process-scoped `PORT=13000` and `HOSTNAME=127.0.0.1`. No global `PORT` is set,
and an ambient caller `PORT` is removed from the Core process, so the Core E2E
service continues to use `SERVER_PORT=18080`.

## Known limitations

- `container-smoke` is deliberately non-protected and loopback-only. It does
  not verify TLS termination, trusted ingress headers, secure cookies, or real
  domains; see [production configuration](production-configuration.md) for
  those protected-environment contracts.
- The Web image embeds its public API URL. The smoke image is therefore
  smoke-specific and is not a future staging artifact.
- No smoke request calls Google, OpenAI, or a real external job page.
- This checkpoint creates no GCP resources, Terraform, registry, Cloud Run,
  Cloud SQL, managed Redis, Secret Manager integration, deployment identity,
  custom domain, or production environment.
- The Core image now provides the one-shot migration runtime contract, but no
  scheduled Cloud Run job or other migration infrastructure is created. There
  is still no image publishing, signing, provenance, or multi-platform
  publishing. Local output is limited to `linux/amd64`.
- SSRF DNS-rebinding defenses and refresh-token/server-side session work remain
  deferred under their existing ADRs.

## Container vulnerability policy

CI independently scans each exact local image with Trivy 0.70.0. Scans cover OS
and library vulnerabilities at HIGH and CRITICAL severity, include unfixed
findings, emit JSON, and use a five-minute scanner timeout. Each scan is allowed
to finish independently so one scanner failure cannot suppress the other two,
and the smoke suite still runs after a successful image build. A shared
evaluator applies the aggregate security result afterward.

The aggregate gate blocks when:

- a scanner fails or is skipped;
- a result is missing, malformed, or schema-invalid; or
- a HIGH or CRITICAL finding has a non-empty `FixedVersion`.

HIGH and CRITICAL findings without a `FixedVersion` are reported separately
but do not block. This is an explicit accepted residual security risk: unfixed
findings remain visible and require review, but cannot be remediated until an
upstream fix exists. They become blocking when Trivy reports a fixed version.
This policy is independent of the repository's blocking OSV dependency scan.

The same evaluator is the local and CI policy entrypoint. After producing the
three Trivy JSON reports with the options above, run the exact `web`,
`core-api`, and `ai-service` scan set. The evaluator derives
`offertrack/<image>:<image-tag>` for each name and requires the report's
`ArtifactName` to match:

```bash
python scripts/containers/evaluate-trivy-results.py \
  --image-tag local \
  --scan web /validated/temp/trivy-web.json success \
  --scan core-api /validated/temp/trivy-core.json success \
  --scan ai-service /validated/temp/trivy-ai.json success
```

Replace each outcome with the scanner's actual outcome; failed or skipped
scans must not be represented as successful.

Raw Trivy JSON and service diagnostics remain in the exact CI temporary root.
The evaluator writes only bounded, sanitized summary data, and CI uploads only
sanitized smoke diagnostics.
