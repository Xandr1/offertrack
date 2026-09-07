#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd -P)"
SPLITTER="$REPO_ROOT/scripts/osv/split-pnpm-lockfile.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/offertrack-osv-pnpm-lockfile.XXXXXX")"

cleanup() {
  if [[ "$TEST_ROOT" == "${TMPDIR:-/tmp}"/offertrack-osv-pnpm-lockfile.* && -d "$TEST_ROOT" ]]; then
    rm -rf -- "$TEST_ROOT"
  fi
}
trap cleanup EXIT

fixture="$TEST_ROOT/pnpm-lock.yaml"
printf '%s\n' \
  '---' \
  "lockfileVersion: '9.0'" \
  '' \
  'importers:' \
  '  .:' \
  '    configDependencies: {}' \
  '    packageManagerDependencies:' \
  '      pnpm:' \
  '        specifier: 12.3.4' \
  '        version: 12.3.4' \
  '' \
  'packages:' \
  '  pnpm@12.3.4: {}' \
  '---' \
  "lockfileVersion: '9.0'" \
  '' \
  'settings:' \
  '  autoInstallPeers: true' \
  '' \
  'importers:' \
  '  .:' \
  '    dependencies:' \
  '      example:' \
  '        specifier: 1.0.0' \
  '        version: 1.0.0' \
  '' \
  'packages:' \
  '  example@1.0.0: {}' \
  >"$fixture"

output_directory="$TEST_ROOT/split"
bash "$SPLITTER" "$fixture" "$output_directory"

environment_lockfile="$output_directory/environment/pnpm-lock.yaml"
project_lockfile="$output_directory/project/pnpm-lock.yaml"

for lockfile in "$environment_lockfile" "$project_lockfile"; do
  [[ -s "$lockfile" ]]
  [[ "$(grep -c -E '^---[[:space:]]*$' "$lockfile")" -eq 1 ]]
  grep -q -E '^lockfileVersion:' "$lockfile"
done

grep -q 'packageManagerDependencies:' "$environment_lockfile"
! grep -q '^settings:' "$environment_lockfile"
grep -q '^settings:' "$project_lockfile"
grep -q 'example@1.0.0:' "$project_lockfile"
! grep -q 'packageManagerDependencies:' "$project_lockfile"

malformed_lockfile="$TEST_ROOT/malformed-pnpm-lock.yaml"
printf '%s\n' "lockfileVersion: '9.0'" 'packages: {}' >"$malformed_lockfile"

if bash "$SPLITTER" "$malformed_lockfile" "$TEST_ROOT/malformed-output" >/dev/null 2>&1; then
  echo "Expected a malformed pnpm lockfile to be rejected." >&2
  exit 1
fi

[[ ! -e "$TEST_ROOT/malformed-output" ]]

echo 'pnpm OSV lockfile splitter tests passed.'
