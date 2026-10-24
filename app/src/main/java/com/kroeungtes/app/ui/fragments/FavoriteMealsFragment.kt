package com.kroeungtes.app.ui.fragments

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.RecipeCardAdapter
import com.kroeungtes.app.data.model.MealDB
import com.kroeungtes.app.data.model.RecipeCardItem
import com.kroeungtes.app.databinding.FragmentFavoriteMealsBinding
import com.kroeungtes.app.ui.activities.MealDetailesActivity
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.common.aboveBar
import com.kroeungtes.app.ui.common.fadeVisible
import com.kroeungtes.app.ui.dialogs.MealBottomDialog
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.CATEGORY_NAME
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_AREA
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_ID
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_NAME
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_STR
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_THUMB
import com.kroeungtes.app.viewmodel.DetailsMVVM
import kotlin.math.abs

/** Wishlist grid: swipe a card left/right to remove (with Undo), or tap its bookmark. */
class FavoriteMealsFragment : Fragment() {

    private var _binding: FragmentFavoriteMealsBinding? = null
    private val binding get() = _binding!!
    private lateinit var detailsMVVM: DetailsMVVM
    private lateinit var adapter: RecipeCardAdapter
    private var savedMeals: List<MealDB> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detailsMVVM = ViewModelProviders.of(this)[DetailsMVVM::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFavoriteMealsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = RecipeCardAdapter(
            onClick = { openRecipe(it) },
            onLongClick = { detailsMVVM.getMealByIdBottomSheet(it.id) },
            onSaveToggle = { item, saved -> if (!saved) removeWithUndo(item.id) }
        )
        binding.favRecView.layoutManager =
            GridLayoutManager(context, resources.getInteger(R.integer.wishlist_columns), GridLayoutManager.VERTICAL, false)
        binding.favRecView.adapter = adapter
        attachSwipeToRemove()

        detailsMVVM.observeSaveMeal().observe(viewLifecycleOwner) { saved ->
            val firstLoad = savedMeals.isEmpty()
            savedMeals = saved.orEmpty().reversed()
            adapter.submit(savedMeals.map { RecipeCardItem.from(it, getString(R.string.your_wishlist)) })
            adapter.setSavedIds(savedMeals.map { it.mealId.toString() }.toSet())
            if (firstLoad) binding.favRecView.scheduleLayoutAnimation()
            binding.tvFavEmpty.fadeVisible(savedMeals.isEmpty())
        }
        observeBottomDialog()
    }

    private fun removeWithUndo(id: String) {
        val meal = savedMeals.firstOrNull { it.mealId.toString() == id } ?: return
        detailsMVVM.deleteMeal(meal)
        Snackbar.make(requireView(), R.string.removed_from_wishlist, Snackbar.LENGTH_LONG).aboveBar()
            .setAction(R.string.undo) { detailsMVVM.insertMeal(meal) }
            .show()
    }

    /** Swipe either way: a red "delete" background follows the card. */
    private fun attachSwipeToRemove() {
        val red = ContextCompat.getColor(requireContext(), R.color.danger)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red }
        val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)
        val radius = resources.getDimension(R.dimen.corner_card)

        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION) return
                removeWithUndo(adapter.itemAt(position).id)
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val item = vh.itemView
                if (dX != 0f) {
                    val rect = if (dX > 0) RectF(item.left.toFloat(), item.top.toFloat(), item.left + dX, item.bottom.toFloat())
                    else RectF(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat())
                    c.drawRoundRect(rect, radius, radius, paint)
                    icon?.let {
                        val size = (item.height / 5).coerceAtMost(72)
                        val top = item.top + (item.height - size) / 2
                        val left = if (dX > 0) item.left + size / 2 else item.right - size - size / 2
                        it.setBounds(left, top, left + size, top + size)
                        it.alpha = (255 * (abs(dX) / item.width).coerceIn(0.3f, 1f)).toInt()
                        it.draw(c)
                    }
                }
                item.alpha = 1f - (abs(dX) / item.width) * 0.4f
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                vh.itemView.alpha = 1f
                super.clearView(rv, vh)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.favRecView)
    }

    private fun observeBottomDialog() {
        detailsMVVM.observeMealBottomSheet().observe(viewLifecycleOwner) { details ->
            val meal = details?.firstOrNull() ?: return@observe
            MealBottomDialog().apply {
                arguments = Bundle().apply {
                    putString(CATEGORY_NAME, meal.strCategory)
                    putString(MEAL_AREA, meal.strArea)
                    putString(MEAL_NAME, meal.strMeal)
                    putString(MEAL_THUMB, meal.strMealThumb)
                    putString(MEAL_ID, meal.idMeal)
                }
            }.show(childFragmentManager, "Favorite bottom dialog")
            detailsMVVM.consumeBottomSheet()
        }
    }

    private fun openRecipe(item: RecipeCardItem) {
        Motion.openRecipe(requireActivity(), Intent(context, MealDetailesActivity::class.java).apply {
            putExtra(MEAL_ID, item.id)
            putExtra(MEAL_STR, item.name)
            putExtra(MEAL_THUMB, item.image.orEmpty())
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
