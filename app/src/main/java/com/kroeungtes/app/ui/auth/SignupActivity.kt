package com.kroeungtes.app.ui.auth

import android.animation.Animator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.kroeungtes.app.R
import com.kroeungtes.app.databinding.ActivitySignupBinding
import com.kroeungtes.app.security.AuthErrors
import com.kroeungtes.app.security.PasswordRules
import com.kroeungtes.app.ui.common.EdgeToEdge
import com.kroeungtes.app.ui.common.marginForSystemBars
import com.kroeungtes.app.ui.common.pressScale
import com.kroeungtes.app.ui.common.riseIn
import com.kroeungtes.app.ui.common.spinForever

/**
 * Create an account. Security: valid email, strong password (8+ characters with a letter and a number),
 * and a verification email — the account can only log in after the link in it is opened.
 */
class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var firebaseAuth: FirebaseAuth
    /** Slowly turning corner blobs, stopped in onDestroy. */
    private val spins = mutableListOf<Animator>()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.light(this)
        // Keep passwords out of screenshots and the Recent apps preview
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.authScroll.marginForSystemBars(ime = true)
        firebaseAuth = FirebaseAuth.getInstance()

        // Logo, title and button glide over from Log in; the other parts rise in
        binding.signupSubtitle.riseIn(delay = 150, distanceDp = 20f)
        listOf(
            binding.signupEmailBox, binding.signupPasswordBox, binding.signupConfirmBox,
            binding.signupOrRow, binding.signupSocialRow, binding.loginRedirectText
        ).forEachIndexed { i, view -> view.riseIn(delay = 220L + 60L * i, distanceDp = 28f, duration = 480) }
        binding.signupButton.pressScale()
        spins += binding.signupBlobTop.spinForever(40_000)
        spins += binding.signupBlobBottom.spinForever(32_000, clockwise = false)

        // Social sign-in is shown in the design but not connected yet
        val comingSoon = View.OnClickListener {
            Snackbar.make(binding.root, R.string.coming_soon, Snackbar.LENGTH_SHORT).show()
        }
        binding.signupGoogle.setOnClickListener(comingSoon)
        binding.signupFacebook.setOnClickListener(comingSoon)

        binding.signupPasswordBox.helperText = getString(R.string.pw_rule_hint)
        binding.signupEmail.doAfterTextChanged { binding.signupEmailBox.error = null }
        binding.signupPassword.doAfterTextChanged {
            binding.signupPasswordBox.error = null
            binding.signupPasswordBox.helperText = getString(R.string.pw_rule_hint)
        }
        binding.signupConfirm.doAfterTextChanged { binding.signupConfirmBox.error = null }
        binding.signupButton.setOnClickListener { signUp() }
        binding.loginRedirectText.setOnClickListener {
            // Back to Log in (it is already under this screen); the shared parts glide back
            supportFinishAfterTransition()
        }
    }

    private fun signUp() {
        if (busy) return
        val email = binding.signupEmail.text?.toString()?.trim().orEmpty()
        val password = binding.signupPassword.text?.toString().orEmpty()
        val confirm = binding.signupConfirm.text?.toString().orEmpty()

        var ok = true
        if (!PasswordRules.isEmail(email)) {
            binding.signupEmailBox.error = getString(R.string.err_bad_email)
            ok = false
        }
        PasswordRules.problem(password)?.let {
            binding.signupPasswordBox.error = getString(it)
            ok = false
        }
        if (confirm != password) {
            binding.signupConfirmBox.error = getString(R.string.err_passwords_differ)
            ok = false
        }
        if (!ok) return

        setBusy(true)
        // Plain listeners (not tied to this screen): the link is sent and the user signed out even if they leave
        firebaseAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            // task.result throws when the task failed, so only read it on success
            val user = if (task.isSuccessful) task.result?.user else null
            if (user == null) {
                if (isDestroyed) return@addOnCompleteListener
                setBusy(false)
                val error = task.exception
                val message = getString(AuthErrors.message(error, AuthErrors.Where.SIGNUP))
                if (error is FirebaseAuthWeakPasswordException) binding.signupPasswordBox.error = message
                else binding.signupEmailBox.error = message
                return@addOnCompleteListener
            }
            // Send the link, then stay logged out until the email is verified
            user.sendEmailVerification().addOnCompleteListener { sent ->
                firebaseAuth.signOut()
                if (isDestroyed) return@addOnCompleteListener
                setBusy(false)
                val message = if (sent.isSuccessful) {
                    getString(R.string.verify_after_signup, email)
                } else {
                    // Account exists but the link didn't go out: they can ask for a new one from Log in
                    getString(AuthErrors.message(sent.exception, AuthErrors.Where.ACCOUNT)) + "\n\n" +
                        getString(R.string.verify_send_failed)
                }
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.verify_title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        startActivity(Intent(this, LoginActivity::class.java)
                            .putExtra(LoginActivity.EXTRA_EMAIL, email)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                    .show()
            }
        }
    }

    private fun setBusy(on: Boolean) {
        busy = on
        binding.signupButton.isEnabled = !on
        binding.signupButton.setText(if (on) R.string.please_wait else R.string.sign_up)
    }

    override fun onDestroy() {
        spins.forEach { it.cancel() }
        spins.clear()
        super.onDestroy()
    }
}
