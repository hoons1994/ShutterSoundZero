package com.charmingcolor.shuttersoundzero.core.adb

import java.util.ArrayDeque

/** Dispatcher-confined queue shared by all discoveries using the same native NSD client. */
internal class SerialResolutionQueue<T>(
    private val start: (Attempt<T>) -> Unit,
    private val stop: (Attempt<T>) -> Unit,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val maxPending: Int = 64,
    private val maxAttempts: Int = 3
) {
    internal class Request<T>(
        val owner: Any,
        val key: String,
        val value: T,
        var onSuccess: ((T) -> Unit)?,
        var onFailure: ((Int) -> Unit)?
    ) {
        var attempts = 0
        var cancelled = false
        fun detach() { onSuccess = null; onFailure = null }
    }
    internal class Attempt<T>(val request: Request<T>)

    private val pending = ArrayDeque<Request<T>>()
    private val delayed = mutableSetOf<Request<T>>()
    private var active: Attempt<T>? = null

    fun submit(owner: Any, key: String, value: T, onSuccess: (T) -> Unit, onFailure: (Int) -> Unit) {
        val all = pending.toList() + delayed + listOfNotNull(active?.request)
        if (all.any { !it.cancelled && it.owner === owner && it.key == key }) return
        if (all.size >= maxPending) {
            onFailure(ERROR_QUEUE_FULL)
            return
        }
        pending.addLast(Request(owner, key, value, onSuccess, onFailure))
        pump()
    }

    fun cancel(owner: Any, key: String? = null) {
        fun matches(request: Request<T>) = request.owner === owner && (key == null || request.key == key)
        pending.filter(::matches).forEach { it.cancelled = true; it.detach() }
        pending.removeAll { it.cancelled }
        delayed.filter(::matches).forEach { it.cancelled = true; it.detach() }
        delayed.removeAll { it.cancelled }
        active?.takeIf { matches(it.request) && !it.request.cancelled }?.let {
            it.request.cancelled = true
            it.request.detach()
            // Keep the native slot until a terminal callback. Pre-34 Android cannot cancel resolve.
            stop(it)
        }
    }

    fun resolved(attempt: Attempt<T>, value: T) {
        if (active !== attempt) return
        active = null
        val request = attempt.request
        val callback = request.onSuccess.takeUnless { request.cancelled }
        request.detach()
        try { callback?.invoke(value) } finally { pump() }
    }

    fun failed(attempt: Attempt<T>, error: Int, retryable: Boolean) {
        if (active !== attempt) return
        active = null
        val request = attempt.request
        if (!request.cancelled && retryable && request.attempts < maxAttempts) {
            delayed.add(request)
            schedule(250L * request.attempts) {
                if (delayed.remove(request) && !request.cancelled) {
                    pending.addLast(request)
                    pump()
                }
            }
            pump()
        } else {
            val callback = request.onFailure.takeUnless { request.cancelled }
            request.detach()
            try { callback?.invoke(error) } finally { pump() }
        }
    }

    fun stopped(attempt: Attempt<T>) {
        if (active !== attempt) return
        active = null
        attempt.request.detach()
        pump()
    }

    fun expire(attempt: Attempt<T>) {
        if (active !== attempt || attempt.request.cancelled) return
        val request = attempt.request
        request.cancelled = true
        val callback = request.onFailure
        request.detach()
        try { callback?.invoke(ERROR_TIMEOUT) } finally { stop(attempt) }
    }

    private fun pump() {
        if (active != null || pending.isEmpty()) return
        val request = pending.removeFirst()
        request.attempts++
        // A new identity per retry prevents a late callback from completing the newer attempt.
        val attempt = Attempt(request)
        active = attempt
        start(attempt)
    }

    companion object {
        const val ERROR_QUEUE_FULL = -1
        const val ERROR_TIMEOUT = -2
    }
}
