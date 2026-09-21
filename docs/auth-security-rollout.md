# Staging auth-security rollout

This runbook applies only to `offertrack-staging` in `europe-central2`.
The cutover requires a maintenance window and reauthentication. Follow
[ADR 0002](adr/0002-refresh-token-session-lifecycle.md) and
[ADR 0003](adr/0003-same-site-browser-auth.md); do not restore stateless auth.

## Prerequisites

- Select a commit with passing backend, Web, Terraform/staging, container-smoke,
  and Playwright checks. Keep immutable image digests and revision names.
- Set Terraform `staging_base_domain` and GitHub `STAGING_BASE_DOMAIN` to the
  same owned registrable domain. Web is `https://staging.<domain>`; Core is
  `https://api.staging.<domain>`. Keep the existing DNS provider.
- Provision the shared HTTPS edge. Point both DNS A records at
  `browser_ipv4_address`, remove conflicting records, check CAA, and wait for
  the managed certificate covering both hosts to become ACTIVE.
- Register `https://api.staging.<domain>/login/oauth2/code/google` on the Google
  OAuth client and the Web origin where required. Keep the old callback only
  until cutover verification completes.
- Verify pinned secrets, database roles, and the narrow Google-callback log
  exclusion for every relevant Cloud Run/LB log destination. Keep ordinary
  request logs and useful PostgreSQL error diagnostics.

## Maintenance-window sequence

1. Notify staging users and apply `auth_maintenance_enabled=true` before
   switching browser traffic. Confirm application/auth routes return 503,
   including with a forged candidate selector; exact deployment probes remain
   available. Restrict old/default Web/Core entry points as configured.
2. Apply the final Terraform runtime settings: same-site public URLs, exact Web
   CORS origin, explicit Google callback, host-only Lax cookies, 15-minute access
   JWTs, seven-day inactivity, thirty-day absolute expiry, and ten sessions.
   Check the effective Cloud SQL logging settings without printing credentials.
3. Enable/run the manual **Deploy staging** workflow for the CI-approved commit.
   Preserve its order: verify/promote AI; execute the additive Flyway migration
   job; verify/promote the session-aware Core candidate; build Web with the final
   Core URL; verify/promote the Web candidate. A failed phase stops the sequence.
4. For Core, verify candidate readiness and authenticated dependency health
   using its pinned health key, plus the workflow's runtime URL/session checks.
   For Web, verify the candidate `/login` probe. The public
   `X-OfferTrack-Route: candidate` header only selects these probe routes; it
   grants no authentication or maintenance exemption for application routes.
5. Confirm serving revisions, immutable digests, migration success, and matching
   Web/Core configuration. Web's public API URL is compiled into its image;
   changing runtime environment alone is insufficient. Run staging smoke while
   maintenance remains active.
6. Review and apply the Terraform plan setting
   `auth_maintenance_enabled=false`. Confirm normal routes reopen, then
   immediately perform the browser acceptance checks below. Re-enable
   maintenance if a release gate fails.
7. Publish the new staging Web URL, require users to sign in again, reissue any
   old query-format recovery links, and remove the old Google callback after
   successful verification. Old-host cookies cannot be cleared from the new
   hostname; they do not provide compatibility.

## Real-browser acceptance

Use authorized disposable staging accounts. Run the opt-in deployed-service
suite (it starts no servers):

```bash
E2E_BASE_URL="https://staging.${STAGING_BASE_DOMAIN}" \
E2E_API_URL="https://api.staging.${STAGING_BASE_DOMAIN}" \
  pnpm --dir apps/web exec playwright test \
  --config playwright.auth-staging.config.ts
```

Supply `E2E_STAGING_EMAIL` and `E2E_STAGING_PASSWORD` through the local process
environment; never commit or print them. Keep traces, HAR, storage-state uploads,
videos, and credential-bearing screenshots disabled.

Verify Chromium, Firefox, and WebKit with third-party cookies blocked: password
login, reload with access removed and refresh retained, multi-tab refresh/logout,
logout-all, and failed refresh returning to login. Inspect cookies on a Core URL
under `/auth` to include the narrow refresh cookie. Manually verify the real
Google redirect/callback and account claim, including removal of the old
unverified password. Verify fragment-only recovery links.

Run `E2E_RATE_LIMIT_PROBE=true` separately when consuming that source IP's login
window is acceptable; forged forwarding headers must not select a different
rate-limit IP. Inspect sanitized auth outcomes, request correlation, and actual
PostgreSQL negative-path diagnostics for prohibited values. Static tests cannot
prove deployed forwarding, TLS, Google configuration, or browser cookie policy.
Record live results separately from local and CI evidence.

## Staging recovery

On failed rollout or acceptance, keep or restore maintenance. Preserve additive
tables, consumed refresh tokens, session revocations, and cleared passwords.
Correct the failure, create new immutable images, rerun the ordered workflow,
and verify again before reopening.

The first staging cutover may require roll-forward recovery and mandatory
reauthentication. Never move traffic to a stateless Core that ignores session
revocation. A later rollback may use only a previously verified, compatible
session-aware revision. This staging exception does not apply to production:
production needs a distinct, previously verified session-aware Core rollback
target and a tested rollback procedure before promotion.

