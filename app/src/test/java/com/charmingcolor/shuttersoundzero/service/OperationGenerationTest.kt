package com.charmingcolor.shuttersoundzero.service

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
}
