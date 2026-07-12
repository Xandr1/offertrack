#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "Linux is required to regenerate the CI/runtime pylock.toml." >&2
  exit 1
fi

if [[ "$(python --version 2>&1)" != Python\ 3.11.* ]]; then
  echo "Python 3.11 is required to regenerate pylock.toml." >&2
  exit 1
fi

python -m pip install --upgrade "pip==26.1.2"
python -m pip lock --output pylock.toml ".[test]"
# pip 26.1.2 cannot hash-verify its local-directory entry when consuming pylock.toml.
python scripts/strip-local-project-from-lock.py
sha256sum pyproject.toml > pylock.toml.sha256
