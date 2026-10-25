package com.kroeungtes.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kroeungtes.app.R
import com.kroeungtes.app.data.model.RecipeCardItem
import com.kroeungtes.app.databinding.ItemRecipeSmallBinding
import com.kroeungtes.app.ui.common.Images
import com.kroeungtes.app.ui.common.SharedImage
import com.kroeungtes.app.ui.common.pressScale

/** Small cards for the "Recent recipe" row on Home (the newest recipes from the dashboard). */
class RecentRecipeAdapter(
    private val onClick: (RecipeCardItem) -> Unit
) : RecyclerView.Adapter<RecentRecipeAdapter.ViewHolder>() {

    private var items: List<RecipeCardItem> = emptyList()

    fun submit(list: List<RecipeCardItem>) {
        items = list
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemRecipeSmallBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemRecipeSmallBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            .also { it.root.pressScale() }
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        holder.binding.apply {
            tvRecentName.text = item.name
            tvRecentAuthor.text = ctx.getString(R.string.krouen_pick)
            Glide.with(holder.itemView).load(Images.model(item.image)).placeholder(R.drawable.placeholder_image).into(imgRecent)
        }
        holder.itemView.setOnClickListener {
            SharedImage.mark(holder.binding.imgRecent)
            onClick(item)
        }
    }

    override fun getItemCount() = items.size
}
