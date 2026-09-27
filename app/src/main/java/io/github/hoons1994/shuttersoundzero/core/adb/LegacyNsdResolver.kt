package io.github.hoons1994.shuttersoundzero.core.adb

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log

/**
 * API 30–33 cannot cancel an in-flight NSD resolve. A bound worker process owns the native
 * client; after timeout/cancellation the queue waits for that process to die before continuing.
 */
internal class LegacyNsdResolver private constructor(context: Context) : AdbNsdResolver {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val restartBudget = NsdWorkerRestartBudget()
    private val queue = SerialResolutionQueue<NsdServiceInfo>(
        start = ::start,
        stop = ::stop,
        schedule = { delay, action -> handler.postDelayed({ safely(action) }, delay) }
    )
    private val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
        safely { onReply(message) }
        true
    })

    private var connection: ServiceConnection? = null
    private var worker: Messenger? = null
    private var activeAttempt: SerialResolutionQueue.Attempt<NsdServiceInfo>? = null
    private var activeRequestId = 0
    private var nextRequestId = 1
    private var retiringAttempt: SerialResolutionQueue.Attempt<NsdServiceInfo>? = null

    override fun resolve(owner: Any, info: NsdServiceInfo, isActive: () -> Boolean, onResolved: (NsdServiceInfo) -> Unit, onFailure: (Int) -> Unit) {
        handler.post { safely {
            if (isActive()) queue.submit(owner, key(info), info, onResolved, onFailure)
        } }
    }

    override fun cancel(owner: Any, info: NsdServiceInfo?) {
        handler.post { safely { queue.cancel(owner, info?.let(::key)) } }
    }

    private fun start(attempt: SerialResolutionQueue.Attempt<NsdServiceInfo>) {
        if (!restartBudget.canStart(SystemClock.elapsedRealtime())) {
            queue.failed(attempt, NsdManager.FAILURE_INTERNAL_ERROR, retryable = false)
            return
        }
        activeAttempt = attempt
        handler.postDelayed({ safely { queue.expire(attempt) } }, RESOLVE_TIMEOUT_MS)
        if (worker != null) sendResolve() else bindWorker()
    }

    private fun bindWorker() {
        if (connection != null) return
        val newConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (connection !== this) return
                if (retiringAttempt != null) {
                    // A new binder means the retired process is gone, even if disconnect raced.
                    if (worker?.binder !== binder) safely { onWorkerGone(this) }
                    return
                }
                worker = Messenger(binder)
                safely { sendResolve() }
            }

            override fun onServiceDisconnected(name: ComponentName) = safely { onWorkerGone(this) }
            override fun onBindingDied(name: ComponentName) = safely { onWorkerGone(this) }
            override fun onNullBinding(name: ComponentName) = safely { onWorkerGone(this) }
        }
        connection = newConnection
        try {
            val intent = Intent(appContext, LegacyNsdResolveService::class.java)
            if (!appContext.bindService(intent, newConnection, Context.BIND_AUTO_CREATE)) {
                onWorkerGone(newConnection)
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to bind NSD worker (${error.javaClass.simpleName})")
            onWorkerGone(newConnection)
        }
    }

    private fun sendResolve() {
        val attempt = activeAttempt ?: return
        val remote = worker ?: return
        val requestId = nextRequestId++
        activeRequestId = requestId
        val message = Message.obtain(null, LegacyNsdProtocol.RESOLVE).apply {
            arg1 = requestId
            obj = attempt.request.value
            replyTo = receiver
        }
        try {
            remote.send(message)
        } catch (error: RemoteException) {
            Log.w(TAG, "NSD worker disappeared while resolving")
            onWorkerGone(connection ?: return)
        }
    }

    private fun onReply(message: Message) {
        val attempt = activeAttempt ?: return
        if (retiringAttempt != null || message.arg1 != activeRequestId) return
        when (message.what) {
            LegacyNsdProtocol.RESOLVED -> {
                val info = message.obj as? NsdServiceInfo ?: return
                activeAttempt = null
                activeRequestId = 0
                restartBudget.recovered()
                queue.resolved(attempt, info)
            }
            LegacyNsdProtocol.FAILED -> {
                activeAttempt = null
                activeRequestId = 0
                if (message.arg2 != NsdManager.FAILURE_ALREADY_ACTIVE) restartBudget.recovered()
                queue.failed(attempt, message.arg2, message.arg2 == NsdManager.FAILURE_ALREADY_ACTIVE)
            }
        }
    }

    private fun stop(attempt: SerialResolutionQueue.Attempt<NsdServiceInfo>) {
        if (activeAttempt !== attempt || retiringAttempt != null) return
        activeAttempt = null
        activeRequestId = 0
        val remote = worker
        if (remote == null) {
            // Binding did not complete: no native resolve was submitted.
            closeBinding()
            queue.stopped(attempt)
            return
        }
        retiringAttempt = attempt
        try {
            remote.send(Message.obtain(null, LegacyNsdProtocol.SHUTDOWN))
        } catch (_: RemoteException) {
            // A dead binder will also trigger onServiceDisconnected; do not reuse it meanwhile.
        }
    }

    private fun onWorkerGone(source: ServiceConnection) {
        if (connection !== source) return
        closeBinding()
        val retired = retiringAttempt
        retiringAttempt = null
        if (retired != null) {
            restartBudget.recordRestart(SystemClock.elapsedRealtime())
            queue.stopped(retired)
        } else {
            val attempt = activeAttempt ?: return
            activeAttempt = null
            activeRequestId = 0
            restartBudget.recordRestart(SystemClock.elapsedRealtime())
            queue.failed(attempt, NsdManager.FAILURE_INTERNAL_ERROR, retryable = false)
        }
    }

    private fun closeBinding() {
        val old = connection ?: return
        connection = null
        worker = null
        try {
            appContext.unbindService(old)
        } catch (_: IllegalArgumentException) {
            // bindService returned false or the system already removed this connection.
        }
    }

    private fun safely(action: () -> Unit) {
        try { action() } catch (error: RuntimeException) {
            Log.w(TAG, "NSD worker callback failed (${error.javaClass.simpleName})")
        }
    }

    private fun key(info: NsdServiceInfo) = "${info.serviceName}|${info.serviceType}"

    companion object {
        private const val TAG = "LegacyNsdResolver"
        private const val RESOLVE_TIMEOUT_MS = 5_000L
        @Volatile private var instance: LegacyNsdResolver? = null

        fun get(context: Context): LegacyNsdResolver =
            instance ?: synchronized(this) {
                instance ?: LegacyNsdResolver(context).also { instance = it }
            }
    }
}
