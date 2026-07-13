# ADR 0002: Refresh-token and session lifecycle

- Status: Proposed
- Date: 2026-07-13
- Follow-up: separate session-lifecycle implementation work after PR #16
- Follow-up: separate schema, backend, and frontend implementation PR

## Context

OfferTrack currently issues a signed access JWT in an HttpOnly cookie. Its default lifetime is 48
hours, it contains the user's ID and email, and logout clears the browser cookie without a
server-side session record. There is no refresh rotation, token-family replay detection, logout-all,
or immediate revocation mechanism.

PR #16 does not replace that contract. This ADR defines the migration to short-lived access tokens
and server-managed refresh sessions.

## Threat model and goals

The design must limit damage from a stolen access or refresh token, detect refresh replay, revoke
sessions after security events, avoid raw-token storage, remain safe under concurrent refreshes,
and preserve CSRF protection for cookie-authenticated mutations. It must also avoid a relational
database lookup on every ordinary authenticated request.

Tokens, hashes, cookie values, email addresses, user IDs, session IDs, and token-family IDs must not
appear in logs, metrics labels, traces, or error bodies. An attacker is assumed able to replay a
stolen token and race a legitimate refresh request, but not to recover a 256-bit random token from
its SHA-256 hash.

## Decision

### Access tokens

Access tokens remain signed JWTs in the existing Secure, HttpOnly access cookie and expire after 15
minutes. Claims are limited to:

- `sub`: user ID.
- `sid`: server-side session ID.
- `iat`: issued-at time.
- `exp`: expiry.
- `jti`: unique access-token ID.

Email and other sensitive or mutable user data are removed. Access tokens are never stored in
browser storage.

Ordinary authenticated requests verify the JWT locally and perform a Redis session-denylist check;
they do not query PostgreSQL. A Redis denylist entry is keyed by a one-way server-generated session
lookup key and expires no later than the latest access token for that session. If the denylist is
unavailable, authenticated requests fail closed with a generic service-unavailable response.

### Refresh tokens and cookies

Refresh tokens are opaque 256-bit cryptographically random values. The browser receives them only
in a cookie with `Secure`, `HttpOnly`, `SameSite=Lax`, `Path=/auth`, the configured production
domain, and a 30-day maximum age. JavaScript never receives the token.

Only `SHA-256(refresh_token)` is stored. The random token has sufficient entropy to make offline
guessing infeasible without storing salts or recoverable token material. Refresh tokens rotate on
every successful refresh and belong to one session family.

Sessions have a seven-day inactivity expiry and a 30-day absolute expiry. At most ten active
sessions are retained per user; creating an eleventh transactionally revokes the least recently
used session. Coarse device metadata is omitted in v1 because no concrete security or support use
case currently justifies retaining it.

### Data model

Add `auth_sessions` with:

- `id`, `user_id`, and `token_family_id` UUIDs.
- `created_at`, `last_used_at`, `inactivity_expires_at`, and `absolute_expires_at`.
- `revoked_at` and a bounded `revocation_reason` enum/string.
- `current_refresh_token_id` referencing the current token after the token table exists.

Add `auth_refresh_tokens` with:

- `id` and `session_id`.
- Unique 32-byte `token_hash`.
- `issued_at`, `expires_at`, `consumed_at`, and `revoked_at`.
- Nullable `replaced_by_id` linking a consumed token to its successor.

Indexes cover user-active-session cleanup, expiry cleanup, session lookup, and unique token hash.
Foreign keys use explicit deletion behavior; application deletion revokes and then removes sessions
through the existing account lifecycle rather than cascading silently.

### API and CSRF behavior

- Login and successful OAuth login retain their current response bodies and additionally issue a
  refresh cookie and a 15-minute access cookie.
- `POST /auth/refresh` is added and returns `204 No Content` with rotated cookies.
- Existing `POST /auth/logout` keeps its response contract, revokes the current session, and clears
  both cookies.
- `POST /auth/logout-all` is added, returns `204 No Content`, and revokes all user sessions.

Refresh, logout, and logout-all require the existing repository-cookie plus masked-header CSRF
pair. Successful login, OAuth login, refresh, and logout rotate CSRF state. Refresh may authenticate
with an expired access cookie because the refresh token and session are independently verified;
other authentication failures do not relax CSRF enforcement.

Cookie changes are written only after the database transaction commits. Failure responses never
echo tokens or indicate whether a supplied token hash exists.

### Rotation transaction and replay detection

Refresh rotation runs in one transaction:

1. Hash the presented token and lock its token row plus session row using `SELECT ... FOR UPDATE`.
2. Verify that the token is current, unconsumed, unrevoked, and within session inactivity and
   absolute expiry.
3. Mark the old token consumed, insert the hashed successor, link `replaced_by_id`, update the
   session's current token and last-used/inactivity timestamps, and commit.
4. Issue the successor cookie and a new access token only after commit.

