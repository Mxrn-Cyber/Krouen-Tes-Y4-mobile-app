package com.kroeungtes.app.security

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

/**
 * Sends a 6-digit SMS code with Firebase Phone Auth and turns the typed code into a credential.
 * Used by Log in and by Delete account (to confirm it's really you).
 */
class PhoneOtp(private val activity: Activity, private val listener: Listener) {

    interface Listener {
        /** The SMS is on its way; show the code box. */
        fun onCodeSent(phone: String)

        /** Android read the SMS by itself (or verified the number instantly): no typing needed. */
        fun onAutoVerified(credential: PhoneAuthCredential)

        fun onSendFailed(error: Exception)
    }

    var phone: String? = null
        private set
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    val hasCode: Boolean get() = verificationId != null

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            listener.onAutoVerified(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            // Full details in Logcat (e.g. SMS region not allowed, billing not enabled)
            Log.w(TAG, "Phone verification failed", e)
            listener.onSendFailed(e)
        }

        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = id
            resendToken = token
            listener.onCodeSent(phone.orEmpty())
        }
    }

    /** Sends a code to [e164] (e.g. "+85512345678"). [resend] reuses the last token so Firebase allows it. */
    fun send(e164: String, resend: Boolean = false) {
        phone = e164
        val builder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(e164)
            .setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        val token = resendToken
        if (resend && token != null) builder.setForceResendingToken(token)
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    /** The credential for a typed code, or null if no code was sent yet. */
    fun credentialFor(code: String): PhoneAuthCredential? =
        verificationId?.let { PhoneAuthProvider.getCredential(it, code) }

    // Survive screen rotation
    fun save(out: Bundle) {
        out.putString(KEY_PHONE, phone)
        out.putString(KEY_ID, verificationId)
        out.putParcelable(KEY_TOKEN, resendToken)
    }

    fun restore(saved: Bundle?) {
        if (saved == null) return
        phone = saved.getString(KEY_PHONE)
        verificationId = saved.getString(KEY_ID)
        @Suppress("DEPRECATION")
        resendToken = saved.getParcelable(KEY_TOKEN)
    }

    companion object {
        private const val TAG = "PhoneOtp"
        const val TIMEOUT_SECONDS = 60L
        const val CODE_LENGTH = 6
        private const val KEY_PHONE = "otp_phone"
        private const val KEY_ID = "otp_verification_id"
        private const val KEY_TOKEN = "otp_resend_token"

        /** Cambodia when no country code is typed. */
        private const val DEFAULT_COUNTRY_CODE = "855"

        /**
         * "012 345 678" → "+85512345678"; "+66 81 234 5678" → "+66812345678".
         * Null when it can't be a phone number (E.164 allows 8 to 15 digits with the country code).
         */
        fun toE164(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.any { !it.isDigit() && it !in " +-()." }) return null
            val digits = asciiDigits(trimmed)
            var full = when {
                trimmed.startsWith("+") -> digits
                digits.startsWith("00") -> digits.removePrefix("00")
                digits.startsWith(DEFAULT_COUNTRY_CODE) && digits.length > 10 -> digits
                else -> DEFAULT_COUNTRY_CODE + digits.removePrefix("0")
            }
            // "+855 012 345 678": the local 0 is dropped after the country code
            if (full.startsWith(DEFAULT_COUNTRY_CODE + "0")) full = DEFAULT_COUNTRY_CODE + full.drop(DEFAULT_COUNTRY_CODE.length + 1)
            return if (full.length in 8..15) "+$full" else null
        }

        /** Keeps only digits and turns Khmer digits (០–៩) and other scripts into 0–9. */
        fun asciiDigits(text: String): String =
            text.mapNotNull { c -> Character.digit(c, 10).takeIf { it >= 0 } }.joinToString("")
    }
}
