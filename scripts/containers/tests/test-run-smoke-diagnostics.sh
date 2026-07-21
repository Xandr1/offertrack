#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd -P)"
RUN_SMOKE="$REPO_ROOT/scripts/containers/run-smoke.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/offertrack-smoke-diagnostics.XXXXXX")"

cleanup() {
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT

FAKE_BIN="$TEST_ROOT/fake-bin"
mkdir -p -- "$FAKE_BIN"
cat >"$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' invoked >"$FAKE_DOCKER_MARKER"
printf '%s\n' 'fake docker reached' >&2
exit 77
EOF
chmod +x "$FAKE_BIN/docker"
cat >"$FAKE_BIN/python3" <<'EOF'
#!/usr/bin/env bash
[[ "${1:-}" == -c ]] && exit 0
exit 78
EOF
chmod +x "$FAKE_BIN/python3"

make_fixture() {
  local fixture="$1"
  mkdir -p -- "$fixture/scripts/containers"
  cp -- "$RUN_SMOKE" "$fixture/scripts/containers/run-smoke.sh"
}

link_directory() {
  local target="$1"
  local link="$2"
  if [[ "$(uname -s)" == MINGW* ]]; then
    local windows_target windows_link
    windows_target="$(cygpath -w "$target")"
    windows_link="$(cygpath -w "$link")"
    MSYS_NO_PATHCONV=1 cmd.exe /d /c mklink /J "$windows_link" "$windows_target" \
      >/dev/null
  else
    ln -s -- "$target" "$link"
  fi
  [[ -L "$link" ]] || {
    echo 'Unable to create a directory symlink for the diagnostics regression test.' >&2
    exit 1
  }
}

SYMLINK_FIXTURE="$TEST_ROOT/symlink-repo"
EXTERNAL_ROOT="$TEST_ROOT/external"
SYMLINK_MARKER="$TEST_ROOT/symlink-docker.marker"
make_fixture "$SYMLINK_FIXTURE"
mkdir -p -- "$EXTERNAL_ROOT/container-smoke"
printf '%s\n' 'do not remove' >"$EXTERNAL_ROOT/container-smoke/canary.txt"
link_directory "$EXTERNAL_ROOT" "$SYMLINK_FIXTURE/test-results"

set +e
FAKE_DOCKER_MARKER="$SYMLINK_MARKER" PATH="$FAKE_BIN:$PATH" \
  bash "$SYMLINK_FIXTURE/scripts/containers/run-smoke.sh" --no-build \
  >"$TEST_ROOT/symlink.stdout" 2>"$TEST_ROOT/symlink.stderr"
SYMLINK_STATUS=$?
set -e

[[ "$SYMLINK_STATUS" -ne 0 ]] || {
  echo 'Expected symlinked test-results to fail closed.' >&2
  exit 1
}
[[ -L "$SYMLINK_FIXTURE/test-results" ]] || {
  echo 'The test-results symlink was removed.' >&2
  exit 1
}
[[ -d "$EXTERNAL_ROOT/container-smoke" ]] || {
  echo 'The external diagnostics directory was removed.' >&2
  exit 1
}
[[ "$(<"$EXTERNAL_ROOT/container-smoke/canary.txt")" == 'do not remove' ]] || {
  echo 'The external diagnostics canary was changed.' >&2
  exit 1
}
[[ ! -e "$SYMLINK_MARKER" ]] || {
  echo 'The script continued past unsafe diagnostics validation.' >&2
  exit 1
}
grep -q 'test-results must not be a symlink' "$TEST_ROOT/symlink.stderr"

FINAL_LINK_FIXTURE="$TEST_ROOT/final-link-repo"
FINAL_EXTERNAL_ROOT="$TEST_ROOT/final-external"
FINAL_LINK_MARKER="$TEST_ROOT/final-link-docker.marker"
make_fixture "$FINAL_LINK_FIXTURE"
mkdir -p -- "$FINAL_LINK_FIXTURE/test-results" "$FINAL_EXTERNAL_ROOT"
printf '%s\n' 'do not remove' >"$FINAL_EXTERNAL_ROOT/canary.txt"
link_directory "$FINAL_EXTERNAL_ROOT" \
  "$FINAL_LINK_FIXTURE/test-results/container-smoke"

set +e
FAKE_DOCKER_MARKER="$FINAL_LINK_MARKER" PATH="$FAKE_BIN:$PATH" \
  bash "$FINAL_LINK_FIXTURE/scripts/containers/run-smoke.sh" --no-build \
  >"$TEST_ROOT/final-link.stdout" 2>"$TEST_ROOT/final-link.stderr"
FINAL_LINK_STATUS=$?
set -e

[[ "$FINAL_LINK_STATUS" -ne 0 ]] || {
  echo 'Expected symlinked container-smoke diagnostics to fail closed.' >&2
  exit 1
}
[[ -L "$FINAL_LINK_FIXTURE/test-results/container-smoke" ]] || {
  echo 'The container-smoke symlink was removed.' >&2
  exit 1
}
[[ "$(<"$FINAL_EXTERNAL_ROOT/canary.txt")" == 'do not remove' ]] || {
  echo 'The final-path external diagnostics canary was changed.' >&2
  exit 1
}
[[ ! -e "$FINAL_LINK_MARKER" ]] || {
  echo 'The script continued past unsafe final diagnostics validation.' >&2
  exit 1
}
grep -q 'container-smoke diagnostics must not be a symlink' \
  "$TEST_ROOT/final-link.stderr"

NORMAL_FIXTURE="$TEST_ROOT/normal-repo"
NORMAL_MARKER="$TEST_ROOT/normal-docker.marker"
make_fixture "$NORMAL_FIXTURE"

set +e
FAKE_DOCKER_MARKER="$NORMAL_MARKER" PATH="$FAKE_BIN:$PATH" \
  bash "$NORMAL_FIXTURE/scripts/containers/run-smoke.sh" --no-build \
  >"$TEST_ROOT/normal.stdout" 2>"$TEST_ROOT/normal.stderr"
NORMAL_STATUS=$?
set -e

[[ "$NORMAL_STATUS" -eq 77 ]] || {
  echo "Expected valid diagnostics path to reach fake Docker (status 77), got $NORMAL_STATUS." >&2
  cat "$TEST_ROOT/normal.stderr" >&2
  exit 1
}
[[ -f "$NORMAL_MARKER" ]] || {
  echo 'The normal repository-local diagnostics path was not accepted.' >&2
  exit 1
}
[[ -d "$NORMAL_FIXTURE/test-results" && ! -L "$NORMAL_FIXTURE/test-results" ]]

echo 'Smoke diagnostics symlink regression test passed.'
