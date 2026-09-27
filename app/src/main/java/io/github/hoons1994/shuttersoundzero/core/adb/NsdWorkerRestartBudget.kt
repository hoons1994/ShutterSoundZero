package io.github.hoons1994.shuttersoundzero.core.adb

import java.util.ArrayDeque

/** Prevents a broken NSD stack from causing an unbounded worker-process restart loop. */
internal class NsdWorkerRestartBudget(
    private val maxRestarts: Int = 5,
    private val windowMs: Long = 60_000L,
    private val cooldownMs: Long = 30_000L
) {
    private val recent = ArrayDeque<Long>()
    private var blockedUntilMs = 0L

    fun canStart(nowMs: Long): Boolean = nowMs >= blockedUntilMs

    fun recordRestart(nowMs: Long) {
        while (recent.isNotEmpty() && nowMs - recent.first >= windowMs) recent.removeFirst()
        recent.addLast(nowMs)
        if (recent.size >= maxRestarts) {
            recent.clear()
            blockedUntilMs = nowMs + cooldownMs
        }
    }

    fun recovered() {
        recent.clear()
        blockedUntilMs = 0L
    }
}
