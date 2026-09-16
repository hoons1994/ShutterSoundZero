package io.github.hoons1994.shuttersoundzero.core.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MulticastLockLeaseManagerTest {
    @Test
    fun sharedResource_isReleasedOnlyAfterLastLeaseCloses() {
        var acquireCount = 0
        var releaseCount = 0
        val manager = MulticastLockLeaseManager(
            acquireResource = {
                acquireCount += 1
                true
            },
            releaseResource = { releaseCount += 1 }
        )

        val discoveryLease = manager.acquire()
        val commandLease = manager.acquire()

        assertEquals(1, acquireCount)
        assertEquals(2, manager.activeLeaseCountForTest())

        commandLease.close()
        assertEquals(0, releaseCount)
        assertEquals(1, manager.activeLeaseCountForTest())

        discoveryLease.close()
        assertEquals(1, releaseCount)
        assertEquals(0, manager.activeLeaseCountForTest())
    }

    @Test
    fun leaseClose_isIdempotent() {
        var releaseCount = 0
        val manager = MulticastLockLeaseManager(
            acquireResource = { true },
            releaseResource = { releaseCount += 1 }
        )

        val lease = manager.acquire()
        lease.close()
        lease.close()

        assertEquals(1, releaseCount)
        assertEquals(0, manager.activeLeaseCountForTest())
    }

    @Test
    fun failedAcquire_doesNotCreatePhantomLease_andNextAcquireRetries() {
        var attempts = 0
        val manager = MulticastLockLeaseManager(
            acquireResource = {
                attempts += 1
                attempts >= 2
            },
            releaseResource = {}
        )

        assertThrows(IllegalStateException::class.java) {
            manager.acquire()
        }
        assertEquals(0, manager.activeLeaseCountForTest())

        val lease = manager.acquire()
        assertEquals(2, attempts)
        assertEquals(1, manager.activeLeaseCountForTest())
        lease.close()
        assertEquals(0, manager.activeLeaseCountForTest())
    }
}
