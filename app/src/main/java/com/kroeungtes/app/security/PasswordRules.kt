package com.kroeungtes.app.security

import android.util.Patterns
import androidx.annotation.StringRes
import com.kroeungtes.app.R

/** What we ask of emails and new passwords (checked on the phone before Firebase sees them). */
object PasswordRules {
    const val MIN_LENGTH = 8

    fun isEmail(text: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(text).matches()

    /** Null when the password is OK; otherwise the message that says what is missing. */
    @StringRes
    fun problem(password: String): Int? = when {
        password.length < MIN_LENGTH -> R.string.pw_too_short
        password.none { it.isLetter() } || password.none { it.isDigit() } -> R.string.pw_letter_number
        else -> null
    }
}
