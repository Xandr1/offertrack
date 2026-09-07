#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
. "$REPO_ROOT/scripts/lib/maven-wrapper.sh"
MAVEN_WRAPPER="$(resolve_maven_wrapper "$REPO_ROOT")"

cd "$REPO_ROOT/apps/core-api"
"$MAVEN_WRAPPER" test