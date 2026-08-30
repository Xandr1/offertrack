#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

echo "Formatting backend..."
(
  cd apps/core-api
  ./mvnw.cmd -q spotless:apply
)

echo "Formatting frontend..."
(
  cd apps/web
  pnpm.cmd lint --fix
)

echo "Formatting AI service..."
(
  cd apps/ai-service
  python -m pip install --upgrade "pip==26.1.2"
  if uname -s | grep -qiE '^(MINGW|MSYS|CYGWIN)'; then
    python -m pip install --editable ".[test]"
  else
    sha256sum --check pylock.test.toml.sha256
    python -m pip install --requirement pylock.test.toml
  fi
  python -m ruff check . --fix
  python -m ruff format .
)

echo "Formatting completed."
