package io.github.muntashirakon.adb;

import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.app.Instrumentation;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import org.conscrypt.Conscrypt;
import static org.junit.Assert.*;

/** Exercises real Android Binder identity, Conscrypt TLS 1.3 and libadb SPAKE2. */
@RunWith(AndroidJUnit4.class)
public class AdbIdentityInstrumentedTest {
    private Instrumentation getInstrumentation() { return InstrumentationRegistry.getInstrumentation(); }
    private String authority() { return getInstrumentation().getTargetContext().getPackageName() + ".adb-auth"; }
    private int userId() { return UserHandle.getUserHandleForUid(android.os.Process.myUid()).getIdentifier(); }

    @Test public void testRealShellBinderIdentityCompletesOneShotChallenge() throws Exception {
        try (AdbShellIdentity.Challenge challenge = AdbShellIdentity.begin(authority(), userId(), 10_000)) {
            String output = shell(challenge.command());
            assertFalse(output, output.contains("Error"));
            challenge.requireAcknowledged();
        }
    }

    @Test public void testAppUidCannotForgeProofEvenWithDumpPermissionAndToken() throws Exception {
        try (AdbShellIdentity.Challenge challenge = AdbShellIdentity.begin(authority(), userId(), 10_000)) {
            String token = challenge.command().split(" --arg ")[1];
            Bundle extras = new Bundle();
            extras.putInt("uid", 2000); // Must be ignored; this is not the kernel-supplied calling UID.
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity("android.permission.DUMP");
            try {
                assertThrows(SecurityException.class, () -> getInstrumentation().getTargetContext()
                        .getContentResolver().call(Uri.parse("content://" + authority()), "attest", token, extras));
                assertThrows(IOException.class, challenge::requireAcknowledged);
            } finally { getInstrumentation().getUiAutomation().dropShellPermissionIdentity(); }
        }
    }

    @Test public void testCancelledChallengeCannotBeReplayedByShell() throws Exception {
        String oldCommand;
        try (AdbShellIdentity.Challenge challenge = AdbShellIdentity.begin(authority(), userId(), 10_000)) {
            oldCommand = challenge.command();
        }
        try (AdbShellIdentity.Challenge next = AdbShellIdentity.begin(authority(), userId(), 10_000)) {
            shell(oldCommand); // Failure text can be on stderr; verify the actual proof state.
            assertThrows(IOException.class, next::requireAcknowledged);
            shell(next.command());
            next.requireAcknowledged();
        }
    }

    @Test public void testConscryptPairingSucceedsWithMatchingCodeAndTlsExporter() throws Exception { pair(false, false, false); }
    @Test public void testConscryptPairingRejectsWrongCode() throws Exception { pair(true, false, false); }
    @Test public void testConscryptPairingRejectsDifferentTlsExporter() throws Exception { pair(false, true, false); }
    @Test public void testConscryptPairingRejectsAuthenticatedWrongPeerType() throws Exception { pair(false, false, true); }

    private void pair(boolean wrongCode, boolean wrongExporter, boolean wrongType) throws Exception {
        Provider provider = Conscrypt.newProvider();
        int inserted = Security.insertProviderAt(provider, 1);
        DeviceTlsKeys clientKeys = new DeviceTlsKeys();
        DeviceTlsKeys serverKeys = new DeviceTlsKeys();
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        try (SSLServerSocket server = (SSLServerSocket) serverKeys.serverContext(clientKeys.certificate)
                .getServerSocketFactory().createServerSocket(0)) {
            server.setSoTimeout(5000);
            server.setNeedClientAuth(true);
            Thread peer = new Thread(() -> {
                PairingAuthCtx bob = null;
                try (SSLSocket socket = (SSLSocket) server.accept()) {
                    socket.setSoTimeout(5000);
                    socket.startHandshake();
                    byte[] exported = Conscrypt.exportKeyingMaterial(socket, "adb-label\u0000", null, 64);
                    if (wrongExporter) exported[0] ^= 1;
                    byte[] code = (wrongCode ? "654321" : "123456").getBytes(StandardCharsets.US_ASCII);
                    byte[] combined = Arrays.copyOf(code, code.length + exported.length);
                    System.arraycopy(exported, 0, combined, code.length, exported.length);
                    bob = PairingAuthCtx.createBob(combined);
                    assertNotNull(bob);
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    byte[] alice = readPacket(input, 0);
                    assertTrue(bob.initCipher(alice));
                    writePacket(output, 0, bob.getMsg());
                    byte[] publicInfo = bob.decrypt(readPacket(input, 1));
                    if (!wrongCode && !wrongExporter) {
                        assertNotNull(publicInfo);
                        assertEquals(8192, publicInfo.length);
                        assertEquals(0, publicInfo[0]);
                    }
                    byte[] device = new byte[8192];
                    device[0] = (byte) (wrongType ? 0 : 1);
                    byte[] guid = "adb-test-device-guid".getBytes(StandardCharsets.US_ASCII);
                    System.arraycopy(guid, 0, device, 1, guid.length);
                    writePacket(output, 1, bob.encrypt(device));
                } catch (Throwable error) { serverFailure.set(error); }
                finally { if (bob != null) bob.destroy(); }
            }, "SSZ-Pairing-Test-Server");
            peer.start();
            try (CancellablePairingConnection client = new CancellablePairingConnection("127.0.0.1",
                    server.getLocalPort(), "123456".getBytes(StandardCharsets.US_ASCII),
                    clientKeys.keys.getPrivate(), clientKeys.certificate, "instrumentation")) {
                if (wrongCode || wrongExporter || wrongType) {
                    assertThrows(IOException.class, () -> client.start(5000));
                } else client.start(5000);
            } finally { peer.join(6000); }
            assertFalse("Pairing test worker did not exit", peer.isAlive());
            if (serverFailure.get() != null) throw new AssertionError("Pairing test server failed", serverFailure.get());
        } finally { if (inserted != -1) Security.removeProvider(provider.getName()); }
    }

    private static byte[] readPacket(DataInputStream input, int expectedType) throws IOException {
        if (input.readUnsignedByte() != 1 || input.readUnsignedByte() != expectedType) throw new IOException("Bad test packet");
        int length = input.readInt();
        if (length <= 0 || length > 16384) throw new IOException("Bad test packet size");
        byte[] result = new byte[length];
        input.readFully(result);
        return result;
    }
    private static void writePacket(DataOutputStream output, int type, byte[] data) throws IOException {
        if (data == null) throw new IOException("Missing encrypted test data");
        output.writeByte(1); output.writeByte(type); output.writeInt(data.length); output.write(data); output.flush();
    }
    private String shell(String command) throws IOException {
        try (ParcelFileDescriptor descriptor = getInstrumentation().getUiAutomation().executeShellCommand(command);
                ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > 8192) throw new IOException("Oversized test shell response");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
