package com.kroeungtes.app.security

import androidx.annotation.StringRes
import com.kroeungtes.app.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/**
 * Turns Firebase errors into short, friendly messages.
 * On Log in we never say whether the email exists — only "email or password is wrong".
 */
object AuthErrors {

    enum class Where { LOGIN, SIGNUP, ACCOUNT }

    @StringRes
    fun message(error: Exception?, where: Where): Int = when (error) {
        is FirebaseNetworkException -> R.string.err_network
        is FirebaseTooManyRequestsException -> R.string.err_too_many
        // Weak password is a kind of "invalid credentials", so it has to be checked first
        is FirebaseAuthWeakPasswordException -> R.string.pw_too_short
        is FirebaseAuthUserCollisionException -> R.string.err_email_in_use
        is FirebaseAuthRecentLoginRequiredException -> R.string.err_login_again
        is FirebaseAuthInvalidCredentialsException ->
            if (where == Where.SIGNUP) R.string.err_bad_email else R.string.err_wrong_login
        is FirebaseAuthInvalidUserException ->
            if (where == Where.ACCOUNT) R.string.err_login_again else R.string.err_wrong_login
        else -> R.string.err_generic
    }

    /** Wrong email/password (counts towards the short wait), not a network or server problem. */
    fun isWrongLogin(error: Exception?): Boolean =
        error is FirebaseAuthInvalidCredentialsException || error is FirebaseAuthInvalidUserException
}
