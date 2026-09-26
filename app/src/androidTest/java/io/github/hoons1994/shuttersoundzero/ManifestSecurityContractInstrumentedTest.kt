package io.github.hoons1994.shuttersoundzero

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hoons1994.shuttersoundzero.receiver.BootReceiver
import io.github.hoons1994.shuttersoundzero.receiver.PairingNotificationReceiver
import io.github.hoons1994.shuttersoundzero.security.AdbIdentityProvider
import io.github.hoons1994.shuttersoundzero.service.CameraMuteTileService
import io.github.hoons1994.shuttersoundzero.service.PairingForegroundService
import io.github.hoons1994.shuttersoundzero.service.TileAuthenticationActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class ManifestSecurityContractInstrumentedTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        packageManager = context.packageManager
    }

    @Test
    fun exportedComponents_keepRequiredPermissionBoundaries() {
        val identityProvider = packageManager.getProviderInfo(
            ComponentName(context, AdbIdentityProvider::class.java),
            PackageManager.GET_META_DATA
        )
        assertTrue(identityProvider.exported)
        assertEquals("android.permission.DUMP", identityProvider.readPermission)
        assertEquals("android.permission.DUMP", identityProvider.writePermission)

        val tileService = packageManager.getServiceInfo(
            ComponentName(context, CameraMuteTileService::class.java),
            PackageManager.GET_META_DATA
        )
        assertTrue(tileService.exported)
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", tileService.permission)
    }

    @Test
    fun internalComponents_remainNonExported() {
        val authenticationActivity = packageManager.getActivityInfo(
            ComponentName(context, TileAuthenticationActivity::class.java),
            PackageManager.GET_META_DATA
        )
        assertFalse(authenticationActivity.exported)

        val pairingService = packageManager.getServiceInfo(
            ComponentName(context, PairingForegroundService::class.java),
            PackageManager.GET_META_DATA
        )
        assertFalse(pairingService.exported)

        val bootReceiver = packageManager.getReceiverInfo(
            ComponentName(context, BootReceiver::class.java),
            PackageManager.GET_META_DATA
        )
        assertFalse(bootReceiver.exported)

        val pairingReceiver = packageManager.getReceiverInfo(
            ComponentName(context, PairingNotificationReceiver::class.java),
            PackageManager.GET_META_DATA
        )
        assertFalse(pairingReceiver.exported)
    }

    @Test
    fun application_disablesBackupAndCleartextTraffic() {
        val applicationInfo = context.applicationInfo

        assertFalse(
            applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0
        )
        assertFalse(
            applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0
        )
    }
}
