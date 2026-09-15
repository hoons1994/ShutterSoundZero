package com.charmingcolor.shuttersoundzero.core.adb

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalNetworkAccessInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        assumeTrue("Android 17(API 37)+ only", Build.VERSION.SDK_INT >= 37)
    }

    @Test
    fun runtimePermission_reflectsActualGrantAndRevokeState() {
        val wasGranted = LocalNetworkAccess.isGranted(context)
        try {
            setPermissionGranted(false)
            assertFalse(LocalNetworkAccess.isGranted(context))

            setPermissionGranted(true)
            assertTrue(LocalNetworkAccess.isGranted(context))
        } finally {
            setPermissionGranted(wasGranted)
        }
    }

    @Test
    fun adbManager_failsClosedBeforeNetworkAccessWhenPermissionDenied() = runBlocking {
        val wasGranted = LocalNetworkAccess.isGranted(context)
        try {
            setPermissionGranted(false)

            val result = StandaloneAdbManager(context).setCameraMute(true)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is LocalNetworkPermissionRequiredException)
        } finally {
            setPermissionGranted(wasGranted)
        }
    }

    private fun setPermissionGranted(granted: Boolean) {
        val action = if (granted) "grant" else "revoke"
        val command = "pm $action ${context.packageName} ${LocalNetworkAccess.PERMISSION}"
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
            .use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { input ->
                    input.readBytes()
                }
            }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
