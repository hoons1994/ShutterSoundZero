package io.github.hoons1994.shuttersoundzero.core.adb

/** Keeps late mDNS callbacks from an older pairing discovery out of the current session. */
internal class PairingDiscoveryPortState {
    private val monitor = Any()
    private var activeSession: Any? = null
    private val pairingServices = linkedMapOf<String, Int>()
    private val connectServices = linkedMapOf<String, Int>()

    @Volatile var pairingPort: Int? = null
        private set
    @Volatile var connectPort: Int? = null
        private set

    fun begin(): Any = synchronized(monitor) {
        val session = Any()
        activeSession = session
        pairingPort = null
        connectPort = null
        pairingServices.clear()
        connectServices.clear()
        session
    }

    fun stop() = synchronized(monitor) {
        activeSession = null
    }

    fun clearPorts() = synchronized(monitor) {
        pairingPort = null
        connectPort = null
        pairingServices.clear()
        connectServices.clear()
    }

    fun rememberConnectedPort(port: Int) = synchronized(monitor) {
        connectPort = port
    }

    fun onPairingPort(session: Any, port: Int, service: String = "", notify: (Int) -> Unit): Boolean = synchronized(monitor) {
        if (activeSession !== session) return@synchronized false
        pairingServices.remove(service)
        pairingServices[service] = port
        pairingPort = port
        notify(port)
        true
    }

    fun onConnectPort(session: Any, port: Int, service: String = "", notify: (Int) -> Unit): Boolean = synchronized(monitor) {
        if (activeSession !== session) return@synchronized false
        connectServices.remove(service)
        connectServices[service] = port
        connectPort = port
        notify(port)
        true
    }

    fun onPairingLost(session: Any, service: String, notify: () -> Unit): Boolean = synchronized(monitor) {
        if (activeSession !== session) return@synchronized false
        val wasSelected = pairingServices.keys.lastOrNull() == service
        pairingServices.remove(service)
        if (!wasSelected) return@synchronized false
        pairingPort = pairingServices.values.lastOrNull()
        if (pairingPort == null) notify()
        true
    }

    fun onConnectLost(session: Any, service: String, notify: () -> Unit): Boolean = synchronized(monitor) {
        if (activeSession !== session) return@synchronized false
        val wasSelected = connectServices.keys.lastOrNull() == service
        connectServices.remove(service)
        if (!wasSelected) return@synchronized false
        connectPort = connectServices.values.lastOrNull()
        if (connectPort == null) notify()
        true
    }
}
