package io.github.hoons1994.shuttersoundzero.pairing

import io.github.hoons1994.shuttersoundzero.core.adb.AdbServiceNotFoundException
import io.github.hoons1994.shuttersoundzero.core.adb.AdbConnectionFailedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface PairingEvent {
    data object DiscoveryTimedOut : PairingEvent
    data object PairingStarted : PairingEvent
    data object PairingSucceeded : PairingEvent
    data object ApplyStarted : PairingEvent
    data object ApplyRetrying : PairingEvent
    data object ApplySucceeded : PairingEvent
    data class ApplyFailed(val error: Throwable?) : PairingEvent
    data class PairingFailed(val error: Throwable?) : PairingEvent
    data object TimedOut : PairingEvent
    data class Failed(val error: Exception) : PairingEvent
}

/** Pairing sequence independent of Android services, preferences and notification rendering. */
internal class PairingWorkflow(
    private val discoveredPort: () -> Int?,
    private val pair: suspend (Int, String) -> Result<Unit>,
    private val applyCameraMute: suspend () -> Result<Unit>
) {
    companion object {
        // Initial setup also includes code authentication (up to 12s) before connection retries.
        const val SETUP_TIMEOUT_MS = 40_000L
        const val APPLY_TIMEOUT_MS = 25_000L
    }

    suspend fun run(code: String, onEvent: (PairingEvent) -> Unit) {
        suspend fun emit(event: PairingEvent) {
            currentCoroutineContext().ensureActive()
            onEvent(event)
        }
        try {
            val completed = withTimeoutOrNull(SETUP_TIMEOUT_MS) {
                var port = discoveredPort()?.takeIf { it in 1..65535 }
                if (port == null) {
                    for (attempt in 0 until 15) {
                        delay(200)
                        port = discoveredPort()?.takeIf { it in 1..65535 }
                        if (port != null) break
                    }
                }
                if (port == null) {
                    emit(PairingEvent.DiscoveryTimedOut)
                    return@withTimeoutOrNull true
                }
                emit(PairingEvent.PairingStarted)
                val pairing = pair(port, code)
                currentCoroutineContext().ensureActive()
                if (pairing.isFailure) {
                    emit(PairingEvent.PairingFailed(pairing.exceptionOrNull()))
                    return@withTimeoutOrNull true
                }
                emit(PairingEvent.PairingSucceeded)
                delay(300)
                emit(PairingEvent.ApplyStarted)
                applyWithRetry(onEvent)
                true
            }
            currentCoroutineContext().ensureActive()
            if (completed == null) emit(PairingEvent.TimedOut)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            emit(PairingEvent.Failed(error))
        }
    }

    /** Retry the established identity without retaining or asking for the pairing code. */
    suspend fun retryApply(onEvent: (PairingEvent) -> Unit) {
        try {
            val completed = withTimeoutOrNull(APPLY_TIMEOUT_MS) {
                onEvent(PairingEvent.ApplyStarted)
                applyWithRetry(onEvent)
                true
            }
            currentCoroutineContext().ensureActive()
            if (completed == null) onEvent(PairingEvent.TimedOut)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onEvent(PairingEvent.Failed(error))
        }
    }

    private suspend fun applyWithRetry(onEvent: (PairingEvent) -> Unit) {
        var applied = applyCameraMute()
        currentCoroutineContext().ensureActive()
        if (applied.exceptionOrNull().let { it is AdbServiceNotFoundException || it is AdbConnectionFailedException }) {
            onEvent(PairingEvent.ApplyRetrying)
            delay(500)
            currentCoroutineContext().ensureActive()
            applied = applyCameraMute()
        }
        currentCoroutineContext().ensureActive()
        onEvent(if (applied.isSuccess) PairingEvent.ApplySucceeded
            else PairingEvent.ApplyFailed(applied.exceptionOrNull()))
    }
}
