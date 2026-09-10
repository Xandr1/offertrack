# GCP staging runtime and deployment

This document is the operational contract for the Terraform root at
`infra/terraform/staging` and the `Deploy staging` GitHub Actions workflow. The
configuration defines the complete staging runtime, but this repository change
does not itself apply Terraform, create secret versions, provision database
roles, execute a migration, publish an image, or deploy to Google Cloud.

## Authoritative environment

| Setting                      | Value                             |
| ---------------------------- | --------------------------------- |
| Project ID                   | `offertrack-staging`              |
| Project number               | `765846644391`                    |
| Region                       | `europe-central2`                 |
| Zone                         | `europe-central2-a`               |
| Terraform state bucket       | `offertrack-staging-tfstate-2908` |
| State prefix                 | `staging`                         |
| Artifact Registry repository | `offertrack`                      |

The state bucket remains an external bootstrap resource. Terraform does not
create, import, grant IAM on, or delete it.

## Runtime topology

```text
Browser
  ├── HTTPS -> offertrack-stg-web  (public, Next.js)
  └── HTTPS -> offertrack-stg-core (public edge, application auth/CSRF/CORS)
                    │
                    ├── private-range Direct VPC egress
                    │     ├── Cloud SQL PostgreSQL private IP
                    │     └── Memorystore private IP + verified TLS
                    │
                    └── HTTPS + Google ID token + X-Internal-Api-Key
                          -> offertrack-stg-ai (IAM protected)

offertrack-stg-migrate (one-shot job)
  └── private-range Direct VPC egress -> Cloud SQL private IP
```

The Core and Web services are browser reachable because Terraform sets
Cloud Run's `invoker_iam_disabled` service field. Public invocation does not
bypass Spring Security:
the existing JWT HttpOnly cookie, OAuth, CSRF, CORS, rate limiting, and user
isolation remain authoritative. AI has no unauthenticated binding. Only the Core
runtime and staging deployer can invoke it.

The public liveness and readiness endpoints are intentionally lightweight. The
detail-free `/actuator/health/dependencies` check requires the separately
pinned `offertrack-stg-dependency-health-key` value in
`X-Dependency-Health-Key`. Core and the deployer are its only accessors. The
deployer cannot access the AI internal key. Deployment checks resolve the
dependency-health version from the exact Core revision, keep the value in the
single shell process that needs it, and never write it to `$GITHUB_ENV`, print
it, or print health response details.

Staging deployment is disabled until the GitHub repository variable
`STAGING_DEPLOY_ENABLED=true` is explicitly set. It is not an environment-level
variable. Keep it disabled until
foundation Terraform is applied, secret versions exist, database users are
provisioned, seed images are published, and the Cloud Run services and
migration job have been created successfully.

Core and the migration job use Direct VPC egress to
`offertrack-staging-subnet`. Egress mode is `PRIVATE_RANGES_ONLY`, so traffic to
Cloud SQL and Memorystore uses the VPC while unrelated public egress does not.
AI and Web have no VPC attachment.

## Cloud Run resources

| Resource                      | Identity                  | Access                  | Scale and concurrency                 | Health/startup              |
| ----------------------------- | ------------------------- | ----------------------- | ------------------------------------- | --------------------------- |
| `offertrack-stg-ai` service   | `offertrack-stg-ai`       | IAM only                | min 0, max 2, concurrency 4           | `/health`                   |
| `offertrack-stg-core` service | `offertrack-stg-core`     | public edge             | min 0, max 2, concurrency 20          | actuator liveness/readiness |
| `offertrack-stg-migrate` job  | `offertrack-stg-migrator` | deployer execution only | one task, parallelism 1, zero retries | process exit status         |
| `offertrack-stg-web` service  | `offertrack-stg-web`      | public                  | min 0, max 2, concurrency 40          | `/login`                    |

All services use second-generation execution, one CPU, conservative staging
memory, startup CPU boost, and bounded maximum instances. Scale-to-zero is
enabled. The Core Hikari pool is explicitly limited to five connections, so the
configured maximum is ten pooled application connections across two instances.
The migration job has an independent connection and one task per execution;
the workflow concurrency group serializes deployments. Operators must not start
a second manual migration execution concurrently.

