package io.github.hoons1994.shuttersoundzero.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CscMuteManagerTest {

    @Test
    fun readMutedState_confirmsZeroWithoutFallback() {
        assertEquals(true, CscMuteManager.readCscMutedState(
            readInt = { 0 },
            readString = { error("A successful integer read must not fall back") }
        ))
    }

    @Test
    fun readMutedState_confirmsOneWithoutFallback() {
        assertEquals(false, CscMuteManager.readCscMutedState(
            readInt = { 1 },
            readString = { error("A successful integer read must not fall back") }
        ))
    }

    @Test
    fun readMutedState_fallbackCanConfirmEitherState() {
        for ((value, expected) in listOf("0" to true, "1" to false)) {
            assertEquals(expected, CscMuteManager.readCscMutedState(
                readInt = { throw SecurityException("integer read unavailable") },
                readString = { value }
            ))
        }
    }

    @Test
    fun readMutedState_bothReadFailuresRemainUnknownAndAreReported() {
        val failures = mutableListOf<Exception>()
        val integerFailure = SecurityException("integer read unavailable")
        val stringFailure = SecurityException("string read unavailable")
        assertNull(CscMuteManager.readCscMutedState(
            readInt = { throw integerFailure },
            readString = { throw stringFailure },
            onReadFailure = { failures.add(it) }
        ))
        assertEquals(listOf(integerFailure, stringFailure), failures)
    }

    @Test
    fun readMutedState_missingValueRemainsUnknown() {
        assertNull(CscMuteManager.readCscMutedState(
            readInt = { throw IllegalStateException("setting absent") },
            readString = { null }
        ))
    }

    @Test
    fun readMutedState_unexpectedIntegerRemainsUnknown() {
        assertNull(CscMuteManager.readCscMutedState(
            readInt = { 2 },
            readString = { "0" }
        ))
    }

    @Test
    fun readMutedState_invalidFallbackValuesRemainUnknown() {
        for (value in listOf("", "invalid", "2")) {
            assertNull(CscMuteManager.readCscMutedState(
                readInt = { throw NumberFormatException("invalid setting") },
                readString = { value }
            ))
        }
    }

    @Test
    fun cscKeyConstant_isCorrect() {
        assertEquals("csc_pref_camera_forced_shuttersound_key", CscMuteManager.CSC_KEY)
    }

}
