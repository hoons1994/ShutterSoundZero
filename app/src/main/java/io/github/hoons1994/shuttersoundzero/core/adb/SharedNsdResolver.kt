package io.github.hoons1994.shuttersoundzero.core.adb

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.IdentityHashMap

/** Process-lifetime coordinator; it stores only an application-context NsdManager, never an Activity. */
internal class SharedNsdResolver private constructor(private val manager: NsdManager) {
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = IdentityHashMap<SerialResolutionQueue.Attempt<NsdServiceInfo>, NsdManager.ResolveListener>()
    private val queue = SerialResolutionQueue<NsdServiceInfo>(
        start = ::start,
        stop = ::stop,
        schedule = { delay, action -> handler.postDelayed({ safely(action) }, delay) }
    )

    fun resolve(owner: Any, info: NsdServiceInfo, isActive: () -> Boolean, onResolved: (NsdServiceInfo) -> Unit, onFailure: (Int) -> Unit) {
        handler.post { safely {
            if (isActive()) queue.submit(owner, key(info), info, onResolved, onFailure)
        } }
    }

    fun cancel(owner: Any, info: NsdServiceInfo? = null) {
        handler.post { safely { queue.cancel(owner, info?.let(::key)) } }
    }

    @Suppress("DEPRECATION")
    private fun start(attempt: SerialResolutionQueue.Attempt<NsdServiceInfo>) {
        val listener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                handler.post { safely {
                    listeners.remove(attempt)
                    queue.resolved(attempt, serviceInfo)
                } }
            }
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                handler.post { safely {
                    listeners.remove(attempt)
                    queue.failed(attempt, errorCode, errorCode == NsdManager.FAILURE_ALREADY_ACTIVE)
                } }
            }
            override fun onResolutionStopped(serviceInfo: NsdServiceInfo) {
                handler.post { safely {
                    listeners.remove(attempt)
                    queue.stopped(attempt)
                } }
            }
            override fun onStopResolutionFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                handler.post { safely {
                    if (errorCode == NsdManager.FAILURE_OPERATION_NOT_RUNNING) {
                        listeners.remove(attempt)
                        queue.stopped(attempt)
                    } else {
                        Log.w(TAG, "mDNS resolution stop failed ($errorCode)")
                    }
                } }
            }
        }
        listeners[attempt] = listener
        try {
            manager.resolveService(attempt.request.value, listener)
            handler.postDelayed({ safely { queue.expire(attempt) } }, RESOLVE_TIMEOUT_MS)
        } catch (error: RuntimeException) {
            listeners.remove(attempt)
            queue.failed(attempt, NsdManager.FAILURE_INTERNAL_ERROR, retryable = false)
        }
    }

    private fun stop(attempt: SerialResolutionQueue.Attempt<NsdServiceInfo>) {
        val listener = listeners[attempt] ?: return
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                manager.stopServiceResolution(listener)
            } catch (error: RuntimeException) {
                // Retain the slot until the original terminal callback, even on a stop failure.
                Log.w(TAG, "Unable to stop mDNS resolution (${error.javaClass.simpleName})")
            }
        }
    }

    private fun safely(action: () -> Unit) {
        try { action() } catch (error: RuntimeException) {
            Log.w(TAG, "mDNS callback failed (${error.javaClass.simpleName})")
        }
    }

    private fun key(info: NsdServiceInfo) = "${info.serviceName}|${info.serviceType}"

    companion object {
        private const val TAG = "SharedNsdResolver"
        private const val RESOLVE_TIMEOUT_MS = 5_000L
        private val instances = IdentityHashMap<NsdManager, SharedNsdResolver>()
        @Synchronized
        fun get(manager: NsdManager): SharedNsdResolver =
            instances.getOrPut(manager) { SharedNsdResolver(manager) }
    }
}
