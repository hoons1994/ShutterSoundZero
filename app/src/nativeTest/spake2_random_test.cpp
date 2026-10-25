/* SPDX-License-Identifier: Apache-2.0 */
#include <jni.h>
#include <spake2/secure_random.h>
#include <spake2/spake2.h>
#include <errno.h>
#include <string.h>

static int mode, reads, destroys;

static ptrdiff_t injected_random(void *out, size_t length) {
    ++reads;
    if (mode == 1 || (mode == 3 && reads > 1)) { errno = EIO; return -1; }
    if (mode == 4) return 0;
    if (mode == 5) return (ptrdiff_t)length + 1;
    if (mode == 2 && reads == 1) { errno = EINTR; return -1; }
    size_t count = length < 7 ? length : 7;
    memset(out, 0x5a, count);
    return (ptrdiff_t)count;
}

extern "C" void spake2_test_on_destroy(void) { ++destroys; }

extern "C" JNIEXPORT void JNICALL Java_Spake2NativeRegression_setEntropyMode(JNIEnv *, jclass, jint value) {
    mode = value;
    reads = 0;
    spake2_set_random_reader_for_test(mode ? injected_random : nullptr);
}

extern "C" JNIEXPORT jint JNICALL Java_Spake2NativeRegression_destroyCount(JNIEnv *, jclass) {
    return destroys;
}

extern "C" JNIEXPORT jboolean JNICALL Java_Spake2NativeRegression_checkEntropyContract(JNIEnv *, jclass) {
    uint8_t buffer[64];
    mode = 2; reads = 0;
    spake2_set_random_reader_for_test(injected_random);
    if (!spake2_secure_random(buffer, sizeof(buffer)) || reads != 11) return false;
    for (uint8_t byte : buffer) if (byte != 0x5a) return false;
    const int failures[] = {1, 3, 4, 5};
    for (int failure : failures) {
        mode = failure; reads = 0;
        memset(buffer, 0xa5, sizeof(buffer));
        if (spake2_secure_random(buffer, sizeof(buffer))) return false;
        for (uint8_t byte : buffer) if (byte != 0) return false;
        const uint8_t name[] = {1};
        auto *ctx = SPAKE2_CTX_new(spake2_role_alice, name, 1, name, 1);
        if (!ctx) return false;
        uint8_t message[SPAKE2_MAX_MSG_SIZE];
        memset(message, 0xa5, sizeof(message));
        size_t written = 99;
        reads = 0;
        int success = SPAKE2_generate_msg(ctx, message, &written, sizeof(message), name, 1);
        bool unchanged = ctx->state == spake2_state_init;
        for (uint8_t byte : message) unchanged &= byte == 0xa5;
        for (uint8_t byte : ctx->private_key) unchanged &= byte == 0;
        SPAKE2_CTX_free(ctx);
        if (success || written != 0 || !unchanged) return false;
    }
    spake2_set_random_reader_for_test(nullptr);
    return true;
}
