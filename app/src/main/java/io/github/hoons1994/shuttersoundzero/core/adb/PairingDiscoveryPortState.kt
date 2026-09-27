package io.github.hoons1994.shuttersoundzero.core.adb

/** Keeps late mDNS callbacks from an older pairing discovery out of the current session. */
internal class PairingDiscoveryPortState {
    private val monitor = Any()
    private var activeSession: Any? = null

    @Volatile var pairingPort: Int? = null
        private set
    @Volatile var connectPort: Int? = null
        private set

    fun begin(): Any = synchronized(monitor) {
        val session = Any()
        activeSession = session
        pairingPort = null
        connectPort = null
        session
    }

    fun stop() = synchronized(monitor) {
        activeSession = null
    }

    fun clearPorts() = synchronized(monitor) {
        pairingPort = null
        connectPort = null
    }

    fun rememberConnectedPort(port: Int) = synchronized(monitor) {
        connectPort = port
    }

    fun onPairingPort(session: Any, port: Int, notify: (Int) -> Unit): Boolean = synchronized(monitor) {
        if (activeSession !== session) return@synchronized false
        pairingPort = port
        notify(port)
        true
    }

    fun onConnectPort(session: Any, port: Int, notify: (Int) -> Unit): Boolean = synchronized(monitor) {
        if (activeSession !== session) return@synchronized false
        connectPort = port
        notify(port)
        true
    }
}
