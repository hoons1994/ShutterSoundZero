// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.adb;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import javax.net.ssl.SSLException;

/** Fixed diagnostic categories; never copies exception messages, endpoints or credentials. */
public final class PairingFailureException extends IOException {
    public enum Phase { IDENTITY, TCP_CONNECT, TLS_HANDSHAKE, TLS_EXPORT, CODE_AUTHENTICATION, PEER_INFO }
    public enum Reason {
        IDENTITY_UNAVAILABLE, CONNECTION_FAILED, TIMEOUT, TLS_ERROR, TLS_EXPORT_FAILED,
        AUTHENTICATION_FAILED, PEER_INFO_FAILED, CRYPTO_ERROR, IO_ERROR, INTERNAL_ERROR
    }
    private final Phase phase;
    private final Reason reason;

    public PairingFailureException(Phase phase, Exception cause) {
        super("Pairing failed during " + phase.name(), cause);
        this.phase = phase;
        this.reason = classify(phase, cause);
    }

    public Phase getPhase() { return phase; }
    public Reason getReason() { return reason; }

    private static Reason classify(Phase phase, Exception cause) {
        if (phase == Phase.IDENTITY) return Reason.IDENTITY_UNAVAILABLE;
        if (cause instanceof SocketTimeoutException) return Reason.TIMEOUT;
        if (phase == Phase.TLS_EXPORT) return Reason.TLS_EXPORT_FAILED;
        if (cause instanceof SSLException) return Reason.TLS_ERROR;
        if (cause instanceof ConnectException) return Reason.CONNECTION_FAILED;
        if (phase == Phase.CODE_AUTHENTICATION) return Reason.AUTHENTICATION_FAILED;
        if (phase == Phase.PEER_INFO) return Reason.PEER_INFO_FAILED;
        if (cause instanceof GeneralSecurityException) return Reason.CRYPTO_ERROR;
        if (cause instanceof IOException) return Reason.IO_ERROR;
        return Reason.INTERNAL_ERROR;
    }
}
