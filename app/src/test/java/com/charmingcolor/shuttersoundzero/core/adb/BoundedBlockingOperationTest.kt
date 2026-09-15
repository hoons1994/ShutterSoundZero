package com.charmingcolor.shuttersoundzero.core.adb

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedBlockingOperationTest {
    @Test
    fun timeout_returnsEvenWhenWorkerIgnoresInterrupt() {
        val release = CountDownLatch(1)

        try {
            assertThrows(BlockingOperationTimeoutException::class.java) {
                BoundedBlockingOperation.run(
                    timeoutMillis = 50,
                    threadName = "timeout-test"
                ) {
                    var released = false
                    while (!released) {
                        try {
                            released = release.await(20, TimeUnit.MILLISECONDS)
                        } catch (_: InterruptedException) {
                            // 외부 라이브러리가 interrupt를 무시하는 상황을 재현한다.
                        }
                    }
                    true
                }
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun workerFailure_isUnwrappedForCaller() {
        val error = assertThrows(IllegalStateException::class.java) {
            BoundedBlockingOperation.run(
                timeoutMillis = 1_000,
                threadName = "failure-test"
            ) {
                error("boom")
            }
        }

        assertEquals("boom", error.message)
    }
}
