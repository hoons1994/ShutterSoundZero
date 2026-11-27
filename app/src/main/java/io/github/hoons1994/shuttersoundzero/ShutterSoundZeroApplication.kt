package io.github.hoons1994.shuttersoundzero

import android.app.Application
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import io.github.hoons1994.shuttersoundzero.core.AppVisibility
import io.github.hoons1994.shuttersoundzero.data.AppLanguage

class ShutterSoundZeroApplication : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() {
        super.onCreate()
        if (getProcessName() != packageName) return
        AppLanguage.migrateAfterOsUpgrade(this)
        registerActivityLifecycleCallbacks(this)
        if (Build.VERSION.SDK_INT < 32) registerLegacyTaskVisibilityReceiver()
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyTaskVisibilityReceiver() {
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Older task queries can return our settings task even after Home.
                // Listen to the system's navigation signal without sending this broadcast.
                val reason = intent.getStringExtra("reason")
                if (reason == "homekey" || reason == "recentapps") AppVisibility.userLeftTask()
            }
        }, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onActivityStarted(activity: Activity) = AppVisibility.started(activity)
    override fun onActivityStopped(activity: Activity) = AppVisibility.stopped(activity)
    override fun onActivityDestroyed(activity: Activity) = AppVisibility.stopped(activity)
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
