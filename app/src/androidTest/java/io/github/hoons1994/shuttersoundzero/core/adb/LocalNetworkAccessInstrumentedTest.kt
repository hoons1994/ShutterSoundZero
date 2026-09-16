package io.github.hoons1994.shuttersoundzero.core.adb

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hoons1994.shuttersoundzero.debug.LocalNetworkPermissionProbeActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalNetworkAccessInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        assumeTrue("Android 17(API 37)+ only", Build.VERSION.SDK_INT >= 37)
    }

    @Test
    fun freshInstall_failsClosedThenRuntimePermissionFlowGrantsAccess() = runBlocking {
        // Disposable API 37 AVD의 새 설치 상태에서는 ACCESS_LOCAL_NETWORK가 기본 거부다.
        assertFalse(LocalNetworkAccess.isGranted(context))

        // 권한이 없으면 ADB 네트워크 접근을 시작하기 전에 명확한 typed failure로 중단한다.
        val deniedResult = StandaloneAdbManager(context).setCameraMute(true)
        assertTrue(deniedResult.isFailure)
        assertTrue(deniedResult.exceptionOrNull() is LocalNetworkPermissionRequiredException)

        // 실제 ActivityResultContracts.RequestPermission 경로로 Android 17 시스템 권한창을 열고
        // permission controller의 허용 버튼을 눌러 앱 콜백과 OS permission state까지 확인한다.
        ActivityScenario.launch(LocalNetworkPermissionProbeActivity::class.java).use { scenario ->
            assertTrue("Android 17 local-network permission dialog was not shown", clickSystemAllowButton())

            var callbackResult: Boolean? = null
            val deadline = SystemClock.elapsedRealtime() + 5_000L
            while (SystemClock.elapsedRealtime() < deadline && callbackResult == null) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    callbackResult = activity.permissionResult
                }
                if (callbackResult == null) SystemClock.sleep(100L)
            }

            assertTrue("Permission result callback did not report granted", callbackResult == true)
            assertTrue(LocalNetworkAccess.isGranted(context))
        }
    }

    private fun clickSystemAllowButton(): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiAutomation = instrumentation.uiAutomation
        val deadline = SystemClock.elapsedRealtime() + 5_000L

        while (SystemClock.elapsedRealtime() < deadline) {
            val root = uiAutomation.rootInActiveWindow
            val allowButton = root?.let(::findAllowButton)
            if (allowButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                instrumentation.waitForIdleSync()
                return true
            }
            SystemClock.sleep(100L)
        }
        return false
    }

    private fun findAllowButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val viewId = node.viewIdResourceName.orEmpty()
        val text = node.text?.toString().orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        val looksLikeAllow =
            viewId.contains("permission_allow", ignoreCase = true) ||
                text.equals("Allow", ignoreCase = true) ||
                text.contains("while using", ignoreCase = true) ||
                text.contains("허용") ||
                description.equals("Allow", ignoreCase = true) ||
                description.contains("허용")

        if (looksLikeAllow && node.isClickable && node.isEnabled) return node

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findAllowButton(child)?.let { return it }
        }
        return null
    }
}
