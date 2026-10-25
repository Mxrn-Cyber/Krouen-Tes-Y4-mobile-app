package com.kroeungtes.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kroeungtes.app.R
import com.kroeungtes.app.data.model.RecipeCardItem
import com.kroeungtes.app.databinding.ItemRecipeCardBinding
import com.kroeungtes.app.ui.common.Images
import com.kroeungtes.app.ui.common.SharedImage
import com.kroeungtes.app.ui.common.pop
import com.kroeungtes.app.ui.common.pressScale

/**
 * One adapter for every recipe card in the app.
 * - [fixedWidthPx]: set for horizontal rows; leave null in grids so the card fills its column.
 * - [onSaveToggle]: when set, the bookmark on the photo saves/unsaves (with a pop animation).
 */
class RecipeCardAdapter(
    private val onClick: (RecipeCardItem) -> Unit,
    private val onLongClick: ((RecipeCardItem) -> Unit)? = null,
    private val onSaveToggle: ((RecipeCardItem, Boolean) -> Unit)? = null,
    private val fixedWidthPx: Int? = null,
    private val author: String? = null
) : RecyclerView.Adapter<RecipeCardAdapter.ViewHolder>() {

    private var items: List<RecipeCardItem> = emptyList()
    private var savedIds: Set<String> = emptySet()

    fun submit(list: List<RecipeCardItem>) {
        items = list
        notifyDataSetChanged()
    }

    /** Ids that are in the Wishlist, so the bookmark shows filled. */
    fun setSavedIds(ids: Set<String>) {
        if (ids == savedIds) return
        savedIds = ids
        notifyItemRangeChanged(0, items.size, PAYLOAD_SAVED)
    }

    fun itemAt(position: Int): RecipeCardItem = items[position]

    class ViewHolder(val binding: ItemRecipeCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecipeCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        fixedWidthPx?.let { binding.root.layoutParams.width = it }
        binding.root.pressScale()
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SAVED)) bindSaved(holder, items[position], animate = false)
        else super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        holder.binding.apply {
            cardName.text = item.name
            cardSubtitle.text = item.subtitle
            val by = item.author ?: author
            cardAuthor.text = if (by != null) ctx.getString(R.string.by_author, by) else ctx.getString(R.string.krouen_pick)
            Glide.with(holder.itemView)
                .load(Images.model(item.image))
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.placeholder_image)
                .into(cardImage)
        }
        bindSaved(holder, item, animate = false)
        holder.itemView.setOnClickListener {
            SharedImage.mark(holder.binding.cardImage)
            onClick(item)
        }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(item)
            onLongClick != null
        }
    }

    private fun bindSaved(holder: ViewHolder, item: RecipeCardItem, animate: Boolean) {
        val button = holder.binding.cardSave
        if (onSaveToggle == null || item.isLocal || !item.savable) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        val saved = item.id in savedIds
        button.setImageResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
        if (animate) button.pop()
        button.setOnClickListener {
            val nowSaved = item.id !in savedIds
            savedIds = if (nowSaved) savedIds + item.id else savedIds - item.id
            bindSaved(holder, item, animate = true)
            onSaveToggle.invoke(item, nowSaved)
        }
    }

    override fun getItemCount() = items.size

    companion object {
        private const val PAYLOAD_SAVED = "saved"
    }
}
