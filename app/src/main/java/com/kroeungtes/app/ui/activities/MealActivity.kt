package com.kroeungtes.app.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.RecipeCardAdapter
import com.kroeungtes.app.data.cloud.CloudContent
import com.kroeungtes.app.data.local.KhmerRecipes
import com.kroeungtes.app.data.model.RecipeCardItem
import com.kroeungtes.app.databinding.ActivityCategoriesBinding
import com.kroeungtes.app.ui.common.EdgeToEdge
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.common.padForSystemBars
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.CATEGORY_NAME
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_ID
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_STR
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_THUMB
import com.kroeungtes.app.viewmodel.DetailsMVVM

/** "See all" screen: Featured dishes, the newest recipes, or every recipe in one category. */
class MealActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCategoriesBinding
    private lateinit var detailsMvvm: DetailsMVVM
    private lateinit var adapter: RecipeCardAdapter
    private var key = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.light(this)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForSystemBars()
        detailsMvvm = ViewModelProvider(this)[DetailsMVVM::class.java]
        key = intent.getStringExtra(CATEGORY_NAME).orEmpty()

        binding.btnListBack.setOnClickListener { finish() }
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
        // Follows changes admins make while this list is open
        KhmerRecipes.changes.observe(this) { show() }
        CloudContent.categories.observe(this) { show() }
        CloudContent.home.observe(this) { show() }
    }

    private fun show() {
        val khmer = KhmerRecipes.isKhmerLanguage()
        val (title, recipes) = when (key) {
            KhmerRecipes.LIST_KEY -> getString(R.string.khmer_dishes) to CloudContent.homeKhmerDishes(this)
            KhmerRecipes.NEWEST_KEY -> getString(R.string.recent_recipe) to KhmerRecipes.newest(this)
            else -> {
                val category = CloudContent.currentCategories().firstOrNull { it.id == key }
                (category?.chipLabel(khmer) ?: key) to KhmerRecipes.inCategory(this, key)
            }
        }
        val first = adapter.itemCount == 0
        binding.tvListTitle.text = title
        adapter.submit(recipes.map { RecipeCardItem.from(it) })
        if (first) binding.mealRecyclerview.scheduleLayoutAnimation()
        binding.tvCategoryCount.text = resources.getQuantityString(R.plurals.recipe_count, recipes.size, recipes.size)
    }

    private fun open(item: RecipeCardItem) {
        Motion.openRecipe(this, Intent(this, MealDetailesActivity::class.java).apply {
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
