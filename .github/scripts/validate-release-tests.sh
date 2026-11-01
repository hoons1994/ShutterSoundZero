#!/usr/bin/env bash
set -euo pipefail

# No signing credentials or packaging tasks. Retain dependency/compiler caches.
./gradlew testDebugUnitTest testReleaseUnitTest lintDebug lintRelease \
  -PreleaseValidation=true -Pandroid.onlyEnableUnitTestForTheTestedBuildType=false --no-daemon --stacktrace --console=plain
