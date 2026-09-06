package com.charmingcolor.shuttersoundzero.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun newerPatchVersion_isDetected() {
        assertTrue(AppUpdateManager.isNewerVersion("1.3.4", "1.3.3"))
    }

    @Test
    fun newerMinorVersion_isDetected() {
        assertTrue(AppUpdateManager.isNewerVersion("1.4.0", "1.3.9"))
    }

    @Test
    fun sameOrOlderVersion_isNotDetectedAsUpdate() {
        assertFalse(AppUpdateManager.isNewerVersion("1.3.3", "1.3.3"))
        assertFalse(AppUpdateManager.isNewerVersion("1.3.2", "1.3.3"))
    }

    @Test
    fun vPrefix_isAccepted() {
        assertTrue(AppUpdateManager.isNewerVersion("v2.0.0", "v1.9.9"))
    }

    @Test
    fun sha256FileLine_isParsed() {
        val hash = "06d3c1a9bd2691f64bf465138419c4fd72ad60955ed1f8e192830f1859715180"
        assertEquals(
            hash,
            AppUpdateManager.parseSha256("$hash  ShutterSoundZero-v1.3.3.apk\n")
        )
    }

    @Test
    fun invalidSha256_isRejected() {
        assertNull(AppUpdateManager.parseSha256("not-a-checksum"))
    }
}