The service URLs are deterministic for the fixed service names and project
number:

```text
https://offertrack-stg-ai-765846644391.europe-central2.run.app
https://offertrack-stg-core-765846644391.europe-central2.run.app
https://offertrack-stg-web-765846644391.europe-central2.run.app
```

Terraform exposes these canonical URLs and the API-reported URIs. Deployment
automation resolves the API-reported URI and requires it to equal the canonical
value before continuing. The AI URL is also the Google ID-token audience. The
Core URL is compiled into the Web image, and the Web URL is the exact Core CORS
origin and redirect target.

## Terraform and deployment ownership

Terraform owns:

- Cloud Run service/job shape, identities, resources, scaling, probes, ingress,
  Direct VPC egress, runtime environment, and Secret Manager references;
- Artifact Registry immutability;
- service, job, repository, secret, and service-account IAM; and
- the initial image digest used to create each resource.

The deployment workflow owns only application revisions:

- it updates the three service images and the migration job image by digest;
- it temporarily creates a zero-traffic `candidate` tag;
- it executes the already-defined migration job; and
- after candidate checks, it restores the Terraform-shaped `100% latest`
  traffic configuration and removes the temporary tag.

The current staging trust model rebuilds production images during deployment
from the exact CI-approved commit, then records and deploys the resulting
immutable digests. Locked dependencies and deterministic production build
inputs are retained, but the digest is not literally the CI-scanned artifact.
Exact CI artifact promotion is intentionally deferred to a future production
deployment workflow; production should promote the scanned artifact directly.

Terraform ignores only the four container image fields. It does not ignore
runtime configuration, IAM, networking, probes, resources, or scaling. This is
the intentional source-of-truth boundary: normal image deployments do not
rewrite Terraform-managed configuration, and a later Terraform apply does not
roll a service back to its seed image.

## Redis TLS trust

Memorystore uses `SERVER_AUTHENTICATION` with Redis AUTH disabled. Terraform
passes the host and returned port to Core, enables Lettuce TLS explicitly, and
joins every certificate currently returned by `server_ca_certs` into
`REDIS_TLS_CA_CERTIFICATES`.

Spring Boot builds the named `offertrack-redis` PEM SSL bundle exclusively for
the Redis client. It does not mutate the global JVM trust store. Protected
startup validates that TLS is enabled, that the expected bundle is selected,
and that the trust value contains only one or more currently valid X.509 CA
certificates capable of initializing an isolated trust manager. Empty,
malformed, expired, or end-entity-only material fails startup. Hostname and
certificate-chain verification remain enabled.

For a Memorystore CA rotation:

1. Refresh Terraform against the instance while both old and new active CAs are
   present.
2. Review the Core environment diff; it may contain public CA material but no
   secret data.
3. Apply before the old CA expires so Cloud Run creates a Core revision trusting
   the full active set.
4. Run dependency health, then repeat after Google removes the retired CA.

Core retains the existing two-second connect/command timeouts and protected
rate limiting remains fail closed.

Every protected SMTP runtime requires STARTTLS with both
`mail.smtp.starttls.enable=true` and `mail.smtp.starttls.required=true`, whether
SMTP authentication is used or the relay accepts anonymous clients. Username
and password validation remains conditional: both may be absent, but a partial
credential pair fails startup. Connection, read, and write timeouts are
bounded. Local development retains its unauthenticated, non-TLS
Mailpit-compatible defaults.

## Database roles and migrations

Terraform deliberately defines neither `google_sql_user` nor any password
value. The credential sources remain:

- `offertrack-stg-db-app-password` for `offertrack_app`; and
- `offertrack-stg-db-migrator-password` for `offertrack_migrator`.

`scripts/staging/provision-database-users.sh` is the deterministic privileged
bootstrap/reconciliation operation. It reads two explicitly pinned secret
versions into restrictive temporary files and process memory, never command
arguments. Its SQL transaction:

- creates or rotates both login roles;
- removes superuser, role/database creation, replication, inheritance, and
  row-level-security bypass capability;
