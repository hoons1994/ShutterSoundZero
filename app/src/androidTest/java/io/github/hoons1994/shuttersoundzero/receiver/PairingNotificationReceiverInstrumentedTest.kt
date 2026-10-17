package io.github.hoons1994.shuttersoundzero.receiver

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hoons1994.shuttersoundzero.service.PairingForegroundService
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationHelper
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingNotificationReceiverInstrumentedTest {
    private val sessionId = "test-session"

    @Test fun codeRetryAndStopPendingIntentsDoNotCrossSessionBoundaries() {
        fun codeNotification(id: String) = PairingNotificationHelper.buildPairingNotification(
            baseContext, sessionId = id, pairingPort = 37123)
        fun retryNotification(id: String) = PairingNotificationHelper.buildPairingNotification(
            baseContext, sessionId = id, state = PairingNotificationState.CONNECTION_RETRY)
        fun action(notification: Notification, title: String) =
            notification.actions.orEmpty().single { it.title.toString() == title }.actionIntent

        val old = codeNotification(sessionId)
        val current = codeNotification("replacement-session")
        assertFalse(action(old, "코드 입력") == action(current, "코드 입력"))
        assertFalse(action(old, "취소") == action(current, "취소"))
        assertEquals(action(old, "코드 입력"), action(codeNotification(sessionId), "코드 입력"))
        val oldRetry = action(retryNotification(sessionId), "연결·설정 재시도")
        val currentRetry = action(retryNotification("replacement-session"), "연결·설정 재시도")
        assertFalse(oldRetry == currentRetry)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            assertTrue(oldRetry.isImmutable)
            assertTrue(action(old, "취소").isImmutable)
            assertFalse(action(old, "코드 입력").isImmutable)
        }
    }

    @Test fun mutableReplyFillInCannotReplaceTheFixedSessionIdentity() {
        val context = RecordingContext(baseContext)
        val command = PairingNotificationHelper.codeSubmissionIntent(context, sessionId)
        val fillIn = Intent().setIdentifier("replacement-session")
            .putExtra("session_id", "replacement-session")
        val remoteInput = RemoteInput.Builder(PairingNotificationHelper.KEY_PAIRING_CODE).build()
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), fillIn, Bundle().apply {
            putCharSequence(PairingNotificationHelper.KEY_PAIRING_CODE, "123456")
        })
        command.fillIn(fillIn, 0)

        PairingNotificationReceiver().onReceive(context, command)

        assertEquals(sessionId, context.lastForegroundServiceIntent?.identifier)
        assertEquals("123456", context.lastForegroundServiceIntent?.getStringExtra("pairing_code"))
    }

    @Test fun extrasWithoutFixedIdentityCannotSubmitOrCancel() {
        val context = RecordingContext(baseContext)
        for (action in listOf(PairingNotificationHelper.ACTION_SUBMIT_PAIRING_CODE,
            PairingNotificationHelper.ACTION_CANCEL_PAIRING)) {
            PairingNotificationReceiver().onReceive(context,
                Intent(action).putExtra("session_id", sessionId))
            assertNull(context.lastForegroundServiceIntent)
            assertNull(context.lastStartedServiceIntent)
        }
    }

    @Test fun authenticatedRecoveryOffersApplyRetryWithoutCodeEntry() {
        for (state in listOf(PairingNotificationState.CONNECTION_RETRY, PairingNotificationState.CAMERA_APPLY_FAILED)) {
            val notification = PairingNotificationHelper.buildPairingNotification(baseContext, sessionId = sessionId, pairingPort = 37123, state = state)
            assertFalse(notification.actions.orEmpty().any { it.title.toString() == "코드 입력" })
            val retry = notification.actions.orEmpty().single { it.title.toString() == "연결·설정 재시도" }
            if (android.os.Build.VERSION.SDK_INT >= 31) assertTrue(retry.actionIntent.isImmutable)
            assertTrue(retry.remoteInputs.isNullOrEmpty())
        }
    }

    @Test fun identityFailureDoesNotOfferCodeEntryEvenWhenAPortIsRediscovered() {
        val notification = PairingNotificationHelper.buildPairingNotification(baseContext, sessionId = sessionId, pairingPort = 37123,
            state = PairingNotificationState.PAIRING_IDENTITY_FAILED)
        assertEquals("연결 인증 정보를 준비하지 못했습니다",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("진단 정보"))
        assertFalse(notification.actions.orEmpty().any { it.title.toString() == "코드 입력" })
        assertNotNull(notification.contentIntent)
    }

    @Test fun tlsAndCodeAuthenticationFailuresShowDifferentRecoveryInstructions() {
        val tls = PairingNotificationHelper.buildPairingNotification(baseContext, sessionId = sessionId,
            state = PairingNotificationState.PAIRING_TLS_FAILED)
        val authentication = PairingNotificationHelper.buildPairingNotification(baseContext, sessionId = sessionId,
            state = PairingNotificationState.PAIRING_AUTHENTICATION_FAILED)
        assertTrue(tls.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("날짜·시간"))
        assertFalse(tls.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("6자리"))
        assertTrue(authentication.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("새 6자리"))
    }

    @Test
    fun unreadableWirelessStateDoesNotOfferCodeEntryOrAskToTurnItOn() {
        val notification = PairingNotificationHelper.buildPairingNotification(
            baseContext,
            sessionId = sessionId,
            state = PairingNotificationState.WIRELESS_DEBUGGING_STATE_UNKNOWN
        )
        assertEquals(
            "무선 디버깅 상태 확인 필요",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        val guidance = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(guidance.contains("상태를 확인하지 못했습니다"))
        assertFalse(guidance.contains("켜 주세요"))
        assertNotNull(notification.contentIntent)
        assertFalse(notification.actions.orEmpty().any { it.title.toString() == "코드 입력" })
    }

    private val baseContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun remoteInput_trimsCodeAndForwardsSubmitCommand() {
        val context = RecordingContext(baseContext)
        val intent = Intent(PairingNotificationHelper.ACTION_SUBMIT_PAIRING_CODE).setIdentifier(sessionId)
        val remoteInput = RemoteInput.Builder(PairingNotificationHelper.KEY_PAIRING_CODE).build()
        val results = Bundle().apply {
            putCharSequence(PairingNotificationHelper.KEY_PAIRING_CODE, " 123456 ")
        }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, results)

        PairingNotificationReceiver().onReceive(context, intent)

        val forwarded = context.lastForegroundServiceIntent
        requireNotNull(forwarded)
        assertEquals(
            "io.github.hoons1994.shuttersoundzero.action.SUBMIT_PAIRING_CODE",
            forwarded.action
        )
        assertEquals("123456", forwarded.getStringExtra("pairing_code"))
        assertEquals(sessionId, forwarded.identifier)
        assertEquals(PairingForegroundService::class.java.name, forwarded.component?.className)
        assertNull(context.lastStartedServiceIntent)
    }

    @Test
    fun cancelAction_forwardsStopCommandWithoutStartingPairing() {
        val context = RecordingContext(baseContext)

        PairingNotificationReceiver().onReceive(
            context,
            Intent(PairingNotificationHelper.ACTION_CANCEL_PAIRING).setIdentifier(sessionId)
        )

        val forwarded = context.lastStartedServiceIntent
        requireNotNull(forwarded)
        assertEquals(
            "io.github.hoons1994.shuttersoundzero.action.STOP_PAIRING",
            forwarded.action
        )
        assertEquals(PairingForegroundService::class.java.name, forwarded.component?.className)
        assertEquals(sessionId, forwarded.identifier)
        assertNull(context.lastForegroundServiceIntent)
    }

    @Test
    fun serviceCompanion_buildsStartTransition() {
        val context = RecordingContext(baseContext)

        PairingForegroundService.start(context, isDevOptionsOff = true)
        val startIntent = context.lastForegroundServiceIntent
        requireNotNull(startIntent)
        assertEquals(
            "io.github.hoons1994.shuttersoundzero.action.START_PAIRING",
            startIntent.action
        )
        assertTrue(startIntent.getBooleanExtra("dev_options_off", false))
    }

    @Test
    fun pairingNotification_hidesInternalPortAndFocusesOnCodeEntry() {
        val notification = PairingNotificationHelper.buildPairingNotification(
            baseContext,
            sessionId = sessionId,
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
    fun developerOptionsReadyState_exposesCurrentNextStepWithoutLegacyCopy() {
        val notification = PairingNotificationHelper.buildPairingNotification(
            baseContext,
            sessionId = sessionId,
            state = PairingNotificationState.DEVELOPER_OPTIONS_READY,
            isDevOptionsOff = true
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
    fun wirelessDebuggingReady_advancesGuidanceWithoutExposingCodeEntryBeforeDiscovery() {
        val notification = PairingNotificationHelper.buildPairingNotification(
            baseContext,
            sessionId = sessionId,
            state = PairingNotificationState.WIRELESS_DEBUGGING_READY
        )

        assertEquals(
            "무선 디버깅 준비 완료",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "무선 디버깅에서 [페어링 코드로 기기 페어링]을 열어 주세요.",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertFalse(notification.actions.orEmpty().any { it.title.toString() == "코드 입력" })
    }

    @Test
    fun pairingFailureState_asksForFreshPairingWindow() {
        val notification = PairingNotificationHelper.buildPairingNotification(
            baseContext,
            sessionId = sessionId,
            state = PairingNotificationState.PAIRING_FAILED
        )

        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

        assertEquals("기기에 연결하지 못했습니다", title)
        assertEquals("페어링 창이 닫혔거나 연결 정보가 바뀌었을 수 있습니다. 창을 다시 열고 새 6자리 코드로 시도해 주세요.", text)
        assertFalse(title.contains("포트"))
        assertFalse(text.contains("포트"))
        assertFalse(notification.actions.orEmpty().any { it.title.toString() == "코드 입력" })
    }

    @Test
    fun discoveryFailure_opensAppForASetupRestartWithoutStaleCodeEntry() {
        val notification = PairingNotificationHelper.buildPairingNotification(
            baseContext,
            sessionId = sessionId,
            state = PairingNotificationState.DISCOVERY_START_FAILED
        )

        assertNotNull(notification.contentIntent)
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text.contains("알림을 눌러 앱으로 돌아간 뒤"))
        assertTrue(text.contains("[1회 설정 시작]"))
        assertFalse(notification.actions.orEmpty().any { it.title.toString() == "코드 입력" })
    }

    @Test
    fun localNetworkPermissionFailure_guidesAppSettings() {
        val notification = PairingNotificationHelper.buildPairingNotification(
            baseContext,
            sessionId = sessionId,
            state = PairingNotificationState.LOCAL_NETWORK_PERMISSION_REQUIRED
        )

        assertEquals(
            "로컬 네트워크 권한이 필요합니다",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            "[앱 권한 설정]에서 [근처 기기]를 허용한 뒤 앱으로 돌아와 1회 설정을 계속해 주세요. 기기에 따라 [로컬 네트워크]로 표시될 수 있습니다.",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertNotNull(notification.contentIntent)
        assertTrue(notification.actions.orEmpty().any { it.title.toString() == "앱 권한 설정" })
    }

    @Test
    fun progressNotification_avoidsPermissionLinkageJargon() {
        val notification = PairingNotificationHelper.buildProgressNotification(baseContext)
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

        assertEquals("카메라 무음 설정 적용 중", title)
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
