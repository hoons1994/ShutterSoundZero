package com.hoons.shutterzero.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

/**
 * Shizuku 프레임워크 연동 매니저
 * 
 * PC 연결 없이 스마트폰 자체 무선 디버깅(Shizuku)을 활용하여
 * 1초 만에 시스템 셔터음 키(csc_pref_camera_forced_shuttersound_key)를 직접 변경합니다.
 */
object ShizukuManager {
    private const val TAG = "ShizukuManager"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_REQ_CODE = 1001

    private var newProcessMethod: Method? = null

    init {
        try {
            newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve Shizuku.newProcess method: ${e.message}")
        }
    }

    /**
     * Shizuku 바인더 초기화 요청 (포그라운드 진입 시 호출)
     */
    fun init(context: Context) {
        try {
            rikka.shizuku.ShizukuProvider.requestBinderForNonProviderProcess(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to requestBinderForNonProviderProcess: ${e.message}")
        }
    }

    /**
     * Shizuku 앱이 스마트폰에 설치되어 있는지 확인
     */
    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Shizuku 서비스가 백그라운드에서 실행 중인지(Binder 연결) 확인
     */
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 우리 앱이 Shizuku 권한을 이미 획득했는지 확인
     */
    fun hasPermission(): Boolean {
        if (!isShizukuRunning()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Shizuku 권한 요청 팝업 띄우기
     */
    fun requestPermission() {
        if (isShizukuRunning() && !hasPermission()) {
            try {
                Shizuku.requestPermission(SHIZUKU_REQ_CODE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request Shizuku permission: ${e.message}", e)
            }
        }
    }

    /**
     * Shizuku 셸 프로세스를 통해 CSC 셔터음 키를 즉시 변경 (0: 무음, 1: 소리)
     */
    suspend fun setCscMuteViaShizuku(enableMute: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isShizukuRunning()) {
            return@withContext Result.failure(IllegalStateException("Shizuku 서비스가 실행 중이지 않습니다."))
        }
        if (!hasPermission()) {
            return@withContext Result.failure(SecurityException("Shizuku 권한이 허용되지 않았습니다."))
        }

        try {
            val value = if (enableMute) "0" else "1"
            val method = newProcessMethod ?: Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
                newProcessMethod = this
            }

            // sh -c 실행 방식 (환경 변수 PATH 및 shebang 완벽 호환)
            val cmd = arrayOf("sh", "-c", "settings put system csc_pref_camera_forced_shuttersound_key $value")
            val process = method.invoke(null, cmd, null, null) as Process
            var exitCode = process.waitFor()

            if (exitCode != 0) {
                // 직접 settings 바이너리 실행으로 2차 시도
                val fallbackCmd = arrayOf("settings", "put", "system", "csc_pref_camera_forced_shuttersound_key", value)
                val fallbackProcess = method.invoke(null, fallbackCmd, null, null) as Process
                exitCode = fallbackProcess.waitFor()
            }

            if (exitCode == 0) {
                Log.i(TAG, "Successfully updated CSC key via Shizuku to $value")
                Result.success(Unit)
            } else {
                val errorMsg = process.errorStream.bufferedReader().readText()
                Log.e(TAG, "Shizuku process failed with code $exitCode: $errorMsg")
                Result.failure(RuntimeException("명령 실행 실패 (코드 $exitCode): $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Shizuku command execution: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Shizuku 앱 열기 또는 플레이스토어로 이동
     */
    fun openShizukuOrStore(context: Context) {
        if (isShizukuInstalled(context)) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                return
            }
        }
        // 플레이스토어 페이지로 연결
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(marketIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        }
    }

    /**
     * 시스템 개발자 옵션 화면으로 바로가기
     */
    fun openDeveloperOptions(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open developer options: ${e.message}")
        }
    }
}
