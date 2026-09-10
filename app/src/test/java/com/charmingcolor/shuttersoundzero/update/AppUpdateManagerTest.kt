package com.charmingcolor.shuttersoundzero.update

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

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

    @Test
    fun boundedText_acceptsPayloadAtLimit() {
        val payload = "12345678"
        val payloadBytes = payload.toByteArray()
        val result = AppUpdateManager.readBoundedText(
            ByteArrayInputStream(payloadBytes),
            maxBytes = payloadBytes.size
        )

        assertEquals(payload, result)
    }

    @Test
    fun boundedText_rejectsPayloadOverLimit() {
        val failure = runCatching {
            AppUpdateManager.readBoundedText(
                ByteArrayInputStream("123456789".toByteArray()),
                maxBytes = 8
            )
        }.exceptionOrNull()

        assertTrue(failure is IOException)
    }

    @Test
    fun boundedText_rejectsNonPositiveLimit() {
        val failure = runCatching {
            AppUpdateManager.readBoundedText(
                ByteArrayInputStream(byteArrayOf()),
                maxBytes = 0
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun boundedText_propagatesCancellationCheck() {
        var checks = 0
        val failure = runCatching {
            AppUpdateManager.readBoundedText(
                ByteArrayInputStream("payload".toByteArray()),
                maxBytes = 32
            ) {
                checks += 1
                throw CancellationException("cancelled")
            }
        }.exceptionOrNull()

        assertEquals(1, checks)
        assertTrue(failure is CancellationException)
    }

    @Test
    fun automaticCheck_disabledIsNeverDue() {
        assertFalse(
            AppUpdateManager.isAutomaticCheckDue(
                enabled = false,
                lastCheckAtMillis = 0L,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun automaticCheck_firstRunIsDue() {
        assertTrue(
            AppUpdateManager.isAutomaticCheckDue(
                enabled = true,
                lastCheckAtMillis = 0L,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun automaticCheck_waitsForTwentyFourHours() {
        val last = 10_000L
        val interval = AppUpdateManager.AUTOMATIC_CHECK_INTERVAL_MILLIS
        assertFalse(AppUpdateManager.isAutomaticCheckDue(true, last, last + interval - 1L))
        assertTrue(AppUpdateManager.isAutomaticCheckDue(true, last, last + interval))
    }

    @Test
    fun automaticCheck_clockRollbackAllowsFreshBaseline() {
        assertTrue(
            AppUpdateManager.isAutomaticCheckDue(
                enabled = true,
                lastCheckAtMillis = 20_000L,
                nowMillis = 10_000L
            )
        )
    }
}
