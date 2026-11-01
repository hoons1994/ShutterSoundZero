package io.github.hoons1994.shuttersoundzero.testing

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.adb.AdbKeyHelperInstrumentedTest
import io.github.hoons1994.shuttersoundzero.receiver.BootReceiverInstrumentedTest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test

/** Runs only behind DisposableAndroidJUnitRunner; exercises the actual fixture lifecycle. */
class TestFixtureLifecycleInstrumentedTest {
    @Test fun identityCleanupBeforePreparationDoesNotTouchUninitializedFields() {
        AdbKeyHelperInstrumentedTest().tearDown()
    }

    @Test fun bootCleanupBeforePreparationDoesNotTouchUninitializedFields() {
        BootReceiverInstrumentedTest().tearDown()
    }

    @Test fun skippedBootPreparationPreservesIdentityAndPreferences() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("galaxy_camera_mute_prefs", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        val identity = File(context.noBackupFilesDir, "adb_identity.enc")
        val originalIdentity = if (identity.exists()) identity.readBytes() else null
        val hadPermission = CscMuteManager.hasWritePermission(context)
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText() }
        try {
            identity.writeText("preserve-fixture-identity")
            assertTrue(prefs.edit().putString("lifecycle_probe", "preserve-fixture-settings").commit())
            shell("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
            assertTrue(CscMuteManager.hasWritePermission(context))
            val fixture = BootReceiverInstrumentedTest()
            try {
                assertThrows(AssumptionViolatedException::class.java) { fixture.setUp() }
            } finally {
                fixture.tearDown()
            }
            assertEquals("preserve-fixture-identity", identity.readText())
            assertEquals("preserve-fixture-settings", prefs.getString("lifecycle_probe", null))
        } finally {
            if (originalIdentity == null) identity.delete() else identity.writeBytes(originalIdentity)
            val editor = prefs.edit().clear()
            original.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            assertTrue(editor.commit())
            if (!hadPermission) shell("pm revoke ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        }
    }
}
