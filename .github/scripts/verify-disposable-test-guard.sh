#!/usr/bin/env bash
set -euo pipefail

# Only called on CI's newly created, disposable AVD. Never run on a user's device.
SERIAL="${1:?Explicit disposable emulator serial required}"
REPORT_DIR="${2:?Log directory required}"
[[ "$SERIAL" == emulator-* ]]
test "$(adb -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" = 1
test -n "${DISPOSABLE_TEST_TOKEN:?CI run token required}"
APP='io.github.hoons1994.shuttersoundzero'
RUNNER="$APP.test/$APP.testing.DisposableAndroidJUnitRunner"
APP_APKS=(app/build/outputs/apk/debug/*.apk)
test "${#APP_APKS[@]}" -eq 1
# Refuse to install over a pre-existing app, even on an emulator.
if adb -s "$SERIAL" shell pm path "$APP" | grep -q '^package:'; then
  echo 'Guard probe requires an AVD without an installed app.'
  exit 1
fi
adb -s "$SERIAL" install -t "${APP_APKS[0]}"
adb -s "$SERIAL" install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p "$REPORT_DIR"
adb -s "$SERIAL" shell run-as "$APP" mkdir -p files
printf 'preserve-existing-identity' | adb -s "$SERIAL" shell run-as "$APP" tee files/adb_private_key.der >/dev/null
for mode in no-token existing-data; do
  args=()
  if [[ "$mode" == existing-data ]]; then
    args=(-e disposableTestToken "$DISPOSABLE_TEST_TOKEN")
  fi
  log="$REPORT_DIR/guard-$mode.log"
  adb -s "$SERIAL" shell am instrument -w -r "${args[@]}" \
    -e class "$APP.core.adb.AdbKeyHelperInstrumentedTest,$APP.receiver.BootReceiverInstrumentedTest" "$RUNNER" \
    > "$log" 2>&1 || true # Instrumentation failure is expected; inspect evidence below.
  grep -Eq 'Disposable emulator and explicit|Existing app data must be preserved' "$log"
  if grep -Eq '^OK \(|INSTRUMENTATION_STATUS: test=' "$log"; then
    echo 'Unsafe fixture entered a test method.'
    exit 1
  fi
  test "$(adb -s "$SERIAL" shell run-as "$APP" cat files/adb_private_key.der | tr -d '\r')" = 'preserve-existing-identity'
done
# These two packages were installed by this probe on the verified empty AVD.
adb -s "$SERIAL" uninstall "$APP.test"
adb -s "$SERIAL" uninstall "$APP"
