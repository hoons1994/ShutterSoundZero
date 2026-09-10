#!/usr/bin/env bash
set -euo pipefail

RES_DIR="app/src/main/res"
MANIFEST="app/src/main/AndroidManifest.xml"
ROOT_ICON="app_icon_512.png"
LAUNCHER_ICON="$RES_DIR/mipmap-nodpi/ic_launcher.png"

for forbidden in \
  "$RES_DIR/mipmap-anydpi/ic_launcher.xml" \
  "$RES_DIR/mipmap-anydpi/ic_launcher_round.xml" \
  "$RES_DIR/drawable/ic_launcher_background.xml"; do
  if [ -e "$forbidden" ]; then
    echo "Unexpected adaptive/alternate launcher resource remains: $forbidden"
    exit 1
  fi
done

if find "$RES_DIR" -type f \( \
  -name 'ic_launcher_foreground.*' -o \
  -name 'ic_launcher_round.*' -o \
  -path '*/mipmap-mdpi/ic_launcher.*' -o \
  -path '*/mipmap-hdpi/ic_launcher.*' -o \
  -path '*/mipmap-xhdpi/ic_launcher.*' -o \
  -path '*/mipmap-xxhdpi/ic_launcher.*' -o \
  -path '*/mipmap-xxxhdpi/ic_launcher.*' \
\) -print | grep -q .; then
  echo 'Alternate launcher icon resources must not exist. Use only the root-derived mipmap-nodpi icon.'
  find "$RES_DIR" -type f \( \
    -name 'ic_launcher_foreground.*' -o \
    -name 'ic_launcher_round.*' -o \
    -path '*/mipmap-mdpi/ic_launcher.*' -o \
    -path '*/mipmap-hdpi/ic_launcher.*' -o \
    -path '*/mipmap-xhdpi/ic_launcher.*' -o \
    -path '*/mipmap-xxhdpi/ic_launcher.*' -o \
    -path '*/mipmap-xxxhdpi/ic_launcher.*' \
  \) -print
  exit 1
fi

if [ ! -s "$ROOT_ICON" ]; then
  echo "Missing root source icon: $ROOT_ICON"
  exit 1
fi

if [ ! -s "$LAUNCHER_ICON" ]; then
  echo "Missing launcher icon copied from root source: $LAUNCHER_ICON"
  exit 1
fi

ROOT_SHA="$(sha256sum "$ROOT_ICON" | awk '{print $1}')"
LAUNCHER_SHA="$(sha256sum "$LAUNCHER_ICON" | awk '{print $1}')"
if [ "$ROOT_SHA" != "$LAUNCHER_SHA" ]; then
  echo 'Launcher icon bytes differ from root app_icon_512.png.'
  echo "root=$ROOT_SHA"
  echo "launcher=$LAUNCHER_SHA"
  exit 1
fi

if ! grep -Fq 'android:icon="@mipmap/ic_launcher"' "$MANIFEST"; then
  echo 'AndroidManifest.xml must use @mipmap/ic_launcher as the application icon.'
  exit 1
fi

if ! grep -Fq 'android:roundIcon="@mipmap/ic_launcher"' "$MANIFEST"; then
  echo 'AndroidManifest.xml must point roundIcon to the same complete @mipmap/ic_launcher resource.'
  exit 1
fi

if grep -Eq '@(mipmap/ic_launcher_foreground|mipmap/ic_launcher_round|drawable/ic_launcher_background)' "$MANIFEST"; then
  echo 'AndroidManifest.xml still references an alternate launcher icon resource.'
  exit 1
fi

echo "Root launcher icon verified: $ROOT_SHA"
