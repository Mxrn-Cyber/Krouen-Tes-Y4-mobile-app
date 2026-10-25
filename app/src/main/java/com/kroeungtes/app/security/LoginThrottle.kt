package com.kroeungtes.app.security

import android.content.Context

/**
 * After 5 wrong passwords in a row, Log in waits 30 seconds before trying again
 * (each later lock-out doubles, up to 15 minutes). Firebase also blocks many tries on its side.
 */
object LoginThrottle {
    private const val PREFS = "krouen_login_guard"
    private const val KEY_FAILS = "fails"
    private const val KEY_UNTIL = "locked_until"
    private const val KEY_ROUNDS = "rounds"

    private const val MAX_TRIES = 5
    private const val FIRST_WAIT_MS = 30_000L
    private const val LONGEST_WAIT_MS = 15 * 60_000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Milliseconds left before Log in can be tried again (0 = go ahead). */
    fun waitLeftMs(context: Context): Long =
        (prefs(context).getLong(KEY_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun recordFailure(context: Context) {
        val p = prefs(context)
        val fails = p.getInt(KEY_FAILS, 0) + 1
        if (fails < MAX_TRIES) {
            p.edit().putInt(KEY_FAILS, fails).apply()
            return
        }
        val rounds = p.getInt(KEY_ROUNDS, 0)
        val wait = (FIRST_WAIT_MS shl rounds.coerceAtMost(5)).coerceAtMost(LONGEST_WAIT_MS)
        p.edit()
            .putInt(KEY_FAILS, 0)
            .putInt(KEY_ROUNDS, rounds + 1)
            .putLong(KEY_UNTIL, System.currentTimeMillis() + wait)
            .apply()
    }

    fun recordSuccess(context: Context) = reset(context)

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
