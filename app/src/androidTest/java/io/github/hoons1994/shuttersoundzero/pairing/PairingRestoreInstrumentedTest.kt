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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Actual repository/CSC readback after re-pairing, using an emulator shell command session. */
class PairingRestoreInstrumentedTest {
    @Test fun rePairingRestoresCscAndDisablesMuteOnBoot() = runBlocking {
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
            shell("settings put system ${CscMuteManager.CSC_KEY} 0")
            prefs.shouldMuteOnBoot = true
            prefs.pendingPairingTarget = false
            val repository = CameraSettingsRepository(context) { _, operation ->
                runCatching { operation(object : AdbCommandSession {
                    override suspend fun connect(port: Int?, timeoutMs: Long) = Unit
                    override suspend fun execute(command: String): String = shell(command)
                }) }
            }
            val events = mutableListOf<PairingEvent>()
            PairingWorkflow({ 37123 }, { _, _ -> Result.success(Unit) }, { target ->
                repository.applyCameraMuteViaAdb(targetMuted = target)
            }, targetMuted = requireNotNull(prefs.pendingPairingTarget)).run("123456", events::add)
            assertEquals(PairingEvent.ApplySucceeded, events.last())
            assertEquals("1", Settings.System.getString(context.contentResolver, CscMuteManager.CSC_KEY))
            assertFalse(prefs.shouldMuteOnBoot)
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
