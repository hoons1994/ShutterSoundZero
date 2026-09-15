package com.charmingcolor.shuttersoundzero.core.adb

/**
 * NsdManager의 비동기 discovery 등록과 취소 사이의 경합을 조율한다.
 *
 * stop 요청이 onDiscoveryStarted보다 먼저 도착하더라도 등록 완료 콜백이 늦게 오면
 * 즉시 stopServiceDiscovery를 실행할 수 있도록 stop 의도를 보존한다.
 */
internal class MdnsDiscoveryRegistration {
    private val monitor = Any()
    private var stopRequested = false
    private var registered = false
    private var stopIssued = false

    fun begin() = synchronized(monitor) {
        stopRequested = false
        registered = false
        stopIssued = false
    }

    /** 현재 등록된 discovery를 즉시 중단해야 하면 true를 반환한다. */
    fun requestStop(): Boolean = synchronized(monitor) {
        stopRequested = true
        claimStopIfNeeded()
    }

    /** 늦은 등록 완료 직후 이미 stop이 요청되어 있으면 즉시 중단하도록 true를 반환한다. */
    fun onDiscoveryStarted(): Boolean = synchronized(monitor) {
        registered = true
        claimStopIfNeeded()
    }

    fun onDiscoveryStopped() = synchronized(monitor) {
        registered = false
        stopIssued = false
    }

    fun onStartFailed() = synchronized(monitor) {
        registered = false
        stopIssued = false
    }

    fun shouldDeliverCallbacks(): Boolean = synchronized(monitor) {
        !stopRequested
    }

    private fun claimStopIfNeeded(): Boolean {
        if (stopRequested && registered && !stopIssued) {
            stopIssued = true
            return true
        }
        return false
    }
}
