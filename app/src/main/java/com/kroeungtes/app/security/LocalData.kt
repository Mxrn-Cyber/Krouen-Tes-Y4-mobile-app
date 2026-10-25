package com.kroeungtes.app.security

import android.content.Context
import com.kroeungtes.app.data.local.AgeGate
import com.kroeungtes.app.data.local.MealsDatabase
import com.kroeungtes.app.data.local.MyRecipeStore
import com.kroeungtes.app.data.local.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the app keeps on the phone for the signed-in person. */
object LocalData {
    private const val PREFS = "krouen_owner"
    private const val KEY_UID = "uid"

    /**
     * Call right after a successful log-in. If a different account used this phone before,
     * its profile, recipes and Wishlist are removed first, so the new person can't see them.
     */
    suspend fun claimFor(context: Context, uid: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val owner = prefs.getString(KEY_UID, null)
        if (owner != null && owner != uid) wipe(context)
        prefs.edit().putString(KEY_UID, uid).apply()
    }

    /** Removes the profile, own recipes and photos, Wishlist, 18+ answer and app-lock setting. */
    suspend fun wipe(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        ProfileStore.clear(app)
        MyRecipeStore.clear(app)
        AgeGate.clear(app)
        AppLock.reset(app)
        LoginThrottle.reset(app)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        MealsDatabase.getInstance(app).clearAllTables()
    }
}
