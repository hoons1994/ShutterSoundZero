/* Host-only endian compatibility for the pinned C implementation on Windows x64.
 * Android builds use the NDK's endian.h, not this header. */
#define LITTLE_ENDIAN 1234
#define BIG_ENDIAN 4321
#define BYTE_ORDER LITTLE_ENDIAN
