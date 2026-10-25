package com.kroeungtes.app.ui.auth

import android.animation.Animator
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.android.gms.tasks.Task
import com.kroeungtes.app.R
import com.kroeungtes.app.data.local.ProfileStore
import com.kroeungtes.app.databinding.ActivityLoginBinding
import com.kroeungtes.app.databinding.DialogEditProfileBinding
import com.kroeungtes.app.security.AppLock
import com.kroeungtes.app.security.AuthErrors
import com.kroeungtes.app.security.LocalData
import com.kroeungtes.app.security.LoginThrottle
import com.kroeungtes.app.security.PhoneOtp
import com.kroeungtes.app.ui.activities.MainActivity
import com.kroeungtes.app.ui.common.EdgeToEdge
import com.kroeungtes.app.ui.common.marginForSystemBars
import com.kroeungtes.app.ui.common.pop
import com.kroeungtes.app.ui.common.pressScale
import com.kroeungtes.app.ui.common.riseIn
import com.kroeungtes.app.ui.common.spinForever
import kotlinx.coroutines.launch

/**
 * Log in or create an account with a phone number and a 6-digit SMS code (OTP).
 * Step 1: phone number → Send code. Step 2: type the code (or Android fills it in) → Verify.
 * After 5 wrong codes there is a short wait; new users are asked for their name.
 */
class LoginActivity : AppCompatActivity(), PhoneOtp.Listener {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var otp: PhoneOtp
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    /** Slowly turning corner blobs, stopped in onDestroy. */
    private val spins = mutableListOf<Animator>()
    private var resendTimer: CountDownTimer? = null
    private var waitTimer: CountDownTimer? = null
    /** Sending a code. */
    private var busy = false
    /** Signing in with a code (separate, because instant verification can sign in while "sending"). */
    private var signingIn = false
    /** When "Resend code" becomes available (wall clock, survives rotation). */
    private var resendAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.light(this)
        // Keep the code out of screenshots and the Recent apps preview
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.authScroll.marginForSystemBars(ime = true)

        otp = PhoneOtp(this, this)
        otp.restore(savedInstanceState)
        resendAt = savedInstanceState?.getLong(KEY_RESEND_AT) ?: 0L

        // Already signed in (e.g. the screen was rebuilt while signing in): finish the job
        auth.currentUser?.takeIf { !it.phoneNumber.isNullOrBlank() }?.let { user ->
            signingIn = true
            lifecycleScope.launch {
                LocalData.claimFor(this@LoginActivity, user.uid)
                AppLock.unlocked = true
                withStarted { openHome() }
            }
            return
        }

