#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SANITIZER="$REPO_ROOT/scripts/e2e/sanitize-service-log.py"
RUNTIME_ROOT="${TMPDIR:-/tmp}"
RUNTIME_DIR="$(mktemp -d "$RUNTIME_ROOT/offertrack-log-sanitizer.XXXXXX")"

cleanup() {
  if [[ "$RUNTIME_DIR" == "$RUNTIME_ROOT"/offertrack-log-sanitizer.* && -d "$RUNTIME_DIR" ]]; then
    rm -rf -- "$RUNTIME_DIR"
  fi
}
trap cleanup EXIT

if command -v python3 >/dev/null 2>&1 &&
  python3 -c 'raise SystemExit(0)' >/dev/null 2>&1; then
  PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1 &&
  python -c 'raise SystemExit(0)' >/dev/null 2>&1; then
  PYTHON_BIN="python"
else
  echo "Python is required to test E2E service-log sanitization."
  exit 1
fi

INPUT="$RUNTIME_DIR/input.log"
OUTPUT="$RUNTIME_DIR/output.log"

printf '%s\n' \
  'request_id=123e4567-e89b-42d3-a456-426614174000 user_id=00000000-0000-0000-0000-000000000001' \
  'email=person@example.test source=192.0.2.10 upstream=[2001:db8::10]:443' \
  'password=disposable-password Authorization: Bearer disposable-access-token' \
  'Cookie: access_token=disposable-cookie; X-CSRF-TOKEN: disposable-csrf' \
  'target=https://example.test/reset?reset_token=disposable-reset-token&code=disposable-code' \
  'unlabelled eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkaXNwb3NhYmxlIn0.disposable-signature' \
  'GOOGLE_CLIENT_ID=disposable-oauth-client' \
  'session_id=opaque-session applicationId=opaque-application subject-id=opaque-subject' \
  >"$INPUT"

"$PYTHON_BIN" "$SANITIZER" "$INPUT" "$OUTPUT"

for sensitive_value in \
  '00000000-0000-0000-0000-000000000001' \
  'person@example.test' \
  '192.0.2.10' \
  '2001:db8::10' \
  'disposable-password' \
  'disposable-access-token' \
  'disposable-cookie' \
  'disposable-csrf' \
  'disposable-reset-token' \
  'disposable-code' \
  'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkaXNwb3NhYmxlIn0.disposable-signature' \
  'disposable-oauth-client' \
  'opaque-session' \
  'opaque-application' \
  'opaque-subject'; do
  if grep -Fq "$sensitive_value" "$OUTPUT"; then
    echo "Sensitive service-log fixture value was not redacted."
    exit 1
  fi
done

if ! grep -Fq 'request_id=123e4567-e89b-42d3-a456-426614174000' "$OUTPUT"; then
  echo "Generated request correlation ID was unexpectedly redacted."
  exit 1
fi

echo "E2E service-log sanitizer self-test passed."
