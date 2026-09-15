package com.charmingcolor.shuttersoundzero.core.adb

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException

/**
 * Android 17(API 37)+ 로컬 네트워크 런타임 권한을 한 곳에서 판단한다.
 *
 * 무선 ADB의 mDNS 탐색과 로컬 TCP 연결은 모두 로컬 네트워크 접근에 해당하므로,
 * 기존 연동 사용자의 OTA 이후 재적용 경로까지 같은 정책을 사용해야 한다.
 */
internal object LocalNetworkAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun isGranted(context: Context): Boolean {
        return !requiresRuntimePermission(Build.VERSION.SDK_INT) ||
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    fun requireGranted(context: Context) {
        if (!isGranted(context)) {
            throw LocalNetworkPermissionRequiredException()
        }
    }

    internal fun requiresRuntimePermission(sdkInt: Int): Boolean = sdkInt >= 37

    internal fun shouldRequestForExistingLinkage(
        sdkInt: Int,
        linkageExpected: Boolean,
        permissionRevokedByUser: Boolean,
        permissionGranted: Boolean
    ): Boolean {
        return requiresRuntimePermission(sdkInt) &&
            linkageExpected &&
            !permissionRevokedByUser &&
            !permissionGranted
    }
}

internal class LocalNetworkPermissionRequiredException : IOException(
    "Android 17에서 무선 ADB를 사용하려면 로컬 네트워크 권한이 필요합니다."
)
