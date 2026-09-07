#!/usr/bin/env bash

resolve_maven_wrapper() {
  local repo_root="$1"
  local wrapper_name="mvnw"

  repo_root="$(cd -- "$repo_root" && pwd -P)"

  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) wrapper_name="mvnw.cmd" ;;
  esac

  printf '%s/apps/core-api/%s\n' "$repo_root" "$wrapper_name"
}