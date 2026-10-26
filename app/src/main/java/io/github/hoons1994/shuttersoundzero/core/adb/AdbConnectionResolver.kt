package io.github.hoons1994.shuttersoundzero.core.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException

/** Retries stale ports through discovery without confusing discovery with connection failure. */
internal object AdbConnectionResolver {
    private const val DISCOVERY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 500L

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

        // The settings switch and mDNS advertisement can precede a usable ADB listener.
        // Reuse the paired identity and rediscover after a short wait before offering pairing.
        var failure: IOException? = null
        repeat(DISCOVERY_ATTEMPTS) { attempt ->
            try {
                return discoverAndConnect(connect, discover)
            } catch (error: AdbServiceNotFoundException) {
                failure = error
            } catch (error: AdbConnectionFailedException) {
                failure = error
            }
            if (attempt < DISCOVERY_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
        }
        throw checkNotNull(failure)
    }

    private suspend fun discoverAndConnect(
        connect: suspend (Int) -> Boolean,
        discover: suspend () -> Int?
    ): Int {
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

internal class AdbServiceNotFoundException(
    cause: Throwable? = null,
    val reason: AdbDiscoveryReason = AdbDiscoveryReason.UNKNOWN,
    val nsdErrorCode: Int? = null
) : IOException(
    "무선 디버깅의 연결 서비스를 찾지 못했습니다.", cause
)

internal class AdbConnectionFailedException(cause: Throwable? = null) : IOException(
    "무선 디버깅 서비스는 찾았지만 기기에 연결하지 못했습니다.", cause
)
