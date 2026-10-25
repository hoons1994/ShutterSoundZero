#!/usr/bin/env bash
set -euo pipefail

REPORT_DIR="${1:?Specify a directory for stability logs}"
SERIAL="$(adb devices | awk '$2 == "device" && $1 ~ /^emulator-/ { print $1; exit }')"
test -n "$SERIAL"
test "$(adb -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" = '1'
test "$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" = '35'
mkdir -p "$REPORT_DIR"
# connectedDebugAndroidTest removes its packages after the managed test run.
# Reinstall the exact APKs it built before invoking the runner directly.
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_APKS=("$ROOT"/app/build/outputs/apk/debug/*.apk)
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
test "${#APP_APKS[@]}" -eq 1
test -s "${APP_APKS[0]}"
test -s "$TEST_APK"
adb -s "$SERIAL" install -r -t "${APP_APKS[0]}"
adb -s "$SERIAL" install -r -t "$TEST_APK"
CLASSES='io.github.hoons1994.shuttersoundzero.security.AppLockAuthenticationInstrumentedTest,io.github.hoons1994.shuttersoundzero.core.WifiConnectionStatusInstrumentedTest'
RUNNER='io.github.hoons1994.shuttersoundzero.test/androidx.test.runner.AndroidJUnitRunner'
for attempt in 1 2; do
  log="$REPORT_DIR/api35-stability-$attempt.log"
  adb -s "$SERIAL" shell am instrument -w -r -e class "$CLASSES" "$RUNNER" \
    | tr -d '\r' | tee "$log"
  # am instrument can exit zero even when JUnit fails. Require its actual result,
  # including no ignored/assumption-failed tests in this controlled emulator fixture.
  if ! grep -Eq '^OK \([1-9][0-9]* tests?\)' "$log" ||
      grep -Eq '^INSTRUMENTATION_STATUS_CODE: -[1-4]$|FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED' "$log"; then
    echo "API 35 stability iteration $attempt failed or skipped tests."
    exit 1
  fi
done
