package com.kroeungtes.app.ui.fragments

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.kroeungtes.app.R
import com.kroeungtes.app.databinding.DialogLogoutBinding
import com.kroeungtes.app.databinding.DialogPasswordsBinding
import com.kroeungtes.app.databinding.SheetAccountSecurityBinding
import com.kroeungtes.app.security.AppLock
import com.kroeungtes.app.security.AuthErrors
import com.kroeungtes.app.security.LocalData
import com.kroeungtes.app.security.PasswordRules
import com.kroeungtes.app.ui.auth.LoginActivity
import com.kroeungtes.app.ui.common.aboveBar
import kotlinx.coroutines.launch

/**
 * Profile → Account & security: change password, app lock, log out, delete account.
 * Changing the password or deleting the account asks for the current password first.
 */
object AccountSecuritySheet {

    fun show(fragment: Fragment) {
        val ctx = fragment.requireContext()
        val sheet = BottomSheetDialog(ctx)
        val b = SheetAccountSecurityBinding.inflate(fragment.layoutInflater)
        b.sheetEmail.text = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        b.switchAppLock.isChecked = AppLock.isEnabled(ctx)

        b.rowChangePassword.setOnClickListener {
            sheet.dismiss()
            changePassword(fragment)
        }
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

    // ---------- Change password ----------

    private fun changePassword(fragment: Fragment) {
        val ctx = fragment.requireContext()
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val f = DialogPasswordsBinding.inflate(fragment.layoutInflater)
        clearErrorsOnType(f)
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.change_password)
            .setView(f.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.secure()
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                val current = f.dialogCurrent.text?.toString().orEmpty()
                val new = f.dialogNew.text?.toString().orEmpty()
                val confirm = f.dialogConfirm.text?.toString().orEmpty()
                var ok = true
                if (current.isEmpty()) { f.dialogCurrentBox.error = ctx.getString(R.string.err_enter_password); ok = false }
                PasswordRules.problem(new)?.let { f.dialogNewBox.error = ctx.getString(it); ok = false }
                if (ok && new == current) { f.dialogNewBox.error = ctx.getString(R.string.err_same_password); ok = false }
                if (confirm != new) { f.dialogConfirmBox.error = ctx.getString(R.string.err_passwords_differ); ok = false }
                if (!ok) return@setOnClickListener

                save.isEnabled = false
                reauthenticate(user, current) { reauthError ->
                    if (reauthError != null) {
                        save.isEnabled = true
                        f.dialogCurrentBox.error = ctx.getString(AuthErrors.message(reauthError, AuthErrors.Where.LOGIN))
                        return@reauthenticate
                    }
                    user.updatePassword(new).addOnCompleteListener { task ->
                        save.isEnabled = true
                        if (task.isSuccessful) {
                            dialog.dismiss()
                            fragment.view?.let { Snackbar.make(it, R.string.password_changed, Snackbar.LENGTH_LONG).aboveBar().show() }
                        } else {
                            f.dialogNewBox.error = ctx.getString(AuthErrors.message(task.exception, AuthErrors.Where.ACCOUNT))
                        }
                    }
                }
            }
        }
        dialog.show()
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

    // ---------- Delete account ----------

    private fun deleteAccount(fragment: Fragment) {
        val ctx = fragment.requireContext()
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val f = DialogPasswordsBinding.inflate(fragment.layoutInflater)
        f.dialogPwNote.visibility = View.VISIBLE
        f.dialogPwNote.setText(R.string.delete_account_warning)
        f.dialogNewBox.visibility = View.GONE
        f.dialogConfirmBox.visibility = View.GONE
        clearErrorsOnType(f)
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.delete_account)
            .setView(f.root)
            .setPositiveButton(R.string.delete_forever, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.secure()
            val delete = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            delete.setTextColor(ContextCompat.getColor(ctx, R.color.danger))
            delete.setOnClickListener {
                val password = f.dialogCurrent.text?.toString().orEmpty()
                if (password.isEmpty()) {
                    f.dialogCurrentBox.error = ctx.getString(R.string.err_enter_password)
                    return@setOnClickListener
                }
                delete.isEnabled = false
                reauthenticate(user, password) { reauthError ->
                    if (reauthError != null) {
                        delete.isEnabled = true
                        f.dialogCurrentBox.error = ctx.getString(AuthErrors.message(reauthError, AuthErrors.Where.LOGIN))
                        return@reauthenticate
                    }
                    user.delete().addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            delete.isEnabled = true
                            f.dialogCurrentBox.error = ctx.getString(AuthErrors.message(task.exception, AuthErrors.Where.ACCOUNT))
                            return@addOnCompleteListener
                        }
                        dialog.dismiss()
                        val activity = fragment.activity ?: return@addOnCompleteListener
                        activity.lifecycleScope.launch {
                            // The account is gone: remove everything it left on this phone too
                            FirebaseAuth.getInstance().signOut()
                            LocalData.wipe(activity)
                            goToLogin(activity, R.string.account_deleted)
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    // ---------- Helpers ----------

    /** Firebase asks for the password again before sensitive changes. */
    private fun reauthenticate(user: FirebaseUser, password: String, done: (Exception?) -> Unit) {
        val email = user.email
        if (email.isNullOrBlank()) {
            done(IllegalStateException("No email on this account"))
            return
        }
        user.reauthenticate(EmailAuthProvider.getCredential(email, password))
            .addOnCompleteListener { done(if (it.isSuccessful) null else it.exception) }
    }

    private fun clearErrorsOnType(f: DialogPasswordsBinding) {
        f.dialogCurrent.doAfterTextChanged { f.dialogCurrentBox.error = null }
        f.dialogNew.doAfterTextChanged { f.dialogNewBox.error = null }
        f.dialogConfirm.doAfterTextChanged { f.dialogConfirmBox.error = null }
    }

    /** Password dialogs stay out of screenshots and screen recordings. */
    private fun AlertDialog.secure() {
        window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun goToLogin(activity: Activity, message: Int? = null) {
        val intent = Intent(activity, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (message != null) intent.putExtra(LoginActivity.EXTRA_MESSAGE, message)
        activity.startActivity(intent)
        activity.finish()
    }
}
