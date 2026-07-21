#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_DIR="$REPO_ROOT/scripts/containers/tests"

if command -v python3 >/dev/null 2>&1 &&
  python3 -c 'raise SystemExit(0)' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 &&
  python -c 'raise SystemExit(0)' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  echo 'Python 3 is required for the fast container contract tests.' >&2
  exit 1
fi

for script in \
  "$REPO_ROOT/scripts/containers/build-images.sh" \
  "$REPO_ROOT/scripts/containers/run-smoke.sh" \
  "$REPO_ROOT/scripts/containers/tests/test-build-images-args.sh" \
  "$REPO_ROOT/scripts/containers/tests/test-build-images-flow.sh" \
  "$REPO_ROOT/scripts/containers/tests/test-run-smoke-diagnostics.sh" \
  "$REPO_ROOT/apps/ai-service/docker-entrypoint.sh" \
  "$REPO_ROOT/apps/ai-service/scripts/verify-runtime-image.sh" \
  "$REPO_ROOT/scripts/e2e/run-e2e.sh" \
  "$REPO_ROOT/scripts/e2e/test-sanitize-service-log.sh"; do
  bash -n "$script"
done

bash "$REPO_ROOT/scripts/containers/tests/test-build-images-args.sh"
bash "$REPO_ROOT/scripts/containers/tests/test-build-images-flow.sh"
bash "$REPO_ROOT/scripts/containers/tests/test-run-smoke-diagnostics.sh"
"$PYTHON_BIN" -m unittest discover \
  --start-directory "$TEST_DIR" \
  --pattern 'test_*.py' \
  --verbose
bash "$REPO_ROOT/scripts/e2e/test-sanitize-service-log.sh"

echo 'Fast container, Trivy evaluator, and sanitizer tests passed.'
