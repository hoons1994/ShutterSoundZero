package io.github.hoons1994.shuttersoundzero.core.adb

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import java.io.IOException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdbKeyHelperInstrumentedTest {

    private lateinit var context: Context
    private lateinit var identityFiles: List<File>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
    }

    @After
    fun tearDown() {
        identityFiles.forEach { it.delete() }
    }

    @Test
    fun resetIdentity_removesCurrentAndLegacyIdentityFiles() {
        AdbKeyHelper.resetIdentity(context)

        identityFiles.forEach { file ->
            assertFalse("Expected ${file.name} to be removed", file.exists())
        }
    }

    @Test fun failedExistingIdentityLoadPreservesCurrentAndLegacyFiles() {
        assertThrows(IOException::class.java) { AdbKeyHelper.getOrCreateKeyPairAndCertificate(context) }
        identityFiles.forEach { file -> assertEquals("stale-test-data", file.readText()) }
    }
}
