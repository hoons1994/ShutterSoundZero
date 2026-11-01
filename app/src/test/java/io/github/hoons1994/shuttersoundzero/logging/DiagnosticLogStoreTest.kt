package io.github.hoons1994.shuttersoundzero.logging

import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticLogStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun missingLogIsEmptyButUnreadableLogAndFailedContextAreFailures() = runBlocking {
        val store = DiagnosticLogStore()
        try {
            val missing = File(temporaryFolder.root, "absent.log")
            assertEquals(emptyList<String>(), store.read { missing }.getOrThrow())
            val directory = temporaryFolder.newFolder("not-a-log-file")
            assertTrue(store.read { directory }.isFailure)
            val failure = SecurityException("sensitive exception details must not become report text")
            val result = store.read { throw failure }
            assertSame(failure, result.exceptionOrNull())
        } finally {
            store.close()
        }
    }

    @Test fun closedQueueCannotMasqueradeAsAnEmptyLog() = runBlocking {
        val store = DiagnosticLogStore()
        store.close()
        assertTrue(store.read { File(temporaryFolder.root, "absent.log") }.isFailure)
    }

    @Test fun cancelledReadIsNotReportedAsAnIoFailureOrEmptyLog() = runBlocking {
        val store = DiagnosticLogStore()
        try {
            try {
                store.read { throw kotlinx.coroutines.CancellationException("cancel read") }
                fail("Cancellation must propagate")
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Cancellation is control flow, not a diagnostic I/O result.
            }
        } finally {
            store.close()
        }
    }

    @Test(timeout = 30000)
    fun queuedBurstCoalescesWritesWithoutCrossingReadOrClear() = runBlocking {
        val file = temporaryFolder.newFile("burst.log").apply { writeText("original\n") }
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val writes = AtomicInteger()
        val store = DiagnosticLogStore(write = { target, lines ->
            if (writes.incrementAndGet() == 1) {
                writeStarted.countDown()
                check(releaseWrite.await(20, TimeUnit.SECONDS))
            }
            target.writeText(lines.joinToString("\n", postfix = "\n"))
        })
        try {
            store.update({ file }) { it + "first" }
            assertTrue(writeStarted.await(10, TimeUnit.SECONDS))
            val burst = (1..100).map { "event-$it" }
            burst.forEach { event -> store.update({ file }) { it + event } }
            val beforeClear = async(start = CoroutineStart.UNDISPATCHED) { store.read { file }.getOrThrow() }
            val clear = async(start = CoroutineStart.UNDISPATCHED) { store.clear { file } }
            store.update({ file }) { it + "after-clear" }
            val afterClear = async(start = CoroutineStart.UNDISPATCHED) { store.read { file }.getOrThrow() }
            releaseWrite.countDown()

            assertEquals(listOf("original", "first") + burst, withTimeout(10000) { beforeClear.await() })
            assertTrue(withTimeout(10000) { clear.await() }.getOrThrow())
            assertEquals(listOf("after-clear"), withTimeout(10000) { afterClear.await() })
            assertEquals("The burst should use one disk write between barriers", 3, writes.get())
        } finally {
            releaseWrite.countDown()
            store.close()
        }
    }

    @Test(timeout = 30000)
    fun blockedDiskDoesNotBlockCallerAndReadClearFollowWriteOrder() = runBlocking {
        val file = temporaryFolder.newFile("events.log").apply { writeText("original\n") }
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val writer = AtomicReference<Thread>()
        val calls = AtomicInteger()
        val caller = Thread.currentThread()
        val store = DiagnosticLogStore(write = { target, lines ->
            if (calls.incrementAndGet() == 1) {
                writer.set(Thread.currentThread())
                writeStarted.countDown()
                check(releaseWrite.await(20, TimeUnit.SECONDS)) { "Test did not release the disk write" }
            }
            DiagnosticLogger.writeLog(target, lines)
        })
        try {
            store.update({ file }) { it + "first" }
            assertTrue(writeStarted.await(10, TimeUnit.SECONDS))
            assertNotSame(caller, writer.get())

            // These submissions and coroutine suspensions must return while disk is blocked.
            store.update({ file }) { it + "second" }
            val beforeClear = async(start = CoroutineStart.UNDISPATCHED) { store.read { file }.getOrThrow() }
            val clear = async(start = CoroutineStart.UNDISPATCHED) { store.clear { file } }
            store.update({ file }) { it + "after-clear" }
            val afterClear = async(start = CoroutineStart.UNDISPATCHED) { store.read { file }.getOrThrow() }
            assertFalse(beforeClear.isCompleted)
            assertFalse(clear.isCompleted)

            releaseWrite.countDown()
            assertEquals(listOf("original", "first", "second"), withTimeout(10000) { beforeClear.await() })
            assertTrue(withTimeout(10000) { clear.await() }.getOrThrow())
            assertEquals(listOf("after-clear"), withTimeout(10000) { afterClear.await() })
            assertEquals("after-clear\n", file.readText())
            assertFalse(File(file.parentFile, "events.log.tmp").exists())
        } finally {
            releaseWrite.countDown()
            store.close()
        }
    }

    @Test(timeout = 30000)
    fun cancellingTheWaitingScreenDoesNotReorderOrDiscardAnAcceptedClear() = runBlocking {
        val file = temporaryFolder.newFile("cancel.log").apply { writeText("old\n") }
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val calls = AtomicInteger()
        val store = DiagnosticLogStore(write = { target, lines ->
            if (calls.incrementAndGet() == 1) {
                writeStarted.countDown()
                check(releaseWrite.await(20, TimeUnit.SECONDS))
            }
            DiagnosticLogger.writeLog(target, lines)
        })
        try {
            store.update({ file }) { it + "before-clear" }
            assertTrue(writeStarted.await(10, TimeUnit.SECONDS))
            val clear = launch(start = CoroutineStart.UNDISPATCHED) { store.clear { file } }
            clear.cancelAndJoin()
            store.update({ file }) { it + "after-clear" }
            releaseWrite.countDown()
            assertEquals(listOf("after-clear"), withTimeout(10000) { store.read { file }.getOrThrow() })
        } finally {
            releaseWrite.countDown()
            store.close()
        }
    }

    @Test(timeout = 30000)
    fun failedPartialWritePreservesExistingEventsAndQueueCanRecover() = runBlocking {
        val file = temporaryFolder.newFile("recovery.log").apply { writeText("original\n") }
        val calls = AtomicInteger()
        val store = DiagnosticLogStore(write = { target, lines ->
            if (calls.incrementAndGet() <= 2) {
                DiagnosticLogger.writeLog(target, lines) { staged, _ ->
                    staged.writeText("partial")
                    throw IOException("injected disk failure")
                }
            } else {
                DiagnosticLogger.writeLog(target, lines)
            }
        })
        try {
            store.update({ file }) { it + "failed" }
            assertEquals(listOf("original"), withTimeout(10000) { store.read { file }.getOrThrow() })
            assertFalse(File(file.parentFile, "recovery.log.tmp").exists())
            store.update({ file }) { it + "recovered" }
            assertEquals(listOf("original", "recovered"), withTimeout(10000) { store.read { file }.getOrThrow() })
        } finally {
            store.close()
        }
    }

    @Test(timeout = 30000)
    fun transientBatchWriteFailureRecoversEventsInOrder() = runBlocking {
        val file = temporaryFolder.newFile("batch-failure.log").apply { writeText("original\n") }
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val writes = AtomicInteger()
        val store = DiagnosticLogStore(write = { target, lines ->
            when (writes.incrementAndGet()) {
                1 -> {
                    writeStarted.countDown()
                    check(releaseWrite.await(20, TimeUnit.SECONDS))
                    DiagnosticLogger.writeLog(target, lines)
                }
                2 -> DiagnosticLogger.writeLog(target, lines) { staged, _ ->
                    staged.writeText("partial")
                    throw IOException("injected batch failure")
                }
                else -> DiagnosticLogger.writeLog(target, lines)
            }
        })
        try {
            store.update({ file }) { it + "first" }
            assertTrue(writeStarted.await(10, TimeUnit.SECONDS))
            store.update({ file }) { it + "second" }
            store.update({ file }) { it + "third" }
            val result = async(start = CoroutineStart.UNDISPATCHED) { store.read { file }.getOrThrow() }
            releaseWrite.countDown()
            assertEquals(listOf("original", "first", "second", "third"), withTimeout(10000) { result.await() })
            assertEquals(4, writes.get())
            assertFalse(File(file.parentFile, "batch-failure.log.tmp").exists())
        } finally {
            releaseWrite.countDown()
            store.close()
        }
    }

    @Test(timeout = 30000)
    fun exceptionAfterAtomicCommitDoesNotDuplicateTheEvent() = runBlocking {
        val file = temporaryFolder.newFile("uncertain.log").apply { writeText("original\n") }
        val writes = AtomicInteger()
        val store = DiagnosticLogStore(write = { target, lines ->
            writes.incrementAndGet()
            DiagnosticLogger.writeLog(target, lines)
            throw IOException("injected exception after commit")
        })
        try {
            store.update({ file }) { it + "event" }
            assertEquals(listOf("original", "event"), withTimeout(10000) { store.read { file }.getOrThrow() })
            assertEquals(1, writes.get())
        } finally {
            store.close()
        }
    }

    @Test(timeout = 30000)
    fun uncertainWriteDoesNotOverwriteUnexpectedCurrentContents() = runBlocking {
        val file = temporaryFolder.newFile("changed.log").apply { writeText("original\n") }
        val writes = AtomicInteger()
        val store = DiagnosticLogStore(write = { target, lines ->
            if (writes.incrementAndGet() == 1) {
                target.writeText("external change\n")
                throw IOException("injected uncertain outcome")
            }
            DiagnosticLogger.writeLog(target, lines)
        })
        try {
            store.update({ file }) { it + "event" }
            assertEquals(listOf("external change"), withTimeout(10000) { store.read { file }.getOrThrow() })
            assertEquals(1, writes.get())
            store.update({ file }) { it + "later" }
            assertEquals(listOf("external change", "later"), withTimeout(10000) { store.read { file }.getOrThrow() })
        } finally {
            store.close()
        }
    }

    @Test(timeout = 30000)
    fun batchedDiscoveryUpdatesKeepEarlierFailuresAndFirstSeenTime() = runBlocking {
        val file = temporaryFolder.newFile("discovery.log").apply { writeText("earlier failure\n") }
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val writes = AtomicInteger()
        val store = DiagnosticLogStore(write = { target, lines ->
            if (writes.incrementAndGet() == 1) {
                writeStarted.countDown()
                check(releaseWrite.await(20, TimeUnit.SECONDS))
            }
            target.writeText(lines.joinToString("\n", postfix = "\n"))
        })
        try {
            store.update({ file }) { it + "other event" }
            assertTrue(writeStarted.await(10, TimeUnit.SECONDS))
            val stage = DiagnosticLogger.Stage.CONNECT_SERVICE_DISCOVERED
            for (repeat in 0..500) {
                val line = "time-$repeat | CONNECT_SERVICE_DISCOVERED | INFO"
                store.update({ file }) { DiagnosticLogger.appendDiscovery(it, stage, line, repeat) }
            }
            val result = async(start = CoroutineStart.UNDISPATCHED) { store.read { file }.getOrThrow() }
            releaseWrite.countDown()
            val lines = withTimeout(10000) { result.await() }
            assertEquals(3, lines.size)
            assertEquals("earlier failure", lines[0])
            assertEquals("other event", lines[1])
            assertTrue(lines[2].startsWith("time-0 | "))
            assertTrue(lines[2].contains("repeats=500 | last_seen=time-500"))
            assertEquals(2, writes.get())
        } finally {
            releaseWrite.countDown()
            store.close()
        }
    }
}
