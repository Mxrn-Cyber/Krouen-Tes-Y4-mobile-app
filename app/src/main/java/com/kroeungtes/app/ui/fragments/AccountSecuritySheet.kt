package com.kroeungtes.app.ui.fragments

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.kroeungtes.app.R
import com.kroeungtes.app.databinding.DialogLogoutBinding
import com.kroeungtes.app.databinding.DialogOtpCodeBinding
import com.kroeungtes.app.databinding.SheetAccountSecurityBinding
import com.kroeungtes.app.security.AppLock
import com.kroeungtes.app.security.AuthErrors
import com.kroeungtes.app.security.LocalData
import com.kroeungtes.app.security.PhoneOtp
import com.kroeungtes.app.ui.auth.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Profile → Account & security: app lock, log out, delete account.
 * Deleting the account asks for a fresh SMS code first.
 */
object AccountSecuritySheet {

    /** Not tied to a screen, so the clean-up after deleting the account always finishes. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun show(fragment: Fragment) {
        val ctx = fragment.requireContext()
        val sheet = BottomSheetDialog(ctx)
        val b = SheetAccountSecurityBinding.inflate(fragment.layoutInflater)
        val user = FirebaseAuth.getInstance().currentUser
        b.sheetEmail.text = user?.phoneNumber ?: user?.email.orEmpty()
        b.switchAppLock.isChecked = AppLock.isEnabled(ctx)

        b.rowAppLock.setOnClickListener { toggleAppLock(fragment, b) }
        b.rowLogOut.setOnClickListener {
            sheet.dismiss()
            confirmLogOut(fragment)
        }
        b.rowDeleteAccount.setOnClickListener {
            sheet.dismiss()
            deleteAccount(fragment)
        }
        sheet.setContentView(b.root)
        sheet.show()
    }

    // ---------- App lock ----------

    private fun toggleAppLock(fragment: Fragment, b: SheetAccountSecurityBinding) {
        val ctx = fragment.requireContext()
        if (AppLock.isEnabled(ctx)) {
            AppLock.setEnabled(ctx, false)
            b.switchAppLock.isChecked = false
            return
        }
        if (!AppLock.isAvailable(ctx)) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.app_lock)
                .setMessage(R.string.app_lock_no_screen_lock)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        // Prove it works before turning it on
        val prompt = BiometricPrompt(fragment, ContextCompat.getMainExecutor(ctx), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AppLock.setEnabled(ctx, true)
                b.switchAppLock.isChecked = true
            }
        })
        prompt.authenticate(AppLock.promptInfo(ctx, R.string.app_lock_prompt_turn_on))
    }

    // ---------- Log out ----------

    fun confirmLogOut(fragment: Fragment) {
        val ctx = fragment.requireContext()
        val f = DialogLogoutBinding.inflate(fragment.layoutInflater)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.log_out_title)
            .setView(f.root)
            .setPositiveButton(R.string.log_out) { _, _ ->
                val wipe = f.checkWipe.isChecked
                val activity = fragment.requireActivity()
                activity.lifecycleScope.launch {
                    FirebaseAuth.getInstance().signOut()
                    if (wipe) LocalData.wipe(activity) else AppLock.unlocked = false
                    goToLogin(activity)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- Delete account (confirmed with a new SMS code) ----------

    private fun deleteAccount(fragment: Fragment) {
        val activity = fragment.requireActivity()
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val phone = user.phoneNumber
        if (phone.isNullOrBlank()) {
            // Older email account: it can't get an SMS, so delete straight after a warning
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.delete_account)
                .setMessage(R.string.delete_account_warning_plain)
                .setPositiveButton(R.string.delete_forever) { _, _ -> delete(activity, user) }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        val f = DialogOtpCodeBinding.inflate(activity.layoutInflater)
        f.dialogOtpNote.text = activity.getString(R.string.delete_account_otp_warning, phone)
        f.dialogOtpBox.isEnabled = false
        f.dialogOtp.doAfterTextChanged { f.dialogOtpBox.error = null }

        lateinit var dialog: AlertDialog
        /** False once the dialog is closed: a late SMS must never delete the account after Cancel. */
        var active = true
        val otp = PhoneOtp(activity, object : PhoneOtp.Listener {
            override fun onCodeSent(phone: String) {
                if (!active) return
                f.dialogOtpBox.isEnabled = true
                f.dialogOtp.requestFocus()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                    isEnabled = true
                    setText(R.string.delete_forever)
                }
            }

            override fun onAutoVerified(credential: PhoneAuthCredential) {
                if (!active) return
                credential.smsCode?.let { f.dialogOtp.setText(it) }
                reauthAndDelete(activity, user, credential, dialog, f) {}
            }

            override fun onSendFailed(error: Exception) {
                if (!active) return
                f.dialogOtpBox.error = activity.getString(AuthErrors.message(error))
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                    isEnabled = true
                    setText(R.string.send_code)
                }
            }
        })

        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.delete_account)
            .setView(f.root)
            .setPositiveButton(R.string.send_code, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnDismissListener { active = false }
        dialog.setOnShowListener {
            dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setTextColor(ContextCompat.getColor(activity, R.color.danger))
            button.setOnClickListener {
                if (!otp.hasCode) {
                    // First press: send the SMS
                    button.isEnabled = false
                    otp.send(phone)
                    return@setOnClickListener
                }
                val code = PhoneOtp.asciiDigits(f.dialogOtp.text?.toString().orEmpty())
                val credential = if (code.length == PhoneOtp.CODE_LENGTH) otp.credentialFor(code) else null
                if (credential == null) {
                    f.dialogOtpBox.error = activity.getString(R.string.err_enter_code)
                    return@setOnClickListener
                }
                reauthAndDelete(activity, user, credential, dialog, f) {
                    // Code expired: send a new one
                    button.setText(R.string.send_code)
                    button.isEnabled = false
                    otp.send(phone, resend = true)
                }
            }
        }
        dialog.show()
    }

    private fun reauthAndDelete(
        activity: FragmentActivity, user: FirebaseUser, credential: PhoneAuthCredential,
        dialog: AlertDialog, f: DialogOtpCodeBinding, onExpired: () -> Unit
    ) {
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button?.isEnabled = false
        user.reauthenticate(credential).addOnCompleteListener { task ->
            if (!dialog.isShowing) return@addOnCompleteListener
            if (!task.isSuccessful) {
                button?.isEnabled = true
                val message = AuthErrors.message(task.exception)
                f.dialogOtpBox.error = activity.getString(message)
                if (message == R.string.err_code_expired) onExpired()
                return@addOnCompleteListener
            }
            dialog.dismiss()
            delete(activity, user)
        }
    }

    private fun delete(activity: FragmentActivity, user: FirebaseUser) {
        val app = activity.applicationContext
        user.delete().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.delete_account)
                    .setMessage(AuthErrors.message(task.exception))
                    .setPositiveButton(R.string.ok, null)
                    .show()
                return@addOnCompleteListener
            }
            appScope.launch {
                // The account is gone: remove everything it left on this phone too
                FirebaseAuth.getInstance().signOut()
                LocalData.wipe(app)
                val intent = Intent(app, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(LoginActivity.EXTRA_MESSAGE, R.string.account_deleted)
                app.startActivity(intent)
                if (!activity.isDestroyed) activity.finish()
            }
        }
    }

    private fun goToLogin(activity: Activity, message: Int? = null) {
        val intent = Intent(activity, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (message != null) intent.putExtra(LoginActivity.EXTRA_MESSAGE, message)
        activity.startActivity(intent)
        activity.finish()
    }
}
