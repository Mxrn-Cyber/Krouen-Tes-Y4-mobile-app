package com.kroeungtes.app.security

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.kroeungtes.app.databinding.ActivityLockBinding
import com.kroeungtes.app.ui.common.EdgeToEdge
import com.kroeungtes.app.ui.common.padForSystemBars
import com.kroeungtes.app.ui.common.pop
import com.kroeungtes.app.ui.common.riseIn

/**
 * Covers the app while it is locked. Opens the fingerprint / PIN prompt by itself;
 * the Unlock button asks again. Back sends the app to the background.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private lateinit var prompt: BiometricPrompt
    private var asking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.light(this)
        // Keep the app's content out of screenshots and the Recent apps preview
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForSystemBars()
        binding.lockLogo.riseIn(distanceDp = 20f)
        binding.lockTitle.riseIn(delay = 80, distanceDp = 20f)

        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                asking = false
                AppLock.unlocked = true
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                asking = false
                when (errorCode) {
                    // The phone no longer has a screen lock or sensor: turn the app lock off so nobody is locked out
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_NO_BIOMETRICS -> if (!AppLock.isAvailable(this@LockActivity)) {
                        AppLock.setEnabled(this@LockActivity, false)
                        finish()
                    }
                    else -> binding.lockButton.pop()
                }
            }
        })

        // The button always asks again, even if an earlier prompt never reported back
        binding.lockButton.setOnClickListener {
            asking = false
            ask()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (AppLock.unlocked) finish() else ask()
    }

    private fun ask() {
        if (asking) return
        asking = true
        prompt.authenticate(AppLock.promptInfo(this))
    }
}
