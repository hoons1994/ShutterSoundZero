package io.github.hoons1994.shuttersoundzero.core.adb

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException

/** API 30–33 resolve runs in a disposable process so a lost callback cannot poison the app process. */
internal class LegacyNsdResolveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val manager by lazy { getSystemService(Context.NSD_SERVICE) as NsdManager }
    private var activeRequestId = 0
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            LegacyNsdProtocol.RESOLVE -> resolve(message)
            LegacyNsdProtocol.SHUTDOWN -> Process.killProcess(Process.myPid())
        }
        true
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        // A caller crash must not leave its unresolved native client in a cached process.
        handler.post { Process.killProcess(Process.myPid()) }
        return false
    }

    @Suppress("DEPRECATION")
    private fun resolve(message: Message) {
        val requestId = message.arg1
        val replyTo = message.replyTo ?: return
        val info = message.obj as? NsdServiceInfo
        if (requestId == 0 || info == null || info.serviceName.isNullOrBlank() || info.serviceType.isNullOrBlank()) {
            reply(replyTo, LegacyNsdProtocol.FAILED, requestId, NsdManager.FAILURE_INTERNAL_ERROR)
            return
        }
        if (activeRequestId != 0) {
            reply(replyTo, LegacyNsdProtocol.FAILED, requestId, NsdManager.FAILURE_ALREADY_ACTIVE)
            return
        }

        activeRequestId = requestId
        val listener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                handler.post {
                    if (activeRequestId == requestId) {
                        activeRequestId = 0
                        reply(replyTo, LegacyNsdProtocol.RESOLVED, requestId, info = serviceInfo)
                    }
                }
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                handler.post {
                    if (activeRequestId == requestId) {
                        activeRequestId = 0
                        reply(replyTo, LegacyNsdProtocol.FAILED, requestId, errorCode)
                    }
                }
            }
        }

        try {
            manager.resolveService(info, listener)
        } catch (_: RuntimeException) {
            activeRequestId = 0
            reply(replyTo, LegacyNsdProtocol.FAILED, requestId, NsdManager.FAILURE_INTERNAL_ERROR)
        }
    }

    private fun reply(to: Messenger, what: Int, requestId: Int, error: Int = 0, info: NsdServiceInfo? = null) {
        val message = Message.obtain(null, what).apply {
            arg1 = requestId
            arg2 = error
            obj = info
        }
        try {
            to.send(message)
        } catch (_: RemoteException) {
            // The caller went away; this process is discarded on the next timeout or unbind.
        }
    }
}
