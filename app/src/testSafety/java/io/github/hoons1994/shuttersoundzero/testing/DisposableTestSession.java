package io.github.hoons1994.shuttersoundzero.testing;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Test-only gate. Rejected sessions perform no writes and never enter JUnit lifecycle methods. */
public final class DisposableTestSession {
    private DisposableTestSession() {}

    public static void acquire(File dataDir, boolean emulator, String token) throws IOException {
        if (!emulator || token == null || !token.matches("[a-f0-9]{32}")) {
            throw new IllegalStateException("Disposable emulator and explicit disposableTestToken are required");
        }
        File marker = new File(dataDir, "no_backup/.disposable-test-session");
        if (marker.isFile()) {
            if (!new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).equals(token)) {
                throw new IllegalStateException("Existing test session belongs to another run");
            }
            return; // Only this run's explicitly provisioned emulator may repeat tests.
        }
        for (String name : new String[] {"files", "no_backup", "shared_prefs", "databases"}) {
            File directory = new File(dataDir, name);
            File[] entries = directory.listFiles();
            if (directory.exists() && (entries == null || entries.length != 0)) {
                throw new IllegalStateException("Existing app data must be preserved: " + name);
            }
        }
        Files.createDirectories(marker.toPath().getParent());
        Files.write(marker.toPath(), token.getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE_NEW);
    }
}
