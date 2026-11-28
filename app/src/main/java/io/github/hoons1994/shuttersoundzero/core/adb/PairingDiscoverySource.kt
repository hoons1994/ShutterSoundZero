package io.github.hoons1994.shuttersoundzero.core.adb

import kotlinx.coroutines.CancellationException

enum class PairingDiscoverySource { PAIRING, CONNECT }

/** Connect advertisements are optional: apply has its own fresh connection discovery. */
internal fun <T> startOptionalConnectDiscovery(start: () -> T, onFailure: () -> Unit): T? =
    try { start() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { onFailure(); null }
