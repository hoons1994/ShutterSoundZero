#!/usr/bin/env bash
set -euo pipefail

CURRENT_APK="${1:?Current APK path is required}"
APKSIGNER="${2:?apksigner path is required}"
EXPECTED_DIGEST_FILE="${3:?Expected signer digest file is required}"

if [ ! -f "$CURRENT_APK" ]; then
  echo "Current APK was not found: $CURRENT_APK"
  exit 1
fi

if [ ! -f "$APKSIGNER" ]; then
  echo "apksigner was not found: $APKSIGNER"
  exit 1
fi

if [ ! -f "$EXPECTED_DIGEST_FILE" ]; then
  echo "Expected signer digest file was not found: $EXPECTED_DIGEST_FILE"
  exit 1
fi

extract_signer_digest() {
  local apk="$1"
  local certificate_output
  local digest

  certificate_output="$("$APKSIGNER" verify --verbose --print-certs "$apk")"

  # Build Tools 36 and older commonly emit:
  #   Signer #1 certificate SHA-256 digest: <digest>
  # Build Tools 37 may emit a scheme-qualified prefix instead:
  #   V3.0 Signer: certificate SHA-256 digest: <digest>
  # Match the stable field label rather than the version-specific prefix.
  digest="$(
    printf '%s\n' "$certificate_output" \
      | awk -F'certificate SHA-256 digest: ' 'NF > 1 { print $2; exit }' \
      | tr '[:upper:]' '[:lower:]' \
      | tr -d '[:space:]'
  )"

  if [[ ! "$digest" =~ ^[0-9a-f]{64}$ ]]; then
    return 0
  fi

  printf '%s\n' "$digest"
}

CURRENT_DIGEST="$(extract_signer_digest "$CURRENT_APK")"
EXPECTED_DIGEST="$(
  tr '[:upper:]' '[:lower:]' < "$EXPECTED_DIGEST_FILE" \
    | tr -d '[:space:]:'
)"

if [ -z "$CURRENT_DIGEST" ]; then
  echo 'Unable to extract the APK signing certificate SHA-256 digest.'
  exit 1
fi

if [[ ! "$EXPECTED_DIGEST" =~ ^[0-9a-f]{64}$ ]]; then
  echo 'Pinned signing certificate SHA-256 digest is invalid.'
  exit 1
fi

if [ "$CURRENT_DIGEST" != "$EXPECTED_DIGEST" ]; then
  echo 'Release signing certificate does not match the pinned certificate.'
  echo "Expected signer SHA-256: $EXPECTED_DIGEST"
  echo "Current signer SHA-256:  $CURRENT_DIGEST"
  exit 1
fi

echo 'Release signer matches the pinned signing certificate.'
echo "Signer certificate SHA-256: $CURRENT_DIGEST"
