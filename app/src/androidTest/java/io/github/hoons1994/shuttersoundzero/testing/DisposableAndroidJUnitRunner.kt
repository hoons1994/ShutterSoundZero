package io.github.hoons1994.shuttersoundzero.testing

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import java.io.File
import java.security.KeyStore

/** Gate the entire suite before any test construction, activity rule, @Before or @After. */
class DisposableAndroidJUnitRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        try {
            check(!targetContext.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
                "An existing screen-lock credential must be preserved"
            }
            val dataDir = File(targetContext.applicationInfo.dataDir)
            if (!File(dataDir, "no_backup/.disposable-test-session").exists()) {
                val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                check(!keys.aliases().hasMoreElements()) { "Existing app authentication keys must be preserved" }
            }
            DisposableTestSession.acquire(
                dataDir,
                Build.HARDWARE in setOf("ranchu", "goldfish"),
                arguments.getString("disposableTestToken")
            )
        } catch (failure: Exception) {
            // MonitoringInstrumentation.finish needs super.onCreate's handlers/registry.
            // Report the refusal through the framework, then fail before JUnit is started.
            sendStatus(0, Bundle().apply {
                putString("stream", "TEST_ENVIRONMENT_REJECTED: ${failure.message}\n")
                putString("shortMsg", "Unsafe instrumented test environment")
            })
            throw IllegalStateException("Unsafe instrumented test environment", failure)
        }
        super.onCreate(arguments)
    }
}
