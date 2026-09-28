package io.github.hoons1994.shuttersoundzero.core

import kotlinx.coroutines.delay

/** Waits briefly for the audio service to report the requested volume. */
internal object SystemVolumeVerifier {
    suspend fun readAfterChange(
        requested: Int,
        readVolume: () -> Int?
    ): Int? {
        var actual: Int? = null
        repeat(6) { attempt ->
            if (attempt > 0) delay(100)
            actual = readVolume()
            if (actual == requested) return actual
        }
        return actual
    }
}