        // e.g. "Your account was deleted"
        intent.getIntExtra(EXTRA_MESSAGE, 0).takeIf { it != 0 }?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
        }

        // The wok arrives from the splash screen; the rest rises in one after another
        binding.loginTitle.riseIn(delay = 120, distanceDp = 20f)
        binding.loginSubtitle.riseIn(delay = 200, distanceDp = 20f)
        listOf(binding.loginForm, binding.loginSmsNote)
            .forEachIndexed { i, view -> view.riseIn(delay = 280L + 90L * i, distanceDp = 28f, duration = 480) }
        binding.btnSendCode.pressScale()
        binding.btnVerify.pressScale()
        spins += binding.loginBlobTop.spinForever(40_000)
        spins += binding.loginBlobBottom.spinForever(32_000, clockwise = false)

        // Social sign-in is shown in the design but not connected yet
        val comingSoon = View.OnClickListener {
            Snackbar.make(binding.root, R.string.coming_soon, Snackbar.LENGTH_SHORT).show()
        }
        binding.loginGoogle.setOnClickListener(comingSoon)
        binding.loginFacebook.setOnClickListener(comingSoon)

        binding.loginPhone.doAfterTextChanged { binding.loginPhoneBox.error = null }
        binding.loginCode.doAfterTextChanged {
            binding.loginCodeBox.error = null
            // All 6 digits typed (or filled in from the SMS): verify straight away
            if (PhoneOtp.asciiDigits(it?.toString().orEmpty()).length == PhoneOtp.CODE_LENGTH && !signingIn) verifyTypedCode()
        }
        binding.loginPhone.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) { sendCode(); true } else false
        }
        binding.btnSendCode.setOnClickListener { sendCode() }
        binding.btnVerify.setOnClickListener { verifyTypedCode() }
        binding.btnChangeNumber.setOnClickListener { showPhoneStep() }
        binding.btnResend.setOnClickListener {
            if (busy || signingIn) return@setOnClickListener
            otp.phone?.let { setBusy(true); otp.send(it, resend = true) }
        }

        if (otp.hasCode) showCodeStep(otp.phone.orEmpty(), startTimer = false) else showPhoneStep()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        otp.save(outState)
        outState.putLong(KEY_RESEND_AT, resendAt)
    }

    override fun onResume() {
        super.onResume()
        showWaitIfLocked()
    }

    // ---------- Step 1: phone number ----------

    private fun sendCode() {
        if (busy) return
        val phone = PhoneOtp.toE164(binding.loginPhone.text?.toString().orEmpty())
        if (phone == null) {
            binding.loginPhoneBox.error = getString(R.string.err_bad_phone)
            binding.loginPhoneBox.pop()
            return
        }
        setBusy(true)
        otp.send(phone)
    }

    override fun onCodeSent(phone: String) {
        if (isDestroyed) return
        setBusy(false)
        showCodeStep(phone, startTimer = true)
    }

    override fun onSendFailed(error: Exception) {
        if (isDestroyed) return
        setBusy(false)
        val message = getString(AuthErrors.message(error))
        if (binding.codeStep.visibility == View.VISIBLE) binding.loginCodeBox.error = message
        else binding.loginPhoneBox.error = message
    }

    override fun onAutoVerified(credential: PhoneAuthCredential) {
        if (isDestroyed) return
        // Android read the SMS for us: show the code, then sign in
        credential.smsCode?.let { binding.loginCode.setText(it) }
        signIn(credential)
    }

    private fun showPhoneStep() {
        resendTimer?.cancel()
        binding.codeStep.visibility = View.GONE
        binding.phoneStep.visibility = View.VISIBLE
        binding.loginCode.text = null
    }

    private fun showCodeStep(phone: String, startTimer: Boolean) {
        binding.phoneStep.visibility = View.GONE
        binding.codeStep.visibility = View.VISIBLE
        binding.tvCodeSentTo.text = getString(R.string.code_sent_to, phone)
        binding.codeStep.riseIn(distanceDp = 20f, duration = 360)
        binding.loginCode.requestFocus()
        if (startTimer) resendAt = System.currentTimeMillis() + PhoneOtp.TIMEOUT_SECONDS * 1000
        startResendTimer()
    }

    /** "Resend code" waits as long as Firebase waits for the SMS to arrive. */
    private fun startResendTimer() {
        resendTimer?.cancel()
        val left = resendAt - System.currentTimeMillis()
        if (left <= 0) {
            binding.btnResend.isEnabled = true
            binding.btnResend.setText(R.string.resend_code)
            return
        }
        binding.btnResend.isEnabled = false
        resendTimer = object : CountDownTimer(left, 1000) {
            override fun onTick(msLeft: Long) {
                binding.btnResend.text = getString(R.string.resend_in, (msLeft / 1000) + 1)
            }

            override fun onFinish() {
                binding.btnResend.isEnabled = true
                binding.btnResend.setText(R.string.resend_code)
            }
        }.start()
    }

    // ---------- Step 2: the code ----------

    private fun verifyTypedCode() {
        if (signingIn || LoginThrottle.waitLeftMs(this) > 0) return
        val code = PhoneOtp.asciiDigits(binding.loginCode.text?.toString().orEmpty())
        if (code.length != PhoneOtp.CODE_LENGTH) {
            binding.loginCodeBox.error = getString(R.string.err_enter_code)
            return
        }
        val credential = otp.credentialFor(code)
        if (credential == null) {
            binding.loginCodeBox.error = getString(R.string.err_code_expired)
            return
        }
        signIn(credential)
    }

    private fun signIn(credential: PhoneAuthCredential) {
        if (signingIn) return
        signingIn = true
        setBusy(true)
        // Plain listener (not tied to this screen) so the result still arrives if the user leaves the app meanwhile
        auth.signInWithCredential(credential).addOnCompleteListener { task -> onSignedIn(task) }
    }

    private fun onSignedIn(task: Task<AuthResult>) {
        if (isDestroyed) return
        // task.result throws when the task failed, so only read it on success
        val result = if (task.isSuccessful) task.result else null
        val user = result?.user
        if (user == null) {
            signingIn = false
            setBusy(false)
            val error = task.exception
            if (AuthErrors.isWrongCode(error)) LoginThrottle.recordFailure(this)
            binding.loginCodeBox.error = getString(AuthErrors.message(error))
            binding.loginCode.text = null
            showWaitIfLocked()
            return
        }
        LoginThrottle.recordSuccess(this)
        resendTimer?.cancel()
        val isNewUser = result?.additionalUserInfo?.isNewUser == true
        lifecycleScope.launch {
            // Another account used this phone before? Remove its data first
            LocalData.claimFor(this@LoginActivity, user.uid)
            // They just proved who they are, so the app lock doesn't need to ask again
            AppLock.unlocked = true
            withStarted {
                if (isNewUser) askName() else openHome()
            }
        }
    }

    /** First time with this number: ask what to call them (can be skipped). */
    private fun askName() {
        val f = DialogEditProfileBinding.inflate(layoutInflater)
        f.edProfileBio.visibility = View.GONE
        (f.edProfileBio.parent?.parent as? View)?.visibility = View.GONE
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.name_title)
            .setMessage(R.string.name_message)
            .setView(f.root)
            .setCancelable(false)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = f.edProfileName.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    ProfileStore.save(this, name, "")
                    auth.currentUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())
                }
                openHome()
            }
            .setNegativeButton(R.string.skip) { _, _ -> openHome() }
            .show()
    }

    private fun openHome() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    // ---------- Busy + wait ----------

    /** After too many wrong codes: Verify counts down until it can be used again. */
    private fun showWaitIfLocked() {
        val left = LoginThrottle.waitLeftMs(this)
        waitTimer?.cancel()
        if (left <= 0) {
            binding.btnVerify.isEnabled = !busy && !signingIn
            if (!signingIn) binding.btnVerify.setText(R.string.verify)
            return
        }
        binding.btnVerify.isEnabled = false
        waitTimer = object : CountDownTimer(left, 1000) {
            override fun onTick(msLeft: Long) {
                binding.btnVerify.text = getString(R.string.login_wait, (msLeft / 1000) + 1)
            }

            override fun onFinish() {
                binding.btnVerify.isEnabled = true
                binding.btnVerify.setText(R.string.verify)
            }
        }.start()
    }

    private fun setBusy(on: Boolean) {
        busy = on
        val working = on || signingIn
        binding.btnSendCode.isEnabled = !working
        binding.btnSendCode.setText(if (working) R.string.please_wait else R.string.send_code)
        val waiting = LoginThrottle.waitLeftMs(this) > 0
        binding.btnVerify.isEnabled = !working && !waiting
        if (working) binding.btnVerify.setText(R.string.please_wait)
        else if (!waiting) binding.btnVerify.setText(R.string.verify)
    }

    override fun onDestroy() {
        resendTimer?.cancel()
        waitTimer?.cancel()
        spins.forEach { it.cancel() }
        spins.clear()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MESSAGE = "com.kroeungtes.app.auth.message"
        private const val KEY_RESEND_AT = "resend_at"
    }
}