- makes the migrator own `public` and any existing public tables/sequences;
- gives the migrator the schema ownership needed by Flyway; and
- gives the application role only schema usage, table DML, and sequence
  read/usage, including matching migrator default privileges.

The application role receives no schema creation or object ownership. The
migrator is not used by the server service.

Initial role creation necessarily needs a separately authorized database
administrator. From a trusted machine that can reach the VPC, start a Cloud SQL
Auth Proxy using private IP and an approved Google identity. Configure the
bootstrap PostgreSQL administrator password through the approved Cloud SQL
administrator/password-manager procedure; do not put it in Terraform, shell
history, or a command argument. With the proxy on `127.0.0.1:5432`, run:

```bash
bash scripts/staging/provision-database-users.sh \
  --app-password-version 1 \
  --migrator-password-version 1
```

`psql` securely prompts for bootstrap authentication when required. The
operator needs access to only the two DB password secret versions plus the
separate database bootstrap authority. The staging deployer does not receive
either permission.

The migration job uses the same immutable Core production image as the server,
with only these application settings: `OFFERTRACK_RUN_MODE=migrate`, protected
profile, database URL, migration user, and the pinned migration-password secret.
It starts no HTTP server or Redis, SMTP, OAuth, JWT, security, or AI components.
Flyway success (including an already-current schema) exits zero; validation,
connection, or migration failure exits nonzero. Cloud Run retries are zero, so
one workflow invocation creates one controlled attempt.

## Secret versions and rotation

Terraform owns these Secret Manager containers and exact accessor bindings, but
never versions or values:

| Secret                                 |            Minimum application contract | Consumers                      |
| -------------------------------------- | --------------------------------------: | ------------------------------ |
| `offertrack-stg-jwt-secret`            |                          32 UTF-8 bytes | Core                           |
| `offertrack-stg-oauth-cookie-secret`   |             32 bytes, distinct from JWT | Core                           |
| `offertrack-stg-rate-limit-key-secret` |       32 bytes, distinct from JWT/OAuth | Core                           |
| `offertrack-stg-dependency-health-key` | 32 bytes, distinct from application keys | Core and deployer              |
| `offertrack-stg-db-app-password`       |           operational policy: 32+ bytes | Core and DB bootstrap          |
| `offertrack-stg-db-migrator-password`  | operational policy: 32+ bytes, distinct | migration job and DB bootstrap |
| `offertrack-stg-google-client-secret`  |                          16 UTF-8 bytes | Core                           |
| `offertrack-stg-ai-internal-key`       |                          32 UTF-8 bytes | Core and AI                    |
| `offertrack-stg-openai-api-key`        |                      valid provider key | AI                             |
| `offertrack-stg-smtp-password`         |                          12 UTF-8 bytes | Core                           |

Create a value without echoing it or passing it in an argument:

```bash
set +x
read -r -s -p 'Secret value: ' OFFERTRACK_SECRET_VALUE
printf '\n' >&2
printf '%s' "$OFFERTRACK_SECRET_VALUE" \
  | gcloud secrets versions add SECRET_NAME \
      --project offertrack-staging \
      --data-file=- \
      --quiet
unset OFFERTRACK_SECRET_VALUE
```

Repeat with each exact secret name. Prefer a password manager or cryptographic
generator feeding stdin directly. Never use `echo`, workflow secrets, Terraform
variables, `-var`, `.tfvars`, or command-line flags for secret data.

Cloud Run references numeric versions from Terraform variable
`secret_versions`; the variable has no hardcoded version default and `latest`
is intentionally not used. It is the single non-secret source of truth for
runtime pins. Deployment health checks read the dependency-health version from
the exact Core revision instead of duplicating a version number. Rotation is:

1. add a new Secret Manager version;
2. for either database password, run the role reconciliation script with the
   new pinned versions;
3. change only the relevant numeric `secret_versions` value in an ignored local
   variable file;
4. review and apply Terraform to create affected Cloud Run revisions; and
5. verify health before disabling the old version.

