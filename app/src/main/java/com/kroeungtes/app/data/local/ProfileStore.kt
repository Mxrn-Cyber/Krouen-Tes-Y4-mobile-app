package com.kroeungtes.app.data.local

import android.content.Context

/** Name, short bio and photo shown on My Profile (kept on the phone). */
object ProfileStore {
    private const val PREFS = "profile"

    data class Profile(val name: String, val bio: String, val avatarPath: String?)

    fun load(context: Context, fallbackName: String): Profile {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Profile(
            name = p.getString("name", null) ?: fallbackName,
            bio = p.getString("bio", null).orEmpty(),
            avatarPath = p.getString("avatar", null)
        )
    }

    fun save(context: Context, name: String, bio: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", name).putString("bio", bio).apply()
    }

    fun saveAvatar(context: Context, path: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("avatar", path).apply()
    }
}
