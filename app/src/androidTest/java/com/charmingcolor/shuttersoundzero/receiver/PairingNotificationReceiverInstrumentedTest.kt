package com.charmingcolor.shuttersoundzero.receiver

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charmingcolor.shuttersoundzero.service.PairingForegroundService
import com.charmingcolor.shuttersoundzero.ui.notification.PairingNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingNotificationReceiverInstrumentedTest {

    private val baseContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun remoteInput_trimsCodeAndForwardsSubmitCommand() {
        val context = RecordingContext(baseContext)
        val intent = Intent(PairingNotificationHelper.ACTION_SUBMIT_PAIRING_CODE)
        val remoteInput = RemoteInput.Builder(PairingNotificationHelper.KEY_PAIRING_CODE).build()
        val results = Bundle().apply {
            putCharSequence(PairingNotificationHelper.KEY_PAIRING_CODE, " 123456 ")
        }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, results)

        PairingNotificationReceiver().onReceive(context, intent)

        val forwarded = context.lastForegroundServiceIntent
        requireNotNull(forwarded)
        assertEquals(
            "com.charmingcolor.shuttersoundzero.action.SUBMIT_PAIRING_CODE",
            forwarded.action
        )
        assertEquals("123456", forwarded.getStringExtra("pairing_code"))
        assertEquals(PairingForegroundService::class.java.name, forwarded.component?.className)
        assertNull(context.lastStartedServiceIntent)
    }

    @Test
    fun cancelAction_forwardsStopCommandWithoutStartingPairing() {
        val context = RecordingContext(baseContext)

        PairingNotificationReceiver().onReceive(
            context,
            Intent(PairingNotificationHelper.ACTION_CANCEL_PAIRING)
        )

        val forwarded = context.lastStartedServiceIntent
        requireNotNull(forwarded)
        assertEquals(
            "com.charmingcolor.shuttersoundzero.action.STOP_PAIRING",
            forwarded.action
        )
        assertEquals(PairingForegroundService::class.java.name, forwarded.component?.className)
        assertNull(context.lastForegroundServiceIntent)
    }

    @Test
    fun serviceCompanion_buildsStartAndCompleteTransitions() {
        val context = RecordingContext(baseContext)

        PairingForegroundService.start(context, isDevOptionsOff = true)
        val startIntent = context.lastForegroundServiceIntent
        requireNotNull(startIntent)
        assertEquals(
            "com.charmingcolor.shuttersoundzero.action.START_PAIRING",
            startIntent.action
        )
        assertTrue(startIntent.getBooleanExtra("dev_options_off", false))

        context.reset()
        PairingForegroundService.complete(context, wirelessDebuggingDisabled = true)
        val completeIntent = context.lastStartedServiceIntent
        requireNotNull(completeIntent)
        assertEquals(
            "com.charmingcolor.shuttersoundzero.action.COMPLETE_PAIRING",
            completeIntent.action
        )
        assertTrue(completeIntent.getBooleanExtra("wireless_debugging_disabled", false))
    }

    @Test
    fun pairingNotification_hidesInternalPortAndFocusesOnCodeEntry() {
        val notification = PairingNotificationHelper.buildPairingNotification(
            baseContext,
            pairingPort = 37123
        )

        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

        assertEquals("6자리 코드 입력", title)
        assertEquals("화면에 표시된 6자리 코드를 [코드 입력]에 입력해 주세요.", text)
        assertFalse(title.contains("포트"))
        assertFalse(text.contains("포트"))
        assertTrue(notification.actions.orEmpty().any { it.title.toString() == "코드 입력" })
    }

    @Test
    fun setupNotification_replacesLegacyButtonNameWithCurrentNextStep() {
        val notification = PairingNotificationHelper.buildPairingNotification(
            baseContext,
            statusMessage = "개발자 옵션 활성화 완료",
            isDevOptionsOff = true,
            statusDetail = "앱으로 돌아가 [권한 요청]을 누르세요."
        )

        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

        assertEquals("개발자 옵션 준비 완료", title)
        assertEquals(
            "무선 디버깅을 켠 뒤 [페어링 코드로 기기 페어링]을 열어 주세요.",
            text
        )
        assertFalse(text.contains("권한 요청"))
    }

    @Test
    fun progressNotification_avoidsPermissionLinkageJargon() {
        val notification = PairingNotificationHelper.buildProgressNotification(baseContext)
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

        assertEquals("카메라 무음 설정 적용 중 ⏳", title)
        assertFalse(text.contains("권한 연동"))
        assertTrue(text.contains("카메라 설정"))
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var lastStartedServiceIntent: Intent? = null
            private set
        var lastForegroundServiceIntent: Intent? = null
            private set
        var lastStoppedServiceIntent: Intent? = null
            private set

        override fun startService(service: Intent): ComponentName? {
            lastStartedServiceIntent = Intent(service)
            return service.component
        }

        override fun startForegroundService(service: Intent): ComponentName? {
            lastForegroundServiceIntent = Intent(service)
            return service.component
        }

        override fun stopService(name: Intent): Boolean {
            lastStoppedServiceIntent = Intent(name)
            return true
        }

        fun reset() {
            lastStartedServiceIntent = null
            lastForegroundServiceIntent = null
            lastStoppedServiceIntent = null
        }
    }
}