JWT, OAuth cookie, rate-limit, Google OAuth, application DB, and SMTP rotation
requires a new Core revision. OpenAI rotation requires AI. The AI internal key
requires coordinated AI and Core revisions; perform it in a staging maintenance
window because the application accepts one internal key at a time. Migration DB
rotation requires the job definition to update. Application DB password rotation
also needs the database role and Core revision coordinated. Secret environment
values are resolved when an instance starts, so merely adding a version does
nothing while numeric references remain unchanged.

Dependency-health rotation requires updating its numeric `secret_versions`
entry and applying Terraform so Core receives the new pin. The next deployment
and smoke run automatically read that same pin from the Core revision; no
workflow constant changes. Existing Core instances/revisions keep the old
version until replaced or restarted under an updated template.

Changing JWT or OAuth-cookie keys invalidates existing corresponding browser
state. Changing the rate-limit key starts a fresh logical counter namespace.

## IAM and WIF

Runtime access is narrowly scoped:

| Identity | Grants                                                                                                                                              |
| -------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Core     | its eight secret containers, including the dedicated dependency-health key; invoke only AI                                                            |
| AI       | OpenAI and shared internal-key containers                                                                                                           |
| Migrator | migration DB password container                                                                                                                     |
| Web      | no secret access                                                                                                                                    |
| Deployer | dedicated dependency-health secret only; repository writer; developer on exactly three services and one job; job execution; AI invocation; `serviceAccountUser` on the four runtime accounts |

The deployer has no project-wide Cloud Run or Secret Manager role, no access to
the AI internal key or other runtime secrets, and no Owner, Editor,
service-account administration, or Terraform/state authority. Its one Secret
Manager accessor binding is resource-level on the dependency-health container.
Terraform uses the separate privileged `offertrack-stg-infra` account and
receives `roles/run.admin` only because it owns the service/job definitions and
IAM.

GitHub authenticates without a key through provider:

```text
projects/765846644391/locations/global/workloadIdentityPools/offertrack-github/providers/github
```

The provider requires immutable repository and owner IDs, the exact repository,
and `refs/heads/main`. Only the repository principal can impersonate
`offertrack-stg-deployer`; GitHub cannot impersonate the infra identity.

## Image publication

Artifact Registry tag immutability is enabled. The production build script can
select components while retaining the existing all-image default:

```bash
# Core and AI do not need staging runtime values.
bash scripts/containers/build-images.sh \
  --components core-api,ai-service \
  --tag "$GIT_SHA"

# Web must be built only after resolving the exact Core URL.
bash scripts/containers/build-images.sh \
  --components web \
  --app-env staging \
  --next-public-api-url \
    https://offertrack-stg-core-765846644391.europe-central2.run.app \
  --tag "$GIT_SHA"
```

Registry tags are the full 40-character Git commit SHA. `latest` is never
published or deployed. During deployment, the workflow resolves each tag to
`LOCATION-docker.pkg.dev/PROJECT/offertrack/COMPONENT@sha256:DIGEST` and gives
Cloud Run only that digest reference. A rerun reuses an existing immutable tag.
The exact image plus the commit-bearing Cloud Run revision suffix makes each
deployment traceable.

## Deployment workflow and rollback

`.github/workflows/deploy-staging.yml` is manual-only through
`workflow_dispatch`. The deploy job accepts only `refs/heads/main` and requires
the `STAGING_DEPLOY_ENABLED=true` repository-variable kill switch. Before cloud
authentication or deployment, the GitHub API must find a successful
push-triggered `CI` run on `main` for the exact selected `github.sha`; otherwise
the workflow fails closed. The workflow checks out and deploys that exact SHA.

The order is fixed:

1. authenticate through WIF as the deployer and configure Artifact Registry;
2. rebuild the production Core and AI images and publish immutable SHA tags;
3. resolve both digests;
4. create AI at zero traffic, call its IAM-authenticated health endpoint, then
   promote it;
5. update the migration job definition to the Core digest;
6. execute the job once and wait for a successful result;
7. create Core at zero traffic, resolve the dedicated health-secret version
   from that revision, and require readiness plus authenticated dependency
   health without exporting the credential globally;
8. promote Core and resolve its actual canonical URI;
9. build Web with that exact URI, publish and resolve its digest;
10. create Web at zero traffic, verify `/login`, then promote it; and
11. run bounded post-rollout checks and report commit, revisions, job, and image
    digests without protected environment output.

