package com.kroeungtes.app.data.local

import android.content.Context

/** Remembers that the user said they are 18 or older (needed to see cocktails). */
object AgeGate {
    private const val PREFS = "krouen_age_gate"
    private const val KEY_ADULT = "is_adult"

    fun isAdult(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ADULT, false)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    fun confirmAdult(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ADULT, true).apply()
    }
}
