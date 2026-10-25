#!/usr/bin/env bash
set -euo pipefail

# Compile the actual production Java/JNI/C sources with test-only entropy controls.
# This library is separate from the APK and tests the Linux getrandom path as well.
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/build/native-spake2-linux"
CPP="$ROOT/app/src/main/cpp"
TESTS="$ROOT/app/src/nativeTest"
: "${JAVA_HOME:?JDK 17 is required}"
ANNOTATIONS="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/androidx.annotation/annotation-jvm" -name '*.jar' -print -quit)"
test -s "$ANNOTATIONS"
mkdir -p "$OUT"
for source in sha512 spake2 secure_random; do
  gcc -fPIC -DSPAKE2_RANDOM_TESTING -I "$CPP/spake2-c/include" \
    -c "$CPP/spake2-c/$source.c" -o "$OUT/$source.o"
done
g++ -std=c++17 -shared -fPIC -DSPAKE2_RANDOM_TESTING \
  -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/linux" -I "$CPP/spake2-c/include" \
  "$CPP/spake2_jni.cpp" "$TESTS/spake2_random_test.cpp" \
  "$OUT/sha512.o" "$OUT/spake2.o" "$OUT/secure_random.o" -o "$OUT/libspake2.so"
"$JAVA_HOME/bin/javac" -cp "$ANNOTATIONS" -d "$OUT" \
  "$ROOT/app/src/main/java/io/github/muntashirakon/crypto/spake2/Spake2Context.java" \
  "$ROOT/app/src/main/java/io/github/muntashirakon/crypto/spake2/Spake2Role.java" \
  "$TESTS/java/Spake2NativeRegression.java"
"$JAVA_HOME/bin/java" "-Djava.library.path=$OUT" -cp "$OUT" Spake2NativeRegression
