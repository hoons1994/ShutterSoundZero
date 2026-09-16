package io.github.hoons1994.shuttersoundzero.core.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.InetAddress

/**
 * 로컬 ADB DNS-SD 탐색을 앱 수명 주기에 맞게 중단할 수 있는 작은 NsdManager 래퍼.
 *
 * NsdManager 등록은 비동기이므로 start 직후 stop하면 onDiscoveryStarted가 뒤늦게 올 수 있다.
 * 이 클래스는 stop 의도를 보존하고 등록 완료 콜백에서 즉시 중단해 orphan discovery를 막는다.
 */
internal class SafeAdbMdnsDiscovery(
    context: Context,
    private val serviceType: String,
    private val onFailure: (Int) -> Unit = {},
    private val onDiscovered: (InetAddress, Int) -> Unit
) {
    companion object {
        private const val TAG = "SafeAdbMdnsDiscovery"
    }

    private val nsdManager = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as? NsdManager
        ?: error("NsdManager is unavailable")
    private val registration = MdnsDiscoveryRegistration()
    private val resolver = SharedNsdResolver.get(nsdManager)
    private val resolutionOwner = Any()

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            if (registration.onDiscoveryStarted()) {
                stopRegisteredDiscovery()
            }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            registration.requestStop()
            registration.onStartFailed()
            resolver.cancel(resolutionOwner)
            onFailure(errorCode)
            Log.w(TAG, "mDNS discovery start failed ($errorCode)")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            registration.requestStop()
            registration.onDiscoveryStopped()
            resolver.cancel(resolutionOwner)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            // 플랫폼이 이미 등록을 정리한 경우도 있으므로 앱 상태는 중단된 것으로 닫는다.
            registration.onDiscoveryStopped()
            Log.w(TAG, "mDNS discovery stop failed ($errorCode)")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (!registration.shouldDeliverCallbacks()) return
            resolve(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            resolver.cancel(resolutionOwner, serviceInfo)
        }
    }

    fun start() {
        registration.begin()
        try {
            nsdManager.discoverServices(
                "_${serviceType}._tcp",
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: RuntimeException) {
            registration.onStartFailed()
            throw e
        }
    }

    fun stop() {
        val stopDiscovery = registration.requestStop()
        resolver.cancel(resolutionOwner)
        if (stopDiscovery) {
            stopRegisteredDiscovery()
        }
    }

    private fun stopRegisteredDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: IllegalArgumentException) {
            // 등록 완료/해제 콜백과 플랫폼 내부 상태가 엇갈린 경우에도 재사용하지 않도록 닫는다.
            registration.onDiscoveryStopped()
            Log.w(TAG, "mDNS discovery was already unregistered")
        } catch (e: RuntimeException) {
            registration.onDiscoveryStopped()
            Log.w(TAG, "Unable to stop mDNS discovery (${e.javaClass.simpleName})")
        }
    }

    private fun resolve(serviceInfo: NsdServiceInfo) {
        resolver.resolve(
            resolutionOwner,
            serviceInfo,
            isActive = registration::shouldDeliverCallbacks,
            onResolved = { resolved ->
                if (registration.shouldDeliverCallbacks()) {
                    val address = resolved.host
                    if (address != null) onDiscovered(address, resolved.port)
                }
            },
            onFailure = { errorCode ->
                if (registration.shouldDeliverCallbacks()) {
                    Log.w(TAG, "mDNS resolution failed ($errorCode)")
                    onFailure(errorCode)
                }
            }
        )
    }
}
