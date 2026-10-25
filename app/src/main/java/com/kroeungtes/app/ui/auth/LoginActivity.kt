package com.kroeungtes.app.ui.auth

import android.animation.Animator
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.kroeungtes.app.R
import com.kroeungtes.app.databinding.ActivityLoginBinding
import com.kroeungtes.app.databinding.DialogEmailFieldBinding
import com.kroeungtes.app.security.AppLock
import com.kroeungtes.app.security.AuthErrors
import com.kroeungtes.app.security.LocalData
import com.kroeungtes.app.security.LoginThrottle
import com.kroeungtes.app.security.PasswordRules
import com.kroeungtes.app.ui.activities.MainActivity
import com.kroeungtes.app.ui.common.EdgeToEdge
import com.kroeungtes.app.ui.common.marginForSystemBars
import com.kroeungtes.app.ui.common.pressScale
import com.kroeungtes.app.ui.common.riseIn
import com.kroeungtes.app.ui.common.spinForever
import kotlinx.coroutines.launch
import androidx.core.util.Pair as UtilPair

/**
 * Log in with email + password.
 * Security: checks the fields first, shows friendly errors (never says whether an email exists),
 * makes you wait after 5 wrong passwords, and only lets verified emails into the app.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth
    /** Slowly turning corner blobs, stopped in onDestroy. */
    private val spins = mutableListOf<Animator>()
    private var countdown: CountDownTimer? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.light(this)
        // Keep passwords out of screenshots and the Recent apps preview
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.authScroll.marginForSystemBars(ime = true)
        firebaseAuth = FirebaseAuth.getInstance()

        // Coming back from Sign up with a new account: fill in the email
        intent.getStringExtra(EXTRA_EMAIL)?.let { binding.loginEmail.setText(it) }
        // e.g. "Your account was deleted"
        intent.getIntExtra(EXTRA_MESSAGE, 0).takeIf { it != 0 }?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
        }

        // The wok arrives from the splash screen; the rest rises in one after another
        binding.loginTitle.riseIn(delay = 120, distanceDp = 20f)
        binding.loginSubtitle.riseIn(delay = 200, distanceDp = 20f)
        listOf(
            binding.loginEmailBox, binding.loginPasswordBox, binding.forgotPassword, binding.loginButton,
            binding.loginOrRow, binding.loginSocialRow, binding.signupRedirectText
        ).forEachIndexed { i, view -> view.riseIn(delay = 280L + 60L * i, distanceDp = 28f, duration = 480) }
        binding.loginButton.pressScale()
        spins += binding.loginBlobTop.spinForever(40_000)
        spins += binding.loginBlobBottom.spinForever(32_000, clockwise = false)

        // Social sign-in is shown in the design but not connected yet
        val comingSoon = View.OnClickListener {
            Snackbar.make(binding.root, R.string.coming_soon, Snackbar.LENGTH_SHORT).show()
        }
        binding.loginGoogle.setOnClickListener(comingSoon)
        binding.loginFacebook.setOnClickListener(comingSoon)

        binding.loginEmail.doAfterTextChanged { binding.loginEmailBox.error = null }
        binding.loginPassword.doAfterTextChanged { binding.loginPasswordBox.error = null }
        binding.loginButton.setOnClickListener { logIn() }
        binding.forgotPassword.setOnClickListener { askResetEmail() }
        binding.signupRedirectText.setOnClickListener {
            // Logo, title and green button glide over to the Sign-up screen
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this,
                UtilPair<View, String>(binding.loginLogo, binding.loginLogo.transitionName),
                UtilPair<View, String>(binding.loginTitle, binding.loginTitle.transitionName),
                UtilPair<View, String>(binding.loginButton, binding.loginButton.transitionName)
            )
            startActivity(Intent(this, SignupActivity::class.java), options.toBundle())
        }
    }

    override fun onResume() {
        super.onResume()
        showWaitIfLocked()
    }

    private fun logIn() {
        if (busy || LoginThrottle.waitLeftMs(this) > 0) return
        val email = binding.loginEmail.text?.toString()?.trim().orEmpty()
        val password = binding.loginPassword.text?.toString().orEmpty()

        var ok = true
        if (!PasswordRules.isEmail(email)) {
            binding.loginEmailBox.error = getString(R.string.err_bad_email)
            ok = false
        }
        if (password.isEmpty()) {
            binding.loginPasswordBox.error = getString(R.string.err_enter_password)
            ok = false
        }
        if (!ok) return

        setBusy(true)
        // Plain listener (not tied to this screen) so the result still arrives if the user leaves the app meanwhile
        firebaseAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (isDestroyed) return@addOnCompleteListener
            setBusy(false)
            // task.result throws when the task failed, so only read it on success
            val user = if (task.isSuccessful) task.result?.user else null
            if (user != null) {
                LoginThrottle.recordSuccess(this)
                if (user.isEmailVerified) openHome(user) else askToVerify(user)
            } else {
                val error = task.exception
                if (AuthErrors.isWrongLogin(error)) LoginThrottle.recordFailure(this)
                binding.loginPasswordBox.error = getString(AuthErrors.message(error, AuthErrors.Where.LOGIN))
                showWaitIfLocked()
            }
        }
    }

    private fun openHome(user: FirebaseUser) {
        setBusy(true)
        lifecycleScope.launch {
            // Another account used this phone before? Remove its data first
            LocalData.claimFor(this@LoginActivity, user.uid)
            // They just typed their password, so the app lock doesn't need to ask again
            AppLock.unlocked = true
            // If they left the app while logging in, open Home when they come back
            withStarted {
                startActivity(Intent(this@LoginActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        }
    }

    /** Email not verified yet: offer to resend the link, and stay logged out until it's done. */
    private fun askToVerify(user: FirebaseUser) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.verify_title)
            .setMessage(getString(R.string.verify_message, user.email.orEmpty()))
            .setCancelable(false)
            .setPositiveButton(R.string.verify_resend) { _, _ ->
                user.sendEmailVerification().addOnCompleteListener { sent ->
                    firebaseAuth.signOut()
                    if (isDestroyed) return@addOnCompleteListener
                    Snackbar.make(
                        binding.root,
                        if (sent.isSuccessful) R.string.verify_sent
                        else AuthErrors.message(sent.exception, AuthErrors.Where.ACCOUNT),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(R.string.ok) { _, _ -> firebaseAuth.signOut() }
            .show()
    }

    /** Forgot password: always the same answer, so nobody can find out which emails have accounts. */
    private fun askResetEmail() {
        val field = DialogEmailFieldBinding.inflate(layoutInflater)
        field.dialogEmail.setText(binding.loginEmail.text)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_title)
            .setMessage(R.string.reset_message)
            .setView(field.root)
            .setPositiveButton(R.string.reset_send, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = field.dialogEmail.text?.toString()?.trim().orEmpty()
                if (!PasswordRules.isEmail(email)) {
                    field.dialogEmailBox.error = getString(R.string.err_bad_email)
                    return@setOnClickListener
                }
                dialog.dismiss()
                firebaseAuth.sendPasswordResetEmail(email).addOnCompleteListener { sent ->
                    if (isDestroyed) return@addOnCompleteListener
                    // Same message whether or not the email has an account; only a network problem is shown
                    val message = if (sent.exception is FirebaseNetworkException) R.string.err_network else R.string.reset_sent
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    /** After too many wrong passwords: the Log-in button counts down until it can be used again. */
    private fun showWaitIfLocked() {
        val left = LoginThrottle.waitLeftMs(this)
        countdown?.cancel()
        if (left <= 0) {
            binding.loginButton.isEnabled = !busy
            binding.loginButton.setText(R.string.log_in)
            return
        }
        binding.loginButton.isEnabled = false
        countdown = object : CountDownTimer(left, 1000) {
            override fun onTick(msLeft: Long) {
                binding.loginButton.text = getString(R.string.login_wait, (msLeft / 1000) + 1)
            }

            override fun onFinish() {
                binding.loginButton.isEnabled = true
                binding.loginButton.setText(R.string.log_in)
            }
        }.start()
    }

    private fun setBusy(on: Boolean) {
        busy = on
        binding.loginButton.isEnabled = !on
        binding.loginButton.setText(if (on) R.string.please_wait else R.string.log_in)
    }

    override fun onDestroy() {
        countdown?.cancel()
        spins.forEach { it.cancel() }
        spins.clear()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_EMAIL = "com.kroeungtes.app.auth.email"
        const val EXTRA_MESSAGE = "com.kroeungtes.app.auth.message"
    }
}
