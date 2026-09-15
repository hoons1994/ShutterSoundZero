package com.charmingcolor.shuttersoundzero.core.adb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MdnsDiscoveryRegistrationTest {
    @Test
    fun stopBeforeDiscoveryStarted_isClaimedWhenRegistrationCompletes() {
        val registration = MdnsDiscoveryRegistration()
        registration.begin()

        assertFalse(registration.requestStop())
        assertFalse(registration.shouldDeliverCallbacks())

        assertTrue(registration.onDiscoveryStarted())
    }

    @Test
    fun stopAfterDiscoveryStarted_isClaimedImmediatelyOnlyOnce() {
        val registration = MdnsDiscoveryRegistration()
        registration.begin()

        assertFalse(registration.onDiscoveryStarted())
        assertTrue(registration.shouldDeliverCallbacks())

        assertTrue(registration.requestStop())
        assertFalse(registration.requestStop())
        assertFalse(registration.shouldDeliverCallbacks())
    }

    @Test
    fun stoppedRegistration_canBeginFreshSession() {
        val registration = MdnsDiscoveryRegistration()
        registration.begin()
        registration.onDiscoveryStarted()
        registration.requestStop()
        registration.onDiscoveryStopped()

        registration.begin()

        assertTrue(registration.shouldDeliverCallbacks())
        assertFalse(registration.onDiscoveryStarted())
    }
}
