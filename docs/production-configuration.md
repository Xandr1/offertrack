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
- AI client: `AI_SERVICE_BASE_URL`, `AI_SERVICE_INTERNAL_API_KEY`
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
