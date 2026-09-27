package io.github.hoons1994.shuttersoundzero.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestSecurityContractInstrumentedTest {

    private lateinit var targetContext: Context
    private lateinit var packageManager: PackageManager

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        packageManager = targetContext.packageManager
    }

    @Test
    fun adbIdentityProviderIsExportedOnlyWithDumpPermission() {
        val provider = packageManager.getProviderInfo(
            appComponent("security.AdbIdentityProvider"),
            0,
        )

        assertTrue(provider.exported)
        assertEquals("android.permission.DUMP", provider.permission)
    }

    @Test
    fun cameraMuteTileServiceUsesQuickSettingsBindingPermission() {
        val service = packageManager.getServiceInfo(
            appComponent("service.CameraMuteTileService"),
            0,
        )

        assertTrue(service.exported)
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", service.permission)
    }

    @Test
    fun internalComponentsAreNotExported() {
        val tileAuthenticationActivity = packageManager.getActivityInfo(
            appComponent("service.TileAuthenticationActivity"),
            0,
        )
        val pairingForegroundService = packageManager.getServiceInfo(
            appComponent("service.PairingForegroundService"),
            0,
        )
        val bootReceiver = packageManager.getReceiverInfo(
            appComponent("receiver.BootReceiver"),
            0,
        )
        val pairingNotificationReceiver = packageManager.getReceiverInfo(
            appComponent("receiver.PairingNotificationReceiver"),
            0,
        )

        assertFalse(tileAuthenticationActivity.exported)
        assertFalse(pairingForegroundService.exported)
        assertFalse(bootReceiver.exported)
        assertFalse(pairingNotificationReceiver.exported)
    }

    @Test
    fun applicationDisablesBackupAndCleartextTraffic() {
        val applicationInfo = packageManager.getApplicationInfo(targetContext.packageName, 0)

        assertFalse(
            "Application backup must remain disabled",
            (applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0,
        )
        assertFalse(
            "Application cleartext traffic must remain disabled",
            applicationInfo.usesCleartextTraffic,
        )
    }

    private fun appComponent(className: String): ComponentName =
        ComponentName(targetContext.packageName, targetContext.packageName + "." + className)
}
