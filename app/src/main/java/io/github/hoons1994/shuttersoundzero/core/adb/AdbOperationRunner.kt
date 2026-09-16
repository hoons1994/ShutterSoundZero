package io.github.hoons1994.shuttersoundzero.core.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One exception/resource boundary for all four public ADB operations, including lease acquisition. */
internal class AdbOperationRunner(
    private val enter: () -> Unit,
    private val exit: () -> Unit,
    private val acquire: () -> AutoCloseable,
    private val cleanup: () -> Unit,
    private val onCleanupFailure: (Exception) -> Unit
) {
    private val mutex = Mutex()

    suspend fun <T> run(operation: suspend () -> T): Result<T> {
        enter()
        try {
            return mutex.withLock {
                var lease: AutoCloseable? = null
                try {
                    lease = acquire()
                    Result.success(operation())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Result.failure(error)
                } finally {
                    safely(cleanup)
                    safely { lease?.close() }
                }
            }
        } finally {
            exit()
        }
    }

    private fun safely(action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            // A cleanup failure must not hide coroutine cancellation or a verified command result.
            try {
                onCleanupFailure(error)
            } catch (_: Exception) {
                // Diagnostics must not prevent the remaining resources from being released.
            }
        }
    }
}
