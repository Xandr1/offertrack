#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage: prepare-deployment-images.sh --project ID --region REGION --repository NAME \
  --commit-sha SHA --core-public-url URL

Publishes only missing immutable staging image tags, resolves their exact registry
digests, and writes core_image, ai_image, and web_image to GITHUB_OUTPUT.
EOF
}

fail() {
  echo "prepare-deployment-images.sh: $*" >&2
  exit 1
}

project_id=""
region=""
repository=""
deploy_sha=""
core_public_url=""

while (($# > 0)); do
  case "$1" in
    --project)
      (($# >= 2)) || fail "--project requires a value"
      project_id="$2"
      shift 2
      ;;
    --region)
      (($# >= 2)) || fail "--region requires a value"
      region="$2"
      shift 2
      ;;
    --repository)
      (($# >= 2)) || fail "--repository requires a value"
      repository="$2"
      shift 2
      ;;
    --commit-sha)
      (($# >= 2)) || fail "--commit-sha requires a value"
      deploy_sha="$2"
      shift 2
      ;;
    --core-public-url)
      (($# >= 2)) || fail "--core-public-url requires a value"
      core_public_url="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      usage
      fail "unknown argument"
      ;;
  esac
done

for value in "$project_id" "$region" "$repository" "$core_public_url"; do
  [[ -n "$value" && "$value" != *$'\n'* && "$value" != *$'\r'* ]] ||
    fail "required arguments must be non-empty single-line values"
done
[[ "$deploy_sha" =~ ^[0-9a-f]{40}$ ]] || fail "--commit-sha must be a lowercase 40-character SHA"
[[ -n "${GITHUB_ENV:-}" && -n "${GITHUB_OUTPUT:-}" ]] ||
  fail "GITHUB_ENV and GITHUB_OUTPUT are required"

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
registry="${region}-docker.pkg.dev/${project_id}/${repository}"
public_config_hash="$(printf '%s\n' "staging" "$core_public_url" | sha256sum | cut -c1-16)"
web_build_tag="${deploy_sha}-${public_config_hash}"

image_exists() {
  local component="$1"
  local tag="$2"
  gcloud artifacts docker images describe "${registry}/${component}:${tag}" \
    --project "$project_id" --format='none' >/dev/null 2>&1
}

missing_components=()
for component in core-api ai-service; do
  if image_exists "$component" "$deploy_sha"; then
    echo "Reusing the existing immutable $component tag for this commit." >&2
  else
    missing_components+=("$component")
  fi
done

if ((${#missing_components[@]} > 0)); then
  component_list="$(IFS=,; echo "${missing_components[*]}")"
  bash "$repo_root/scripts/containers/build-images.sh" \
    --components "$component_list" \
    --tag "$deploy_sha"
  for component in "${missing_components[@]}"; do
    tagged_ref="${registry}/${component}:${deploy_sha}"
    docker tag "offertrack/${component}:${deploy_sha}" "$tagged_ref"
    docker push "$tagged_ref" >&2
  done
fi

if image_exists web "$web_build_tag"; then
  echo "Reusing the immutable Web tag for this commit and public build configuration." >&2
else
  bash "$repo_root/scripts/containers/build-images.sh" \
    --components web \
    --app-env staging \
    --next-public-api-url "$core_public_url" \
    --tag "$web_build_tag"
  web_tagged_ref="${registry}/web:${web_build_tag}"
  docker tag "offertrack/web:${web_build_tag}" "$web_tagged_ref"
  docker push "$web_tagged_ref" >&2
fi

resolve_digest() {
  local component="$1"
  local tag="$2"
  local tagged_ref="${registry}/${component}:${tag}"
  local expected_prefix="${registry}/${component}@sha256:"
  local digest_ref

  # A sentinel retains newlines so extra registry output cannot be silently trimmed.
  digest_ref="$(gcloud artifacts docker images describe "$tagged_ref" \
    --project "$project_id" \
    --format='value(image_summary.fully_qualified_digest)' && printf '.')" || {
    echo "Unable to resolve the immutable $component image." >&2
    return 1
  }
  digest_ref="${digest_ref%.}"
  digest_ref="${digest_ref%$'\n'}"
  if [[ -z "$digest_ref" || "$digest_ref" == *$'\n'* || \
        "$digest_ref" != "$expected_prefix"* || \
        ! "${digest_ref#"$expected_prefix"}" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Invalid registry digest for $component." >&2
    return 1
  fi
  printf '%s' "$digest_ref"
}

core_image="$(resolve_digest core-api "$deploy_sha")"
ai_image="$(resolve_digest ai-service "$deploy_sha")"
web_image="$(resolve_digest web "$web_build_tag")"
printf '%s\n' \
  "CORE_IMAGE=$core_image" \
  "AI_IMAGE=$ai_image" \
  "WEB_IMAGE=$web_image" \
  "WEB_BUILD_TAG=$web_build_tag" >> "$GITHUB_ENV"
printf '%s\n' \
  "core_image=$core_image" \
  "ai_image=$ai_image" \
  "web_image=$web_image" >> "$GITHUB_OUTPUT"
