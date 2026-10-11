package io.github.hoons1994.shuttersoundzero.core.adb

/** A lost service cannot be resurrected by a queued resolution callback. */
internal class MdnsServicePresence {
    private val present = mutableMapOf<String, Any>()

    @Synchronized fun found(key: String): Any = present.getOrPut(key) { Any() }
    @Synchronized fun lost(key: String, notify: () -> Unit = {}) {
        present.remove(key)
        notify()
    }
    @Synchronized fun clear() { present.clear() }

    @Synchronized fun deliverIfCurrent(key: String, token: Any, deliver: () -> Unit): Boolean {
        if (present[key] !== token) return false
        deliver()
        return true
    }
}
