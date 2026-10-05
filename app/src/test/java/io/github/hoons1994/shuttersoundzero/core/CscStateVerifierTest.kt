package io.github.hoons1994.shuttersoundzero.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CscStateVerifierTest {

    @Test
    fun unknownStateCannotVerifyEitherRequestedState() = runTest {
        for (expectedMuted in listOf(true, false)) {
            var reads = 0
            val result = CscStateVerifier.waitFor(expectedMuted, attempts = 3, intervalMillis = 0L) {
                reads++
                null
            }
            assertFalse(result)
            org.junit.Assert.assertEquals(3, reads)
        }
    }

    @Test
    fun failedCscReadsCannotVerifyRestore() = runTest {
        assertFalse(CscStateVerifier.waitFor(false, attempts = 3, intervalMillis = 0L) {
            CscMuteManager.readCscMutedState(
                readInt = { throw SecurityException("integer read unavailable") },
                readString = { throw SecurityException("string read unavailable") }
            )
        })
    }

    @Test
    fun missingCscValueCannotVerifyRestore() = runTest {
        assertFalse(CscStateVerifier.waitFor(false, attempts = 3, intervalMillis = 0L) {
            CscMuteManager.readCscMutedState(
                readInt = { throw IllegalStateException("setting absent") },
                readString = { null }
            )
        })
    }

    @Test
    fun readRecoveryCanVerifyEitherRequestedStateAfterRetry() = runTest {
        for (expectedMuted in listOf(true, false)) {
            var reads = 0
            val result = CscStateVerifier.waitFor(expectedMuted, attempts = 3, intervalMillis = 100L) {
                reads++
                CscMuteManager.readCscMutedState(
                    readInt = {
                        if (reads < 3) throw SecurityException("temporarily unreadable")
                        if (expectedMuted) 0 else 1
                    },
                    readString = { null }
                )
            }
            assertTrue(result)
            org.junit.Assert.assertEquals(3, reads)
        }
    }

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
