package io.github.hoons1994.shuttersoundzero.core.adb

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 여러 mDNS 사용자가 하나의 MulticastLock을 공유할 때 각 사용자의 소유권을 분리한다.
 * 첫 lease가 실제 리소스를 획득하고 마지막 lease가 닫힐 때만 리소스를 해제한다.
 *
 * 첫 리소스 획득이 실패한 경우에는 lease를 발급하지 않는다. 실제 MulticastLock이 없는
 * phantom lease가 남으면 이후 탐색이 재획득을 시도하지 못하므로 반드시 fail-closed 한다.
 */
internal class MulticastLockLeaseManager(
    private val acquireResource: () -> Boolean,
    private val releaseResource: () -> Unit
) {
    private val monitor = Any()
    private var activeLeases = 0

    fun acquire(): Lease {
        synchronized(monitor) {
            if (activeLeases == 0) {
                check(acquireResource()) { "Unable to acquire shared multicast resource" }
            }
            activeLeases += 1
        }
        return Lease(::release)
    }

    private fun release() {
        synchronized(monitor) {
            check(activeLeases > 0) { "Multicast lock lease underflow" }
            activeLeases -= 1
            if (activeLeases == 0) {
                releaseResource()
            }
        }
    }

    internal fun activeLeaseCountForTest(): Int = synchronized(monitor) {
        activeLeases
    }

    internal class Lease(
        private val releaseAction: () -> Unit
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                releaseAction()
            }
        }
    }
}
