package io.github.hoons1994.shuttersoundzero.security

import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationProofTest {
    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val challenge = ByteArray(32) { it.toByte() }

    private fun sign(): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(keyPair.private)
        update(challenge)
        sign()
    }

    @Test fun validProofIsAccepted() {
        assertTrue(AuthenticationProof.verify(keyPair.public, challenge, sign()))
    }

    @Test fun proofForPreviousChallengeIsRejected() {
        val nextChallenge = challenge.copyOf().apply { this[0] = 99 }
        assertFalse(AuthenticationProof.verify(keyPair.public, nextChallenge, sign()))
    }

    @Test fun proofFromDifferentKeyIsRejected() {
        val otherKey = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        assertFalse(AuthenticationProof.verify(otherKey.public, challenge, sign()))
    }
}
