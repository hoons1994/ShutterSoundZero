package io.github.hoons1994.shuttersoundzero.core.adb

import org.junit.Assert.*
import org.junit.Test

class MdnsServicePresenceTest {
    @Test fun queuedResolutionCannotResurrectALostOrReappearedService() {
        val services = MdnsServicePresence()
        val old = services.found("service")
        services.lost("service")
        assertFalse(services.deliverIfCurrent("service", old) { error("lost service delivered") })
        val current = services.found("service")
        assertFalse(services.deliverIfCurrent("service", old) { error("old result delivered") })
        var deliveries = 0
        assertTrue(services.deliverIfCurrent("service", current) { deliveries++ })
        assertEquals(1, deliveries)
    }

    @Test fun duplicateFoundEventsKeepThePendingResolutionValid() {
        val services = MdnsServicePresence()
        val pending = services.found("service")
        assertSame(pending, services.found("service"))
        assertTrue(services.deliverIfCurrent("service", pending) {})
    }

    @Test fun lossIsScopedAndStoppingInvalidatesEveryPendingResult() {
        val services = MdnsServicePresence()
        val first = services.found("first")
        val second = services.found("second")
        services.lost("first")
        assertTrue(services.deliverIfCurrent("second", second) {})
        services.clear()
        assertFalse(services.deliverIfCurrent("first", first) { error("stale first") })
        assertFalse(services.deliverIfCurrent("second", second) { error("stale second") })
    }
}
