package io.github.muntashirakon.adb;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdbShellIdentityTest {
    private static final String AUTHORITY = "com.example.adb_auth";
    private static String token(AdbShellIdentity.Challenge challenge) throws IOException {
        return challenge.command().split(" --arg ")[1];
    }
    @Test public void untrustedUidCannotAttestEvenWithCorrectToken() throws Exception {
        try (AdbShellIdentity.Challenge challenge = AdbShellIdentity.begin(AUTHORITY, 0, 1000)) {
            assertThrows(SecurityException.class, () -> AdbShellIdentity.acknowledge(10001, token(challenge)));
            assertThrows(SecurityException.class, () -> AdbShellIdentity.acknowledge(1000, token(challenge)));
            assertThrows(IOException.class, challenge::requireAcknowledged);
        }
    }
    @Test public void onlyCorrectFreshShellProofIsAcceptedOnce() throws Exception {
        try (AdbShellIdentity.Challenge challenge = AdbShellIdentity.begin(AUTHORITY, 10, 1000)) {
            assertTrue(challenge.command().contains("--user 10"));
            assertFalse(AdbShellIdentity.acknowledge(2000, "0".repeat(64)));
            assertThrows(IOException.class, challenge::requireAcknowledged);
            assertTrue(AdbShellIdentity.acknowledge(2000, token(challenge)));
            challenge.requireAcknowledged();
            assertFalse(AdbShellIdentity.acknowledge(2000, token(challenge)));
        }
    }
    @Test public void cancelledTokenCannotAcknowledgeNextAttempt() throws Exception {
        String old;
        try (AdbShellIdentity.Challenge first = AdbShellIdentity.begin(AUTHORITY, 0, 1000)) { old = token(first); }
        try (AdbShellIdentity.Challenge second = AdbShellIdentity.begin(AUTHORITY, 0, 1000)) {
            assertFalse(AdbShellIdentity.acknowledge(2000, old));
            assertThrows(IOException.class, second::requireAcknowledged);
        }
    }
    @Test public void expiryRejectsLateProof() throws Exception {
        try (AdbShellIdentity.Challenge challenge = AdbShellIdentity.begin(AUTHORITY, 0, 25)) {
            String token = token(challenge);
            Thread.sleep(40);
            assertFalse(AdbShellIdentity.acknowledge(2000, token));
            assertThrows(IOException.class, challenge::requireAcknowledged);
        }
    }
    @Test public void invalidAuthorityAndConcurrentChallengesFailClosed() throws Exception {
        assertThrows(IOException.class, () -> AdbShellIdentity.begin("com.example;id", 0, 1000));
        assertThrows(IOException.class, () -> AdbShellIdentity.begin(AUTHORITY, -1, 1000));
        try (AdbShellIdentity.Challenge ignored = AdbShellIdentity.begin(AUTHORITY, 0, 1000)) {
            assertThrows(IOException.class, () -> AdbShellIdentity.begin(AUTHORITY, 0, 1000));
        }
    }
    @Test public void rootIsTrustedButClosingInvalidatesProof() throws Exception {
        AdbShellIdentity.Challenge challenge = AdbShellIdentity.begin(AUTHORITY, 0, 1000);
        assertTrue(AdbShellIdentity.acknowledge(0, token(challenge)));
        challenge.close();
        challenge.close();
        assertThrows(IOException.class, challenge::requireAcknowledged);
    }
}
