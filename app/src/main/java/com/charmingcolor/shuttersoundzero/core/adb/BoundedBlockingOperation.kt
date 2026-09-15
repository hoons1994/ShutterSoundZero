package com.charmingcolor.shuttersoundzero.core.adb

import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 취소를 직접 지원하지 않는 외부 블로킹 API를 호출자 수명과 분리해 제한시간 안에 반환한다.
 *
 * timeout 시 worker를 interrupt하고 Future를 폐기한다. 블로킹 API가 interrupt를 무시하더라도
 * 호출자는 더 이상 해당 worker를 기다리지 않는다. ADB 페어링처럼 공유 manager lock을 잡지 않는
 * 단발 작업에만 사용한다.
 */
internal object BoundedBlockingOperation {
    fun <T> run(
        timeoutMillis: Long,
        threadName: String,
        block: () -> T
    ): T {
        require(timeoutMillis > 0L) { "timeoutMillis must be positive" }

        val task = FutureTask(Callable(block))
        Thread(task, threadName).apply {
            isDaemon = true
            start()
        }

        return try {
            task.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            task.cancel(true)
            throw BlockingOperationTimeoutException(timeoutMillis)
        } catch (e: InterruptedException) {
            task.cancel(true)
            Thread.currentThread().interrupt()
            throw e
        } catch (e: ExecutionException) {
            val cause = e.cause
            when (cause) {
                is Exception -> throw cause
                is Error -> throw cause
                else -> throw IOException("Blocking operation failed", cause)
            }
        }
    }
}

internal class BlockingOperationTimeoutException(timeoutMillis: Long) : IOException(
    "Blocking operation exceeded ${timeoutMillis}ms"
)
