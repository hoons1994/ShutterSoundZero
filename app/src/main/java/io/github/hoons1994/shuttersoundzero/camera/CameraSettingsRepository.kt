package io.github.hoons1994.shuttersoundzero.camera

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import io.github.hoons1994.shuttersoundzero.core.CscMuteManager
import io.github.hoons1994.shuttersoundzero.core.CscStateVerifier
import io.github.hoons1994.shuttersoundzero.core.adb.AdbCommandSession
import io.github.hoons1994.shuttersoundzero.core.adb.AdbShellCommands
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.logging.DiagnosticLogger
import io.github.muntashirakon.adb.AdbShellIdentity
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Camera policy lives here; the injected runner owns ADB serialization and resource cleanup. */
internal class CameraSettingsRepository(
    context: Context,
    private val runCommands: suspend (String, suspend AdbCommandSession.() -> Unit) -> Result<Unit>
) {
    private val context = context.applicationContext

    private fun logFailure(summary: String, error: Throwable) {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.w("CameraSettings", "$summary: ${error.message}", error)
        } else {
            Log.w("CameraSettings", "$summary (${error.javaClass.simpleName})")
        }
    }

    private suspend fun <T> diagnosed(stage: DiagnosticLogger.Stage, block: suspend () -> T): T {
        DiagnosticLogger.record(context, stage, DiagnosticLogger.Outcome.STARTED)
        try {
            return block().also { DiagnosticLogger.record(context, stage, DiagnosticLogger.Outcome.SUCCESS) }
        } catch (error: CancellationException) {
            DiagnosticLogger.record(context, stage, DiagnosticLogger.Outcome.CANCELLED)
            throw error
        } catch (error: Exception) {
            DiagnosticLogger.record(context, stage, DiagnosticLogger.Outcome.FAILURE, error)
            throw error
        }
    }

    suspend fun applyCameraMuteViaAdb(connectPort: Int? = null): Result<Unit> = runCommands(
        "ADB 권한 적용 중 오류가 발생했습니다. 무선 디버깅 상태를 확인해 주세요."
    ) {
        connect(connectPort, 7_000)
        val prefs = PreferencesRepository.getInstance(context)
        val userId = AdbShellIdentity.androidUserIdForUid(context.applicationInfo.uid)
        execute(AdbShellCommands.grantWriteSecureSettings(context.packageName, userId))
        if (!CscMuteManager.hasWritePermission(context)) throw IOException("WRITE_SECURE_SETTINGS 권한 부여 상태를 확인할 수 없습니다.")
        prefs.isPermissionRevokedByUser = false
        applyAndVerifyMute(true)
        prefs.shouldMuteOnBoot = true
    }

    suspend fun revokePermissionViaAdb(): Result<Unit> = runCommands(
        "권한 연동 해제 중 오류가 발생했습니다. 무선 디버깅 상태를 확인해 주세요."
    ) {
        val prefs = PreferencesRepository.getInstance(context)
        try {
            connect(null, 4_000)
            applyAndVerifyMute(false)
            prefs.shouldMuteOnBoot = false
            var commandFailure: Exception? = null
            try {
                val userId = AdbShellIdentity.androidUserIdForUid(context.applicationInfo.uid)
                execute(AdbShellCommands.revokeWriteSecureSettings(context.packageName, userId))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                commandFailure = error
                logFailure("Permission revoke command did not complete cleanly", error)
            }
            if (CscMuteManager.hasWritePermission(context)) {
                prefs.isPermissionRevokedByUser = false
                throw commandFailure ?: IOException("WRITE_SECURE_SETTINGS 권한 회수 상태를 확인할 수 없습니다.")
            }
            prefs.lastConnectPort = -1
            prefs.isPermissionRevokedByUser = true
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (!CscMuteManager.hasWritePermission(context)) {
                prefs.lastConnectPort = -1
                prefs.isPermissionRevokedByUser = true
            }
            throw error
        }
    }

    suspend fun setCameraMute(enableMute: Boolean): Result<Unit> = runCommands(
        "셔터음 설정 변경 중 오류가 발생했습니다. 무선 디버깅 상태를 확인해 주세요."
    ) {
        diagnosed(if (enableMute) DiagnosticLogger.Stage.CSC_REAPPLY else DiagnosticLogger.Stage.CSC_RESTORE) {
            connect(null, 4_000)
            applyAndVerifyMute(enableMute)
            PreferencesRepository.getInstance(context).shouldMuteOnBoot = enableMute
        }
    }

    private suspend fun AdbCommandSession.applyAndVerifyMute(mute: Boolean) {
        val userId = AdbShellIdentity.androidUserIdForUid(context.applicationInfo.uid)
        diagnosed(DiagnosticLogger.Stage.CSC_WRITE) {
            execute(AdbShellCommands.setCameraMute(mute, userId))
        }
        diagnosed(DiagnosticLogger.Stage.CSC_VERIFY) {
            if (!CscStateVerifier.waitFor(mute) { CscMuteManager.readCscMutedState(context) }) {
                throw IOException("카메라 설정 적용 상태를 확인할 수 없습니다.")
            }
        }
    }

}
