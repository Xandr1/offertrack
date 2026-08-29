# GCP staging infrastructure foundation

This document describes the first Infrastructure-as-Code checkpoint for the
OfferTrack staging environment. The Terraform root defines the foundation only:
it does not deploy applications, publish images, or create secret values.

## Existing bootstrap

The following resources and identifiers already exist outside this Terraform
root and are authoritative:

| Setting | Value |
| --- | --- |
| Project ID | `offertrack-staging` |
| Project number | `765846644391` |
| Region | `europe-central2` |
| Zone | `europe-central2-a` |
| Terraform state bucket | `offertrack-staging-tfstate-2908` |
| State prefix | `staging` |

The state bucket is the intentional bootstrap exception. Terraform uses it as a
GCS backend but does not create, import, configure, grant IAM on, or delete it.
Bucket public-access prevention, uniform access, versioning, soft-delete, and
encryption remain externally managed. A future automation identity must receive
the required bucket object access through a separate bootstrap action.

## Architecture

```text
offertrack-staging
├── Artifact Registry: offertrack
├── offertrack-staging-vpc
│   ├── offertrack-staging-subnet (10.20.0.0/24)
│   └── Private Services Access (10.20.4.0/22)
│       ├── Cloud SQL PostgreSQL 16
│       └── Memorystore for Redis 7.2 with TLS
├── Secret Manager containers (values intentionally absent)
├── runtime, deployer, and infrastructure service accounts
└── GitHub Workload Identity Pool and provider
```

The VPC uses custom subnet mode and regional routing. The subnet enables Private
Google Access and is intended for later Cloud Run Direct VPC egress. The private
services range supplies addresses for Cloud SQL, Memorystore, and reasonable
early staging growth without reserving an oversized `/16`.

No default-VPC resources, public database endpoint, firewall rules, Cloud NAT,
load balancer, GKE resources, Cloud Run resources, or production resources are
defined here.

## Managed services and lifecycle

### Artifact Registry

One regional Docker repository, `offertrack`, will eventually hold `web`,
`core-api`, and `ai-service` images. This checkpoint publishes no images and
defers cleanup and tag-immutability policy until the image publishing contract
is implemented.

### Cloud SQL

`offertrack-stg-postgres` runs PostgreSQL 16 as a zonal Enterprise-edition
`db-g1-small` instance with private IPv4 only. It starts with a 10 GiB SSD,
automatically grows up to 50 GiB, retains seven daily backups, and retains three
days of transaction logs for point-in-time recovery. The application database
is named `offertrack`.

Terraform ignores only API-driven growth of the reported disk size. All other
configuration remains drift-visible. Instance deletion protection is enabled in
Terraform and in the Cloud SQL API, and the database resource uses a preventing
deletion policy. A destroy therefore cannot casually remove the database.

No `google_sql_user` resource exists. Application and migration users and their
passwords are deferred so no database credential is written to Terraform state.

### Memorystore

`offertrack-stg-redis` is a one-GiB Basic-tier Redis 7.2 instance using Private
Services Access. It is intentionally non-HA and destroyable for staging. The
`noeviction` max-memory policy protects the rate limiter from silently evicting
keys; the existing protected-environment application behavior still fails
closed when Redis is unavailable.

In-transit encryption is enabled at instance creation with
`SERVER_AUTHENTICATION`. Non-TLS clients cannot connect. The future Cloud Run
runtime checkpoint must:

1. Use the host and port returned by the instance rather than assuming local
   Redis port `6379`.
2. Install every active CA returned in `server_ca_certs` into the Core client
   trust configuration.
3. Enable TLS in the Spring Data Redis/Lettuce client.
4. Retain reconnect/backoff behavior so server-certificate rotation causes only
   a transient interruption.
5. Refresh the trusted CA set before an instance CA expires or rotates.

The CA output is marked sensitive only to avoid printing a long infrastructure
value routinely; a CA certificate is not a password. Redis AUTH remains disabled
in this checkpoint because enabling it would generate a credential before the
secret-version and runtime configuration flow exists.

## Secret handling

Terraform creates these regional secret containers only:

- `offertrack-stg-jwt-secret`
- `offertrack-stg-oauth-cookie-secret`
- `offertrack-stg-rate-limit-key-secret`
- `offertrack-stg-db-app-password`
- `offertrack-stg-db-migrator-password`
- `offertrack-stg-google-client-secret`
- `offertrack-stg-ai-internal-key`
- `offertrack-stg-openai-api-key`
- `offertrack-stg-smtp-password`

No secret versions, generated secrets, passwords, OAuth credentials, or API
keys are stored in Terraform source, variables, outputs, workflow YAML, or
documentation. Add values later through an explicitly reviewed operational
process outside Terraform.

Secret accessor grants are resource-specific:

| Runtime identity | Accessible containers |
| --- | --- |
| Core | JWT, OAuth cookie, rate-limit key, DB application password, Google client secret, AI internal key, SMTP password |
| AI | OpenAI API key, AI internal key |
| Migrator | DB migrator password |
| Web | None |

The AI identity receives neither database/Redis credentials nor project-level
secret access. Runtime identities receive no infrastructure administration.

## Identity and IAM

All service accounts are keyless. Runtime accounts are distinct from the future
deployer and Terraform accounts.

The privileged `offertrack-stg-infra` account receives additive project IAM
members only:

