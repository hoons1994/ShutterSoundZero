package com.charmingcolor.shuttersoundzero.receiver

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charmingcolor.shuttersoundzero.core.CscMuteManager
import com.charmingcolor.shuttersoundzero.data.PreferencesRepository
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootReceiverInstrumentedTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesRepository
    private lateinit var identityFiles: List<File>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPreferences()
        prefs = PreferencesRepository(context)
        identityFiles = listOf(
            File(context.noBackupFilesDir, "adb_private_key.enc"),
            File(context.noBackupFilesDir, "adb_private_key.enc.tmp"),
            File(context.noBackupFilesDir, "adb_private_key.der"),
            File(context.noBackupFilesDir, "adb_cert.der"),
            File(context.filesDir, "adb_private_key.der"),
            File(context.filesDir, "adb_cert.der")
        )
        identityFiles.forEach { file ->
            file.parentFile?.mkdirs()
            file.writeText("stale-test-data")
        }
        assumeFalse(CscMuteManager.hasWritePermission(context))
    }

    @After
    fun tearDown() {
        identityFiles.forEach { it.delete() }
        clearPreferences()
    }

    @Test
    fun appUpdate_clearsTransientPortAndResetsIdentityAfterUnexpectedPermissionLoss() {
        prefs.lastConnectPort = 43210
        prefs.shouldMuteOnBoot = false
        prefs.isAppLockEnabled = true

        BootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        val reloaded = PreferencesRepository(context)
        assertEquals(-1, reloaded.lastConnectPort)
        assertTrue(reloaded.hasAdbLinkageHistory)
        assertFalse(reloaded.shouldMuteOnBoot)
        assertTrue(reloaded.isAppLockEnabled)
        identityFiles.forEach { assertFalse(it.exists()) }
    }

    @Test
    fun normalBoot_clearsTransientPortButPreservesIdentityAndUserSettings() {
        prefs.lastConnectPort = 43210
        prefs.shouldMuteOnBoot = false
        prefs.isAppLockEnabled = true
        prefs.lastSoftwareFingerprint = Build.FINGERPRINT

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val reloaded = PreferencesRepository(context)
        assertEquals(-1, reloaded.lastConnectPort)
        assertTrue(reloaded.hasAdbLinkageHistory)
        assertFalse(reloaded.shouldMuteOnBoot)
        assertTrue(reloaded.isAppLockEnabled)
        identityFiles.forEach { assertTrue(it.exists()) }
    }

    @Test
    fun softwareUpdate_clearsTransientPortAndResetsIdentityAfterUnexpectedPermissionLoss() {
        prefs.lastConnectPort = 43210
        prefs.shouldMuteOnBoot = false
        prefs.isAppLockEnabled = true
        prefs.lastSoftwareFingerprint = "previous-build-fingerprint"

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val reloaded = PreferencesRepository(context)
        assertEquals(-1, reloaded.lastConnectPort)
        assertEquals(Build.FINGERPRINT, reloaded.lastSoftwareFingerprint)
        assertTrue(reloaded.hasAdbLinkageHistory)
        assertFalse(reloaded.shouldMuteOnBoot)
        assertTrue(reloaded.isAppLockEnabled)
        identityFiles.forEach { assertFalse(it.exists()) }
    }

    @Test
    fun explicitRevoke_preventsIdentityResetOnAppUpdate() {
        prefs.lastConnectPort = 43210
        prefs.isPermissionRevokedByUser = true

        BootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        val reloaded = PreferencesRepository(context)
        assertEquals(-1, reloaded.lastConnectPort)
        assertFalse(reloaded.hasAdbLinkageHistory)
        assertTrue(reloaded.isPermissionRevokedByUser)
        identityFiles.forEach { assertTrue(it.exists()) }
    }

    private fun clearPreferences() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private companion object {
        const val PREF_NAME = "galaxy_camera_mute_prefs"
    }
}
