#!/usr/bin/env bash
set -euo pipefail

# Official Gradle 9.9.2 wrapper checksum from services.gradle.org/distributions.
# Update this together with the wrapper; never derive the expected hash from the checkout.
printf '%s  %s\n' \
  '621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8' \
  'gradle/wrapper/gradle-wrapper.jar' | sha256sum --check --strict
