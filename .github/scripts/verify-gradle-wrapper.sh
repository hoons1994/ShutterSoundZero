#!/usr/bin/env bash
set -euo pipefail

# Official Gradle 9.8.1 wrapper checksum from services.gradle.org/distributions.
# Update this together with the wrapper; never derive the expected hash from the checkout.
printf '%s  %s\n' \
  '3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98' \
  'gradle/wrapper/gradle-wrapper.jar' | sha256sum --check --strict
