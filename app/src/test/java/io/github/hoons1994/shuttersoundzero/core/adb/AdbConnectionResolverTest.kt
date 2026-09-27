package io.github.hoons1994.shuttersoundzero.core.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

class AdbConnectionResolverTest {
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
