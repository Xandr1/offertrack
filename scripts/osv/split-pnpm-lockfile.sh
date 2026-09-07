#!/usr/bin/env bash

set -euo pipefail

usage() {
  echo "Usage: split-pnpm-lockfile.sh INPUT_LOCKFILE OUTPUT_DIRECTORY" >&2
}

fail() {
  echo "split-pnpm-lockfile.sh: $*" >&2
  exit 2
}

if [[ $# -ne 2 ]]; then
  usage
  exit 2
fi

input_lockfile="$1"
output_directory="$2"

[[ -f "$input_lockfile" ]] || fail "input lockfile does not exist: $input_lockfile"
[[ ! -e "$output_directory" ]] || fail "output directory already exists: $output_directory"

output_parent="$(dirname -- "$output_directory")"
[[ -d "$output_parent" ]] || fail "output parent does not exist: $output_parent"

mapfile -t document_starts < <(
  grep -n -E '^---[[:space:]]*$' "$input_lockfile" | cut -d: -f1 || true
)

if [[ ${#document_starts[@]} -ne 2 || "${document_starts[0]}" -ne 1 ]]; then
  fail "expected pnpm 12's two-document lockfile layout"
fi

temporary_directory="$(mktemp -d "$output_parent/.offertrack-osv-pnpm-lockfiles.XXXXXX")"
cleanup() {
  if [[ -n "$temporary_directory" && -d "$temporary_directory" ]]; then
    rm -rf -- "$temporary_directory"
  fi
}
trap cleanup EXIT

mkdir -p "$temporary_directory/environment" "$temporary_directory/project"
second_document_start="${document_starts[1]}"

awk -v end="$second_document_start" 'NR < end { print }' "$input_lockfile" \
  >"$temporary_directory/environment/pnpm-lock.yaml"
awk -v start="$second_document_start" 'NR >= start { print }' "$input_lockfile" \
  >"$temporary_directory/project/pnpm-lock.yaml"

environment_lockfile="$temporary_directory/environment/pnpm-lock.yaml"
project_lockfile="$temporary_directory/project/pnpm-lock.yaml"

for lockfile in "$environment_lockfile" "$project_lockfile"; do
  [[ -s "$lockfile" ]] || fail "split lockfile is empty: $lockfile"
  [[ "$(grep -c -E '^---[[:space:]]*$' "$lockfile" || true)" -eq 1 ]] \
    || fail "split lockfile must contain exactly one YAML document: $lockfile"
  grep -q -E '^lockfileVersion:' "$lockfile" \
    || fail "split lockfile is missing lockfileVersion: $lockfile"
done

grep -q -E '^[[:space:]]+packageManagerDependencies:' "$environment_lockfile" \
  || fail "environment lockfile is missing package manager metadata"
grep -q -E '^settings:' "$project_lockfile" \
  || fail "project lockfile is missing dependency graph settings"

mv "$temporary_directory" "$output_directory"
temporary_directory=""
trap - EXIT
