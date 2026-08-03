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
  'DATABASE_URL=jdbc:postgresql://db-user:jdbc-password@192.0.2.20:5432/private-db?sslmode=require' \
  'postgres_uri=postgresql://pg-user:postgres-password@database.example.test:5432/private-db' \
  'redis_uri=rediss://cache-user:redis-password@cache.example.test:6380/0' \
  'upstream=https://uri-user:uri-password@example.test/private' \
  'callback=https://example.test/callback?password=query-password&client_secret=query-secret&email=query@example.test' \
  'X-Internal-API-Key: disposable-internal-header-key' \
  'X-Serverless-Authorization: Bearer disposable-google-identity-token' \
  'AI_SERVICE_INTERNAL_API_KEY=disposable-ai-service-key' \
  '{"Authorization":"Bearer disposable-json-auth","Cookie":"access_token=disposable-json-cookie"}' \
  '{"Set-Cookie":"access_token=disposable-json-set-cookie; HttpOnly"}' \
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
  'opaque-subject' \
  'db-user' \
  'jdbc-password' \
  'private-db' \
  'pg-user' \
  'postgres-password' \
  'cache-user' \
  'redis-password' \
  'uri-user' \
  'uri-password' \
  'query-password' \
  'query-secret' \
  'query@example.test' \
  'disposable-internal-header-key' \
  'disposable-google-identity-token' \
  'disposable-ai-service-key' \
  'disposable-json-auth' \
  'disposable-json-cookie' \
  'disposable-json-set-cookie'; do
  if grep -Fq "$sensitive_value" "$OUTPUT"; then
    echo "Sensitive service-log fixture value was not redacted."
    exit 1
  fi
done

if ! grep -Fq 'request_id=123e4567-e89b-42d3-a456-426614174000' "$OUTPUT"; then
  echo "Generated request correlation ID was unexpectedly redacted."
  exit 1
fi

if [[ "$(grep -Fc '[REDACTED_CONNECTION_URL]' "$OUTPUT")" -ne 3 ]]; then
  echo "Expected JDBC, PostgreSQL, and Redis URLs to be redacted."
  exit 1
fi

echo "E2E service-log sanitizer self-test passed."
