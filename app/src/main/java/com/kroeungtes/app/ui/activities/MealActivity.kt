package com.kroeungtes.app.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.RecipeCardAdapter
import com.kroeungtes.app.data.local.KhmerRecipes
import com.kroeungtes.app.data.model.RecipeCardItem
import com.kroeungtes.app.databinding.ActivityCategoriesBinding
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.CATEGORY_NAME
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_ID
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_STR
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_THUMB
import com.kroeungtes.app.viewmodel.DetailsMVVM
import com.kroeungtes.app.viewmodel.MealActivityMVVM

/** "See all" screen: the built-in Khmer dishes, or every recipe in one TheMealDB category. */
class MealActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCategoriesBinding
    private lateinit var listMvvm: MealActivityMVVM
    private lateinit var detailsMvvm: DetailsMVVM
    private lateinit var adapter: RecipeCardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        listMvvm = ViewModelProviders.of(this)[MealActivityMVVM::class.java]
        detailsMvvm = ViewModelProviders.of(this)[DetailsMVVM::class.java]

        val category = intent.getStringExtra(CATEGORY_NAME).orEmpty()
        val isKhmer = category == KhmerRecipes.LIST_KEY

        binding.btnListBack.setOnClickListener { finish() }
        binding.tvListTitle.text = if (isKhmer) getString(R.string.khmer_dishes) else category

        adapter = RecipeCardAdapter(
            onClick = { open(it) },
            onSaveToggle = { item, saved ->
                if (saved) detailsMvvm.quickSave(item.id, item.name, item.image) else detailsMvvm.deleteMealById(item.id)
            }
        )
        binding.mealRecyclerview.layoutManager =
            GridLayoutManager(this, resources.getInteger(R.integer.meal_list_columns), GridLayoutManager.VERTICAL, false)
        binding.mealRecyclerview.adapter = adapter

        detailsMvvm.observeSaveMeal().observe(this) { saved ->
            adapter.setSavedIds(saved.orEmpty().map { it.mealId.toString() }.toSet())
        }

        if (isKhmer) {
            show(KhmerRecipes.all(this).map { RecipeCardItem.from(it) })
        } else {
            binding.listLoading.visibility = View.VISIBLE
            listMvvm.getMealsByCategory(category)
            listMvvm.observeMeal().observe(this) { meals ->
                binding.listLoading.visibility = View.GONE
                if (meals.isNullOrEmpty()) {
                    Toast.makeText(this, getString(R.string.no_results, category), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    val subtitle = getString(R.string.recipe_with, category)
                    show(meals.map { RecipeCardItem.from(it, subtitle) })
                }
            }
        }
    }

    private fun show(items: List<RecipeCardItem>) {
        adapter.submit(items)
        binding.mealRecyclerview.scheduleLayoutAnimation()
        binding.tvCategoryCount.text = resources.getQuantityString(R.plurals.recipe_count, items.size, items.size)
    }

    private fun open(item: RecipeCardItem) {
        Motion.open(this, Intent(this, MealDetailesActivity::class.java).apply {
            putExtra(MEAL_ID, item.id)
            putExtra(MEAL_STR, item.name)
            putExtra(MEAL_THUMB, item.image.orEmpty())
        })
    }

    override fun finish() {
        super.finish()
        Motion.closeToRight(this)
    }
}
