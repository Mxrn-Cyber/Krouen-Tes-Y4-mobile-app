package com.kroeungtes.app.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kroeungtes.app.data.cloud.CloudContent.Announcement
import com.kroeungtes.app.databinding.ItemNotificationBinding

/** News cards in the bell tab. [isNew] marks the ones that arrived since the tab was last opened. */
class NotificationAdapter(
    private val khmer: Boolean,
    private val canOpen: (Announcement) -> Boolean,
    private val onOpen: (Announcement) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.Holder>() {

    class Holder(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

    private var items: List<Announcement> = emptyList()
    private var newerThan = Long.MAX_VALUE

    fun submit(list: List<Announcement>, seenBefore: Long) {
        items = list
        newerThan = seenBefore
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val note = items[position]
        val b = holder.binding
        b.tvNoteTitle.text = note.title(khmer)
        b.tvNoteBody.text = note.body(khmer)
        b.tvNoteBody.visibility = if (note.body(khmer).isBlank()) View.GONE else View.VISIBLE
        val time = note.publishedAt?.time
        b.tvNoteTime.text = time?.let {
            DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        } ?: ""
        b.dotNew.visibility = if (time != null && time > newerThan) View.VISIBLE else View.GONE

        if (note.imageUrl != null) {
            b.imgNote.visibility = View.VISIBLE
            Glide.with(b.imgNote).load(note.imageUrl).centerCrop().into(b.imgNote)
        } else {
            Glide.with(b.imgNote).clear(b.imgNote)
            b.imgNote.visibility = View.GONE
        }

        val openable = canOpen(note)
        b.tvNoteOpen.visibility = if (openable) View.VISIBLE else View.GONE
        b.root.isClickable = openable
        b.root.setOnClickListener(if (openable) View.OnClickListener { onOpen(note) } else null)
    }
}
