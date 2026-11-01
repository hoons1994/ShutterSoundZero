package io.github.hoons1994.shuttersoundzero.core

import kotlinx.coroutines.CancellationException

/** An applied CSC setting remains successful if wireless debugging cleanup fails. */
internal inline fun attemptWirelessDebuggingCleanup(action: () -> Result<Unit>): Result<Unit> =
    try {
        action()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
