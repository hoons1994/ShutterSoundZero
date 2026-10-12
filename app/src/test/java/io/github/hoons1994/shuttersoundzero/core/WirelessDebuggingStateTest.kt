package io.github.hoons1994.shuttersoundzero.core

import org.junit.Assert.*
import org.junit.Test

class WirelessDebuggingStateTest {
    @Test fun readFailureIsUnknownRatherThanOff() {
        assertNull(readWirelessDebuggingState { throw SecurityException("read denied") })
        assertNull(readWirelessDebuggingState { throw IllegalStateException("provider unavailable") })
    }

    @Test fun aLaterSuccessfulReadRecoversWithoutRememberingTheFailure() {
        var setting: Int? = null
        val read = { readWirelessDebuggingState { checkNotNull(setting) } }
        assertNull(read())
        setting = 1
        assertEquals(true, read())
        setting = 0
        assertEquals(false, read())
    }
}
