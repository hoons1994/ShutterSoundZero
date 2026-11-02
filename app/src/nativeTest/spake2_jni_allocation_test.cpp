/* SPDX-License-Identifier: Apache-2.0 */
// Exercise the production JNI functions with a failing JNI table. No hooks or
// allocation controls are compiled into the APK. Real crypto/JVM ownership is
// covered separately by Spake2NativeRegression.
#include <string.h>
#include "../main/cpp/spake2_jni.cpp"

static void require(bool condition, const char *message) {
    if (!condition) {
        fprintf(stderr, "FAIL: %s\n", message);
        exit(1);
    }
}

struct TestArray {
    jbyte bytes[SPAKE2_MAX_KEY_SIZE];
    jsize length;
    bool acquired;
};

static TestArray first, second, output;
static spake2_ctx_st context;
static int failAt, allocations, acquisitions, releases, cryptoCalls, frees;
static bool failed, throwOnFailure, ownsContext;
static jthrowable pendingException;
static _jthrowable originalException;

static void continuing() {
    require(!failed && pendingException == nullptr, "Continued JNI/crypto work after allocation failure");
}

static bool allocationFails() {
    continuing();
    if (++allocations != failAt) return false;
    failed = true;
    if (throwOnFailure) pendingException = &originalException;
    return true;
}

static TestArray *array(jbyteArray value) {
    require(value != nullptr, "Null array used");
    return reinterpret_cast<TestArray *>(value);
}

static jsize JNICALL length(JNIEnv *, jarray value) {
    continuing();
    return array(reinterpret_cast<jbyteArray>(value))->length;
}

static jbyte *JNICALL elements(JNIEnv *, jbyteArray value, jboolean *) {
    if (allocationFails()) return nullptr;
    auto *a = array(value);
    require(!a->acquired, "Array acquired twice");
    a->acquired = true;
    ++acquisitions;
    return a->bytes;
}

static void JNICALL release(JNIEnv *, jbyteArray value, jbyte *bytes, jint mode) {
    auto *a = array(value);
    require(a->acquired && bytes == a->bytes, "Unacquired or duplicate array release");
    require(mode == 0 || mode == JNI_ABORT, "Invalid array release mode");
    a->acquired = false;
    ++releases;
}

static jbyteArray JNICALL newArray(JNIEnv *, jsize size) {
    if (allocationFails()) return nullptr;
    require(size > 0 && size <= SPAKE2_MAX_KEY_SIZE, "Invalid output size");
    output.length = size;
    return reinterpret_cast<jbyteArray>(&output);
}

static void JNICALL setRegion(JNIEnv *, jbyteArray value, jsize start, jsize size, const jbyte *bytes) {
    continuing();
    auto *a = array(value);
    require(start == 0 && size == a->length, "Invalid output region");
    memcpy(a->bytes, bytes, size);
}

static void JNICALL clearException(JNIEnv *) {
    require(false, "Original exception was cleared");
}

extern "C" spake2_ctx_st *SPAKE2_CTX_new(spake2_role_t, const uint8_t *a, size_t,
                                         const uint8_t *b, size_t) {
    continuing();
    require(a && b && first.acquired && second.acquired, "Crypto received invalid names");
    ++cryptoCalls;
    ownsContext = true;
    return &context;
}

extern "C" void SPAKE2_CTX_free(spake2_ctx_st *ctx) {
    require(ctx == &context && ownsContext, "Duplicate/invalid context free");
    ownsContext = false;
    ++frees;
}

static int cryptoOutput(spake2_ctx_st *ctx, uint8_t *out, size_t *size,
                        const uint8_t *input, size_t written) {
    continuing();
    require(ctx == &context && ownsContext && input && first.acquired, "Crypto received invalid input");
    ++cryptoCalls;
    memset(out, 0x5a, written);
    *size = written;
    return 1;
}

extern "C" int SPAKE2_generate_msg(spake2_ctx_st *ctx, uint8_t *out, size_t *size,
                                   size_t, const uint8_t *input, size_t) {
    return cryptoOutput(ctx, out, size, input, SPAKE2_MAX_MSG_SIZE);
}

extern "C" int SPAKE2_process_msg(spake2_ctx_st *ctx, uint8_t *out, size_t *size,
                                  size_t, const uint8_t *input, size_t) {
    return cryptoOutput(ctx, out, size, input, SPAKE2_MAX_KEY_SIZE);
}

int main() {
    JNINativeInterface_ functions = {};
    functions.GetArrayLength = length;
    functions.GetByteArrayElements = elements;
    functions.ReleaseByteArrayElements = release;
    functions.NewByteArray = newArray;
    functions.SetByteArrayRegion = setRegion;
    functions.ExceptionClear = clearException;
    JNIEnv env = {&functions};
    // Constructor: first/second input; generation/processing: input/output.
    // Also exercise a null return without an exception: it still must stop.
    for (int operation = 0; operation < 3; ++operation) {
        for (int withException = 0; withException < 2; ++withException) {
            for (int failure = 0; failure <= 2; ++failure) {
                first = {}; first.length = 1;
                second = {}; second.length = 1;
                output = {};
                failAt = failure;
                allocations = acquisitions = releases = cryptoCalls = frees = 0;
                failed = false;
                throwOnFailure = withException;
                pendingException = nullptr;
                ownsContext = operation != 0;
                auto a = reinterpret_cast<jbyteArray>(&first);
                auto b = reinterpret_cast<jbyteArray>(&second);
                bool success;
                if (operation == 0) {
                    success = Spake2Context_AllocNewContext(&env, nullptr, 0, a, b) != 0;
                } else {
                    auto result = operation == 1
                            ? Spake2Context_GenerateMessage(&env, nullptr, (jlong)&context, a)
                            : Spake2Context_ProcessMessage(&env, nullptr, (jlong)&context, a);
                    success = result != nullptr;
                    if (success) {
                        require(result == reinterpret_cast<jbyteArray>(&output), "Wrong output array");
                        require(output.length == (operation == 1 ? SPAKE2_MAX_MSG_SIZE : SPAKE2_MAX_KEY_SIZE),
                                "Wrong output length");
                        for (int i = 0; i < output.length; ++i) require(output.bytes[i] == 0x5a, "Wrong output bytes");
                    }
                }
                require(success == (failure == 0), "Failure was returned as success");
                require(allocations == (failure ? failure : 2), "Unexpected allocation after failure");
                require(releases == acquisitions && !first.acquired && !second.acquired,
                        "Acquired array leaked or released twice");
                require(pendingException == (failure && withException ? &originalException : nullptr),
                        "Original exception was replaced or lost");
                require(cryptoCalls == (operation == 0 ? (failure ? 0 : 1) : (failure == 1 ? 0 : 1)),
                        "Unexpected crypto work after failure");
                require(frees == 0, "JNI failure changed context ownership");
                if (ownsContext) {
                    Spake2Context_Destroy(&env, nullptr, (jlong)&context);
                    require(frees == 1 && !ownsContext, "Owner did not release exactly once");
                }
            }
        }
    }
    puts("PASS: 6 JNI allocation failure sites, with/without pending exceptions; original exception preserved; acquired arrays released once; context ownership unchanged; successful output preserved.");
}
