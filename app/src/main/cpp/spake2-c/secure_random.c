/* SPDX-License-Identifier: Apache-2.0 */
#include <spake2/secure_random.h>
#include <errno.h>

#if defined(_WIN32)
/* The host regression uses Windows' OS CSPRNG; Android uses getrandom below. */
#include <windows.h>
#include <bcrypt.h>
#include <limits.h>
static ptrdiff_t os_random(void *out, size_t length) {
  if (length > ULONG_MAX || BCryptGenRandom(NULL, (PUCHAR)out, (ULONG)length,
                                           BCRYPT_USE_SYSTEM_PREFERRED_RNG) < 0) {
    errno = EIO;
    return -1;
  }
  return (ptrdiff_t)length;
}
#else
#include <sys/random.h>
static ptrdiff_t os_random(void *out, size_t length) {
  return (ptrdiff_t)getrandom(out, length, 0);
}
#endif

#ifdef SPAKE2_RANDOM_TESTING
static spake2_random_reader test_reader;
void spake2_set_random_reader_for_test(spake2_random_reader reader) {
  test_reader = reader;
}
#endif

int spake2_secure_random(uint8_t *out, size_t length) {
  size_t filled = 0;
  while (filled < length) {
#ifdef SPAKE2_RANDOM_TESTING
    ptrdiff_t count = (test_reader ? test_reader : os_random)(out + filled, length - filled);
#else
    ptrdiff_t count = os_random(out + filled, length - filled);
#endif
    if (count < 0 && errno == EINTR) continue;
    if (count <= 0 || (size_t)count > length - filled) {
      /* Erase partially acquired entropy even in optimized builds. */
      volatile uint8_t *wipe = out;
      for (size_t i = 0; i < length; ++i) wipe[i] = 0;
      return 0;
    }
    filled += (size_t)count;
  }
  return 1;
}
