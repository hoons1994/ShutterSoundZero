package io.github.hoons1994.shuttersoundzero.pairing

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import io.github.hoons1994.shuttersoundzero.core.DeveloperOptionsManager
import io.github.hoons1994.shuttersoundzero.core.adb.LocalNetworkPermissionRequiredException
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import io.github.hoons1994.shuttersoundzero.data.SetupIssue
import io.github.hoons1994.shuttersoundzero.logging.DiagnosticLogger
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationHelper
import io.github.hoons1994.shuttersoundzero.ui.notification.PairingNotificationState

/** Android feedback adapter. The service guards every event with its active generation. */
internal class PairingWorkflowFeedback(
    private val context: Context,
    private val stopPairingDiscovery: () -> Unit,
    private val restartPairingDiscovery: () -> Unit,
    private val onCompleting: () -> Unit,
    private val onFailed: () -> Unit,
    private val onCompleted: (Boolean) -> Unit
) {
    private val prefs = PreferencesRepository.getInstance(context)
    private val TAG = "PairingWorkflow"

    fun handle(event: PairingEvent) {
        when (event) {
            PairingEvent.DiscoveryTimedOut -> {
                prefs.lastSetupIssue = SetupIssue.PAIRING_DISCOVERY
                DiagnosticLogger.record(
                    context,
                    DiagnosticLogger.Stage.PAIRING_DISCOVERY,
                    DiagnosticLogger.Outcome.TIMEOUT
                )
                PairingNotificationHelper.showPairingNotification(
                    context,
                    state = PairingNotificationState.DISCOVERY_WAITING
                )
            }
            PairingEvent.PairingStarted -> {
                stopPairingDiscovery()
                Log.i(TAG, "Attempting pairing using app-discovered endpoint")
                DiagnosticLogger.record(
                    context,
                    DiagnosticLogger.Stage.PAIRING,
                    DiagnosticLogger.Outcome.STARTED
                )
            }
            PairingEvent.PairingSucceeded -> {
                DiagnosticLogger.record(
                    context,
                    DiagnosticLogger.Stage.PAIRING,
                    DiagnosticLogger.Outcome.SUCCESS
                )
                Log.i(TAG, "Pairing successful; applying camera mute and permissions")
            }
            PairingEvent.ApplyStarted -> {
                DiagnosticLogger.record(
                    context,
                    DiagnosticLogger.Stage.ADB_PERMISSION_AND_CSC_APPLY,
                    DiagnosticLogger.Outcome.STARTED
                )
            }
            PairingEvent.ApplySucceeded -> {
                onCompleting()
                prefs.lastSetupIssue = null
                DiagnosticLogger.record(
                    context,
                    DiagnosticLogger.Stage.ADB_PERMISSION_AND_CSC_APPLY,
                    DiagnosticLogger.Outcome.SUCCESS
                )
                val wirelessCleanup = DeveloperOptionsManager.disableWirelessDebugging(
                    context
                )
                val wirelessDebuggingDisabled = wirelessCleanup.isSuccess
                DiagnosticLogger.record(
                    context,
                    DiagnosticLogger.Stage.WIRELESS_DEBUGGING_CLEANUP,
                    if (wirelessDebuggingDisabled) {
                        DiagnosticLogger.Outcome.SUCCESS
                    } else {
                        DiagnosticLogger.Outcome.FAILURE
                    },
                    wirelessCleanup.exceptionOrNull()
                )
                if (wirelessDebuggingDisabled) {
                    Log.i(TAG, "Wireless debugging disabled after successful setup")
                } else {
                    wirelessCleanup.exceptionOrNull()?.let {
                        logFailure("Unable to disable wireless debugging after setup", it)
                    }
                }

                DiagnosticLogger.record(
                    context,
                    DiagnosticLogger.Stage.PAIRING_WORKFLOW,
                    DiagnosticLogger.Outcome.SUCCESS
                )
                Log.i(TAG, "Pairing workflow completed successfully")

                onCompleted(wirelessDebuggingDisabled)
            }
            is PairingEvent.ApplyFailed -> {
                prefs.lastSetupIssue = SetupIssue.CAMERA_APPLY
                DiagnosticLogger.record(
                    context,
                    DiagnosticLogger.Stage.ADB_PERMISSION_AND_CSC_APPLY,
                    DiagnosticLogger.Outcome.FAILURE,
                    event.error
                )
                event.error?.let {
                    logFailure("Mute apply failed after pairing", it)
                } ?: Log.w(TAG, "Mute apply failed after pairing")
                PairingNotificationHelper.showPairingNotification(
                    context,
                    state = PairingNotificationState.CAMERA_APPLY_FAILED
                )
                restartPairingDiscovery()
            }
            is PairingEvent.PairingFailed -> {
                val localNetworkPermissionMissing =
                    event.error is LocalNetworkPermissionRequiredException
                prefs.lastSetupIssue = if (localNetworkPermissionMissing) {
                    SetupIssue.LOCAL_NETWORK_PERMISSION
                } else {
                    SetupIssue.PAIRING_CONNECTION
                }
                DiagnosticLogger.record(
                    context,
                    DiagnosticLogger.Stage.PAIRING,
                    DiagnosticLogger.Outcome.FAILURE,
                    event.error
                )
                event.error?.let {
                    logFailure("Pairing failed", it)
                } ?: Log.w(TAG, "Pairing failed")
                PairingNotificationHelper.showPairingNotification(
                    context,
                    state = if (localNetworkPermissionMissing) {
                        PairingNotificationState.LOCAL_NETWORK_PERMISSION_REQUIRED
                    } else {
                        PairingNotificationState.PAIRING_FAILED
                    }
                )
                if (!localNetworkPermissionMissing) restartPairingDiscovery()
            }
            PairingEvent.TimedOut -> {
                prefs.lastSetupIssue = SetupIssue.PAIRING_TIMEOUT
                DiagnosticLogger.record(
                    context,
                    DiagnosticLogger.Stage.PAIRING_WORKFLOW,
                    DiagnosticLogger.Outcome.TIMEOUT
                )
                Log.w(TAG, "Pairing timed out after 25 seconds")
                PairingNotificationHelper.showPairingNotification(
                    context,
                    state = PairingNotificationState.PAIRING_TIMEOUT
                )
                restartPairingDiscovery()
            }
            is PairingEvent.Failed -> {
                onFailed()
                val localNetworkPermissionMissing = event.error is LocalNetworkPermissionRequiredException
                prefs.lastSetupIssue = if (localNetworkPermissionMissing) {
                    SetupIssue.LOCAL_NETWORK_PERMISSION
                } else {
                    SetupIssue.PAIRING_CONNECTION
                }
                DiagnosticLogger.record(
                    context,
                    DiagnosticLogger.Stage.PAIRING_WORKFLOW,
                    DiagnosticLogger.Outcome.FAILURE,
                    event.error
                )
                logFailure("Pairing error", event.error)
                PairingNotificationHelper.showPairingNotification(
                    context,
                    state = if (localNetworkPermissionMissing) {
                        PairingNotificationState.LOCAL_NETWORK_PERMISSION_REQUIRED
                    } else {
                        PairingNotificationState.PAIRING_ERROR
                    }
                )
                if (!localNetworkPermissionMissing) restartPairingDiscovery()
            }
        }
    }

    private fun logFailure(summary: String, error: Throwable) {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.w(TAG, "$summary: ${error.message}", error)
        } else {
            Log.w(TAG, "$summary (${error.javaClass.simpleName})")
        }
    }
}
