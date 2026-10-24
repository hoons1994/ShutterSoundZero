package io.github.muntashirakon.adb;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import io.github.muntashirakon.crypto.spake2.Spake2Context;
import io.github.muntashirakon.crypto.spake2.Spake2Role;
import org.junit.Test;

/** Exercises the actual packaged JNI, including finally cleanup after malformed peer input. */
public class PairingNativeOwnershipInstrumentedTest {
    @Test public void invalidLengthsFailAndCanBeDestroyedRepeatedly() {
        for (int length : new int[]{0, 1, 31, 33, 16384}) {
            assertRejectedAndDestroyed(new byte[length]);
        }
    }

    @Test public void invalidCurvePointFailsAndCanBeDestroyedRepeatedly() {
        byte[] invalidPoint = new byte[32];
        invalidPoint[0] = 2; // y=2 does not decode to an Edwards25519 point.
        assertRejectedAndDestroyed(invalidPoint);
    }

    private void assertRejectedAndDestroyed(byte[] message) {
        PairingAuthCtx auth = PairingAuthCtx.createAlice(new byte[]{1, 2, 3});
        assertNotNull(auth);
        try {
            assertThrows(IllegalStateException.class, () -> auth.initCipher(message));
        } finally {
            auth.destroy();
            auth.destroy();
        }
        assertTrue(auth.isDestroyed());
    }

    @Test public void validExchangeStillDerivesTheSameKey() {
        byte[] aliceName = "alice".getBytes(StandardCharsets.UTF_8);
        byte[] bobName = "bob".getBytes(StandardCharsets.UTF_8);
        Spake2Context alice = new Spake2Context(Spake2Role.Alice, aliceName, bobName);
        Spake2Context bob = new Spake2Context(Spake2Role.Bob, bobName, aliceName);
        try {
            byte[] password = new byte[]{1, 2, 3};
            byte[] aliceMsg = alice.generateMessage(password);
            byte[] bobMsg = bob.generateMessage(password);
            assertArrayEquals(alice.processMessage(bobMsg), bob.processMessage(aliceMsg));
        } finally {
            alice.destroy();
            bob.destroy();
        }
        assertThrows(IllegalStateException.class, () -> alice.processMessage(new byte[32]));
    }

    @Test public void failedGenerationReleasesTheContextExactlyOnce() {
        Spake2Context ctx = new Spake2Context(Spake2Role.Alice, new byte[]{1}, new byte[]{2});
        ctx.generateMessage(new byte[]{3});
        assertThrows(IllegalStateException.class, () -> ctx.generateMessage(new byte[]{3}));
        assertTrue(ctx.isDestroyed());
        ctx.destroy();
    }
}
