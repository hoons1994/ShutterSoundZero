#!/usr/bin/env bash
set -euo pipefail

RES_DIR="app/src/main/res"
MANIFEST="app/src/main/AndroidManifest.xml"

for forbidden in \
  "$RES_DIR/mipmap-anydpi/ic_launcher.xml" \
  "$RES_DIR/mipmap-anydpi/ic_launcher_round.xml" \
  "$RES_DIR/drawable/ic_launcher_background.xml"; do
  if [ -e "$forbidden" ]; then
    echo "Unexpected adaptive/alternate launcher resource remains: $forbidden"
    exit 1
  fi
done

if find "$RES_DIR" -type f \( -name 'ic_launcher_foreground.*' -o -name 'ic_launcher_round.*' \) -print | grep -q .; then
  echo 'Alternate launcher icon layers/resources must not exist. Use only the complete ic_launcher image.'
  find "$RES_DIR" -type f \( -name 'ic_launcher_foreground.*' -o -name 'ic_launcher_round.*' \) -print
  exit 1
fi

for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  icon="$RES_DIR/mipmap-$density/ic_launcher.webp"
  if [ ! -s "$icon" ]; then
    echo "Missing complete launcher icon: $icon"
    exit 1
  fi
done

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

echo 'Complete launcher icon resource layout verified.'
