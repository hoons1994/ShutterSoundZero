package io.github.hoons1994.shuttersoundzero.core.adb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeTest {
    @Test
    fun `accepts exactly six ASCII digits including leading zero`() {
        assertTrue(PairingCode.isValid("012345"))
        assertTrue(PairingCode.isValid("000000"))
    }

    @Test
    fun `rejects codes of other lengths or with non-digits`() {
        assertFalse(PairingCode.isValid("12345"))
        assertFalse(PairingCode.isValid("1234567"))
        assertFalse(PairingCode.isValid("12345a"))
        assertFalse(PairingCode.isValid(" 123456"))
    }

    @Test
    fun `rejects Unicode digits that the ADB pairing path cannot accept`() {
        assertFalse(PairingCode.isValid("１２３４５６"))
        assertFalse(PairingCode.isValid("١٢٣٤٥٦"))
        assertFalse(PairingCode.isValid("12345６"))
    }
}
