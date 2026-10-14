package io.github.hoons1994.shuttersoundzero.core.adb

import org.junit.Assert.*
import org.junit.Test

class AdbDiscoveryTraceTest {
    @Test fun noServiceAndUnresolvedServiceHaveDifferentTimeoutReasons() {
        val trace = AdbDiscoveryTrace()
        assertEquals(AdbDiscoveryReason.NO_SERVICE, trace.failure().reason)
        trace.serviceFound()
        assertEquals(AdbDiscoveryReason.RESOLVE_TIMEOUT, trace.failure().reason)
    }

    @Test fun unrelatedEndpointsDoNotHideAResolutionFailureOrItsCode() {
        val trace = AdbDiscoveryTrace()
        trace.serviceFound()
        trace.failed(AdbDiscoveryReason.RESOLVE_FAILED, 3)
        trace.failed(AdbDiscoveryReason.NO_LOCAL_ENDPOINT)
        assertEquals(AdbDiscoveryReason.RESOLVE_FAILED, trace.failure().reason)
        assertEquals(3, trace.failure().nsdErrorCode)
    }

    @Test fun startFailureIsNotOverwrittenByLateCallbacks() {
        val trace = AdbDiscoveryTrace()
        trace.failed(AdbDiscoveryReason.START_FAILED, 2)
        trace.serviceFound()
        trace.failed(AdbDiscoveryReason.RESOLVE_FAILED, 3)
        assertEquals(AdbDiscoveryReason.START_FAILED, trace.failure().reason)
        assertEquals(2, trace.failure().nsdErrorCode)
    }
}
