package io.github.hoons1994.shuttersoundzero.core.adb

/** Owned by the ADB runner; callers must not retain this session beyond its block. */
internal interface AdbCommandSession {
    suspend fun connect(port: Int?, timeoutMs: Long)
    suspend fun execute(command: String): String
}
