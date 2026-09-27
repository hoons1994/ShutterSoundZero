package io.github.hoons1994.shuttersoundzero.core.adb

/** Private Messenger protocol between the app and its legacy NSD worker process. */
internal object LegacyNsdProtocol {
    const val RESOLVE = 1
    const val SHUTDOWN = 2
    const val RESOLVED = 3
    const val FAILED = 4
}
