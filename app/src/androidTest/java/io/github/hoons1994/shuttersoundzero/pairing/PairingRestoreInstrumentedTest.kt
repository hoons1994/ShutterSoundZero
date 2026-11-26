package io.github.hoons1994.shuttersoundzero.pairing

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hoons1994.shuttersoundzero.camera.CameraSettingsRepository
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.adb.AdbCommandSession
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Actual repository/CSC readback after re-pairing, using an emulator shell command session. */
class PairingRestoreInstrumentedTest {
    @Test fun supersededSetupStopsAtConnectionGrantAndCscBoundaries() = runBlocking {
        withCameraRepository { context, prefs, _ ->
            for (replaceAfter in 0..2) {
                prefs.shouldMuteOnBoot = false
                val revision = prefs.beginPairingSetup(true).revision
                var commands = 0
                val repository = CameraSettingsRepository(context) { _, operation ->
                    runCatching { operation(object : AdbCommandSession {
                        override suspend fun connect(port: Int?, timeoutMs: Long) {
                            if (replaceAfter == 0) prefs.beginPairingSetup(false)
                        }
                        override suspend fun execute(command: String): String {
                            val result = shell(command)
                            if (++commands == replaceAfter) prefs.beginPairingSetup(false)
                            return result
                        }
                    }) }
                }
                val result = repository.applyCameraMuteViaAdb(targetMuted = true, setupRevision = revision)
                assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
                assertEquals(replaceAfter, commands)
                assertEquals(false, prefs.pendingPairingTarget)
                assertFalse(prefs.shouldMuteOnBoot)
            }
        }
    }

    @Test fun rePairingRestoresCscAndDisablesMuteOnBoot() = runBlocking {
        withCameraRepository { context, prefs, repository ->
            shell("settings put system ${CscMuteManager.CSC_KEY} 0")
            prefs.shouldMuteOnBoot = true
            prefs.pendingPairingTarget = false
            val revision = prefs.pairingSetupRevision
            val events = mutableListOf<PairingEvent>()
            PairingWorkflow({ 37123 }, { _, _ -> Result.success(Unit) }, { target ->
                repository.applyCameraMuteViaAdb(setupRevision = revision, targetMuted = target)
            }, targetMuted = requireNotNull(prefs.pendingPairingTarget)).run("123456", events::add)
            assertEquals(PairingEvent.ApplySucceeded, events.last())
            assertEquals("1", Settings.System.getString(context.contentResolver, CscMuteManager.CSC_KEY))
            assertFalse(prefs.shouldMuteOnBoot)
        }
    }

    @Test fun verifiedDirectChangeSupersedesAnOlderOppositeSetupInBothDirections() = runBlocking {
        withCameraRepository { context, prefs, repository ->
            for (target in listOf(true, false)) {
                prefs.pendingPairingTarget = !target
                prefs.lastSetupIssue = SetupIssue.CAMERA_APPLY
                repository.setCameraMute(target).getOrThrow()
                assertEquals(target, CscMuteManager.readCscMutedState(context))
                assertEquals(target, prefs.shouldMuteOnBoot)
                assertNull(prefs.pendingPairingTarget)
                assertNull(prefs.lastSetupIssue)
            }
        }
    }

    private suspend fun withCameraRepository(
        test: suspend (Context, PreferencesRepository, CameraSettingsRepository) -> Unit
    ) {
        assumeTrue("Only isolated emulators may change system settings",
            Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("sdk_gphone"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsFile = context.getSharedPreferences("galaxy_camera_mute_prefs", Context.MODE_PRIVATE)
        val originalPrefs = prefsFile.all.toMap()
        val originalCsc = Settings.System.getString(context.contentResolver, CscMuteManager.CSC_KEY)
        val hadPermission = CscMuteManager.hasWritePermission(context)
        assumeTrue(originalCsc == null || originalCsc.toIntOrNull() != null)
        val prefs = PreferencesRepository.getInstance(context)
        try {
            val repository = CameraSettingsRepository(context) { _, operation ->
                runCatching { operation(object : AdbCommandSession {
                    override suspend fun connect(port: Int?, timeoutMs: Long) = Unit
                    override suspend fun execute(command: String): String = shell(command)
                }) }
            }
            test(context, prefs, repository)
        } finally {
            shell(if (originalCsc == null) "settings delete system ${CscMuteManager.CSC_KEY}"
                else "settings put system ${CscMuteManager.CSC_KEY} $originalCsc")
            if (!hadPermission) shell("pm revoke ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
            val editor = prefsFile.edit().clear()
            originalPrefs.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            assertTrue(editor.commit())
        }
    }

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() }
        }
}
