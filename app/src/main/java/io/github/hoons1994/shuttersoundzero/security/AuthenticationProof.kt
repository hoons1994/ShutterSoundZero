package io.github.hoons1994.shuttersoundzero.security

import java.security.PublicKey
import java.security.Signature

internal object AuthenticationProof {
    fun verify(publicKey: PublicKey, challenge: ByteArray, proof: ByteArray): Boolean =
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(challenge)
            verify(proof)
        }
}
