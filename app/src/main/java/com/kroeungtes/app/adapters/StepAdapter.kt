package com.kroeungtes.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kroeungtes.app.R
import com.kroeungtes.app.databinding.ItemStepBinding
import com.kroeungtes.app.ui.common.pop

/** Numbered step cards; tap one to mark it done (number turns into a tick). */
class StepAdapter : RecyclerView.Adapter<StepAdapter.ViewHolder>() {

    private var steps: List<String> = emptyList()
    private val done = mutableSetOf<Int>()

    fun submit(list: List<String>) {
        steps = list
        done.clear()
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemStepBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemStepBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val isDone = position in done
        val ctx = holder.itemView.context
        holder.binding.apply {
            tvStepText.text = steps[position]
            tvStepNumber.text = if (isDone) "✓" else ctx.getString(R.string.step_number, position + 1)
            tvStepNumber.setBackgroundResource(if (isDone) R.drawable.bg_step_done else R.drawable.bg_step_number)
            root.alpha = if (isDone) 0.6f else 1f
            root.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                if (!done.add(pos)) done.remove(pos)
                notifyItemChanged(pos)
                tvStepNumber.pop()
            }
        }
    }

    override fun getItemCount() = steps.size
}
