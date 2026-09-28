package io.github.hoons1994.shuttersoundzero.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemVolumeVerifierTest {
    @Test
    fun delayedAudioServiceUpdateDoesNotImmediatelyRestoreOldVolume() = runTest {
        val started = testScheduler.currentTime
        val actual = SystemVolumeVerifier.readAfterChange(5) {
            if (testScheduler.currentTime - started >= 300) 5 else 2
        }

        assertEquals(5, actual)
        assertEquals(300L, testScheduler.currentTime - started)
    }

    @Test
    fun ignoredChangeReturnsActualValueInsteadOfRequestedValue() = runTest {
        val actual = SystemVolumeVerifier.readAfterChange(5) { 2 }

        assertEquals(2, actual)
        assertEquals(500L, testScheduler.currentTime)
    }

    @Test
    fun mutedStreamDoesNotLookLikeSuccessfullyRaisedVolume() = runTest {
        assertEquals(0, SystemVolumeVerifier.readAfterChange(5) { 0 })
    }

    @Test
    fun zeroVolumeIsAValidConfirmedValue() = runTest {
        assertEquals(0, SystemVolumeVerifier.readAfterChange(0) { 0 })
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun unavailableReadDoesNotReportSuccess() = runTest {
        assertNull(SystemVolumeVerifier.readAfterChange(5) { null })
    }

    @Test(expected = CancellationException::class)
    fun cancellationPropagatesInsteadOfReportingAnAudioFailure() = runTest {
        SystemVolumeVerifier.readAfterChange(5) { throw CancellationException() }
    }
}
