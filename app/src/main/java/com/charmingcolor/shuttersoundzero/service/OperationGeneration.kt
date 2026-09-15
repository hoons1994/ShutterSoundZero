package com.charmingcolor.shuttersoundzero.service

/** 이전 비동기 작업의 늦은 완료가 새 작업 상태를 덮지 못하도록 세대 번호를 관리한다. */
internal class OperationGeneration {
    private val monitor = Any()
    private var generation = 0L

    fun next(): Long = synchronized(monitor) {
        generation += 1
        generation
    }

    fun invalidate() {
        synchronized(monitor) {
            generation += 1
        }
    }

    fun isCurrent(token: Long): Boolean = synchronized(monitor) {
        generation == token
    }

    /**
     * 현재 세대인 경우에만 부작용을 실행한다. 세대 확인과 블록 실행을 같은 monitor에서
     * 수행해 stop/invalidate가 확인 직후 끼어드는 stale completion 경쟁을 막는다.
     */
    fun runIfCurrent(token: Long, block: () -> Unit): Boolean = synchronized(monitor) {
        if (generation != token) {
            false
        } else {
            block()
            true
        }
    }
}
