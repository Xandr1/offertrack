# OfferTrack

OfferTrack is a job application tracker for software engineers.

It helps track applications, stages, interview rounds, follow-ups, and the next steps in a job search process.

## Stack

**Frontend**

* Next.js
* React
* TypeScript
* Tailwind
* TanStack Query
* Zod

**Backend**

* Java 21
* Spring Boot
* Spring Security
* JWT
* jOOQ
* Flyway
* PostgreSQL

**Local infrastructure**

* Docker Compose
* PostgreSQL
* Redis
* Mailpit
* MinIO
* FastAPI AI service

## Local Setup

Use Git Bash on Windows.

Create a root `.env` file from `.env.example`:

```bash
cp .env.example .env
```

Start local infrastructure:

```bash
docker compose up -d
```

Run database migrations and generate jOOQ classes:

```bash
./scripts/backend-codegen.sh
```

Start the backend:

```bash
npm run dev:api
```

Start the frontend:

```bash
npm run dev:web
```

The frontend runs on:

```text
http://localhost:3000
```

The backend runs on:

```text
http://localhost:8080
```

The backend calls the internal AI service at `AI_SERVICE_BASE_URL`, which defaults to:

```text
http://localhost:8000
```

## Development Checks

Frontend:

```bash
pnpm.cmd --dir apps/web run lint
pnpm.cmd --dir apps/web run typecheck
pnpm.cmd --dir apps/web run test
```

Backend:

```bash
cd apps/core-api
./mvnw.cmd test
```

AI service:

```bash
cd apps/ai-service
sha256sum --check pylock.test.toml.sha256
python -m pip install --upgrade "pip==26.1.2"
python -m pip install --requirement pylock.test.toml
python -m ruff check .
python -m ruff format --check .
python -m pyright
python -m pytest
```

Production build:

```bash
APP_ENV=test NEXT_PUBLIC_API_URL=http://127.0.0.1:18080 \
  pnpm.cmd --dir apps/web run build
```

Full isolated browser smoke test (requires Docker, Java 21, Node, pnpm, and
Chromium installed by Playwright):

```bash
pnpm.cmd run e2e
```

## Backend Codegen

Run codegen after changing Flyway migrations, resetting the database, or when jOOQ classes are missing:

```bash
./scripts/backend-codegen.sh
```

This script starts Postgres if needed, waits for readiness, runs Flyway migrations, and generates jOOQ classes.

## Production hardening

See [Production configuration](docs/production-configuration.md) for protected
profile requirements, CSRF behavior, rate-limit defaults, proxy handling, E2E
configuration, Python lock regeneration, and the complete CI command set.

See [Production container images](docs/container-images.md) for the exact
`linux/amd64` image build and smoke commands, runtime contracts, standalone Web
layout, cleanup behavior, and aggregate Trivy policy.
