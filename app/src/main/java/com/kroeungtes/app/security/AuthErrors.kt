package com.kroeungtes.app.security

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.kroeungtes.app.R

/** Turns Firebase phone-sign-in errors into short, friendly messages. */
object AuthErrors {

    /** Firebase errors that mean SMS sign-in isn't set up for this app yet (console settings). */
    private val notSetUp = setOf(
        "ERROR_OPERATION_NOT_ALLOWED", "ERROR_QUOTA_EXCEEDED", "ERROR_APP_NOT_AUTHORIZED",
        "ERROR_MISSING_CLIENT_IDENTIFIER", "ERROR_INVALID_APP_CREDENTIAL"
    )

    @StringRes
    fun message(error: Exception?): Int = when {
        error is FirebaseNetworkException -> R.string.err_network
        error is FirebaseTooManyRequestsException -> R.string.err_too_many
        error?.message?.contains("BILLING_NOT_ENABLED") == true -> R.string.err_sms_not_enabled
        // Invalid credentials is a kind of FirebaseAuthException, so it is checked first
        error is FirebaseAuthInvalidCredentialsException -> when (error.errorCode) {
            "ERROR_INVALID_PHONE_NUMBER" -> R.string.err_bad_phone
            "ERROR_SESSION_EXPIRED" -> R.string.err_code_expired
            else -> R.string.err_wrong_code
        }
        error is FirebaseAuthRecentLoginRequiredException -> R.string.err_login_again
        error is FirebaseAuthException && error.errorCode in notSetUp -> R.string.err_sms_not_enabled
        else -> R.string.err_generic
    }

    /** A wrongly typed code (counts towards the short wait), not a network or setup problem. */
    fun isWrongCode(error: Exception?): Boolean =
        error is FirebaseAuthInvalidCredentialsException && error.errorCode == "ERROR_INVALID_VERIFICATION_CODE"
}
