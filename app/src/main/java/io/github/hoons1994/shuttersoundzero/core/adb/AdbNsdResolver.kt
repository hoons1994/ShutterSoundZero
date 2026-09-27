package io.github.hoons1994.shuttersoundzero.core.adb

import android.net.nsd.NsdServiceInfo

internal interface AdbNsdResolver {
    fun resolve(
        owner: Any,
        info: NsdServiceInfo,
        isActive: () -> Boolean,
        onResolved: (NsdServiceInfo) -> Unit,
        onFailure: (Int) -> Unit
    )

    fun cancel(owner: Any, info: NsdServiceInfo? = null)
}
