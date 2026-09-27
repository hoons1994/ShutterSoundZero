package io.github.hoons1994.shuttersoundzero.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings

/** 1회 설정에 필요한 Android 설정 화면으로 이동한다. */
object SetupSettingsNavigator {
    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val SETTINGS_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val SOFTWARE_INFO_PREFERENCE_KEY = "software_info"
    private const val WIRELESS_DEBUGGING_PREFERENCE_KEY = "toggle_adb_wireless"

    /** 휴대전화 정보 화면을 열고 소프트웨어 정보 항목을 강조한다. */
    fun openSoftwareInfoSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
                putExtra(SETTINGS_FRAGMENT_ARG_KEY, SOFTWARE_INFO_PREFERENCE_KEY)
                flags = settingsTaskFlags(context)
            })
        } catch (_: Exception) {
        }
    }

    /** 개발자 옵션을 열고 무선 디버깅 항목으로 이동해 강조한다. */
    fun openWirelessDebuggingOrDevOptions(context: Context) {
        val developerOptionsIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            setPackage(SETTINGS_PACKAGE)
            putExtra(SETTINGS_FRAGMENT_ARG_KEY, WIRELESS_DEBUGGING_PREFERENCE_KEY)
            flags = settingsTaskFlags(context)
        }

        try {
            context.startActivity(developerOptionsIntent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    putExtra(SETTINGS_FRAGMENT_ARG_KEY, WIRELESS_DEBUGGING_PREFERENCE_KEY)
                    flags = settingsTaskFlags(context)
                })
            } catch (_: Exception) {
                // 설정 앱을 열 수 없는 기기에서는 아무 작업도 하지 않는다.
            }
        }
    }

    /** 개발자 옵션 상태에 맞는 페어링 설정 화면으로 이동한다. */
    fun openPairingSetupScreen(context: Context) {
        if (!DeveloperOptionsManager.isDeveloperOptionsEnabled(context)) {
            openSoftwareInfoSettings(context)
        } else {
            openWirelessDebuggingOrDevOptions(context)
        }
    }

    /** Wi-Fi 설정 화면을 연다. */
    fun openWifiSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun settingsTaskFlags(context: Context): Int {
        var flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (!context.hasActivity()) flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
        return flags
    }

    private fun Context.hasActivity(): Boolean {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return true
            val base = current.baseContext
            if (base === current) break
            current = base
        }
        return current is Activity
    }
}
