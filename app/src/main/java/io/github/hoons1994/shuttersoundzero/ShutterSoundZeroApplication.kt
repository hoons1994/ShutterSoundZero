package io.github.hoons1994.shuttersoundzero

import android.app.Application
import io.github.hoons1994.shuttersoundzero.data.AppLanguage

class ShutterSoundZeroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (getProcessName() == packageName) AppLanguage.migrateAfterOsUpgrade(this)
    }
}
