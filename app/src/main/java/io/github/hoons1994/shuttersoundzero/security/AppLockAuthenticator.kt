package io.github.hoons1994.shuttersoundzero.security

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec

object AppLockAuthenticator {
    private const val KEY_ALIAS = "app_lock_authentication_v1"
    // Accessed only from the Activity lifecycle and biometric main executor.
    private val authenticationRequests = WeakHashMap<ComponentActivity, AuthenticationRequest>()
    private const val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean {
        return runCatching {
            val manager = context.getSystemService(BiometricManager::class.java)
            manager?.canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }

    fun isAuthenticating(activity: ComponentActivity): Boolean = authenticationRequests.containsKey(activity)

    class AuthenticationRequest internal constructor(private var cancelAction: (() -> Unit)?) {
        fun cancel() {
            val action = cancelAction
            cancelAction = null
            action?.invoke()
        }

        internal fun complete() {
            cancelAction = null
        }
    }

    fun authenticate(
        activity: ComponentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onCancelled: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): AuthenticationRequest {
        authenticationRequests[activity]?.cancel()
        val gate = AuthenticationCallbackGate()
        val cancellationSignal = CancellationSignal()
        val activityReference = WeakReference(activity)
        val lifecycle = activity.lifecycle
        var successCallback: (() -> Unit)? = onSuccess
        var cancelledCallback: (() -> Unit)? = onCancelled
        var errorCallback: ((String) -> Unit)? = onError
        lateinit var observer: DefaultLifecycleObserver
        lateinit var request: AuthenticationRequest

        fun isOwnerAlive(): Boolean = activityReference.get()?.let {
            !it.isFinishing && !it.isDestroyed
        } == true

        fun clearCallbacks() {
            successCallback = null
            cancelledCallback = null
            errorCallback = null
            lifecycle.removeObserver(observer)
            activityReference.get()?.let { owner ->
                if (authenticationRequests[owner] === request) authenticationRequests.remove(owner)
            }
            request.complete()
        }

        fun deliverSuccess() {
            val callback = successCallback
            clearCallbacks()
            callback?.invoke()
        }

        request = AuthenticationRequest {
            // Invalidate before cancelling: the platform may enqueue a terminal callback.
            gate.cancel()
            clearCallbacks()
            cancellationSignal.cancel()
        }
        observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (!isOwnerAlive()) request.cancel()
                else if (gate.resume()) deliverSuccess()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                request.cancel()
            }
        }
        if (!isOwnerAlive() || lifecycle.currentState == Lifecycle.State.DESTROYED) {
            request.cancel()
            return request
        }
        authenticationRequests[activity] = request
        lifecycle.addObserver(observer)

        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        var signingOperation: Signature? = null
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (!isOwnerAlive()) {
                    request.cancel()
                    return
                }
                // A callback alone is insufficient: require the per-use Keystore operation.
                try {
                    val signature = requireNotNull(result.cryptoObject?.signature)
                    check(signature === signingOperation)
                    signature.update(challenge)
                    val proof = signature.sign()
                    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    check(AuthenticationProof.verify(keyStore.getCertificate(KEY_ALIAS).publicKey, challenge, proof))
                    if (gate.succeed(lifecycle.currentState == Lifecycle.State.RESUMED)) deliverSuccess()
                } catch (error: Exception) {
                    if (!gate.fail()) return
                    val failureCallback = errorCallback
                    clearCallbacks()
                    failureCallback?.invoke(error.javaClass.simpleName)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!gate.fail()) return
                val cancelled = cancelledCallback
                val error = errorCallback
                clearCallbacks()
                if (isUserCancellation(errorCode)) cancelled?.invoke()
                else error?.invoke(errString.toString())
            }
        }
        try {
            signingOperation = createSigningOperation()
            BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build()
                .authenticate(
                    BiometricPrompt.CryptoObject(signingOperation),
                    cancellationSignal,
                    activity.mainExecutor,
                    callback
                )
        } catch (error: Exception) {
            if (gate.fail()) {
                val failureCallback = errorCallback
                clearCallbacks()
                cancellationSignal.cancel()
                failureCallback?.invoke(error.javaClass.simpleName)
            }
        }
        return request
    }

    private fun createSigningOperation(recreateInvalidatedKey: Boolean = true): Signature {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationParameters(
                            0,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                        )
                        .setInvalidatedByBiometricEnrollment(false)
                        .build()
                )
                generateKeyPair()
            }
        }
        try {
            return Signature.getInstance("SHA256withECDSA").apply {
                initSign(keyStore.getKey(KEY_ALIAS, null) as PrivateKey)
            }
        } catch (error: KeyPermanentlyInvalidatedException) {
            if (!recreateInvalidatedKey) throw error
            // This key protects no stored data. Screen-lock changes may invalidate it;
            // replace it and still require a fresh system authentication before signing.
            keyStore.deleteEntry(KEY_ALIAS)
            return createSigningOperation(recreateInvalidatedKey = false)
        }
    }

    private fun isUserCancellation(errorCode: Int): Boolean {
        return errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
    }
}
