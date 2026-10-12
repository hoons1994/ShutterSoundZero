package io.github.hoons1994.shuttersoundzero.core

/** Preserve read failures so callers cannot confuse an unavailable setting with OFF. */
internal fun readWirelessDebuggingState(readSetting: () -> Int): Boolean? =
    try {
        readSetting() != 0
    } catch (_: Exception) {
        null
    }
