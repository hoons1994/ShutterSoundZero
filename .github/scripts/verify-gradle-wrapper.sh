#!/usr/bin/env bash
set -euo pipefail

# Official Gradle 9.8.0 wrapper checksum from services.gradle.org/distributions.
# Update this together with the wrapper; never derive the expected hash from the checkout.
printf '%s  %s\n' \
  '238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5' \
  'gradle/wrapper/gradle-wrapper.jar' | sha256sum --check --strict
