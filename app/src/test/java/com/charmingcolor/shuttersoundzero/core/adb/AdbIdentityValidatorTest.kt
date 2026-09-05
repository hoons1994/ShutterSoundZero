package com.charmingcolor.shuttersoundzero.core.adb

import java.security.KeyPairGenerator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbIdentityValidatorTest {
    @Test
    fun `matching RSA key pair is accepted`() {
        val keyPair = generateRsaKeyPair()

        assertTrue(AdbIdentityValidator.keysMatch(keyPair.private, keyPair.public))
    }

    @Test
    fun `different RSA key pair is rejected`() {
        val first = generateRsaKeyPair()
        val second = generateRsaKeyPair()

        assertFalse(AdbIdentityValidator.keysMatch(first.private, second.public))
    }

    @Test
    fun `non RSA public key is rejected`() {
        val rsa = generateRsaKeyPair()
        val ecGenerator = KeyPairGenerator.getInstance("EC").apply { initialize(256) }
        val ec = ecGenerator.generateKeyPair()

        assertFalse(AdbIdentityValidator.keysMatch(rsa.private, ec.public))
    }

    private fun generateRsaKeyPair() = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.generateKeyPair()
}
