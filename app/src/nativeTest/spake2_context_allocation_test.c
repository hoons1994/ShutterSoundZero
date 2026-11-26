#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#include <spake2/spake2.h>

// Only spake2.c is built with the allocator names remapped to these functions.
static int fail_at;
static int allocations;
static void *live[3];

void *spake2_test_malloc(size_t size) {
    assert(allocations < 3);
    const int slot = allocations++;
    if (allocations == fail_at) return NULL;
    live[slot] = malloc(size);
    assert(live[slot] != NULL);
    return live[slot];
}

void spake2_test_free(void *pointer) {
    if (pointer == NULL) return;
    for (int slot = 0; slot < 3; ++slot) {
        if (live[slot] == pointer) {
            live[slot] = NULL;
            free(pointer);
            return;
        }
    }
    abort();
}

static void check_names(int failure) {
    const uint8_t alice[] = "Alice";
    const uint8_t bob[] = "Bob";
    fail_at = failure;
    allocations = 0;
    struct spake2_ctx_st *ctx = SPAKE2_CTX_new(spake2_role_alice, alice, sizeof(alice), bob, sizeof(bob));
    assert((ctx == NULL) == (failure != 0));
    assert(allocations == (failure == 0 ? 3 : failure));
    SPAKE2_CTX_free(ctx);
    for (int slot = 0; slot < 3; ++slot) assert(live[slot] == NULL);
}

int main(int argc, char **argv) {
    if (argc == 2) {
        check_names(atoi(argv[1]));
        return 0;
    }
    for (int failure = 0; failure <= 3; ++failure) check_names(failure);
    fail_at = 0;
    allocations = 0;
    struct spake2_ctx_st *ctx = SPAKE2_CTX_new(spake2_role_alice, NULL, 0, NULL, 0);
    assert(ctx != NULL && allocations == 1);
    SPAKE2_CTX_free(ctx);
    assert(live[0] == NULL);
    puts("SPAKE2 context allocation and partial cleanup checks passed");
    return 0;
}
