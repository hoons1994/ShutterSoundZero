package io.github.hoons1994.shuttersoundzero.core.adb

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 카메라 CSC 변경 작업과 무선 디버깅 정리의 경계를 앱 전체에서 조율한다.
 *
 * 실행 중이거나 ADB mutex를 기다리는 작업은 [enter]/[exit]으로 추적한다. 정리는
 * [runCleanupIfIdle]을 통해서만 시작하며, idle 확인과 실제 정리 작업을 같은 락 안에서
 * 수행해 확인 직후 새 작업이 진입하는 TOCTOU 경쟁을 막는다.
 */
internal object CameraMuteOperationGate {
    private val coordinationLock = ReentrantLock(true)
    private var pendingOperations = 0

    fun enter() {
        coordinationLock.withLock {
            pendingOperations += 1
        }
    }

    fun exit() {
        coordinationLock.withLock {
            check(pendingOperations > 0) { "Camera mute operation gate underflow" }
            pendingOperations -= 1
        }
    }

    fun hasPendingOperations(): Boolean = coordinationLock.withLock {
        pendingOperations > 0
    }

    /**
     * 현재 대기·실행 중인 ADB 작업이 없을 때만 정리를 실행한다.
     *
     * 정리 블록이 끝날 때까지 새 [enter]가 같은 락에서 대기하므로, 정리 여부 확인과
     * 무선 디버깅 종료 사이에 새 작업이 끼어드는 경쟁이 발생하지 않는다.
     */
    fun runCleanupIfIdle(block: () -> Unit): Boolean = coordinationLock.withLock {
        if (pendingOperations > 0) {
            false
        } else {
            block()
            true
        }
    }

    internal fun resetForTest() {
        coordinationLock.withLock {
            pendingOperations = 0
        }
    }
}
