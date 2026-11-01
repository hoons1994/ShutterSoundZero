package io.github.hoons1994.shuttersoundzero.testing;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class DisposableTestSessionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @Test public void realDeviceAndMissingOptInAreRejectedWithoutCreatingFiles() throws Exception {
        File root = temporary.newFolder();
        assertThrows(IllegalStateException.class, () -> DisposableTestSession.acquire(root, false, TOKEN));
        assertThrows(IllegalStateException.class, () -> DisposableTestSession.acquire(root, true, null));
        assertEquals(0, root.list().length);
    }

    @Test public void existingSettingsAndIdentitiesAreNeverOverwrittenOrDeleted() throws Exception {
        for (String location : new String[] {"shared_prefs/settings.xml", "no_backup/adb_identity.enc", "files/adb_private_key.der", "databases/state.db"}) {
            File root = temporary.newFolder();
            File sentinel = new File(root, location);
            assertTrue(sentinel.getParentFile().mkdirs());
            Files.writeString(sentinel.toPath(), "original-user-data");
            assertThrows(IllegalStateException.class, () -> DisposableTestSession.acquire(root, true, TOKEN));
            assertEquals("original-user-data", Files.readString(sentinel.toPath()));
            assertFalse(new File(root, "no_backup/.disposable-test-session").exists());
        }
    }

    @Test public void freshCiSessionCanRunAndRepeatButAnotherRunCannotClaimIt() throws Exception {
        File root = temporary.newFolder();
        DisposableTestSession.acquire(root, true, TOKEN);
        File data = new File(root, "files");
        assertTrue(data.mkdir());
        Files.writeString(new File(data, "fixture").toPath(), "test-data");
        DisposableTestSession.acquire(root, true, TOKEN);
        assertThrows(IllegalStateException.class, () -> DisposableTestSession.acquire(root, true, "a".repeat(32)));
        assertEquals("test-data", Files.readString(new File(data, "fixture").toPath()));
    }
}
