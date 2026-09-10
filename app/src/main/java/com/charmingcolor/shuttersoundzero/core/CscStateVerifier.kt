package com.charmingcolor.shuttersoundzero.core

import kotlinx.coroutines.delay

/**
 * ADB 명령이 성공으로 끝난 뒤 실제 CSC 설정값이 요청한 상태로 반영됐는지 확인한다.
 *
 * 일부 펌웨어에서는 `settings put` 종료 직후 값 반영이 약간 늦을 수 있으므로 짧게 재시도한다.
 * 호출자는 명령 종료 코드와 이 검증 결과가 모두 성공일 때만 영구 상태를 갱신해야 한다.
 */
internal object CscStateVerifier {
    const val DEFAULT_ATTEMPTS = 20
    const val DEFAULT_INTERVAL_MS = 100L

    suspend fun waitFor(
        expectedMuted: Boolean,
        attempts: Int = DEFAULT_ATTEMPTS,
        intervalMillis: Long = DEFAULT_INTERVAL_MS,
        readMutedState: () -> Boolean
    ): Boolean {
        require(attempts > 0) { "attempts must be positive" }
        require(intervalMillis >= 0L) { "intervalMillis must not be negative" }

        repeat(attempts) { attempt ->
            if (readMutedState() == expectedMuted) return true
            if (attempt < attempts - 1 && intervalMillis > 0L) delay(intervalMillis)
        }
        return false
    }
}
