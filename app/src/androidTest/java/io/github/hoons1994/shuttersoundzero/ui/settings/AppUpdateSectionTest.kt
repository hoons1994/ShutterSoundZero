package io.github.hoons1994.shuttersoundzero.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.hoons1994.shuttersoundzero.R
import io.github.hoons1994.shuttersoundzero.update.AppVersion
import io.github.hoons1994.shuttersoundzero.update.LatestRelease
import io.github.hoons1994.shuttersoundzero.update.RELEASES_PAGE
import io.github.hoons1994.shuttersoundzero.update.UpdateCheckFailure
import io.github.hoons1994.shuttersoundzero.update.UpdateCheckResult
import io.github.hoons1994.shuttersoundzero.update.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppUpdateSectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun availableReleaseDisplaysVersionAndOpensThatRelease() {
        val release = LatestRelease("v1.5.14", AppVersion(1, 5, 14))
        var opened: String? = null
        show(AppUpdateUiState(result = UpdateCheckResult(UpdateStatus.AVAILABLE, release)),
            onOpen = { opened = it })
        compose.onNodeWithText(text(R.string.app_update_available, release.tag)).assertExists()
        compose.onNodeWithText(text(R.string.app_update_view_release)).performClick()
        assertEquals(release.pageUrl, opened)
    }

    @Test fun checkingPreventsDuplicateTap() {
        show(AppUpdateUiState(isChecking = true))
        // Status and button share the same wording; select the clickable button semantics.
        compose.onNode(androidx.compose.ui.test.hasText(text(R.string.app_update_checking)) and
            androidx.compose.ui.test.hasClickAction()).assertIsNotEnabled()
    }

    @Test fun failedLookupKeepsManualReleaseLinkAndRetryAvailable() {
        var checks = 0
        var opened: String? = null
        show(AppUpdateUiState(failure = UpdateCheckFailure.NETWORK),
            onCheck = { checks++ }, onOpen = { opened = it })
        compose.onNodeWithText(text(R.string.app_update_network_failed)).assertExists()
        compose.onNodeWithText(text(R.string.app_update_check)).performClick()
        compose.onNodeWithText(text(R.string.app_update_open_releases)).performClick()
        assertEquals(1, checks)
        assertEquals(RELEASES_PAGE, opened)
    }

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private fun show(state: AppUpdateUiState, onCheck: () -> Unit = {}, onOpen: (String) -> Unit = {}) {
        compose.setContent {
            MaterialTheme { AppUpdateContent("1.5.13-debug", state, onCheck, onOpen) }
        }
    }
}
