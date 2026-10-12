package io.github.hoons1994.shuttersoundzero.logging

/** In-memory endpoint comparison only; addresses and ports are never written to diagnostics. */
internal class DiscoveryLogState {
    private val sightings = mutableMapOf<DiagnosticLogger.Stage, Pair<Int, Int>>()

    @Synchronized fun found(stage: DiagnosticLogger.Stage, port: Int): Int {
        val previous = sightings[stage]
        val repeats = if (previous?.first == port) (previous.second + 1).coerceAtMost(Int.MAX_VALUE - 1) else 0
        sightings[stage] = port to repeats
        return repeats
    }

    @Synchronized fun lost(stage: DiagnosticLogger.Stage) { sightings.remove(stage) }
    @Synchronized fun reset() { sightings.clear() }
}
