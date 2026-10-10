package io.github.hoons1994.shuttersoundzero.update

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AppUpdateRepositoryTest {
    private fun release(tag: String) = LatestRelease(tag, AppVersion.parse(tag)!!)

    @Test fun versionsCompareNumericallyAcrossAllComponents() {
        for ((older, newer) in listOf("1.5.9" to "1.5.10", "1.9.99" to "1.10.0", "1.99.99" to "2.0.0")) {
            assertTrue(AppVersion.parse(older)!! < AppVersion.parse(newer)!!)
        }
        assertEquals(AppVersion.parse("1.5.13"), AppVersion.parse("v1.5.13"))
    }

    @Test fun malformedOrOverflowingVersionsAreNotTreatedAsLatest() {
        for (value in listOf("", "1.5", "01.5.13", "1.5.13-beta", "v1.5.13-debug", "2147483648.0.0", "1.5.13/../../evil")) {
            assertNull(value, AppVersion.parse(value))
        }
    }

    @Test fun debugSuffixIsAcceptedOnlyForInstalledVersion() {
        assertEquals(AppVersion(1, 5, 13), AppVersion.parse("1.5.13-debug", allowDebugSuffix = true))
        assertNull(AppVersion.parse("1.5.13-debug"))
    }

    @Test fun newerReleaseIsAvailableAndUsesItsSpecificGitHubPage() = runTest {
        val result = AppUpdateRepository { release("v1.5.14") }.check("1.5.13-debug")
        assertEquals(UpdateStatus.AVAILABLE, result.status)
        assertEquals("https://github.com/hoons1994/ShutterSoundZero/releases/tag/v1.5.14", result.release.pageUrl)
    }

    @Test fun equalReleaseIsUpToDate() = runTest {
        assertEquals(UpdateStatus.UP_TO_DATE, AppUpdateRepository { release("v1.5.13") }.check("1.5.13-debug").status)
    }

    @Test fun olderReleaseDoesNotSuggestDowngradingNewerBuild() = runTest {
        assertEquals(UpdateStatus.INSTALLED_NEWER, AppUpdateRepository { release("v1.5.12") }.check("1.5.13").status)
    }

    @Test fun unknownInstalledVersionFailsBeforeNetworkRequest() = runTest {
        try {
            AppUpdateRepository { error("must not fetch") }.check("-")
            fail("expected failure")
        } catch (error: UpdateCheckException) {
            assertEquals(UpdateCheckFailure.INSTALLED_VERSION, error.reason)
        }
    }

    @Test fun fetchFailureIsNotConvertedToUpToDate() = runTest {
        val failure = UpdateCheckException(UpdateCheckFailure.NETWORK, IOException())
        try {
            AppUpdateRepository { throw failure }.check("1.5.13")
            fail("expected failure")
        } catch (actual: UpdateCheckException) {
            assertSame(failure, actual)
        }
    }

    @Test fun fetchCancellationIsPropagated() = runTest {
        val cancellation = CancellationException("cancelled")
        try {
            AppUpdateRepository { throw cancellation }.check("1.5.13")
            fail("expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }
}
