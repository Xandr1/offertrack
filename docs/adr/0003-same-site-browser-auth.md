# ADR 0003: Same-site browser authentication through one HTTPS edge

- Status: Accepted; live edge and browser verification required before reopening staging
- Date: 2026-09-20

## Decision

Use `https://staging.<domain>` for Web and `https://api.staging.<domain>` for Core.
The operator supplies the owned registrable domain as Terraform
`staging_base_domain` and the matching GitHub variable `STAGING_BASE_DOMAIN`.
Browser requests remain direct to Core with `credentials: include`.
Core permits exactly the Web origin with credentials; no wildcard CORS or proxy
through Next.js. Do not host untrusted applications under this same-site boundary.

One global external HTTPS Application Load Balancer routes the two exact hosts
to their existing Cloud Run services in `europe-central2`. Keep the existing DNS
provider. Cloud Run domain mapping is [unavailable in this region and remains
preview](https://docs.cloud.google.com/run/docs/mapping-custom-domains#run);
moving regions is outside this decision. Accept the shared load balancer's cost.

Terraform owns the IPv4 address, managed certificate, TLS 1.2 minimum policy,
host routing, stable/candidate serverless NEGs, logging exclusion, and restricted
Cloud Run ingress. CDN stays disabled. Public default Web/Core run.app URLs are
disabled; the AI service keeps its deterministic run.app audience and IAM boundary.
Terraform ignores deployment-owned images and traffic.

## Cookies and redirects

All cookies are host-only: never set Domain.

| Cookie | Attributes |
| --- | --- |
| access_token | Secure, HttpOnly, SameSite=Lax, Path=/; lifetime matches JWT |
| refresh_token | Secure, HttpOnly, SameSite=Lax, Path=/auth; lifetime matches refresh expiry |
| XSRF-TOKEN | Secure, HttpOnly, SameSite=Lax, Path=/; masked value returned by /auth/csrf |
| OAuth request | signed, Secure, HttpOnly, SameSite=Lax, Path=/; 180 seconds |

Use Google's top-level GET authorization-code callback at
`https://api.staging.<domain>/login/oauth2/code/google`, allowing the Lax
authorization-request cookie on the cross-site navigation. Core public and Web
redirect URLs are explicit configuration, never inferred from forwarded input.
Recovery links use `#token=...`, not query parameters.

## Trust at the edge

Replace incoming X-Forwarded-For using the load balancer's client and server
address variables. Remove Forwarded and conflicting forwarded host, port and
prefix headers; set the HTTPS scheme. Core retains framework forwarding, and
rate limiting uses the resolved remote address. Forged-header browser tests at
the deployed edge are mandatory: static Terraform validation cannot prove the
final Cloud Run forwarding chain.

`X-OfferTrack-Route: candidate` is a public routing selector, not a credential.
It selects candidate revisions only for exact GET health/readiness/dependency
paths and the Web login probe. It cannot grant application access, substitute
the dependency-health key, weaken AI IAM, or bypass maintenance on auth/API
operations. Unknown hosts are rejected.

Maintenance is an explicit Terraform switch, enabled by default. The URL map
returns 503 for application traffic while preserving the exact deployment probes.
Do not clear it until Core and Web revisions and their public configuration agree.
Keep request logging; exclude only the Core Google callback URL from the relevant
Cloud Run and load-balancer request logs. Application callback outcome events
remain. Apply the equivalent exclusion to any independently managed log sinks.

## Rollout

Provision the edge, publish both DNS A records, verify compatible CAA and active
TLS, register the new Google callback, then execute the maintenance-window
cutover in [the runbook](../auth-security-rollout.md). Web must be rebuilt with the
final NEXT_PUBLIC_API_URL; a runtime environment update cannot replace its
inlined URL. Its immutable image tag includes the public configuration hash.

The first staging cutover is forward-only if no compatible revision exists.
Production requires a previously verified session-aware rollback revision.
Old-host cookies cannot be cleared on the new host; old entry points are
restricted and legacy JWTs are rejected. Third-party-cookie-blocked Chromium,
Firefox, WebKit and manual Google redirects are release gates.