Presentation of any consumed token is treated as replay and revokes the complete family, including
the most recently issued successor. It also adds the session to the access-token denylist until the
latest possible access expiry.

### Legitimate concurrent refreshes

Strict replay detection can misclassify two legitimate simultaneous requests. The implementation
uses client coordination rather than weakening server replay rules:

- One in-memory refresh promise coalesces requests within a tab.
- The frontend uses the Web Locks API for a cross-tab refresh lock and BroadcastChannel to publish
  completion. Where Web Locks is unavailable, BroadcastChannel leader election plus a bounded wait
  prevents routine duplicate rotation.
- Refresh is never automatically retried after an ambiguous network failure. The client first
  rechecks authentication state and otherwise requires login.
- The server does not use IP address, user agent, timing, or a grace window to guess whether replay
  is legitimate.

If two refresh requests nevertheless reach the server, exactly one rotation succeeds. The loser
observes a consumed token, revokes the family, and forces reauthentication; the first successor is
therefore unusable. This rare false-positive availability cost is explicitly accepted in favor of
not granting a replay window to a stolen bearer token.

Rejected concurrency alternatives are:

- A time-based grace window, which lets an attacker race a stolen token without family revocation.
- Returning the same successor for duplicate requests, which requires temporary recoverable
  storage of the raw successor and additional encryption/key-rotation machinery.
- IP/user-agent matching, which is unreliable, privacy-sensitive, and attacker-influenceable.

### Revocation events

- Logout revokes the current refresh session and clears cookies. Its access token becomes
  unusable immediately through the session denylist.
- Logout-all revokes and denylists every active session for the user.
- Password change/reset, account disable/delete, and security-sensitive OAuth credential changes
  revoke and denylist all sessions.
- Refresh-token replay revokes and denylists the complete family.
- Expired sessions reject refresh but need no denylist entry after all related access tokens expire.

The Redis denylist is the explicit immediate-access-revocation choice. It adds one Redis lookup per
authenticated request but avoids a PostgreSQL lookup. Availability alerts distinguish denylist
outages without logging identifiers.

### Cleanup, rate limiting, and observability

A daily bounded cleanup job removes refresh-token rows and sessions after their audit-retention
period. Revoked/expired records are retained for 30 days before deletion. Cleanup is indexed,
paginated, idempotent, and safe to run concurrently.

Refresh is rate-limited by safe IP-derived and session-derived keys using the existing abstraction.
Failures expose generic error codes and request IDs. Metrics cover refresh success, expiry,
revocation reason, replay, concurrency-forced reauthentication, cleanup counts, and denylist
availability without subject values.

## Migration and rollout

1. Add the Flyway schema and jOOQ sources without changing current authentication behavior.
2. Deploy dual-capable backend code behind flags for refresh issuance, refresh acceptance, and
   denylist enforcement.
3. Begin issuing refresh sessions on password and OAuth login while continuing to accept legacy
   48-hour access JWTs as non-refreshable tokens.
4. Deploy frontend single-flight and cross-tab coordination, then enable refresh calls.
5. Wait at least 48 hours after the last legacy token could have been issued, remove legacy email
   claims and legacy-token acceptance, and change the access TTL default to 15 minutes.
6. Enable maximum-session cleanup and final monitoring alerts.

Rollback disables refresh issuance and frontend refresh use while leaving additive schema in place.
Already-issued 15-minute access tokens continue to work. Refresh endpoints can be disabled without
restoring the 48-hour token lifetime; a rollback release may temporarily require users to log in
again. Schema removal occurs only in a later migration after the rollback window.

## Alternatives rejected

- Keeping 48-hour access JWTs: revocation exposure is too long.
- Storing refresh tokens in local storage: exposes bearer tokens to JavaScript/XSS.
- Long-lived JWT refresh tokens without server state: cannot provide robust rotation or family
  replay detection.
- A PostgreSQL session lookup for every request: unnecessary load when a short access JWT plus
  Redis denylist provides the selected immediate-revocation semantics.
- Access-token denylisting by raw token or raw `jti`: violates token-safe storage/observability
  goals; the implementation uses a one-way session lookup key.

## Required verification for the implementation PR

- Successful password-login and OAuth refresh rotation.
- The old refresh token cannot be used after rotation; replay revokes the family.
- Two concurrent refreshes yield one initial success followed by family revocation, and the issued
  successor then fails.
- Logout and logout-all revoke the intended sessions and immediately invalidate access.
- Password reset/change and account disable/delete revoke all sessions.
- Expired, revoked, malformed, and unknown refresh tokens fail generically.
- CSRF omission, mismatch, and old-token/new-cookie combinations fail.
- Refresh and access cookie attributes, paths, expiry, and clearing behavior are exact.
- Absolute and inactivity expiry and the ten-session limit are enforced transactionally.
- Redis denylist outage fails authenticated requests closed and emits safe rate-suppressed logs.
- Migration accepts legacy access JWTs only for the bounded compatibility window.
- Database rows and all logs/artifacts contain no raw access or refresh tokens or subject values.
