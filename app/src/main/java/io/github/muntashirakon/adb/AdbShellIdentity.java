// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.adb;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * A one-shot, in-process challenge. Only the ContentProvider may supply a caller UID, and it must
 * obtain that UID from Binder (never from the command, an Intent, a Bundle or shell output).
 * Normal application UIDs cannot acknowledge even if they learn the entire probe command.
 */
public final class AdbShellIdentity {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static Challenge active;
    private AdbShellIdentity() { }

    public static synchronized Challenge begin(String authority, int userId, long timeoutMillis)
            throws IOException {
        if (authority == null || !authority.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")
                || userId < 0 || timeoutMillis <= 0 || timeoutMillis > 15_000) {
            throw new IOException("Invalid ADB identity challenge parameters");
        }
        if (active != null) throw new IOException("An ADB identity challenge is already active");
        byte[] entropy = new byte[32];
        RANDOM.nextBytes(entropy);
        StringBuilder hex = new StringBuilder(64);
        for (byte b : entropy) {
            hex.append(Character.forDigit((b >>> 4) & 15, 16));
            hex.append(Character.forDigit(b & 15, 16));
        }
        Arrays.fill(entropy, (byte) 0);
        active = new Challenge(authority, userId, hex.toString(), timeoutMillis);
        return active;
    }

    /** The exported provider passes Binder.getCallingUid(); it must not accept a UID argument. */
    public static synchronized boolean acknowledge(int binderUid, String token) {
        if (binderUid != 2000 && binderUid != 0) {
            throw new SecurityException("Only Android shell/root can attest an ADB endpoint");
        }
        Challenge current = active;
        if (current == null || current.closed || current.acknowledged || current.expired()
                || token == null || token.length() != 64) return false;
        byte[] supplied = token.getBytes(StandardCharsets.US_ASCII);
        boolean matches = MessageDigest.isEqual(current.token, supplied);
        Arrays.fill(supplied, (byte) 0);
        if (matches) current.acknowledged = true;
        return matches;
    }

    public static final class Challenge implements Closeable {
        private final String authority;
        private final int userId;
        private final byte[] token;
        private final long deadline;
        private boolean acknowledged;
        private boolean closed;

        private Challenge(String authority, int userId, String token, long timeoutMillis) {
            this.authority = authority;
            this.userId = userId;
            this.token = token.getBytes(StandardCharsets.US_ASCII);
            deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        }

        /** A fixed, side-effect-free command. No arbitrary command or path comes from the peer. */
        public String command() throws IOException {
            synchronized (AdbShellIdentity.class) {
                if (closed || expired()) throw new IOException("ADB identity challenge expired");
                return "/system/bin/content call --user " + userId + " --uri content://" + authority
                        + " --method attest --arg " + new String(token, StandardCharsets.US_ASCII);
            }
        }

        public void requireAcknowledged() throws IOException {
            synchronized (AdbShellIdentity.class) {
                if (closed || expired() || !acknowledged) {
                    throw new IOException("The local ADB endpoint did not prove Android shell identity");
                }
            }
        }

        private boolean expired() { return System.nanoTime() - deadline >= 0; }

        @Override public void close() {
            synchronized (AdbShellIdentity.class) {
                closed = true;
                Arrays.fill(token, (byte) 0);
                if (active == this) active = null;
            }
        }
    }
}
