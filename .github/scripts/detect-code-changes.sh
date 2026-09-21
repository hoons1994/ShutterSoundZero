#!/usr/bin/env bash

set -euo pipefail

output_file="${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"
event_name="${GITHUB_EVENT_NAME:-}"

# Manual and scheduled runs are explicit requests to run the full check.
if [[ "$event_name" == "workflow_dispatch" || "$event_name" == "schedule" ]]; then
  echo 'code_changed=true' >> "$output_file"
  exit 0
fi

changed_files="$(mktemp)"
trap 'rm -f "$changed_files"' EXIT

diff_succeeded=true
if [[ "$event_name" == "pull_request" ]]; then
  base_sha="${PR_BASE_SHA:-}"
  if [[ -z "$base_sha" ]]; then
    diff_succeeded=false
  elif ! git diff --name-only "$base_sha" "$GITHUB_SHA" > "$changed_files"; then
    diff_succeeded=false
  fi
elif [[ "$event_name" == "push" ]]; then
  before_sha="${PRIOR_SHA:-}"
  if [[ -z "$before_sha" || "$before_sha" =~ ^0+$ ]]; then
    git ls-files > "$changed_files"
  elif ! git diff --name-only "$before_sha" "$GITHUB_SHA" > "$changed_files"; then
    diff_succeeded=false
  fi
else
  diff_succeeded=false
fi

# Failure to resolve the changed-file set fails open so a code change cannot
# accidentally bypass the required checks.
if [[ "$diff_succeeded" != true ]]; then
  echo 'code_changed=true' >> "$output_file"
  exit 0
fi

code_changed=false
while IFS= read -r path; do
  [[ -z "$path" ]] && continue

  case "$path" in
    # Application, build, dependency, and CI files always require code checks.
    app/*|gradle/*|build.gradle.kts|settings.gradle.kts|gradle.properties|gradlew|gradlew.bat|.github/workflows/*|.github/scripts/*|.github/dependabot.yml|.github/release-signing-certificate.sha256)
      code_changed=true
      break
      ;;
    # Documentation and standalone visual/reference assets do not require them.
    README*|SECURITY.md|LICENSE*|CONTRIBUTING*|CHANGELOG*|NOTICE*|*.md|*.mdx|*.adoc|*.rst|*.pdf|*.doc|*.docx|*.ppt|*.pptx|docs/*|documentation/*|images/*|img/*|infographics/*|media/*|.github/ISSUE_TEMPLATE/*|.github/PULL_REQUEST_TEMPLATE*|.github/release-notes/*|*.png|*.jpg|*.jpeg|*.gif|*.webp|*.avif|*.bmp|*.tif|*.tiff|*.svg)
      ;;
    *)
      code_changed=true
      break
      ;;
  esac
done < "$changed_files"

echo "code_changed=$code_changed" >> "$output_file"
