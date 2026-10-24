package com.kroeungtes.app.adapters

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kroeungtes.app.R
import com.kroeungtes.app.data.model.Ingredient
import com.kroeungtes.app.data.model.RecipeText
import com.kroeungtes.app.databinding.ItemIngredientBinding
import com.kroeungtes.app.ui.common.pop

/** Ingredient checklist: tap a row to tick it off; amounts follow the portion stepper. */
class IngredientAdapter(
    private val onCheckedChanged: (checked: Int, total: Int) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<IngredientAdapter.ViewHolder>() {

    private var items: List<Ingredient> = emptyList()
    private val checked = mutableSetOf<Int>()
    private var factor = 1

    fun submit(list: List<Ingredient>) {
        items = list
        checked.clear()
        notifyDataSetChanged()
        onCheckedChanged(0, items.size)
    }

    fun setFactor(value: Int) {
        factor = value
        notifyItemRangeChanged(0, items.size)
    }

    class ViewHolder(val binding: ItemIngredientBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemIngredientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isChecked = position in checked
        holder.binding.apply {
            tvIngredientName.text = item.name
            tvIngredientAmount.text = RecipeText.scale(item.amount, factor)
            tvIngredientName.paintFlags = if (isChecked) tvIngredientName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else tvIngredientName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            root.alpha = if (isChecked) 0.55f else 1f
            imgCheck.setImageResource(if (isChecked) R.drawable.ic_check_circle else R.drawable.ic_circle_outline)

            ImageViewCompat.setImageTintList(imgIngredient, null)
            Glide.with(holder.itemView)
                .load(item.imageUrl)
                .placeholder(R.drawable.ic_restaurant)
                .error(R.drawable.ic_restaurant)
                .into(imgIngredient)

            root.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                if (!checked.add(pos)) checked.remove(pos)
                notifyItemChanged(pos)
                imgCheck.pop()
                onCheckedChanged(checked.size, items.size)
            }
        }
    }

    override fun getItemCount() = items.size
}
