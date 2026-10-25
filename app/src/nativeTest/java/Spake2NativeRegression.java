import io.github.muntashirakon.crypto.spake2.Spake2Context;
import io.github.muntashirakon.crypto.spake2.Spake2Role;
import java.util.Arrays;

/** Runs the production Java wrapper and JNI on the host, without Android stubs. */
public final class Spake2NativeRegression {
    static { System.loadLibrary("spake2"); }
    private static native void setEntropyMode(int mode);
    private static native int destroyCount();
    private static native boolean checkEntropyContract();
    private static Spake2Context context(Spake2Role role) {
        byte[] alice = new byte[]{1}, bob = new byte[]{2};
        return new Spake2Context(role, role == Spake2Role.Alice ? alice : bob,
                role == Spake2Role.Alice ? bob : alice);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void rejects(byte[] message) {
        Spake2Context ctx = context(Spake2Role.Alice);
        try {
            ctx.generateMessage(new byte[]{3});
            try {
                ctx.processMessage(message);
                throw new AssertionError("Malformed message was accepted");
            } catch (IllegalStateException expected) {
                require(!ctx.isDestroyed(), "Processing failure must retain Java ownership");
            }
        } finally {
            ctx.destroy();
            ctx.destroy();
        }
        require(ctx.isDestroyed(), "Context must be destroyed");
        try {
            ctx.processMessage(new byte[32]);
            throw new AssertionError("Destroyed context was usable");
        } catch (IllegalStateException expected) { }
    }

    public static void main(String[] args) {
        require(checkEntropyContract(), "Entropy must handle EINTR/short reads and fail without emitting a message");
        for (int mode : new int[]{1, 3, 4, 5}) {
            setEntropyMode(mode);
            Spake2Context ctx = context(Spake2Role.Alice);
            int before = destroyCount();
            try {
                ctx.generateMessage(new byte[]{3});
                throw new AssertionError("Entropy failure emitted a pairing message");
            } catch (IllegalStateException expected) {
                require(ctx.isDestroyed(), "Entropy failure must destroy its context");
            } finally {
                ctx.destroy();
                ctx.destroy();
            }
            require(destroyCount() == before + 1, "Entropy failure must release exactly once");
        }
        setEntropyMode(0);
        Spake2Context failedGeneration = context(Spake2Role.Alice);
        failedGeneration.generateMessage(new byte[]{3});
        try {
            failedGeneration.generateMessage(new byte[]{3});
            throw new AssertionError("Repeated message generation was accepted");
        } catch (IllegalStateException expected) {
            require(failedGeneration.isDestroyed(), "Failed generation must release its context");
        } finally {
            failedGeneration.destroy();
        }
        for (int iteration = 0; iteration < 100; iteration++) {
            for (int length : new int[]{0, 1, 31, 33, 16384}) rejects(new byte[length]);
            byte[] invalidPoint = new byte[32];
            invalidPoint[0] = 2;
            rejects(invalidPoint);
            Spake2Context alice = context(Spake2Role.Alice);
            Spake2Context bob = context(Spake2Role.Bob);
            try {
                byte[] aliceMsg = alice.generateMessage(new byte[]{3});
                byte[] bobMsg = bob.generateMessage(new byte[]{3});
                require(Arrays.equals(alice.processMessage(bobMsg), bob.processMessage(aliceMsg)),
                        "Valid exchange derived different keys");
            } finally {
                alice.destroy();
                bob.destroy();
            }
        }
        System.out.println("PASS: OS entropy; short reads and EINTR; 4 injected entropy failures emit no message and release exactly once; 600 malformed inputs; 100 valid exchanges; post-destroy access rejected.");
    }
}
