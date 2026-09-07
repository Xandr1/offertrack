#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
HELPER="$REPO_ROOT/scripts/lib/maven-wrapper.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/offertrack-maven-wrapper.XXXXXX")"

cleanup() {
  local exit_code=$?
  trap - EXIT
  case "$TEST_ROOT" in
    "${TMPDIR:-/tmp}"/offertrack-maven-wrapper.*) rm -rf -- "$TEST_ROOT" ;;
    *)
      echo "Refusing to remove unexpected test directory: $TEST_ROOT" >&2
      exit_code=1
      ;;
  esac
  exit "$exit_code"
}
trap cleanup EXIT

run_selection_case() {
  local os_name="$1"
  local expected_wrapper="$2"
  local working_directory="$3"

  (
    cd "$working_directory"
    uname() { printf '%s\n' "$os_name"; }
    . "$HELPER"
    resolved_wrapper="$(resolve_maven_wrapper "$REPO_ROOT")"
    expected_path="$REPO_ROOT/apps/core-api/$expected_wrapper"

    [[ "$resolved_wrapper" == "$expected_path" ]] || {
      echo "Expected $os_name to resolve to $expected_path, got $resolved_wrapper" >&2
      exit 1
    }
    [[ "$resolved_wrapper" == /* ]] || {
      echo "Expected an absolute path, got $resolved_wrapper" >&2
      exit 1
    }
  )
}

mkdir -p -- "$TEST_ROOT/first" "$TEST_ROOT/second"
run_selection_case Linux mvnw "$TEST_ROOT/first"
run_selection_case Darwin mvnw "$TEST_ROOT/second"
run_selection_case MINGW64_NT mvnw.cmd "$TEST_ROOT/first"
run_selection_case MSYS_NT mvnw.cmd "$TEST_ROOT/second"
run_selection_case CYGWIN_NT mvnw.cmd "$TEST_ROOT/first"

echo 'Maven wrapper selection tests passed.'