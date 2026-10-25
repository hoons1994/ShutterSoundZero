/* SPDX-License-Identifier: Apache-2.0 */
#ifndef SPAKE2_SECURE_RANDOM_H
#define SPAKE2_SECURE_RANDOM_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Returns one only after the OS has filled the complete buffer. No weak fallback. */
int spake2_secure_random(uint8_t *out, size_t length);

#ifdef SPAKE2_RANDOM_TESTING
/* Compiled into the isolated host test library only, never the Android APK. */
typedef ptrdiff_t (*spake2_random_reader)(void *, size_t);
void spake2_set_random_reader_for_test(spake2_random_reader reader);
void spake2_test_on_destroy(void);
#endif

#ifdef __cplusplus
}
#endif
#endif
