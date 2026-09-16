package com.charmingcolor.shuttersoundzero.core.adb

import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs one owned blocking operation at a time. Cancellation must close its transport, not just
 * interrupt its worker. A timed-out worker retains the slot until it actually exits, so a broken
 * platform/native call cannot accumulate workers on retries. Never pass a shared manager lock here.
 */
internal object BoundedBlockingOperation {
    private val running = AtomicBoolean(false)
    private const val CLEANUP_GRACE_MS = 1_000L

    fun <T> run(
        timeoutMillis: Long,
        threadName: String,
        onCancel: () -> Unit = {},
        block: () -> T
    ): T {
        require(timeoutMillis > 0L) { "timeoutMillis must be positive" }
        if (!running.compareAndSet(false, true)) throw BlockingOperationBusyException()

        val finished = CountDownLatch(1)
        val task = FutureTask(Callable(block))
        val worker = Thread({
            try {
                task.run()
            } finally {
                running.set(false)
                finished.countDown()
            }
        }, threadName).apply { isDaemon = true }
        try {
            worker.start()
        } catch (error: Throwable) {
            running.set(false)
            throw error
        }

        return try {
            val result = task.get(timeoutMillis, TimeUnit.MILLISECONDS)
            // FutureTask can publish its result just before the worker releases the slot.
            finished.await()
            result
        } catch (error: TimeoutException) {
            val failure = BlockingOperationTimeoutException(timeoutMillis)
            cancelAndDrain(task, finished, onCancel, failure)
            throw failure
        } catch (error: InterruptedException) {
            cancelAndDrain(task, finished, onCancel, error)
            Thread.currentThread().interrupt()
            throw error
        } catch (error: ExecutionException) {
            // The operation's finally has already run, but wait for slot bookkeeping as well.
            drain(finished)
            when (val cause = error.cause) {
                is Exception -> throw cause
                is Error -> throw cause
                else -> throw IOException("Blocking operation failed", cause)
            }
        }
    }

    private fun cancelAndDrain(
        task: FutureTask<*>,
        finished: CountDownLatch,
        onCancel: () -> Unit,
        failure: Throwable
    ) {
        // The hook must be short, idempotent and close the socket owned by THIS attempt.
        try {
            onCancel()
        } catch (error: Exception) {
            failure.addSuppressed(error)
        } finally {
            task.cancel(true)
            drain(finished)
        }
    }

    private fun drain(finished: CountDownLatch) {
        var interrupted = Thread.interrupted()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLEANUP_GRACE_MS)
        try {
            while (finished.count != 0L) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) break
                try {
                    finished.await(remaining, TimeUnit.NANOSECONDS)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}

internal class BlockingOperationTimeoutException(timeoutMillis: Long) : IOException(
    "Blocking operation exceeded ${timeoutMillis}ms"
)

internal class BlockingOperationBusyException : IOException(
    "Previous ADB operation is still releasing its resources"
)
