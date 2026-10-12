package io.github.hoons1994.shuttersoundzero.pairing

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
    suspend fun run(code: String, onEvent: (PairingEvent) -> Unit) {
        suspend fun emit(event: PairingEvent) {
            currentCoroutineContext().ensureActive()
            onEvent(event)
        }
        try {
            val completed = withTimeoutOrNull(25_000) {
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
                val applied = applyCameraMute()
                emit(if (applied.isSuccess) PairingEvent.ApplySucceeded
                    else PairingEvent.ApplyFailed(applied.exceptionOrNull()))
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
}
