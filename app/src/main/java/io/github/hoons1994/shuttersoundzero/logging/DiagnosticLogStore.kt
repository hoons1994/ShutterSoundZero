package io.github.hoons1994.shuttersoundzero.logging

import java.io.Closeable
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** One process-wide FIFO for writes, reads and clears; no disk I/O runs on the caller. */
internal class DiagnosticLogStore(
    private val write: (File, List<String>) -> Unit = { file, lines ->
        DiagnosticLogger.writeLog(file, lines)
    }
) : Closeable {
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>()
    ) { work ->
        Thread(work, "SSZ-DiagnosticLog").apply { isDaemon = true }
    }

    fun update(file: () -> File, change: (List<String>) -> List<String>) {
        try {
            executor.execute(UpdateTask(file, change))
        } catch (_: RuntimeException) {
            // Diagnostics cannot break the operation that produced the event.
        }
    }

    private inner class UpdateTask(
        val file: () -> File,
        val change: (List<String>) -> List<String>
    ) : Runnable {
        override fun run() {
            val batch = mutableListOf(this)
            while (true) {
                // A read or clear is a barrier: later writes cannot join this batch.
                val next = executor.queue.peek() as? UpdateTask ?: break
                if (!executor.queue.remove(next)) break
                batch.add(next)
            }
            applyUpdates(batch)
        }
    }

    private fun applyUpdates(batch: List<UpdateTask>) {
        var target: File? = null
        val changes = mutableListOf<(List<String>) -> List<String>>()
        for (task in batch) {
            val nextTarget = runCatching(task.file).getOrNull() ?: continue
            if (target != null && target != nextTarget) {
                applyChanges(target, changes)
                changes.clear()
            }
            target = nextTarget
            changes.add(task.change)
        }
        target?.let { applyChanges(it, changes) }
    }

    private fun applyChanges(target: File, changes: List<(List<String>) -> List<String>>) {
        val before = runCatching { readExisting(target) }.getOrElse {
            changes.forEach { change -> applyOne(target, change) }
            return
        }
        var updated = before
        val applied = mutableListOf<(List<String>) -> List<String>>()
        for (change in changes) {
            // Updates capture fixed, non-sensitive values and do not perform side effects.
            val next = runCatching { DiagnosticLogger.trimForStorage(change(updated)) }.getOrNull() ?: continue
            updated = next
            applied.add(change)
        }
        if (applied.isEmpty() || runCatching { write(target, updated) }.isSuccess) return

        // A reported I/O failure can occur after the rename. Replay only if readback
        // proves that the batch was not committed and the original file is intact.
        val observed = runCatching { readExisting(target) }.getOrNull()
        if (observed == before && observed != updated) {
            applied.forEach { change -> applyOne(target, change) }
        }
    }

    private fun applyOne(target: File, change: (List<String>) -> List<String>) {
        runCatching {
            write(target, DiagnosticLogger.trimForStorage(change(readExisting(target))))
        }
    }

    private fun readExisting(file: File): List<String> =
        if (file.exists()) file.readLines(Charsets.UTF_8) else emptyList()

    suspend fun read(file: () -> File): Result<List<String>> = try {
        submit {
            try {
                val target = file()
                Result.success(
                    if (java.nio.file.Files.notExists(target.toPath())) emptyList()
                    else DiagnosticLogger.trimForStorage(target.readLines(Charsets.UTF_8))
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun clear(file: () -> File): Result<Boolean> = try {
        submit { runCatching { DiagnosticLogger.clear(file()).getOrThrow() } }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun <T> submit(action: () -> T): T {
        // The queued operation is independent of its waiter's lifecycle. In particular,
        // leaving the screen after requesting a clear must not silently cancel that clear.
        val completed = CompletableDeferred<T>()
        try {
            executor.execute {
                runCatching(action).fold(
                    onSuccess = { completed.complete(it) },
                    onFailure = { completed.completeExceptionally(it) }
                )
            }
        } catch (error: RuntimeException) {
            completed.completeExceptionally(error)
        }
        return completed.await()
    }

    override fun close() {
        executor.shutdown()
    }
}
