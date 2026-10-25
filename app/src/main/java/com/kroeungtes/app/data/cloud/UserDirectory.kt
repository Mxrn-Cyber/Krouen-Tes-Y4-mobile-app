package com.kroeungtes.app.data.cloud

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.kroeungtes.app.R
import com.kroeungtes.app.data.local.ProfileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The signed-in person's entry in Firestore "users" (name, phone, joined, last seen, blocked),
 * which admins see in the dashboard. Problems here never stop the app (logged only).
 */
object UserDirectory {
    private const val TAG = "UserDirectory"
    private const val NETWORK_WAIT_MS = 4_000L
    private const val DELETE_WAIT_MS = 10_000L

    private fun myDoc() = FirebaseAuth.getInstance().currentUser?.let {
        FirebaseFirestore.getInstance().collection("users").document(it.uid)
    }

    /** Creates the entry on first sign-in, otherwise updates "last seen" (and the name if given). */
    suspend fun touch(context: Context, name: String? = null) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val phone = user.phoneNumber?.takeIf { it.isNotBlank() } ?: return
        val doc = myDoc() ?: return
        try {
            withTimeoutOrNull(NETWORK_WAIT_MS) {
                val snap = doc.get().await()
                if (!snap.exists()) {
                    val displayName = name
                        ?: ProfileStore.load(context, "").name.takeIf { it.isNotBlank() }
                        ?: user.displayName
                        ?: context.getString(R.string.default_author)
                    doc.set(
                        mapOf(
                            "name" to displayName,
                            "phone" to phone,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "lastSeenAt" to FieldValue.serverTimestamp(),
                            "blocked" to false,
                            "platform" to "android"
                        )
                    ).await()
                } else {
                    val changes = mutableMapOf<String, Any>("lastSeenAt" to FieldValue.serverTimestamp())
                    if (!name.isNullOrBlank()) changes["name"] = name
                    if (snap.getString("phone") != phone) changes["phone"] = phone
                    doc.update(changes).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't update the user entry", e)
        }
    }

    /** True when an admin has blocked this account. Unknown (offline) counts as not blocked. */
    suspend fun isBlocked(): Boolean {
        val doc = myDoc() ?: return false
        return try {
            withTimeoutOrNull(NETWORK_WAIT_MS) { doc.get().await().getBoolean("blocked") } == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** Calls [onBlocked] as soon as an admin blocks this account (while the app is open). */
    fun watchBlocked(onBlocked: () -> Unit): ListenerRegistration? =
        myDoc()?.addSnapshotListener { snap, _ ->
            if (snap?.getBoolean("blocked") == true) onBlocked()
        }

    /**
     * Removes this person's entry (used when they delete their account).
     * Returns false when the server didn't confirm it (e.g. no internet), so the account is kept.
     */
    suspend fun deleteMine(): Boolean {
        val doc = myDoc() ?: return true
        return try {
            withTimeoutOrNull(DELETE_WAIT_MS) { doc.delete().await(); true } == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't delete the user entry", e)
            false
        }
    }
}
