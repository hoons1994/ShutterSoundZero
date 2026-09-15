package com.charmingcolor.shuttersoundzero.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.charmingcolor.shuttersoundzero.core.adb.LocalNetworkAccess

/**
 * Debug-only Activity used by API 37 instrumentation to exercise the real Android
 * runtime-permission dialog for ACCESS_LOCAL_NETWORK on a fresh installation.
 */
class LocalNetworkPermissionProbeActivity : ComponentActivity() {
    @Volatile
    var permissionResult: Boolean? = null
        private set

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionResult = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(LocalNetworkAccess.PERMISSION)
    }
}
