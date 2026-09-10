package com.charmingcolor.shuttersoundzero.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CscStateVerifierTest {

    @Test
    fun waitFor_returnsTrueImmediately_whenStateAlreadyMatches() = runTest {
        val result = CscStateVerifier.waitFor(
            expectedMuted = true,
            attempts = 3,
            intervalMillis = 0L
        ) { true }

        assertTrue(result)
    }

    @Test
    fun waitFor_retriesUntilStateMatches() = runTest {
        var reads = 0

        val result = CscStateVerifier.waitFor(
            expectedMuted = true,
            attempts = 3,
            intervalMillis = 0L
        ) {
            reads += 1
            reads >= 3
        }

        assertTrue(result)
    }

    @Test
    fun waitFor_returnsFalse_whenStateNeverMatches() = runTest {
        val result = CscStateVerifier.waitFor(
            expectedMuted = false,
            attempts = 3,
            intervalMillis = 0L
        ) { true }

        assertFalse(result)
    }
}
