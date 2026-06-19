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
* FastAPI AI parser

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

The backend calls the internal AI parser at `AI_PARSER_BASE_URL`, which defaults to:

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

AI parser:

```bash
cd apps/ai-parser
python -m pip install -e ".[test]"
python -m ruff check .
python -m ruff format --check .
python -m pytest
```

## Backend Codegen

Run codegen after changing Flyway migrations, resetting the database, or when jOOQ classes are missing:

```bash
./scripts/backend-codegen.sh
```

This script starts Postgres if needed, waits for readiness, runs Flyway migrations, and generates jOOQ classes.
