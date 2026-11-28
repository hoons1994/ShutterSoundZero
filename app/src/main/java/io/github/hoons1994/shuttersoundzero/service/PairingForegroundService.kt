package io.github.hoons1994.shuttersoundzero.service

import io.github.hoons1994.shuttersoundzero.data.withAppLanguage
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import io.github.hoons1994.shuttersoundzero.AppDependencies
import io.github.hoons1994.shuttersoundzero.pairing.PairingWorkflow
import io.github.hoons1994.shuttersoundzero.pairing.PairingEvent
import io.github.hoons1994.shuttersoundzero.core.adb.AdbServiceNotFoundException
import io.github.hoons1994.shuttersoundzero.core.adb.AdbConnectionFailedException
import java.net.SocketTimeoutException
import io.github.hoons1994.shuttersoundzero.pairing.PairingWorkflowFeedback
import io.github.hoons1994.shuttersoundzero.MainActivity
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkPermissionRequiredException
import io.github.hoons1994.shuttersoundzero.core.adb.PairingCode
import io.github.hoons1994.shuttersoundzero.core.adb.StandaloneAdbManager
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.logging.DiagnosticLogger
import io.github.hoons1994.shuttersoundzero.logging.DiscoveryLogState
import io.github.hoons1994.shuttersoundzero.pairing.PairingFailureFeedback
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationHelper
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PairingForegroundService : Service() {
    private val discoveryLog = DiscoveryLogState()
    companion object {
        private const val TAG = "PairingForegroundService"
        private const val ACTION_START = "io.github.hoons1994.shuttersoundzero.action.START_PAIRING"
        private const val ACTION_STOP = "io.github.hoons1994.shuttersoundzero.action.STOP_PAIRING"
        private const val ACTION_RECOVER_NOTIFICATION = "io.github.hoons1994.shuttersoundzero.action.RECOVER_PAIRING_NOTIFICATION"
        private const val ACTION_DISMISS_NOTIFICATION = "io.github.hoons1994.shuttersoundzero.action.DISMISS_PAIRING_NOTIFICATION"
        private const val RECOVERY_RETRY_DELAY_MS = 4_000L
        private val liveCodePrompt = MutableStateFlow<PairingCodePrompt?>(null)
        internal val codePrompt = liveCodePrompt.asStateFlow()

        internal fun recoverNotification(context: Context, prompt: PairingCodePrompt) {
            checkNotNull(context.startService(recoverNotificationIntent(context, prompt.sessionId)))
        }

        internal fun recoverNotificationIntent(context: Context, sessionId: String): Intent =
            Intent(ACTION_RECOVER_NOTIFICATION).setClass(context, PairingForegroundService::class.java)
                .setIdentifier(sessionId)

        internal fun dismissNotificationIntent(context: Context, sessionId: String): Intent =
            Intent(ACTION_DISMISS_NOTIFICATION).setClass(context, PairingForegroundService::class.java)
                .setIdentifier(sessionId)
        private const val ACTION_SUBMIT_CODE = "io.github.hoons1994.shuttersoundzero.action.SUBMIT_PAIRING_CODE"
        private const val ACTION_RETRY_APPLY = "io.github.hoons1994.shuttersoundzero.action.RETRY_PAIRING_APPLY"
        private const val EXTRA_DEV_OPTIONS_OFF = "dev_options_off"
        private const val EXTRA_PAIRING_CODE = "pairing_code"
        private const val EXTRA_RESUME_APPLY = "resume_apply"
        private const val EXTRA_TARGET_MUTED = "target_muted"
        private const val EXTRA_RESET_IDENTITY = "reset_identity"
        private const val EXTRA_SETUP_REVISION = "setup_revision"

        fun start(context: Context, isDevOptionsOff: Boolean, resumeApply: Boolean = false, targetMuted: Boolean = true,
                  resetIdentity: Boolean = false, setupRevision: Long? = null) {
            val intent = Intent(context, PairingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEV_OPTIONS_OFF, isDevOptionsOff)
                putExtra(EXTRA_RESUME_APPLY, resumeApply)
                putExtra(EXTRA_TARGET_MUTED, targetMuted)
                putExtra(EXTRA_RESET_IDENTITY, resetIdentity)
                if (setupRevision != null) putExtra(EXTRA_SETUP_REVISION, setupRevision)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun submitCode(context: Context, code: String, sessionId: String) {
            val intent = Intent(context, PairingForegroundService::class.java).apply {
                action = ACTION_SUBMIT_CODE
                identifier = sessionId
                putExtra(EXTRA_PAIRING_CODE, code)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, sessionId: String) {
            try {
                context.startService(stopIntent(context, sessionId))
            } catch (error: Exception) {
                // A stale action must never fall back to stopping a replacement session globally.
                Log.w(TAG, "Unable to deliver pairing cancellation (${error.javaClass.simpleName})")
            }
        }

        fun stopIntent(context: Context, sessionId: String): Intent {
            return Intent(ACTION_STOP)
                .setClass(context, PairingForegroundService::class.java)
                .setIdentifier(sessionId)
        }

        fun retryApplyIntent(context: Context, sessionId: String): Intent =
            Intent(ACTION_RETRY_APPLY).setClass(context, PairingForegroundService::class.java)
                .setIdentifier(sessionId)

    }

    private val adbManager by lazy { StandaloneAdbManager.getInstance(this) }
    private val prefs by lazy { PreferencesRepository.getInstance(this) }
    // ADB operations dispatch their blocking work to IO; service/notification state stays on main.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pairingGeneration = OperationGeneration()
    private val discoveryNotificationGeneration = OperationGeneration()
    private var pairingJob: Job? = null
    private var isSetupObserverRegistered = false
    private val session = PairingSessionState(liveCodePrompt)
    private var isPairingCompleting = false
    private var setupRevision = -1L
    private var applyFailureState = PairingNotificationState.CONNECTION_RETRY
    private val notificationRefresh = PairingNotificationRefreshState()

    private val setupObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            refreshPairingSetupNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val request = if (intent.hasExtra(EXTRA_SETUP_REVISION)) {
                    prefs.pendingPairingSetup()?.takeIf {
                        it.revision == intent.getLongExtra(EXTRA_SETUP_REVISION, -1)
                    }
                } else {
                    prefs.beginPairingSetup(
                        intent.getBooleanExtra(EXTRA_TARGET_MUTED, true),
                        intent.getBooleanExtra(EXTRA_RESET_IDENTITY, false)
                    )
                }
                if (request == null) {
                    if (!session.isActive) stopSelf(startId)
                    return START_NOT_STICKY
                }
                val sameSetup = session.isActive && setupRevision == request.revision
                val targetMuted = request.targetMuted
                setupRevision = request.revision
                when {
                    sameSetup && pairingJob?.isActive == true -> Unit
                    request.resetIdentity -> startPairing(targetMuted)
                    sameSetup && session.hasPaired -> retryApply()
                    prefs.lastSetupIssue == SetupIssue.CAMERA_APPLY && intent.getBooleanExtra(EXTRA_RESUME_APPLY, false) ->
                        startApplyRecovery(targetMuted)
                    else -> startPairing(targetMuted)
                }
            }
            ACTION_SUBMIT_CODE -> {
                val accepted = session.dispatchCode(
                    sessionId = intent.identifier,
                    onPair = { submitPairingCode(intent.getStringExtra(EXTRA_PAIRING_CODE).orEmpty().trim()) },
                    onRetryApply = ::retryApply
                )
                if (!accepted && !session.isActive) stopSelf(startId)
            }
            ACTION_RETRY_APPLY -> {
                val accepted = session.dispatchCommand(intent.identifier) {
                    if (session.hasPaired) retryApply()
                }
                if (!accepted && !session.isActive) stopSelf(startId)
            }
            ACTION_STOP -> {
                val accepted = session.dispatchCommand(intent.identifier) {
                    stopPairing(showSuccess = false, wirelessDebuggingDisabled = false)
                }
                if (!accepted && !session.isActive) stopSelf(startId)
            }
            ACTION_DISMISS_NOTIFICATION -> {
                session.dismissNotification(intent.identifier)
                session.requestDismissalConfirmation(
                    intent.identifier,
                    prefs.pairingSetupRevision,
                    notificationsEnabled = {
                        io.github.hoons1994.shuttersoundzero.core.AppVisibility.canOfferRecovery(this) &&
                            PairingNotificationHelper.areNotificationsEnabled(this)
                    },
                    showConfirmation = {
                        PairingNotificationHelper.showRecoveryConfirmation(this, requireNotNull(session.id))
                    }
                )
                if (!session.isActive) stopSelf(startId)
            }
            ACTION_RECOVER_NOTIFICATION -> {
                session.recoverNotification(intent.identifier, prefs.pairingSetupRevision,
                    notificationsEnabled = { PairingNotificationHelper.areNotificationsEnabled(this) },
                    onBlocked = { Toast.makeText(withAppLanguage(), R.string.setup_notifications_off, Toast.LENGTH_LONG).show() },
                    rediscover = { target -> startPairing(target, recovering = true) })
                if (!session.isActive) stopSelf(startId)
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startPairing(targetMuted: Boolean, recovering: Boolean = false) {
        // 새 1회 설정 시작은 이전 코드 제출 작업의 완료보다 우선한다.
        pairingGeneration.invalidate()
        pairingJob?.cancel()
        pairingJob = null
        stopPairingDiscovery()
        adbManager.clearDiscoveredPorts()
        val sessionId = session.begin(targetMuted = targetMuted, revision = setupRevision, recovering = recovering)
        if (recovering) {
            mainHandler.postDelayed({ session.finishNotificationRecovery(sessionId) }, RECOVERY_RETRY_DELAY_MS)
        }
        isPairingCompleting = false
        val setupState = readPairingSetupState()
        notificationRefresh.reset(setupState)

        prefs.lastSetupIssue = null
        DiagnosticLogger.record(
            this,
            DiagnosticLogger.Stage.PAIRING_DISCOVERY,
            DiagnosticLogger.Outcome.STARTED
        )

        val notification = PairingNotificationHelper.buildPairingNotification(
            this,
            sessionId = requireNotNull(session.id),
            state = setupState.notificationState,
            isDevOptionsOff = !setupState.developerOptionsEnabled
        )
        startForeground(
            PairingNotificationHelper.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        registerSetupObserver()

        if (setupState.canDiscover) {
            startPairingDiscovery()
        }
        // Re-read after registration so a change during service startup cannot be missed.
        refreshPairingSetupNotification()
    }

    private fun startApplyRecovery(targetMuted: Boolean) {
        pairingGeneration.invalidate()
        pairingJob?.cancel()
        pairingJob = null
        stopPairingDiscovery()
        session.begin(paired = true, targetMuted = targetMuted)
        isPairingCompleting = false
        notificationRefresh.reset(readPairingSetupState())
        retryApply()
        registerSetupObserver()
    }

    private fun startPairingDiscovery(): Boolean {
        notificationRefresh.clearDiscovery()
        discoveryLog.reset()
        val notificationGeneration = discoveryNotificationGeneration.next()
        return try {
            adbManager.startPairingDiscovery(
                onPairingPortDiscovered = {
                    DiagnosticLogger.recordDiscovery(
                        this,
                        DiagnosticLogger.Stage.PAIRING_SERVICE_DISCOVERED,
                        discoveryLog.found(DiagnosticLogger.Stage.PAIRING_SERVICE_DISCOVERED, it)
                    )
                    mainHandler.post {
                        if (session.isActive && !isPairingCompleting &&
                            discoveryNotificationGeneration.isCurrent(notificationGeneration)) {
                            notificationRefresh.portDiscovered()
                            refreshPairingSetupNotification()
                        }
                    }
                },
                onConnectPortDiscovered = {
                    DiagnosticLogger.recordDiscovery(
                        this,
                        DiagnosticLogger.Stage.CONNECT_SERVICE_DISCOVERED,
                        discoveryLog.found(DiagnosticLogger.Stage.CONNECT_SERVICE_DISCOVERED, it)
                    )
                },
                onPairingPortLost = {
                    discoveryLog.lost(DiagnosticLogger.Stage.PAIRING_SERVICE_DISCOVERED)
                    DiagnosticLogger.record(this, DiagnosticLogger.Stage.PAIRING_SERVICE_LOST, DiagnosticLogger.Outcome.INFO)
                    mainHandler.post {
                        if (session.isActive && !isPairingCompleting &&
                            discoveryNotificationGeneration.isCurrent(notificationGeneration)) {
                            notificationRefresh.portLost()
                            refreshPairingSetupNotification()
                        }
                    }
                },
                onConnectPortLost = {
                    discoveryLog.lost(DiagnosticLogger.Stage.CONNECT_SERVICE_DISCOVERED)
                    DiagnosticLogger.record(this, DiagnosticLogger.Stage.CONNECT_SERVICE_LOST, DiagnosticLogger.Outcome.INFO)
                },
                onFailure = { source, errorCode ->
                    mainHandler.post {
                        if (session.isActive && !isPairingCompleting &&
                            discoveryNotificationGeneration.isCurrent(notificationGeneration)) {
                            notificationRefresh.discoveryFailed(errorCode, source)
                            refreshPairingSetupNotification()
                        }
                    }
                }
            )
            true
        } catch (e: Exception) {
            stopPairingDiscovery()
            adbManager.clearDiscoveredPorts()
            val localNetworkPermissionMissing = e is LocalNetworkPermissionRequiredException
            prefs.lastSetupIssue = if (localNetworkPermissionMissing) {
                SetupIssue.LOCAL_NETWORK_PERMISSION
            } else {
                SetupIssue.PAIRING_DISCOVERY
            }
            DiagnosticLogger.record(
                this,
                DiagnosticLogger.Stage.PAIRING_DISCOVERY,
                DiagnosticLogger.Outcome.FAILURE,
                e
            )
            Log.w(TAG, "Unable to start pairing discovery (${e.javaClass.simpleName})")
            showDiscoveryNotification(
                sessionId = requireNotNull(session.id),
                state = if (localNetworkPermissionMissing) {
                    PairingNotificationState.LOCAL_NETWORK_PERMISSION_REQUIRED
                } else {
                    PairingNotificationState.DISCOVERY_START_FAILED
                }
            )
            false
        }
    }

    private fun submitPairingCode(code: String) {
        if (!ensureCurrentSetup()) return
        if (pairingJob?.isActive == true || isPairingCompleting) return
        val pairingPort = adbManager.lastDiscoveredPairingPort?.takeIf { it in 1..65535 }

        if (!PairingCode.isValid(code)) {
            prefs.lastSetupIssue = SetupIssue.PAIRING_CODE
            DiagnosticLogger.record(
                this,
                DiagnosticLogger.Stage.PAIRING_CODE_SUBMITTED,
                DiagnosticLogger.Outcome.FAILURE
            )
            val notification = PairingNotificationHelper.buildPairingNotification(
                this,
                sessionId = requireNotNull(session.id),
                pairingPort = pairingPort,
                state = PairingNotificationState.INVALID_PAIRING_CODE
            )
            startForeground(
                PairingNotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
            return
        }

        prefs.lastSetupIssue = null
        DiagnosticLogger.record(
            this,
            DiagnosticLogger.Stage.PAIRING_CODE_SUBMITTED,
            DiagnosticLogger.Outcome.INFO
        )

        launchWorkflow(code)
    }

    private fun retryApply() {
        if (!ensureCurrentSetup()) return
        if (pairingJob != null || isPairingCompleting || !session.hasPaired) return
        launchWorkflow(null)
    }

    private fun launchWorkflow(code: String?) {
        isPairingCompleting = false
        prefs.lastSetupIssue = null

        startForeground(
            PairingNotificationHelper.NOTIFICATION_ID,
            PairingNotificationHelper.buildProgressNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        val generation = pairingGeneration.next()
        val revision = setupRevision
        val targetMuted = session.targetMuted
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                val feedback = PairingWorkflowFeedback(
                    context = this@PairingForegroundService,
                    sessionId = requireNotNull(session.id),
                    stopPairingDiscovery = ::stopPairingDiscovery,
                    restartPairingDiscovery = ::restartPairingDiscovery,
                    onCompleting = { isPairingCompleting = true },
                    onFailed = { isPairingCompleting = false },
                    onCompleted = { postSuccessfulCompletion(generation, it) }
                )
                val workflow = PairingWorkflow(
                    discoveredPort = { adbManager.lastDiscoveredPairingPort },
                    pair = { port, pairingCode ->
                        adbManager.pairLocal(
                            port, pairingCode,
                            resetIdentity = prefs.pendingPairingSetup()?.resetIdentity == true,
                            isCurrent = { prefs.pairingSetupRevision == revision },
                            onIdentityReset = { prefs.pairingIdentityReset(revision) }
                        )
                    },
                    applyCameraMute = { target ->
                        AppDependencies.cameraSettings(this@PairingForegroundService)
                            .applyCameraMuteViaAdb(setupRevision = revision, targetMuted = target)
                    },
                    targetMuted = targetMuted
                )
                val handleEvent: (PairingEvent) -> Unit = { event ->
                    commitPairingSideEffect(generation) {
                        if (event == PairingEvent.PairingSucceeded) session.markPaired()
                        val pairedEvent = when {
                            session.hasPaired && event == PairingEvent.TimedOut ->
                                PairingEvent.ApplyFailed(SocketTimeoutException())
                            session.hasPaired && event is PairingEvent.Failed ->
                                PairingEvent.ApplyFailed(event.error)
                            else -> event
                        }
                        if (pairedEvent is PairingEvent.ApplyFailed) {
                            applyFailureState = if (pairedEvent.error is AdbServiceNotFoundException ||
                                pairedEvent.error is AdbConnectionFailedException) {
                                PairingNotificationState.CONNECTION_RETRY
                            } else PairingNotificationState.CAMERA_APPLY_FAILED
                        }
                        feedback.handle(pairedEvent)
                    }
                }
                if (code == null) workflow.retryApply(handleEvent) else workflow.run(code, handleEvent)
            } finally {
                // 이전 세대의 늦은 finally가 새 pairingJob 참조를 지우지 못하게 한다.
                if (pairingGeneration.isCurrent(generation) && pairingJob === coroutineContext[Job]) {
                    pairingJob = null
                    session.setSubmitting(false)
                    refreshPairingSetupNotification()
                }
            }
        }
        pairingJob = job
        session.setSubmitting(true)
        job.start()
    }

    private fun commitPairingSideEffect(generation: Long, block: () -> Unit) {
        if (prefs.pairingSetupRevision != setupRevision || !pairingGeneration.runIfCurrent(generation, block)) {
            throw CancellationException("Stale pairing workflow")
        }
    }

    private fun logFailure(summary: String, error: Throwable) {
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Log.w(TAG, "$summary: ${error.message}", error)
        } else {
            Log.w(TAG, "$summary (${error.javaClass.simpleName})")
        }
    }

    private fun restartPairingDiscovery() {
        val state = readPairingSetupState()
        if (!state.canDiscover) return
        if (startPairingDiscovery()) {
            Log.i(TAG, "Restarted pairing discovery after unsuccessful attempt")
        }
    }

    private fun postSuccessfulCompletion(generation: Long, wirelessDebuggingDisabled: Boolean) {
        mainHandler.post {
            if (!pairingGeneration.isCurrent(generation) || !ensureCurrentSetup()) return@post
            val accepted = pairingGeneration.runIfCurrent(generation) {
                prefs.recordCameraMuteSuccess(session.targetMuted)
                Toast.makeText(
                    this,
                    if (!session.targetMuted && wirelessDebuggingDisabled) {
                        withAppLanguage().getString(R.string.pairing_restore_success_wireless_disabled)
                    } else if (!session.targetMuted) {
                        withAppLanguage().getString(R.string.pairing_restore_success_disable_wireless_manually)
                    } else if (wirelessDebuggingDisabled) {
                        withAppLanguage().getString(R.string.foreground_pairing_success_wireless_disabled)
                    } else {
                        withAppLanguage().getString(R.string.foreground_pairing_success_disable_wireless_manually)
                    },
                    Toast.LENGTH_LONG
                ).show()

                try {
                    val launchIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                    }
                    startActivity(launchIntent)
                } catch (e: Exception) {
                    logFailure("Background activity launch restricted", e)
                }

                stopPairing(
                    showSuccess = true,
                    wirelessDebuggingDisabled = wirelessDebuggingDisabled
                )
            }
            if (!accepted) {
                Log.i(TAG, "Ignored stale pairing completion")
            }
        }
    }

    private fun stopPairing(showSuccess: Boolean, wirelessDebuggingDisabled: Boolean) {
        val targetMuted = session.targetMuted
        prefs.finishPairingSetup(setupRevision)
        session.end()
        unregisterSetupObserver()
        pairingGeneration.invalidate()
        pairingJob?.cancel()
        pairingJob = null
        stopPairingDiscovery()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (showSuccess) {
            PairingNotificationHelper.showSuccessNotification(
                this,
                wirelessDebuggingDisabled = wirelessDebuggingDisabled,
                targetMuted = targetMuted
            )
        }
        stopSelf()
    }

    override fun onDestroy() {
        session.end()
        unregisterSetupObserver()
        pairingGeneration.invalidate()
        pairingJob?.cancel()
        pairingJob = null
        serviceScope.cancel()
        stopPairingDiscovery()
        super.onDestroy()
    }

    private fun readPairingSetupState() = PairingSetupState(
        developerOptionsEnabled = DeveloperOptionsManager.isDeveloperOptionsEnabled(this),
        wirelessDebuggingEnabled = DeveloperOptionsManager.readWirelessDebuggingEnabled(this)
    )

    private fun refreshPairingSetupNotification() {
        if (!session.isActive || !ensureCurrentSetup() || isPairingCompleting) return
        val state = readPairingSetupState()
        notificationRefresh.observeSetup(state)
        if (session.hasPaired) {
            if (pairingJob == null) {
                PairingNotificationHelper.showPairingNotification(this, requireNotNull(session.id), state = applyFailureState)
            }
            return
        }
        when (val update = notificationRefresh.takeUpdate(blocked = pairingJob != null)) {
            null -> return
            is PairingNotificationRefreshState.Update.DiscoveryFailed -> {
                stopPairingDiscovery()
                adbManager.clearDiscoveredPorts()
                prefs.lastSetupIssue = SetupIssue.PAIRING_DISCOVERY
                DiagnosticLogger.record(
                    this,
                    DiagnosticLogger.Stage.PAIRING_DISCOVERY,
                    DiagnosticLogger.Outcome.FAILURE
                )
                Log.w(TAG, "Asynchronous pairing discovery failed (${update.errorCode})")
                showDiscoveryNotification(
                    sessionId = requireNotNull(session.id),
                    state = PairingNotificationState.DISCOVERY_START_FAILED
                )
                return
            }
            is PairingNotificationRefreshState.Update.Setup -> {
                if (!state.canDiscover) {
                    stopPairingDiscovery()
                    adbManager.clearDiscoveredPorts()
                } else {
                    // Restart even after an off/on cycle during a pairing attempt.
                    if (!startPairingDiscovery()) return
                }
            }
            PairingNotificationRefreshState.Update.PortDiscovered -> Unit
        }
        if (prefs.lastSetupIssue == SetupIssue.LOCAL_NETWORK_PERMISSION) return

        val port = adbManager.lastDiscoveredPairingPort?.takeIf {
            state.canDiscover && it in 1..65535
        }
        if (port != null && prefs.lastSetupIssue == SetupIssue.PAIRING_DISCOVERY) {
            prefs.lastSetupIssue = null
        }
        showDiscoveryNotification(
            sessionId = requireNotNull(session.id),
            pairingPort = port,
            state = if (!state.canDiscover) {
                state.notificationState
            } else {
                PairingFailureFeedback.notificationStateFor(prefs.lastSetupIssue)
                    ?: if (port == null) state.notificationState else null
            },
            isDevOptionsOff = !state.developerOptionsEnabled
        )
    }

    private fun showDiscoveryNotification(sessionId: String, pairingPort: Int? = null,
                                          state: PairingNotificationState?, isDevOptionsOff: Boolean = false) {
        if (!session.notificationDismissed) {
            session.updateRecoveryPort(pairingPort != null)
            PairingNotificationHelper.showPairingNotification(this, sessionId, pairingPort, state, isDevOptionsOff)
        }
    }

    private fun ensureCurrentSetup(): Boolean {
        if (!session.isActive) return false
        if (setupRevision == prefs.pairingSetupRevision) return true
        stopPairing(showSuccess = false, wirelessDebuggingDisabled = false)
        return false
    }

    private fun stopPairingDiscovery() {
        discoveryNotificationGeneration.invalidate()
        notificationRefresh.clearDiscovery()
        adbManager.stopPairingDiscovery()
    }

    private fun registerSetupObserver() {
        if (isSetupObserverRegistered) return
        try {
            // The same observer stays registered for both prerequisites throughout setup.
            isSetupObserverRegistered = true
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
                false,
                setupObserver
            )
            contentResolver.registerContentObserver(
                DeveloperOptionsManager.wirelessDebuggingUri,
                false,
                setupObserver
            )
        } catch (e: Exception) {
            unregisterSetupObserver()
            Log.w(TAG, "Unable to observe pairing settings (${e.javaClass.simpleName})")
        }
    }

    private fun unregisterSetupObserver() {
        if (!isSetupObserverRegistered) return
        try {
            contentResolver.unregisterContentObserver(setupObserver)
        } catch (_: Exception) {
        }
        isSetupObserverRegistered = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
