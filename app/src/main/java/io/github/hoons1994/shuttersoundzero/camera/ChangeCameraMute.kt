package io.github.hoons1994.shuttersoundzero.camera

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class CameraMuteChange(val wirelessCleanup: Result<Unit>)

/** Shared by home, settings and the quick-settings tile. UI wording stays at the caller. */
internal class ChangeCameraMute(
    private val apply: suspend (Boolean) -> Result<Unit>,
    private val verify: suspend (Boolean) -> Boolean,
    private val disableWirelessDebugging: () -> Result<Unit>
) {
    suspend operator fun invoke(muted: Boolean): Result<CameraMuteChange> {
        val applied = apply(muted)
        currentCoroutineContext().ensureActive()
        applied.exceptionOrNull()?.let { return Result.failure(it) }
        if (!verify(muted)) {
            return Result.failure(IOException("카메라 설정 적용 상태를 확인할 수 없습니다."))
        }
        currentCoroutineContext().ensureActive()
        // Cleanup failure must not turn a successfully applied camera setting into failure.
        return Result.success(CameraMuteChange(disableWirelessDebugging()))
    }
}
