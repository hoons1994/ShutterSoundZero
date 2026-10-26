package io.github.hoons1994.shuttersoundzero.core.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

@OptIn(ExperimentalCoroutinesApi::class)
class AdbConnectionResolverTest {
    @Test fun newlyEnabledWirelessServiceCanBecomeDiscoverableBeforePairingRecovery() = runTest {
        var discoveries = 0
        val port = AdbConnectionResolver.resolve(
            emptyList(),
            connect = { true },
            discover = { if (++discoveries == 1) null else 41000 }
        )
        assertEquals(41000, port)
        assertEquals(2, discoveries)
        assertTrue("Startup recovery must wait before retrying", currentTime > 0)
    }

    @Test fun advertisedPortCanBecomeConnectableAfterTheFirstAttempt() = runTest {
        val attempted = mutableListOf<Int>()
        val port = AdbConnectionResolver.resolve(
            emptyList(),
            connect = {
                attempted += it
                if (attempted.size == 1) throw java.net.ConnectException("service starting")
                true
            },
            discover = { 41000 }
        )
        assertEquals(41000, port)
        assertEquals(listOf(41000, 41000), attempted)
        assertTrue(currentTime > 0)
    }

    @Test fun startupRecoveryRediscoversThePortAfterWirelessRestart() = runTest {
        var discoveries = 0
        val attempted = mutableListOf<Int>()
        val port = AdbConnectionResolver.resolve(
            listOf(40000),
            connect = { attempted += it; it == 42000 },
            discover = { if (++discoveries == 1) 41000 else 42000 }
        )
        assertEquals(42000, port)
        assertEquals(listOf(40000, 41000, 42000), attempted)
    }

    @Test fun persistentConnectionFailureStopsAfterBoundedReadinessAttempts() = runTest {
        var connections = 0
        var discoveries = 0
        val cause = SSLHandshakeException("peer rejected identity")
        val failure = try {
            AdbConnectionResolver.resolve(emptyList(),
                connect = { connections++; throw cause },
                discover = { discoveries++; 41000 })
            error("Must not accept an unauthenticated peer")
        } catch (error: AdbConnectionFailedException) { error }
        assertSame(cause, failure.cause)
        assertEquals(3, connections)
        assertEquals(3, discoveries)
        assertEquals(1_000L, currentTime)
    }

    @Test fun missingServiceStopsAfterBoundedReadinessAttempts() = runTest {
        var discoveries = 0
        try {
            AdbConnectionResolver.resolve(emptyList(), { error("No endpoint") }, { discoveries++; null })
            fail("Missing service must still be reported")
        } catch (_: AdbServiceNotFoundException) { }
        assertEquals(3, discoveries)
        assertEquals(1_000L, currentTime)
    }

    @Test fun permissionLossOrBusyWorkerDuringStartupStopsImmediately() = runTest {
        for (terminal in listOf(LocalNetworkPermissionRequiredException(), BlockingOperationBusyException())) {
            var discoveries = 0
            try {
                AdbConnectionResolver.resolve(emptyList(),
                    connect = { if (discoveries > 1) throw terminal else false },
                    discover = { discoveries++; 41000 })
                fail("Terminal failure must not be retried")
            } catch (error: IOException) { assertSame(terminal, error) }
            assertEquals(2, discoveries)
        }
    }

    @Test fun cancellationDuringReadinessWaitDoesNotStartAnotherAttempt() = runTest {
        var discoveries = 0
        val job = launch {
            AdbConnectionResolver.resolve(emptyList(), { false }, { discoveries++; 41000 })
        }
        runCurrent()
        assertEquals(1, discoveries)
        job.cancelAndJoin()
        assertEquals(1, discoveries)
        assertTrue(job.isCancelled)
    }

    @Test fun cancellationDuringConnectRemainsCancellation() = runTest {
        var discoveries = 0
        val job = launch {
            AdbConnectionResolver.resolve(emptyList(), { awaitCancellation() }, { discoveries++; 41000 })
        }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(1, discoveries)
        assertTrue(job.isCancelled)
    }

