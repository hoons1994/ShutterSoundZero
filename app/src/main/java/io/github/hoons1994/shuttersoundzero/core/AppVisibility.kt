package io.github.hoons1994.shuttersoundzero.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Main-thread visibility of SSZ screens, independent of the pairing foreground service. */
internal object AppVisibility {
    private val startedScreens = mutableSetOf<Any>()
    private var legacyUserLeft = false
    val isVisible: Boolean get() = startedScreens.isNotEmpty()

    @Suppress("DEPRECATION")
    fun canOfferRecovery(context: Context): Boolean = canOfferRecovery {
        try {
            val manager = context.getSystemService(ActivityManager::class.java)
            val tasks = if (Build.VERSION.SDK_INT >= 32) {
                manager.appTasks.mapNotNull { it.taskInfo }.filter { it.isVisible }
            } else {
                manager.getRunningTasks(1).filterNotNull()
            }
            tasks.any { info ->
                info.baseActivity?.packageName == context.packageName &&
                    info.topActivity?.packageName == "com.android.settings"
            }
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    internal fun canOfferRecovery(settingsTaskVisible: () -> Boolean): Boolean =
        isVisible || (!legacyUserLeft && settingsTaskVisible())

    fun userLeftTask() {
        legacyUserLeft = true
    }

    fun started(screen: Any) {
        legacyUserLeft = false
        startedScreens.add(screen)
    }

    fun stopped(screen: Any) {
        startedScreens.remove(screen)
    }
}
