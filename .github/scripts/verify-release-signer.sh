#!/usr/bin/env bash
set -euo pipefail

CURRENT_APK="${1:?Current APK path is required}"
APKSIGNER="${2:?apksigner path is required}"
CURRENT_TAG="${3:-}"

if [ ! -f "$CURRENT_APK" ]; then
  echo "Current APK was not found: $CURRENT_APK"
  exit 1
fi

if [ ! -x "$APKSIGNER" ]; then
  echo "apksigner was not found or is not executable: $APKSIGNER"
  exit 1
fi

if [ -z "${GITHUB_REPOSITORY:-}" ]; then
  echo 'GITHUB_REPOSITORY is not set.'
  exit 1
fi

PREVIOUS_TAG=''
while IFS= read -r tag; do
  if [ -n "$tag" ] && [ "$tag" != "$CURRENT_TAG" ]; then
    PREVIOUS_TAG="$tag"
    break
  fi
done < <(
  gh api "repos/$GITHUB_REPOSITORY/releases?per_page=100" \
    --jq '.[] | select(.draft == false and .prerelease == false) | .tag_name'
)

if [ -z "$PREVIOUS_TAG" ]; then
  echo 'No previous stable GitHub Release was found for signer continuity verification.'
  exit 1
fi

PREVIOUS_DIR="$RUNNER_TEMP/previous-release-signer-check"
rm -rf "$PREVIOUS_DIR"
mkdir -p "$PREVIOUS_DIR"

gh release download "$PREVIOUS_TAG" \
  --repo "$GITHUB_REPOSITORY" \
  --pattern '*.apk' \
  --dir "$PREVIOUS_DIR"

mapfile -t PREVIOUS_APKS < <(find "$PREVIOUS_DIR" -maxdepth 1 -type f -name '*.apk' -print | sort)
if [ "${#PREVIOUS_APKS[@]}" -ne 1 ]; then
  echo "Expected exactly one APK in release $PREVIOUS_TAG, found ${#PREVIOUS_APKS[@]}."
  exit 1
fi

PREVIOUS_APK="${PREVIOUS_APKS[0]}"

extract_signer_digest() {
  local apk="$1"
  local certificate_output
  certificate_output="$("$APKSIGNER" verify --verbose --print-certs "$apk")"
  printf '%s\n' "$certificate_output" \
    | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
    | tr '[:upper:]' '[:lower:]' \
    | tr -d '[:space:]'
}

CURRENT_DIGEST="$(extract_signer_digest "$CURRENT_APK")"
PREVIOUS_DIGEST="$(extract_signer_digest "$PREVIOUS_APK")"

if [ -z "$CURRENT_DIGEST" ] || [ -z "$PREVIOUS_DIGEST" ]; then
  echo 'Unable to extract APK signing certificate SHA-256 digest.'
  exit 1
fi

if [ "$CURRENT_DIGEST" != "$PREVIOUS_DIGEST" ]; then
  echo "Release signing certificate mismatch with previous official release $PREVIOUS_TAG."
  echo "Previous signer SHA-256: $PREVIOUS_DIGEST"
  echo "Current signer SHA-256:  $CURRENT_DIGEST"
  exit 1
fi

echo "Release signer continuity verified against $PREVIOUS_TAG."
echo "Signer certificate SHA-256: $CURRENT_DIGEST"
