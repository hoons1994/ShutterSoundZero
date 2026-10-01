package io.github.hoons1994.shuttersoundzero.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hoons1994.shuttersoundzero.MainActivity
import io.github.hoons1994.shuttersoundzero.data.PreferencesRepository
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Changes screen-lock settings only on a fresh emulator, never on a user's phone. */
@RunWith(AndroidJUnit4::class)
class AppLockAuthenticationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private var configuredCredential = false
    private var originalAccessibilityFlags: Int? = null

    @Before
    fun setUp() {
        assumeTrue("Credential tests require an emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        assumeFalse("Do not replace an existing screen-lock credential", keyguard.isDeviceSecure)
        shell("locksettings set-pin $TEST_PIN")
        configuredCredential = true
        awaitCondition("Emulator PIN was not configured") { keyguard.isDeviceSecure }
        deleteAuthenticationKey()
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        originalAccessibilityFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        PreferencesRepository.getInstance(context).apply {
            hasSeenInitialNotice = false
            isAppLockEnabled = false
        }
    }

    @After
    fun tearDown() {
        // @After also runs for skipped @Before: leave non-emulators and existing credentials untouched.
        if (!configuredCredential) return
        try {
            deleteAuthenticationKey()
        } finally {
            shell("locksettings clear --old $TEST_PIN")
            awaitCondition("Emulator PIN cleanup failed") { !keyguard.isDeviceSecure }
            originalAccessibilityFlags?.let { flags ->
                val info = instrumentation.uiAutomation.serviceInfo
                info.flags = flags
                instrumentation.uiAutomation.serviceInfo = info
            }
            context.getSharedPreferences("galaxy_camera_mute_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun keystoreRequiresPerUseStrongBiometricOrDeviceCredential() {
        AppLockAuthenticator.createSigningOperation()
        val privateKey = keyStore().getKey(AppLockAuthenticator.KEY_ALIAS, null) as PrivateKey
        val info = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            .getKeySpec(privateKey, KeyInfo::class.java)
        assertTrue(info.isUserAuthenticationRequired)
        assertEquals(0, info.userAuthenticationValidityDurationSeconds)
        assertEquals(KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL, info.userAuthenticationType)
    }

    @Test
    fun signingWithoutSystemAuthenticationIsRejected() {
        assertUnauthenticatedSigningRejected(AppLockAuthenticator.createSigningOperation())
    }

    @Test
    fun removingAndRestoringScreenLockRecreatesInvalidatedKey() {
        AppLockAuthenticator.createSigningOperation()
        val previous = keyStore().getCertificate(AppLockAuthenticator.KEY_ALIAS).publicKey.encoded
        shell("locksettings clear --old $TEST_PIN")
        shell("locksettings set-pin $TEST_PIN")
        val operation = AppLockAuthenticator.createSigningOperation()
        val replacement = keyStore().getCertificate(AppLockAuthenticator.KEY_ALIAS).publicKey.encoded
        assertFalse("Invalidated authentication key must be replaced", previous.contentEquals(replacement))
        assertUnauthenticatedSigningRejected(operation)
        completeActualPinAuthentication()
    }

    @Test
    fun devicePinCompletesActualCryptoAuthenticationOnce() {
        completeActualPinAuthentication()
    }

    private fun completeActualPinAuthentication() {
        val completed = CountDownLatch(1)
        val successes = AtomicInteger()
        val error = AtomicReference<String?>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                AppLockAuthenticator.authenticate(activity, "Authentication integration test", "Enter the emulator PIN",
                    onSuccess = { successes.incrementAndGet(); completed.countDown() },
                    onError = { error.set(it); completed.countDown() },
                    onCancelled = { error.set("cancelled"); completed.countDown() })
            }
            val pinField = awaitCredentialPrompt()
            assertEquals(0, successes.get())
            val input = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, TEST_PIN)
            }
            assertTrue("System PIN field did not accept input",
                pinField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, input))
            shell("input keyevent KEYCODE_ENTER")
            assertTrue("PIN authentication did not finish: ${error.get()}", completed.await(20, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals("PIN must unlock only after Keystore signing succeeds: ${error.get()}", 1, successes.get())
        }
    }

    private fun assertUnauthenticatedSigningRejected(operation: Signature) {
        try {
            operation.update(ByteArray(32) { it.toByte() })
            operation.sign()
            fail("Unauthenticated Keystore signing must not be possible")
        } catch (_: GeneralSecurityException) {
            // The actual Android Keystore must enforce authentication, not only an app callback.
        }
    }

    @Test
    fun cancellingActualCredentialPromptDoesNotUnlock() {
        val completed = CountDownLatch(1)
        val successes = AtomicInteger()
        val terminalResult = AtomicReference<String?>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                AppLockAuthenticator.authenticate(activity, "Authentication cancellation test", "Cancel the emulator PIN",
                    onSuccess = { successes.incrementAndGet(); completed.countDown() },
                    onCancelled = { terminalResult.set("cancelled"); completed.countDown() },
                    onError = { terminalResult.set(it); completed.countDown() })
            }
            awaitCredentialPrompt()
            shell("input keyevent KEYCODE_BACK")
            // Older Android versions consume the first Back to dismiss the PIN keyboard.
            // Send a second Back only while the credential prompt is still present.
            if (!completed.await(3, TimeUnit.SECONDS) &&
                credentialField() != null
            ) shell("input keyevent KEYCODE_BACK")
            assertTrue("Cancellation was not delivered", completed.await(20, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals(0, successes.get())
            assertEquals("cancelled", terminalResult.get())
        }
    }

    private fun awaitCredentialPrompt(): AccessibilityNodeInfo {
        var field: AccessibilityNodeInfo? = null
        awaitCondition("System credential prompt did not appear") {
            field = credentialField()
            field?.isFocused == true
        }
        return requireNotNull(field)
    }

    private fun credentialField(): AccessibilityNodeInfo? {
        val automation = instrumentation.uiAutomation
        // The IME can be the active window, so inspect the other interactive windows too.
        val roots = listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root }
        return roots.firstNotNullOfOrNull { findCredentialField(it) }
    }

    private fun findCredentialField(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val isSystemCredential = node.packageName?.toString() in setOf("com.android.systemui", "com.android.settings")
        if (isSystemCredential && node.isEnabled && node.isVisibleToUser &&
            (node.isPassword || node.viewIdResourceName?.endsWith("/lockPassword") == true)
        ) return node
        for (index in 0 until node.childCount) {
            findCredentialField(node.getChild(index))?.let { return it }
        }
        return null
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue(message, condition())
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun deleteAuthenticationKey() = keyStore().deleteEntry(AppLockAuthenticator.KEY_ALIAS)

    private companion object {
        const val TEST_PIN = "246810"
    }
}