| Role | Terraform-owned responsibility |
| --- | --- |
| `roles/serviceusage.serviceUsageAdmin` | Required API enablement |
| `roles/artifactregistry.admin` | Staging repository management |
| `roles/compute.networkAdmin` | VPC, subnet, and reserved address management |
| `roles/servicenetworking.networksAdmin` | Private Services Access connection |
| `roles/cloudsql.admin` | Cloud SQL instance and database |
| `roles/redis.admin` | Memorystore instance |
| `roles/iam.serviceAccountAdmin` | Keyless service-account resources and their IAM policies |
| `roles/iam.workloadIdentityPoolAdmin` | GitHub pool and provider |
| `roles/resourcemanager.projectIamAdmin` | Additive project-level IAM members |
| `roles/iam.roleViewer` | Refresh the custom role without changing it |
| Custom secret metadata role | Secret containers and per-secret IAM only |

The project custom role `offertrackStgSecretMetadataAdmin` permits only Secret
Manager location reads plus secret container create/delete/get/list/update and
get/set IAM policy. It contains no `secretmanager.versions.*` permission and is
protected from deletion. Creating or changing that custom role requires a
separately authorized bootstrap principal; the infra account can read but cannot
broaden it.

The `offertrack-stg-deployer` account has no project role, Artifact Registry
writer role, Cloud Run role, or service-account administration role. Those
permissions are deferred until a workflow has something explicit to deploy.

No Terraform IAM resource grants `Owner` or `Editor`.

## GitHub Workload Identity Federation

The pool `offertrack-github` and provider `github-actions` trust GitHub's issuer
at `https://token.actions.githubusercontent.com/`. The primary trust boundary
uses immutable GitHub identifiers:

```text
repository_id       == "1252789489"
repository_owner_id == "15733165"
ref                 == "refs/heads/main"
ref_type            == "branch"
```

Exact repository and owner names, `Xandr1/offertrack` and `Xandr1`, are retained
as secondary defense-in-depth and readability checks. A repository rename
requires updating those secondary checks but does not replace the immutable-ID
boundary.

Only the repository-ID principal set can impersonate
`offertrack-stg-deployer` through `roles/iam.workloadIdentityUser`. There is no
GitHub binding on `offertrack-stg-infra`, so GitHub cannot obtain the privileged
Terraform identity in this checkpoint. The CI validation job also receives no
`id-token: write` permission and performs no Google authentication.

An authenticated Terraform workflow requires a separate security review that
explicitly adds an infra-account WIF binding and externally managed state-bucket
access. Creating the pool now does not authorize that path.

## Local prerequisites and safe commands

Use Terraform 1.15.8. The root constrains Terraform to the 1.15 patch line and
the Google provider to the 7.45 patch line. The provider lock contains both
Windows AMD64 and Linux AMD64 checksums for local and CI use.

Static validation needs network access to install the locked provider but no
GCP credentials:

```bash
terraform -chdir=infra/terraform/staging fmt -check
terraform -chdir=infra/terraform/staging init -backend=false -input=false -lockfile=readonly
terraform -chdir=infra/terraform/staging validate -no-color
```

A real staging plan additionally needs Google Application Default Credentials,
access to project `offertrack-staging`, and read/write/list/delete object access
to the external state bucket:

```bash
terraform -chdir=infra/terraform/staging init
terraform -chdir=infra/terraform/staging plan
```

The initial bootstrap operator must be able to enable the declared APIs, manage
the declared networking and managed services, create service accounts and the
custom role, and add the exact IAM members. Custom-role creation requires role
administration that is deliberately not granted to the infra account.

Review the complete real plan and its target project before the first manual
staging apply. The repository CI never runs `plan`, `apply`, or `destroy` and no
automation should apply a plan casually.

## Cost-sensitive choices

Cloud SQL and Memorystore are the main continuously allocated resources. The
defaults favor staging cost over availability:

- `cloud_sql_tier = "db-g1-small"` selects a shared-core, non-HA instance.
- `cloud_sql_disk_size_gb = 10` and
  `cloud_sql_disk_autoresize_limit_gb = 50` bound initial and maximum storage.
- `redis_memory_size_gb = 1` selects the smallest planned Basic instance with
  no replica.

Review these inputs and current Google Cloud pricing before every first apply or
material size change. No fixed monthly estimate is asserted here.

## Deletion behavior

- The external state bucket is never part of the Terraform resource graph.
- Cloud SQL has both Terraform and API deletion protection.
- The `offertrack` database has a preventing deletion policy.
- The custom IAM role has a preventing deletion policy.
- Redis and the remaining staging foundation resources are intentionally
  destroyable, subject to their platform behavior.

Do not treat a broad destroy as a normal staging operation. Any intentional
teardown must first review the protected resources and data-retention outcome.

## Deferred work

The next deployment checkpoint may add:

- Cloud Run Web, Core API, and private IAM-protected AI services;
- the one-shot Core migration Cloud Run Job;
- runtime environment variables, secret versions, and Redis TLS client trust;
- application and migrator database users;
- optionally Redis AUTH with an explicit credential-handling design;
- image publication and authenticated deployment workflows; and
- staging smoke and acceptance validation.

Production infrastructure, the controlled AI egress/SSRF ADR, and the refresh
token/session ADR remain separate work. This foundation does not claim to solve
those deferred risks.
