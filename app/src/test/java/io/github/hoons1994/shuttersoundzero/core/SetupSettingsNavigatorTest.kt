package io.github.hoons1994.shuttersoundzero.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupSettingsNavigatorTest {
    @Test fun primarySuccessDoesNotLaunchFallback() {
        val calls = mutableListOf<String>()
        val result = SetupSettingsNavigator.launchSettings(
            primary = { calls.add("primary") },
            fallback = { calls.add("fallback") }
        )
        assertTrue(result.isSuccess)
        assertEquals(listOf("primary"), calls)
    }

    @Test fun failedPrimaryCanBeRecoveredByFallback() {
        val calls = mutableListOf<String>()
        val result = SetupSettingsNavigator.launchSettings(
            primary = { calls.add("primary"); throw IllegalStateException("unavailable") },
            fallback = { calls.add("fallback") }
        )
        assertTrue(result.isSuccess)
        assertEquals(listOf("primary", "fallback"), calls)
    }

    @Test fun bothFailuresAreReturnedWithTheirCauses() {
        val primaryFailure = IllegalStateException("unavailable")
        val fallbackFailure = SecurityException("blocked")
        val result = SetupSettingsNavigator.launchSettings(
            primary = { throw primaryFailure },
            fallback = { throw fallbackFailure }
        )
        assertSame(fallbackFailure, result.exceptionOrNull())
        assertEquals(listOf(primaryFailure), fallbackFailure.suppressed.toList())
    }

    @Test fun failureWithoutFallbackIsNotReportedAsOpened() {
        val failure = SecurityException("blocked")
        val result = SetupSettingsNavigator.launchSettings(primary = { throw failure })
        assertSame(failure, result.exceptionOrNull())
    }

    @Test fun sameExceptionFromBothAttemptsRemainsAFailure() {
        val failure = SecurityException("blocked")
        val result = SetupSettingsNavigator.launchSettings(
            primary = { throw failure },
            fallback = { throw failure }
        )
        assertSame(failure, result.exceptionOrNull())
        assertTrue(failure.suppressed.isEmpty())
    }
}
