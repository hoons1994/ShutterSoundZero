package io.github.hoons1994.shuttersoundzero.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationGenerationTest {
    @Test
    fun newerGeneration_invalidatesOlderToken() {
        val generation = OperationGeneration()
        val first = generation.next()
        val second = generation.next()

        assertFalse(generation.isCurrent(first))
        assertTrue(generation.isCurrent(second))
    }

    @Test
    fun invalidate_rejectsPreviouslyCurrentToken() {
        val generation = OperationGeneration()
        val token = generation.next()

        assertTrue(generation.isCurrent(token))
        generation.invalidate()
        assertFalse(generation.isCurrent(token))
    }

    @Test
    fun runIfCurrent_executesOnlyForCurrentGeneration() {
        val generation = OperationGeneration()
        val stale = generation.next()
        val current = generation.next()
        var staleRan = false
        var currentRan = false

        val staleAccepted = generation.runIfCurrent(stale) {
            staleRan = true
        }
        val currentAccepted = generation.runIfCurrent(current) {
            currentRan = true
        }

        assertFalse(staleAccepted)
        assertFalse(staleRan)
        assertTrue(currentAccepted)
        assertTrue(currentRan)
    }
}
