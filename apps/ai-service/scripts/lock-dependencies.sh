#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "Linux is required to regenerate the Python dependency locks." >&2
  exit 1
fi

if [[ "$(python --version 2>&1)" != Python\ 3.11.* ]]; then
  echo "Python 3.11 is required to regenerate the Python dependency locks." >&2
  exit 1
fi

python -m pip install --upgrade "pip==26.1.2"

generate_lock() {
  local lock_path="$1"
  local project_requirement="$2"

  python -m pip lock --output "$lock_path" "$project_requirement"
  # pip 26.1.2 cannot hash-verify its local-directory entry when consuming the lock.
  python scripts/strip-local-project-from-lock.py "$lock_path"
  sha256sum pyproject.toml "$lock_path" > "${lock_path}.sha256"
}

generate_lock pylock.toml "."
generate_lock pylock.test.toml ".[test]"
