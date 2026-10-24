# Pinned SPAKE2 sources

- JNI and Java API: https://github.com/MuntashirAkon/spake2-java/tree/7615ddd680b990e14513ebb66eac4cb0dbf82464/android/src/main
- C implementation: https://github.com/MuntashirAkon/spake2-c/tree/0d15933e5ba3e662cb01245a7ac0dc9fca3eac31
- Licenses: `LICENSE.spake2-java`, `spake2-c/LICENSE`, individual source headers, and the repository's GPL-3.0 `LICENSE`.

This replaces the transitive spake2-android 2.2.1 binary. The C implementation
is unchanged. JNI message-generation/processing failures leave the context
owned by Java; only destroy frees it. The Java API serializes native access,
invalidates its pointer on destroy and makes repeated destruction harmless.
Failed message generation also destroys its context before throwing because
libadb cannot retain a context whose construction failed.
JNI also explicitly includes stdint.h for host compilation. This does not
change the cryptographic implementation.

The pinned source files are versioned directly, so Gradle artifact checksums
are not used for this replacement. Other dependency verification remains strict.
NDK and CMake versions are pinned in app/build.gradle.kts; the library uses 16 KiB
ELF segment alignment for Android devices with 16 KiB pages.
Copies of the LGPL-3.0 and GPL-3.0 licenses are packaged in assets/licenses.
