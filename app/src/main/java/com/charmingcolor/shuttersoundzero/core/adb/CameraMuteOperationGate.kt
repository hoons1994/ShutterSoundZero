package com.charmingcolor.shuttersoundzero.core.adb

import java.util.concurrent.atomic.AtomicInteger

/**
 * 카메라 CSC 변경 작업이 실행 중이거나 ADB mutex를 기다리는 동안 후처리 작업이
 * 무선 디버깅을 먼저 꺼서 다음 변경을 중단시키지 않도록 앱 전체 작업 수를 추적한다.
 */
internal object CameraMuteOperationGate {
    private val pendingOperations = AtomicInteger(0)

    fun enter() {
        pendingOperations.incrementAndGet()
    }

    fun exit() {
        val remaining = pendingOperations.decrementAndGet()
        check(remaining >= 0) { "Camera mute operation gate underflow" }
    }

    fun hasPendingOperations(): Boolean = pendingOperations.get() > 0

    internal fun resetForTest() {
        pendingOperations.set(0)
    }
}
