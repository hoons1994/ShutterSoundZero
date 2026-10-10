package io.github.hoons1994.shuttersoundzero

import android.content.Context
import io.github.hoons1994.shuttersoundzero.camera.CameraSettingsRepository
import io.github.hoons1994.shuttersoundzero.camera.ChangeCameraMute
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.CscStateVerifier
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.adb.StandaloneAdbManager

/** Composition root for camera operations. Factories retain only the application context. */
internal object AppDependencies {
    fun cameraSettings(context: Context): CameraSettingsRepository =
        CameraSettingsRepository(context, StandaloneAdbManager.getInstance(context)::runCommands)

    fun changeCameraMute(context: Context): ChangeCameraMute {
        val app = context.applicationContext
        return ChangeCameraMute(
            apply = cameraSettings(app)::setCameraMute,
            verify = { muted -> CscStateVerifier.waitFor(muted) { CscMuteManager.readCscMutedState(app) } },
            disableWirelessDebugging = { DeveloperOptionsManager.disableWirelessDebugging(app) }
        )
    }
}
