package io.github.hoons1994.shuttersoundzero.core.adb

import org.junit.Assert.*
import org.junit.Test

class MdnsDiscoveryFailureReporterTest {
    @Test fun unrelatedResolutionFailureKeepsLocalPortAndFutureDiscoveryAvailable() {
        val ports = PairingDiscoveryPortState()
        val session = ports.begin()
        val registration = MdnsDiscoveryRegistration().apply { begin(); onDiscoveryStarted() }
        val trace = AdbDiscoveryTrace()
        var fatalFailures = 0
        val failures = MdnsDiscoveryFailureReporter(trace::failed) {
            fatalFailures++
            registration.requestStop()
            ports.clearPorts()
        }
        ports.onPairingPort(session, 37123, "local") {}

        failures.report(AdbDiscoveryReason.RESOLVE_FAILED, 3)

        assertEquals(0, fatalFailures)
        assertEquals(37123, ports.pairingPort)
        assertTrue(registration.shouldDeliverCallbacks())
        assertTrue(ports.onPairingPort(session, 48123, "local-new") {})
        assertEquals(48123, ports.pairingPort)
        assertEquals(AdbDiscoveryReason.RESOLVE_FAILED, trace.failure().reason)
        assertEquals(3, trace.failure().nsdErrorCode)
    }

    @Test fun discoveryStartFailureStillStopsTheSessionAndPreservesItsDiagnostic() {
        val registration = MdnsDiscoveryRegistration().apply { begin() }
        val trace = AdbDiscoveryTrace()
        val fatalCodes = mutableListOf<Int>()
        val failures = MdnsDiscoveryFailureReporter(trace::failed) {
            fatalCodes += it
            registration.requestStop()
        }

        failures.report(AdbDiscoveryReason.START_FAILED, 2)

        assertEquals(listOf(2), fatalCodes)
        assertFalse(registration.shouldDeliverCallbacks())
        assertEquals(AdbDiscoveryReason.START_FAILED, trace.failure().reason)
        assertEquals(2, trace.failure().nsdErrorCode)
    }
}
