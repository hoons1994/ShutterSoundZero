package com.charmingcolor.shuttersoundzero.core.adb

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    fun deniedByDefault_failsClosedThenGrantIsReflected() = runBlocking {
        // 새 AVD의 새 설치 상태에서는 Android 17 로컬 네트워크 런타임 권한이 허용되지 않는다.
        assertFalse(LocalNetworkAccess.isGranted(context))

        // 권한이 없으면 ADB 네트워크 접근을 시작하기 전에 명확한 typed failure로 중단한다.
        val deniedResult = StandaloneAdbManager(context).setCameraMute(true)
        assertTrue(deniedResult.isFailure)
        assertTrue(deniedResult.exceptionOrNull() is LocalNetworkPermissionRequiredException)

        // revoke는 실행 중인 앱 프로세스를 종료할 수 있으므로, disposable AVD의 기본 거부 상태에서
        // grant 전환만 검증한다. 실제 OS permission state가 LocalNetworkAccess에 즉시 반영돼야 한다.
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            LocalNetworkAccess.PERMISSION
        )
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertTrue(LocalNetworkAccess.isGranted(context))
    }
}
