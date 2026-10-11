package io.github.hoons1994.shuttersoundzero.core.adb

/** Keeps late mDNS callbacks from an older pairing discovery out of the current session. */
internal class PairingDiscoveryPortState {
    private val monitor = Any()
    private var activeSession: Any? = null
    private var pairingService: String? = null
    private var connectService: String? = null

    @Volatile var pairingPort: Int? = null
        private set
    @Volatile var connectPort: Int? = null
        private set

    fun begin(): Any = synchronized(monitor) {
        val session = Any()
        activeSession = session
        pairingPort = null
        connectPort = null
        pairingService = null
        connectService = null
        session
    }

    fun stop() = synchronized(monitor) {
        activeSession = null
    }

    fun clearPorts() = synchronized(monitor) {
        pairingPort = null
        connectPort = null
        pairingService = null
        connectService = null
    }

    fun rememberConnectedPort(port: Int) = synchronized(monitor) {
        connectPort = port
    }

    fun onPairingPort(session: Any, port: Int, service: String = "", notify: (Int) -> Unit): Boolean = synchronized(monitor) {
        if (activeSession !== session) return@synchronized false
        pairingService = service
        pairingPort = port
        notify(port)
        true
    }

    fun onConnectPort(session: Any, port: Int, service: String = "", notify: (Int) -> Unit): Boolean = synchronized(monitor) {
        if (activeSession !== session) return@synchronized false
        connectService = service
        connectPort = port
        notify(port)
        true
    }

    fun onPairingLost(session: Any, service: String, notify: () -> Unit): Boolean = synchronized(monitor) {
        if (activeSession !== session || pairingService != service) return@synchronized false
        pairingPort = null
        pairingService = null
        notify()
        true
    }

    fun onConnectLost(session: Any, service: String, notify: () -> Unit): Boolean = synchronized(monitor) {
        if (activeSession !== session || connectService != service) return@synchronized false
        connectPort = null
        connectService = null
        notify()
        true
    }
}
