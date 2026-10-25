package com.kroeungtes.app.data.local

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.kroeungtes.app.data.cloud.CloudContent

/**
 * Remembers the newest notification this phone has seen, so the bell tab can show a dot
 * for news admins sent after that (dashboard → Notifications).
 */
object NotificationsSeen {
    private const val PREFS = "krouen_notifications"
    private const val KEY_SEEN = "seen_at"

    private val seenLive = MutableLiveData<Long>()

    /** Time (ms) of the newest notification already seen; 0 = none yet. */
    fun seenAt(context: Context): Long = seenLive.value
        ?: context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SEEN, 0L)
            .also { seenLive.value = it }

    /** How many published notifications are newer than the last one seen. */
    fun unread(context: Context): LiveData<Int> {
        val app = context.applicationContext
        seenAt(app)
        val out = MediatorLiveData<Int>()
        val update = {
            val seen = seenAt(app)
            out.value = CloudContent.announcements.value.orEmpty().count { (it.publishedAt?.time ?: 0L) > seen }
        }
        out.addSource(CloudContent.announcements) { update() }
        out.addSource(seenLive) { update() }
        return out
    }

    /** Called while the Notifications tab is open. */
    fun markAllSeen(context: Context) {
        val newest = CloudContent.announcements.value.orEmpty().maxOfOrNull { it.publishedAt?.time ?: 0L } ?: return
        if (newest <= seenAt(context)) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_SEEN, newest).apply()
        seenLive.value = newest
    }
}