An AI failure stops before migration. A migration failure stops before Core and
Web. Core or Web candidates receive no traffic until ready and checked, leaving
the prior serving revision untouched on candidate failure. The workflow never
runs a reverse migration or destructive schema rollback. After promotion, a
smoke failure fails the workflow visibly but retains Cloud Run revision history
and immutable images for a manual traffic rollback. Database changes must be
forward/backward compatible with the previously serving Core because schema is
not rolled back automatically.

Manual application rollback selects an older known-good service revision in
Cloud Run and moves traffic to it. Do not change its image reference and do not
roll the schema backward automatically. Restore Terraform-shaped `100% latest`
traffic before a later routine deployment, or intentionally reconcile the
manual rollback state first.

## Smoke and acceptance checks

`scripts/staging/smoke.sh` performs bounded HTTPS and control-plane checks:

- AI `/health` with a deployer Google ID token;
- Core liveness and readiness;
- the detail-free Core `dependencies` group, which proves PostgreSQL, verified
  TLS Redis, and Core-to-AI Google ID token plus `X-Internal-Api-Key`;
- Web `/login`;
- latest-ready revision names and resolved revision image digests; and
- the migration job's Core image digest.

It fails rather than skipping a downstream check. It reads no application
secret. Browser OAuth redirect, secure-cookie, and CSRF acceptance is a separate
real-browser staging test after Google OAuth callback registration.

## Terraform inputs, outputs, and validation

`enable_cloud_run_runtime` defaults to `false`, allowing an empty-project
foundation apply to create the repository, secret containers, database,
Redis, identities, and WIF without inventing image or secret values. Setting it
to `true` requires three valid `initial_images` digests. Runtime creation also
requires non-secret `google_oauth_client_id`, `smtp_host`, `smtp_username`,
`mail_from`, and numeric `secret_versions`. Keep environment-specific values in
an ignored `*.local.tfvars` file. Never put secret values there.

Useful non-secret outputs include canonical and API-reported service URLs, the
migration job name, Cloud Run resource names, deployer/runtime identities,
repository URL, WIF provider, and seed image digests. Private IP/CA outputs stay
marked sensitive to avoid routine console noise; no output is a credential.

Run the exact static checks:

```bash
terraform -chdir=infra/terraform/staging fmt -check
terraform -chdir=infra/terraform/staging init -backend=false -input=false -lockfile=readonly
terraform -chdir=infra/terraform/staging validate -no-color
python -m unittest discover \
  --start-directory scripts/staging/tests \
  --pattern 'test_*.py' \
  --verbose
```

A real plan/apply additionally needs the separately authorized infra identity
and external state-bucket access. CI and the staging deployer do not run
Terraform apply.

## First deployment prerequisites

Before enabling the deployment workflow:

1. Confirm the external state bucket protections and infra identity access.
2. Review current GCP pricing and the Terraform plan for the exact project.
3. Apply with `enable_cloud_run_runtime = false` using the approved infra
   identity. This creates or reconciles the foundation, deployer publication
   rights, secret containers, and database without Cloud Run resources.
4. Add all ten secret versions through the non-logging process above.
5. Run the database role bootstrap through an authorized private path.
6. Build and publish seed Core/AI/Web images by full commit SHA. The Web seed
   uses the deterministic Core URL above. Record their digests.
7. Set `enable_cloud_run_runtime = true`, supply those digests and pinned secret
   version numbers, and review the full Terraform plan that creates the three
   services and migration job.
8. Execute `offertrack-stg-migrate` and require success before accepting Core or
   Web as usable. A first environment has no prior application revision to
   preserve; subsequent releases must use the workflow.
9. In Google OAuth, register the exact callback
   `https://offertrack-stg-core-765846644391.europe-central2.run.app/login/oauth2/code/google`
   and the exact staging Web origin where the OAuth client requires it.
10. Run the workflow or equivalent candidate rollout once, then complete the
    real-browser OAuth/cookie/CSRF acceptance check.

Do not claim a deployment until Terraform apply, role bootstrap, migration job,
service rollout, and real staging checks have actually run.
