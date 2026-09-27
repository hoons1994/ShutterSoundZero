package io.github.hoons1994.shuttersoundzero.core.adb

import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Retries stale ports through discovery without confusing discovery with connection failure. */
internal object AdbConnectionResolver {
    suspend fun resolve(
        candidatePorts: List<Int>,
        connect: suspend (Int) -> Boolean,
        discover: suspend () -> Int?
    ): Int {
        for (port in candidatePorts.filter { it in 1..65535 }.distinct()) {
            try {
                if (connect(port)) return port
            } catch (error: CancellationException) {
                throw error
            } catch (error: LocalNetworkPermissionRequiredException) {
                throw error
            } catch (error: BlockingOperationBusyException) {
                throw error
            } catch (_: Exception) {
                // A saved port can belong to an old wireless-debugging session.
            }
        }

        val discoveredPort = try {
            discover()
        } catch (error: CancellationException) {
            throw error
        } catch (error: LocalNetworkPermissionRequiredException) {
            throw error
        } catch (error: BlockingOperationBusyException) {
            throw error
        } catch (error: AdbServiceNotFoundException) {
            throw error
        } catch (error: Exception) {
            throw AdbServiceNotFoundException(error)
        }
        val port = discoveredPort?.takeIf { it in 1..65535 }
            ?: throw AdbServiceNotFoundException()
        try {
            if (connect(port)) return port
        } catch (error: CancellationException) {
            throw error
        } catch (error: LocalNetworkPermissionRequiredException) {
            throw error
        } catch (error: BlockingOperationBusyException) {
            throw error
        } catch (error: Exception) {
            throw AdbConnectionFailedException(error)
        }
        throw AdbConnectionFailedException()
    }
}

internal class AdbServiceNotFoundException(cause: Throwable? = null) : IOException(
    "무선 디버깅의 연결 서비스를 찾지 못했습니다.", cause
)

internal class AdbConnectionFailedException(cause: Throwable? = null) : IOException(
    "무선 디버깅 서비스는 찾았지만 기기에 연결하지 못했습니다.", cause
)
