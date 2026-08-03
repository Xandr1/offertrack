# Production configuration

## Environments

Spring treats `prod`, `production`, `stage`, and `staging` as protected
profiles. Next.js uses `APP_ENV`; `staging` and `production` require an explicit
non-loopback HTTPS `NEXT_PUBLIC_API_URL`. `local` development may default to
`http://localhost:8080`, while `test` and `e2e` may use loopback HTTP.

Protected Spring startup requires safe explicit values for:

- Database: `DATABASE_URL`, `DB_USER`, `DB_PASSWORD`
- Redis: `REDIS_HOST`, `REDIS_PORT`, `REDIS_CONNECT_TIMEOUT`, `REDIS_TIMEOUT`
- JWT/OAuth: `JWT_SECRET`, `JWT_ACCESS_TOKEN_TTL`, `OAUTH_COOKIE_SECRET`,
  `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- Browser topology: `APP_WEB_URL`, `CORS_ALLOWED_ORIGINS`,
  `AUTH_COOKIE_NAME`, `AUTH_COOKIE_PATH`, optional `AUTH_COOKIE_DOMAIN`,
  `AUTH_COOKIE_SECURE`, and `AUTH_COOKIE_SAME_SITE`
- AI client: `AI_SERVICE_BASE_URL`, `AI_SERVICE_INTERNAL_API_KEY`,
  `AI_SERVICE_AUTH_MODE`, and `AI_SERVICE_AUDIENCE`
- Mail: `SMTP_HOST`, `SMTP_PORT`, optional `SMTP_USERNAME` and
  `SMTP_PASSWORD`, plus `MAIL_FROM`
- Rate limiting: `RATE_LIMIT_KEY_SECRET` and `RATE_LIMIT_FAIL_OPEN`
- Proxy processing: `SERVER_FORWARD_HEADERS_STRATEGY`

Protected validation applies these exact UTF-8 byte minimums:

- `JWT_SECRET`, `OAUTH_COOKIE_SECRET`, `RATE_LIMIT_KEY_SECRET`, and
  `AI_SERVICE_INTERNAL_API_KEY`: 32 bytes; JWT and OAuth must differ, and the
  rate-limit key must differ from both
- `GOOGLE_CLIENT_SECRET`: 16 bytes
- `DB_USER`: 4 bytes and `DB_PASSWORD`: 12 bytes
- `SMTP_USERNAME`: 1 byte and `SMTP_PASSWORD`: 12 bytes when SMTP credentials
  are supplied; both remain optional, but must be supplied together

Known local/default placeholders are rejected. These checks validate byte
length and known unsafe values; they do not estimate cryptographic entropy.
Never commit a real environment file or secret.

## Core-to-AI authentication

Local, test, and E2E environments use:

```dotenv
AI_SERVICE_AUTH_MODE=internal-key
AI_SERVICE_AUDIENCE=
```

They continue to send `X-Internal-Api-Key` and `X-Request-Id` and do not load
Google Application Default Credentials (ADC). Protected `stage`, `staging`,
`prod`, and `production` profiles instead require:

```dotenv
AI_SERVICE_AUTH_MODE=google-id-token
AI_SERVICE_BASE_URL=https://<ai-service>.run.app
AI_SERVICE_AUDIENCE=https://<ai-service>.run.app
AI_SERVICE_INTERNAL_API_KEY=<independent-32-byte-secret>
```

In Google mode, the Core API obtains an ID token lazily from ADC for the exact
configured audience and adds it as
`X-Serverless-Authorization: Bearer <google-id-token>`. The internal API key and
request ID remain required and are sent on the same request. Token acquisition
fails closed before the HTTP request; logs contain only a request ID and safe
error category.

The base URL and audience must both be absolute HTTPS roots with no credentials,
query, fragment, or path other than empty or `/`. Comparison lowercases the
scheme and host, treats an empty path and `/` equally, and treats explicit port
443 as the HTTPS default. Their normalized values must match, but the exact
validated `AI_SERVICE_AUDIENCE` string is passed to Google Auth. Protected
configuration also rejects loopback, unspecified, `localhost`, `.local`,
`.localdomain`, and single-label hosts.

ADC is expected to come from the eventual Cloud Run workload identity. No
service-account JSON file, private key, or other static GCP credential is
expected. Cloud Run service IAM and invocation permissions are deliberately
deferred to the infrastructure milestone; this repository does not implement
them yet.

## Core runtime and migrations

`OFFERTRACK_RUN_MODE` accepts exactly `server` or `migrate`. Omission defaults
to `server`; an explicit blank or any other value fails before Spring starts.
Normal server behavior is unchanged. Server-mode automatic Flyway startup can
be controlled with `SPRING_FLYWAY_ENABLED` and remains enabled by default.

Use the normal Core production image as a one-shot migration process:

```dotenv
OFFERTRACK_RUN_MODE=migrate
DATABASE_URL=jdbc:postgresql://database.example.com:5432/offertrack
DB_USER=offertrack_migrator
DB_PASSWORD=<database-password>
```

Migration mode resolves only those three database settings, validates them
before connecting, runs the existing `classpath:db/migration` Flyway migrations,
and exits. It ignores `SPRING_FLYWAY_ENABLED`. Success or an already-current
schema exits with code 0; invalid settings, connection errors, Flyway validation
errors, and migration errors exit nonzero.

The database URL must be a credential-free PostgreSQL JDBC URL with a database
name; user and password stay in their dedicated environment variables.
Protected profiles additionally retain the server startup rules for a
non-loopback host, explicit valid port, minimum credential lengths, and known
placeholder rejection. The same pure validator enforces those decisions for
protected server startup and migration mode.

Migration mode accepts profile selection only through
`SPRING_PROFILES_ACTIVE` and `SPRING_PROFILES_DEFAULT`. Supplying
`spring.profiles.active` or `spring.profiles.default` as a JVM system property
or command-line option fails startup and directs the caller to the corresponding
environment variable. This prevents pre-Spring migration startup from silently
using the standard database policy when a protected profile was intended.

Migration mode is selected before the regular Spring application is created.
It does not initialize an HTTP listener, Redis, rate limiting, Spring Security,
Google OAuth, SMTP, JWT configuration, AI clients, controllers, or web filters.

Keep `SERVER_FORWARD_HEADERS_STRATEGY=none` unless the API is behind a trusted
ingress that strips client-supplied forwarded headers and supplies its own.

The FastAPI service uses the same `APP_ENV` concept (`local`, `test`, `staging`,
or `production`) plus `OPENAI_API_KEY`, `OPENAI_MODEL`,
`OPENAI_TIMEOUT_SECONDS`, `AI_SERVICE_INTERNAL_API_KEY`,
`AI_SERVICE_FETCH_TIMEOUT_SECONDS`, `AI_SERVICE_MAX_RESPONSE_BYTES`,
`AI_SERVICE_MAX_JOB_TEXT_CHARS`, `AI_SERVICE_MAX_REDIRECTS`,
`AI_SERVICE_ALLOWED_HOSTS`, and `AI_SERVICE_DOCS_ENABLED`. Protected
environments require explicit trusted hosts and disable docs unless deliberately
enabled.

## CSRF contract

Browser mutations first call `GET /auth/csrf`. The JSON response contains a
masked token and the header name `X-XSRF-TOKEN`; the frontend keeps that JSON
token only in memory and sends it unchanged on `POST`, `PUT`, `PATCH`, and
`DELETE`. JavaScript never reads the HttpOnly `XSRF-TOKEN` repository cookie.
The response is not cacheable. Successful login/logout and Google OAuth success
invalidate the repository token, so the next mutation obtains one lazily.

Bearer-only requests without the access cookie are exempt. Public auth
mutations and every unsafe request containing the configured access cookie
remain protected.

## Rate limits

Every limit and window is configurable using the following pairs:

- `RATE_LIMIT_LOGIN_EMAIL_MAX_ATTEMPTS` / `RATE_LIMIT_LOGIN_EMAIL_WINDOW` — 5/15m
- `RATE_LIMIT_LOGIN_IP_MAX_ATTEMPTS` / `RATE_LIMIT_LOGIN_IP_WINDOW` — 20/15m
- `RATE_LIMIT_REGISTRATION_EMAIL_MAX_ATTEMPTS` / `RATE_LIMIT_REGISTRATION_EMAIL_WINDOW` — 3/1h
- `RATE_LIMIT_REGISTRATION_IP_MAX_ATTEMPTS` / `RATE_LIMIT_REGISTRATION_IP_WINDOW` — 5/1h
- `RATE_LIMIT_VERIFICATION_RESEND_EMAIL_MAX_ATTEMPTS` / `RATE_LIMIT_VERIFICATION_RESEND_EMAIL_WINDOW` — 3/1h
- `RATE_LIMIT_VERIFICATION_RESEND_IP_MAX_ATTEMPTS` / `RATE_LIMIT_VERIFICATION_RESEND_IP_WINDOW` — 10/1h
- `RATE_LIMIT_FORGOT_PASSWORD_EMAIL_MAX_ATTEMPTS` / `RATE_LIMIT_FORGOT_PASSWORD_EMAIL_WINDOW` — 5/1h
- `RATE_LIMIT_FORGOT_PASSWORD_IP_MAX_ATTEMPTS` / `RATE_LIMIT_FORGOT_PASSWORD_IP_WINDOW` — 20/1h
- `RATE_LIMIT_RESET_PASSWORD_TOKEN_MAX_ATTEMPTS` / `RATE_LIMIT_RESET_PASSWORD_TOKEN_WINDOW` — 10/1h
- `RATE_LIMIT_RESET_PASSWORD_IP_MAX_ATTEMPTS` / `RATE_LIMIT_RESET_PASSWORD_IP_WINDOW` — 20/1h
- `RATE_LIMIT_AI_USER_MINUTE_MAX_ATTEMPTS` / `RATE_LIMIT_AI_USER_MINUTE_WINDOW` — 10/1m
- `RATE_LIMIT_AI_USER_DAY_MAX_ATTEMPTS` / `RATE_LIMIT_AI_USER_DAY_WINDOW` — 100/1d

The limiter uses fixed windows, so bursts at a window boundary are an accepted
trade-off. Subject keys are HMAC-SHA-256 derived; rotating
`RATE_LIMIT_KEY_SECRET` resets effective counters. Protected profiles fail
closed when Redis is unavailable.

## E2E and CI

Production image construction, the bounded production-like container smoke
suite, standalone Web asset staging, and the container Trivy gate are documented
in [Production container images](container-images.md).

`.env.e2e.example` contains only fixed test values. `pnpm run e2e` removes the
isolated Compose volumes, migrates a fresh database, seeds six dedicated verified
fake users, starts the API and built web app, runs all seven Chromium smoke
scenarios with one worker, and always cleans up. Each state-mutating scenario
uses its own account. It does not call Google or OpenAI. On failure, the harness
prints sanitized service-log tails and preserves sanitized service logs plus the
Playwright failure artifacts for short-retention CI upload.

The CI-equivalent checks are:

```bash
pnpm install --frozen-lockfile
pnpm --dir apps/web run lint
pnpm --dir apps/web run typecheck
pnpm --dir apps/web run test
APP_ENV=test NEXT_PUBLIC_API_URL=http://127.0.0.1:18080 pnpm --dir apps/web run build

cd apps/core-api
./mvnw spotless:check
./mvnw test

cd apps/ai-service
sha256sum --check pylock.test.toml.sha256
python -m pip install --upgrade "pip==26.1.2"
python -m pip install --requirement pylock.test.toml
python -m ruff check .
python -m ruff format --check .
python -m pyright
python -m pytest
```

Python lock regeneration is documented in
[Dependency security scanning](dependency-security.md). Dependabot updates
npm/pnpm, Maven, pip declarations, and full-SHA GitHub Action pins weekly;
Python declaration updates require manual lock regeneration.
