package com.charmingcolor.shuttersoundzero.service

import java.util.concurrent.atomic.AtomicLong

/** 이전 비동기 작업의 늦은 완료가 새 작업 상태를 지우지 못하도록 세대 번호를 관리한다. */
internal class OperationGeneration {
    private val generation = AtomicLong(0)

    fun next(): Long = generation.incrementAndGet()

    fun invalidate() {
        generation.incrementAndGet()
    }

    fun isCurrent(token: Long): Boolean = generation.get() == token
}
