package io.github.hoons1994.shuttersoundzero.core.adb

import android.net.nsd.NsdServiceInfo
import android.os.Build

/** The same DNS-SD service can be discovered independently on multiple networks. */
internal fun NsdServiceInfo.discoveryKey(): String {
    val networkHandle = if (Build.VERSION.SDK_INT >= 33) network?.networkHandle else null
    return "$serviceName|$serviceType|$networkHandle"
}
