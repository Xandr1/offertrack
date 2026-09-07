# OfferTrack

OfferTrack is a job application tracker for managing applications, interview stages, follow-ups, and job-search progress

## Stack

- **Web:** Next.js, React, TypeScript, Tailwind CSS, TanStack Query, Zod
- **Core API:** Java 21, Spring Boot, Spring Security, jOOQ, Flyway
- **AI service:** Python 3.11, FastAPI, Pydantic, OpenAI API
- **Data:** PostgreSQL, Redis
- **Local infrastructure:** Docker Compose, Mailpit, MinIO

## Repository structure

```text
apps/
  web/          Next.js frontend
  core-api/     Spring Boot API
  ai-service/   FastAPI AI service

scripts/        Local development, testing, E2E, and container tooling
infra/          Infrastructure configuration
docs/           Production, security, deployment, and architecture documentation
```

## Prerequisites

- Docker with Docker Compose
- Node.js 24
- pnpm 12.3.4
- Java 21
- Python 3.11 for AI service development and tests
- Git Bash when running the repository scripts on Windows

Maven does not need to be installed separately; the repository uses the Maven Wrapper.

## Local development

Create the local environment file:

```bash
cp .env.example .env
```

Install JavaScript dependencies:

```bash
pnpm install --frozen-lockfile
```

Start the local infrastructure:

```bash
pnpm run dev:infra
```

Prepare the database and generate jOOQ sources:

```bash
./scripts/backend-codegen.sh
```

Start the Core API:

```bash
pnpm run dev:api
```

Start the Web application in another terminal:

```bash
pnpm run dev:web
```

Local services:

- Web: http://localhost:3000
- Core API: http://localhost:8080
- AI service: http://localhost:8000
- Mailpit: http://localhost:8025
- MinIO console: http://localhost:9001

The defaults in `.env.example` are suitable for local infrastructure. Real Google OAuth requires `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`; AI extraction requires `OPENAI_API_KEY`.

## Commands

| Command                    | Purpose                                                    |
| -------------------------- | ---------------------------------------------------------- |
| `pnpm run dev:infra`       | Start local Docker Compose services                        |
| `pnpm run dev:api`         | Start the Core API                                         |
| `pnpm run dev:web`         | Start the Web application                                  |
| `pnpm run format`          | Format supported project code                              |
| `pnpm run lint`            | Run linting, type checks, and backend compile checks       |
| `pnpm run test`            | Run the main backend, frontend, and AI service test suites |
| `pnpm run test:web`        | Run frontend lint, typecheck, and unit tests               |
| `pnpm run test:api`        | Run Core API tests                                         |
| `pnpm run test:ai-service` | Run AI service checks and tests                            |
| `pnpm run e2e`             | Run the isolated Playwright E2E suite                      |
| `pnpm run db:up`           | Start local Docker Compose services                        |
| `pnpm run db:down`         | Stop local Docker Compose services                         |
| `pnpm run db:reset`        | Recreate local Docker Compose volumes and services         |

After changing Flyway migrations, or when generated jOOQ sources need to be refreshed:

```bash
./scripts/backend-codegen.sh
```

## Testing

For the standard local validation:

```bash
pnpm run test
```

For the isolated browser flow, install Playwright Chromium once and run:

```bash
pnpm --dir apps/web exec playwright install chromium
pnpm run e2e
```

The E2E runner uses `.env.e2e.example` with deterministic test-only configuration and isolated Docker Compose services.
