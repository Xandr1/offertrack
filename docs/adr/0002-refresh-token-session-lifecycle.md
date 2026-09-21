# ADR 0002: PostgreSQL authentication sessions and durable Google identities

- Status: Accepted; implemented in this change, live staging acceptance remains a rollout gate
- Revised: 2026-09-20
- Supersedes: the proposed Redis denylist, legacy JWT compatibility, and refresh-time CSRF rotation

## Decision

PostgreSQL is the authoritative session store. Access JWTs live for at most
15 minutes, capped by the session deadline, and contain exactly `sub`, `sid`,
`iat`, `exp`, and `jti`. Every cookie or Bearer authentication verifies the
signature and claims locally, then loads the session by its primary key and
checks its user, revocation, inactivity expiry, and absolute expiry. There is no
positive session cache and ordinary requests do not write activity. A database
failure fails closed with `503 AUTH_SERVICE_UNAVAILABLE`; invalid or absent
protected authentication returns `401 AUTHENTICATION_REQUIRED`.

Sessions have seven-day inactivity and immutable thirty-day absolute deadlines.
Only refresh extends inactivity. Session creation serializes on the user row and
evicts the least recently refreshed active session if ten are already active.

Refresh credentials are 32 random bytes encoded as unpadded base64url. PostgreSQL
holds only SHA-256 hashes. One partial unique index allows one unconsumed,
unrevoked refresh token per session. Consumed hashes remain throughout the active
session lifetime, even after their individual expiry.

## Identity ownership

`user_identities` links Google subjects to users using unique `(provider,
provider_subject)` and `(user_id, provider)` constraints. Subjects are opaque,
case-sensitive ASCII strings of one to 255 characters; blank values are invalid.
They are never normalized, truncated, displayed as names, or logged.

An existing Google subject authenticates its linked user even when Google email
changes; it never silently moves the identity or updates the OfferTrack email.
A first identity requires Google's verified email. A new email creates an
OAuth-only user. An existing verified account retains its password. An existing
unverified account is claimed atomically: clear its password, verify its email,
consume its recovery tokens, revoke its sessions, link the identity, and create
the new session. This prevents the pre-registration password from authenticating.

Existing OAuth-only users are linked lazily through verified email on their next
Google login. No fabricated subjects or display-name backfills. A changed email
before first linking cannot establish ownership of an old row. Historical
verified accounts are not automatically cleared; this cannot repair or identify
all historical pre-hijacking.

## Transactions and revocation

All operations use READ COMMITTED. Lock order is Google-subject transaction
advisory lock (when applicable), user, session rows in deterministic order, then
refresh/recovery rows. Discovery reads are rechecked after locking; sample time
after lock waits. Expected uniqueness races use constraints and ON CONFLICT.
Never recover from a PostgreSQL uniqueness exception inside an aborted
transaction. Password login rechecks the locked hash and verification status.

Refresh locates consumed tokens too, locks user/session/token, validates state,
consumes the old token, creates its successor, and updates the session in one
transaction. Cookies are written only after commit. A consumed token revokes the
whole session with `refresh_replay`; that rejection is returned as a value from
a separate transaction, and converted to HTTP failure only after commit. Two
concurrent uses produce one initial success and one rejection that invalidates
the winner's access token and successor. There is no grace period.

Revocation reasons are the bounded Java enum and database CHECK set:
`logout`, `logout_all`, `password_reset`, `oauth_account_claim`,
`refresh_replay`, `session_limit`. Preserve the first reason. Timestamp and
reason are either both absent or both present. Expiry uses deadlines, not a
revocation reason.

Logout accepts a valid access session or refresh session, including with expired
access. If credentials prove two sessions, revoke both with deterministic locks.
Unknown sessions permit idempotent clearing; a database failure cannot report
successful revocation. Logout-all rechecks the authenticated session under user
locking. Successful password reset changes the password, consumes recovery
tokens, and revokes all sessions atomically.

## Browser contract

See [ADR 0003](0003-same-site-browser-auth.md) for HTTPS topology and cookie flags.
Refresh, logout, and logout-all require masked CSRF, including when only a refresh
cookie remains. Other unsafe cookie-authenticated requests recognize either
authentication cookie. The repository cookie stays HttpOnly. Login and logout
invalidate CSRF; refresh preserves it to avoid cross-tab races.

The Web client keeps credentials out of browser storage. Automatic refresh
requires exactly `401 AUTHENTICATION_REQUIRED`, including its coordination
probe. Auth/recovery operations are excluded. In-tab single flight and Web Locks
serialize auth lifecycle work; BroadcastChannel carries only signed-in,
signed-out, and refreshed events. Without Web Locks, automatic refresh fails
closed and requires reauthentication; explicit sign-in/out remain available
through the tab-local queue. No distributed lock emulation or change to server
replay detection. Ambiguous outcomes require reauthentication, never blind
rotation retry. A confirmed refresh permits at most one replay, sharing the
existing CSRF replay budget. A generation change discards late responses and
prevents mutation replay under another account. Logout/account change cancels
and removes protected TanStack queries. Focus, reconnect, and reload revalidate
through the server.

## Cleanup and diagnostics

At most every five minutes per instance, a separate bounded cleanup transaction
tries a nonblocking advisory lock. It selects at most twenty terminal sessions
older than thirty days using an expression index and SKIP LOCKED, deletes at
most one hundred refresh rows, then deletes empty parents only. It never relies
on an unbounded cascade during cleanup. Failures are sanitized and cannot fail a
completed auth operation.

Keep ordinary Core/Web/edge request logs and useful PostgreSQL error diagnostics.
Recovery credentials use URL fragments, removed after client-only parsing, and
never appear in query keys. Auth DTO diagnostics are redacted; framework verbose
auth diagnostics are suppressed only within sensitive request scope, with
sanitized status/latency/request-ID events retained. Disable rendered SQL and bind
value logging. The edge excludes only credential-bearing Google callback request
URLs. PostgreSQL retains error-level statements and normal error verbosity;
expected auth conflicts must avoid constraint exceptions because native DETAIL
can contain row values.

## Cutover and consequences

Reject legacy JWTs without `sid`. Staging accepts mandatory reauthentication
under maintenance and roll-forward recovery for the first cutover. Never restore
a stateless Core after session tokens exist: it can ignore revoked sessions.
Production requires a distinct, previously verified session-aware Core revision
and a tested security-compatible rollback procedure before promotion. Preserve
the additive schema, consumed tokens, revocations, and cleared passwords during
rollback. See [the rollout runbook](../auth-security-rollout.md).

Indexed reads increase PostgreSQL load; measure the existing small instance and
pool before production. In-flight work already authorized when a revocation
commits is not retroactively cancelled. Strict refresh replay deliberately favors
reauthentication over guessing after a lost response.