    @Test fun staleSavedPortFallsBackToNewDiscoveredPort() = runBlocking {
        val attempted = mutableListOf<Int>()
        val port = AdbConnectionResolver.resolve(
            candidatePorts = listOf(-1, 40000, 40000, 70000),
            connect = {
                attempted += it
                if (it == 40000) throw IOException("old port")
                true
            },
            discover = { 41000 }
        )
        assertEquals(41000, port)
        assertEquals(listOf(40000, 41000), attempted)
    }

    @Test fun usableSavedPortDoesNotRequireDiscovery() = runBlocking {
        val port = AdbConnectionResolver.resolve(
            candidatePorts = listOf(40000),
            connect = { true },
            discover = { error("should not discover") }
        )
        assertEquals(40000, port)
    }

    @Test fun absentServiceIsNotMisreportedAsAuthenticationFailure() {
        val failure = assertThrows(AdbServiceNotFoundException::class.java) {
            runBlocking {
                AdbConnectionResolver.resolve(listOf(40000), { false }, { null })
            }
        }
        assertEquals(CameraMuteFailure.DISCOVERY, CameraMuteFailure.from(failure))
    }

    @Test fun discoveryErrorRetainsCauseAndDiscoveryCategory() {
        val cause = IOException("discovery failed")
        val failure = assertThrows(AdbServiceNotFoundException::class.java) {
            runBlocking {
                AdbConnectionResolver.resolve(emptyList(), { true }, { throw cause })
            }
        }
        assertSame(cause, failure.cause)
        assertEquals(CameraMuteFailure.DISCOVERY, CameraMuteFailure.from(failure))
    }

    @Test fun discoveredEndpointHandshakeFailureOffersReconnection() {
        val cause = SSLHandshakeException("peer rejected identity")
        val failure = assertThrows(AdbConnectionFailedException::class.java) {
            runBlocking {
                AdbConnectionResolver.resolve(emptyList(), { throw cause }, { 41000 })
            }
        }
        assertSame(cause, failure.cause)
        assertEquals(CameraMuteFailure.CONNECTION, CameraMuteFailure.from(failure))
        assertTrue(CameraMuteFailure.from(failure).canReconnect)
    }

    @Test fun discoveredEndpointReturningFalseIsStillConnectionFailure() {
        assertThrows(AdbConnectionFailedException::class.java) {
            runBlocking {
                AdbConnectionResolver.resolve(emptyList(), { false }, { 41000 })
            }
        }
    }

    @Test fun cancellationAtEachStageNeverBecomesARecoveryFailure() {
        val cancellation = CancellationException("cancel")
        for (stage in 0..2) {
            val thrown = assertThrows(CancellationException::class.java) {
                runBlocking {
                    AdbConnectionResolver.resolve(
                        candidatePorts = if (stage == 0) listOf(40000) else emptyList(),
                        connect = { throw cancellation },
                        discover = { if (stage == 1) throw cancellation else 41000 }
                    )
                }
            }
            assertSame(cancellation, thrown)
        }
    }

    @Test fun missingPermissionNeverFallsBackToAnotherConnectionAttempt() {
        val cause = LocalNetworkPermissionRequiredException()
        val failure = assertThrows(LocalNetworkPermissionRequiredException::class.java) {
            runBlocking {
                AdbConnectionResolver.resolve(
                    listOf(40000, 41000), { throw cause }, { error("should not discover") }
                )
            }
        }
        assertSame(cause, failure)
        assertEquals(CameraMuteFailure.LOCAL_NETWORK_PERMISSION, CameraMuteFailure.from(failure))
    }

    @Test fun busyWorkerDoesNotStartDiscoveryOrSuggestLostPairing() {
        val cause = BlockingOperationBusyException()
        val failure = assertThrows(BlockingOperationBusyException::class.java) {
            runBlocking {
                AdbConnectionResolver.resolve(listOf(40000), { throw cause }, { error("should not discover") })
            }
        }
        assertSame(cause, failure)
        assertFalse(CameraMuteFailure.from(failure).canReconnect)
    }

    @Test fun commandOrVerificationFailureDoesNotSuggestPairingWasLost() {
        val failure = CameraMuteFailure.from(IOException("settings command failed"))
        assertEquals(CameraMuteFailure.APPLY, failure)
        assertFalse(failure.canReconnect)
    }
}
