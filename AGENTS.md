# AI Agent Instructions

These instructions apply to AI coding agents working in this repository.

## General Rules

- Keep changes small, focused, and reviewable.
- Follow existing project patterns before introducing new abstractions.
- Do not introduce new libraries unless explicitly requested.
- Do not change backend/API contracts unless the task explicitly asks for it.
- Do not run long-lived dev servers.
- Prefer Git Bash-compatible commands on Windows.

## Architecture Notes

OfferTrack is a monorepo with:

- `apps/web` — Next.js frontend
- `apps/core-api` — Spring Boot backend
- `scripts` — local development and codegen scripts
- `docker-compose.yml` — local infrastructure

## Frontend Rules

- Use TanStack Query for server state.
- Keep local UI state local unless there is a clear reason to extract it.
- Do not add Zustand, Redux, or another global state manager unless explicitly requested.
- Use `src/lib/query-keys.ts` for query keys.
- Use shared API helpers instead of direct `fetch` calls in components.
- Use `src/lib/styles.ts` for repeated Tailwind primitives.
- Keep one-off layout classes local to components.
- Keep URL state for applications filters/search/sort where currently used.
- Do not store auth tokens in browser storage.

## Backend Rules

- Auth uses JWT in HttpOnly cookies.
- Enforce user isolation in backend queries.
- Missing or foreign user-owned resources should return 404.
- Use Flyway for schema changes.
- Use jOOQ for database access.
- Run backend codegen after changing migrations.

## Safety

- Do not commit `.env` or secrets.
- Do not add real API keys, OAuth secrets, JWT secrets, or production credentials.
- Keep `.env.example` safe and example-only.

## Validation Before Finishing

Run the relevant checks for changed areas.

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

Codegen after migrations:

```bash
./scripts/backend-codegen.sh
```
