package com.kroeungtes.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kroeungtes.app.R
import com.kroeungtes.app.data.model.Ingredient
import com.kroeungtes.app.databinding.ItemIngredientBinding

class IngredientAdapter : RecyclerView.Adapter<IngredientAdapter.ViewHolder>() {

    private var items: List<Ingredient> = emptyList()

    fun submit(list: List<Ingredient>) {
        items = list
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemIngredientBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemIngredientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvIngredientName.text = item.name
            tvIngredientAmount.text = item.amount
            // Real photo -> no tint; fall back to the green fork-and-knife icon
            ImageViewCompat.setImageTintList(imgIngredient, null)
            Glide.with(holder.itemView)
                .load(item.imageUrl)
                .placeholder(R.drawable.ic_restaurant)
                .error(R.drawable.ic_restaurant)
                .into(imgIngredient)
        }
    }

    override fun getItemCount() = items.size
}
