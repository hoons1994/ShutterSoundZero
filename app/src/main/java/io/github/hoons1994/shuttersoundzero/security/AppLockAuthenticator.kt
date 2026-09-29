package io.github.hoons1994.shuttersoundzero.security

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference
import java.util.WeakHashMap

object AppLockAuthenticator {
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

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (!isOwnerAlive()) request.cancel()
                else if (gate.succeed(lifecycle.currentState == Lifecycle.State.RESUMED)) deliverSuccess()
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
            BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build()
                .authenticate(
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

    private fun isUserCancellation(errorCode: Int): Boolean {
        return errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
    }
}
