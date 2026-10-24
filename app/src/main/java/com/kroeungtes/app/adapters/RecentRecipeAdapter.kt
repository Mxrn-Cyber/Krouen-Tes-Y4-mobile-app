package com.kroeungtes.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kroeungtes.app.R
import com.kroeungtes.app.data.model.Meal
import com.kroeungtes.app.databinding.ItemRecipeSmallBinding
import com.kroeungtes.app.ui.common.pressScale

/** Small cards for the "Recent recipe" row on Home. */
class RecentRecipeAdapter(
    private val onClick: (Meal) -> Unit,
    private val onLongClick: (Meal) -> Unit = {}
) : RecyclerView.Adapter<RecentRecipeAdapter.ViewHolder>() {

    private var meals: List<Meal> = emptyList()

    fun submit(list: List<Meal>) {
        meals = list
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemRecipeSmallBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemRecipeSmallBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            .also { it.root.pressScale() }
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val meal = meals[position]
        val ctx = holder.itemView.context
        holder.binding.apply {
            tvRecentName.text = meal.strMeal
            tvRecentAuthor.text = ctx.getString(R.string.krouen_pick)
            Glide.with(holder.itemView).load(meal.strMealThumb).placeholder(R.drawable.placeholder_image).into(imgRecent)
        }
        holder.itemView.setOnClickListener { onClick(meal) }
        holder.itemView.setOnLongClickListener { onLongClick(meal); true }
    }

    override fun getItemCount() = meals.size
}
