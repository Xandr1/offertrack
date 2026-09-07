#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"
. "$REPO_ROOT/scripts/lib/maven-wrapper.sh"
MAVEN_WRAPPER="$(resolve_maven_wrapper "$REPO_ROOT")"

echo "Running frontend lint..."
pnpm --filter web run lint

echo "Running frontend typecheck..."
pnpm --filter web run typecheck

echo "Running backend formatting check..."
(
  cd apps/core-api
  "$MAVEN_WRAPPER" -q spotless:check
)

echo "Running backend compile..."
(
  cd apps/core-api
  "$MAVEN_WRAPPER" -q -DskipTests compile
)

echo "Code checks passed."
