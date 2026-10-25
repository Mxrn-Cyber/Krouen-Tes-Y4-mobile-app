package com.kroeungtes.app.security

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import com.google.firebase.auth.FirebaseAuth
import com.kroeungtes.app.R

/**
 * Optional app lock (switch in Profile → Account & security).
 * When it is on, the app asks for fingerprint, face or the phone's PIN/pattern/password
 * every time it starts and when you come back after more than 30 seconds away.
 */
object AppLock {
    private const val PREFS = "krouen_app_lock"
    private const val KEY_ON = "enabled"
    private const val AWAY_LIMIT_MS = 30_000L

    /** Fingerprint/face, or the phone's own screen lock. */
    const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** False when the process starts, so a cold start always asks. */
    @Volatile var unlocked = false
    private var leftAt = 0L

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
        // Turning it on happens right after a successful check, so we're unlocked now
        unlocked = true
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        unlocked = false
    }

    /** True when the phone has a fingerprint/face or a screen lock we can use. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** The app went to the background. */
    fun onAppHidden() {
        leftAt = SystemClock.elapsedRealtime()
    }

    /** The app came back: lock again if it was away too long. */
    fun onAppShown() {
        if (leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > AWAY_LIMIT_MS) unlocked = false
        leftAt = 0L
    }

    /**
     * With the lock on, the Recent apps preview must not show the app's content.
     * Android 13+ can hide just the preview; older versions use FLAG_SECURE (which also blocks screenshots).
     */
    fun applyPrivacy(activity: Activity) {
        val on = isEnabled(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!on)
        } else if (on) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun needsUnlock(context: Context): Boolean =
        !unlocked && isEnabled(context) && FirebaseAuth.getInstance().currentUser != null

    fun promptInfo(context: Context, subtitle: Int = R.string.app_lock_prompt_sub): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.app_lock_prompt_title))
            .setSubtitle(context.getString(subtitle))
            // With DEVICE_CREDENTIAL allowed there is no "Cancel" button text to set
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
}
