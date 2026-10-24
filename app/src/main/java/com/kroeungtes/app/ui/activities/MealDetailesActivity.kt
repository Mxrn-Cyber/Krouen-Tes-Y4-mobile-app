package com.kroeungtes.app.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.IngredientAdapter
import com.kroeungtes.app.adapters.StepAdapter
import com.kroeungtes.app.data.local.MyRecipeStore
import com.kroeungtes.app.data.local.ProfileStore
import com.kroeungtes.app.data.model.Ingredient
import com.kroeungtes.app.data.model.MealDB
import com.kroeungtes.app.data.model.MealDetail
import com.kroeungtes.app.data.model.MyRecipe
import com.kroeungtes.app.data.model.RecipeText
import com.kroeungtes.app.databinding.ActivityMealDetailesBinding
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.common.pop
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_ID
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_STR
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_THUMB
import com.kroeungtes.app.viewmodel.DetailsMVVM

/**
 * Recipe page. Works for TheMealDB recipes (loaded by id) and for recipes the user made
 * (ids starting with "local-", read from MyRecipeStore).
 */
class MealDetailesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMealDetailesBinding
    private lateinit var detailsMVVM: DetailsMVVM
    private var mealId = ""
    private var mealStr = ""
    private var mealThumb = ""
    private var ytUrl = ""
    private var dtMeal: MealDetail? = null
    private var portion = 1

    private val stepAdapter = StepAdapter()
    private val ingredientAdapter = IngredientAdapter { checked, total ->
        binding.tvIngredientProgress.text =
            if (checked == 0) getString(R.string.tap_to_tick) else getString(R.string.ingredients_ready, checked, total)
    }

    private val isLocal get() = mealId.startsWith(MyRecipeStore.LOCAL_PREFIX)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detailsMVVM = ViewModelProviders.of(this)[DetailsMVVM::class.java]
        binding = ActivityMealDetailesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mealId = intent.getStringExtra(MEAL_ID).orEmpty()
        mealStr = intent.getStringExtra(MEAL_STR).orEmpty()
        mealThumb = intent.getStringExtra(MEAL_THUMB).orEmpty()

        binding.rvIngredients.adapter = ingredientAdapter
        binding.rvSteps.adapter = stepAdapter
        binding.btnBack.setOnClickListener { onBackPressed() }
        binding.btnMore.setOnClickListener { showMoreMenu(it) }
        binding.btnPortionMinus.setOnClickListener { changePortion(-1) }
        binding.btnPortionPlus.setOnClickListener { changePortion(+1) }
        binding.imgYoutube.setOnClickListener {
            if (ytUrl.isNotBlank()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)))
        }
        updatePortionLabel()
        showHeader(mealStr, mealThumb)

        if (isLocal) showLocalRecipe() else loadFromApi()
    }

    // ---------- TheMealDB recipe ----------

    private fun loadFromApi() {
        binding.progressBar.visibility = View.VISIBLE
        showSaved(isMealSavedInDatabase())
        detailsMVVM.getMealById(mealId)
        detailsMVVM.observeMealDetail().observe(this) { list ->
            val meal = list?.firstOrNull() ?: return@observe
            dtMeal = meal
            ytUrl = meal.strYoutube.orEmpty()
            showInfo(meal.strCategory.orEmpty(), meal.strArea.orEmpty())
            ingredientAdapter.submit(Ingredient.fromMeal(meal))
            stepAdapter.submit(RecipeText.steps(meal.strInstructions))
            binding.rvIngredients.scheduleLayoutAnimation()
            binding.imgYoutube.visibility = if (ytUrl.isBlank()) View.GONE else View.VISIBLE
            binding.progressBar.visibility = View.GONE
            binding.btnSave.visibility = View.VISIBLE
        }

        binding.btnSave.setOnClickListener {
            val nowSaved = !isMealSavedInDatabase()
            if (nowSaved) saveMeal() else detailsMVVM.deleteMealById(mealId)
            showSaved(nowSaved)
            binding.btnSave.pop()
            Snackbar.make(binding.root,
                if (nowSaved) R.string.saved_to_wishlist else R.string.removed_from_wishlist,
                Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun saveMeal() {
        val d = dtMeal
        if (d == null) {
            detailsMVVM.quickSave(mealId, mealStr, mealThumb)
            return
        }
        detailsMVVM.insertMeal(
            MealDB(d.idMeal.toInt(), d.strMeal, d.strArea.orEmpty(), d.strCategory.orEmpty(),
                d.strInstructions.orEmpty(), d.strMealThumb.orEmpty(), d.strYoutube.orEmpty())
        )
    }

    private fun isMealSavedInDatabase(): Boolean = detailsMVVM.isMealSavedInDatabase(mealId)

    /** Green "Save" button (the slide shows "Follow" here). */
    private fun showSaved(saved: Boolean) {
        binding.btnSave.apply {
            text = getString(if (saved) R.string.saved else R.string.save)
            setIconResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
            setBackgroundColor(getColor(if (saved) R.color.brand_green else R.color.brand_splash))
        }
    }

    // ---------- Recipe made by the user ----------

    private fun showLocalRecipe() {
        val recipe: MyRecipe = MyRecipeStore.find(this, mealId) ?: run { finish(); return }
        mealStr = recipe.title
        ytUrl = recipe.videoUrl.orEmpty()
        showHeader(recipe.title, recipe.photoPath.orEmpty())
        showInfo(getString(R.string.serves_value, recipe.serves), getString(R.string.cook_time_value, recipe.cookMinutes))
        binding.tvAreaInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        val profile = ProfileStore.load(this, getString(R.string.default_author))
        binding.tvAuthorName.text = profile.name
        binding.tvAuthorPlace.text = getString(R.string.my_recipe)
        profile.avatarPath?.let { Glide.with(this).load(it).into(binding.imgAuthor) }
        ingredientAdapter.submit(recipe.ingredients)
        stepAdapter.submit(recipe.steps)
        binding.imgYoutube.visibility = if (ytUrl.isBlank()) View.GONE else View.VISIBLE
        binding.btnSave.visibility = View.GONE
    }

    // ---------- Shared UI ----------

    private fun showHeader(name: String, image: String) {
        binding.tvTitle.text = getString(R.string.how_to_make, name)
        binding.collapsingToolbar.title = name
        Glide.with(this).load(image).placeholder(R.drawable.placeholder_image).error(R.drawable.placeholder_image)
            .into(binding.imgMealDetail)
    }

    private fun showInfo(first: String, second: String) {
        binding.tvCategoryInfo.text = first
        binding.tvAreaInfo.text = second
        binding.tvAuthorPlace.text = second
        binding.tvCategoryInfo.visibility = if (first.isBlank()) View.GONE else View.VISIBLE
        binding.tvAreaInfo.visibility = if (second.isBlank()) View.GONE else View.VISIBLE
    }

    private fun changePortion(delta: Int) {
        val next = (portion + delta).coerceIn(1, 10)
        if (next == portion) return
        portion = next
        updatePortionLabel()
        binding.tvPortion.pop()
        ingredientAdapter.setFactor(portion)
    }

    private fun updatePortionLabel() {
        binding.tvPortion.text = getString(R.string.portion_value, portion)
        binding.btnPortionMinus.isEnabled = portion > 1
        binding.btnPortionMinus.alpha = if (portion > 1) 1f else 0.35f
    }

    /** "…" menu: share, and delete for the user's own recipes. */
    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.share_recipe)
            if (isLocal) menu.add(0, 2, 1, R.string.delete_recipe)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> shareRecipe()
                    2 -> {
                        MyRecipeStore.delete(this@MealDetailesActivity, mealId)
                        Snackbar.make(binding.root, R.string.recipe_deleted, Snackbar.LENGTH_SHORT).show()
                        finish()
                    }
                }
                true
            }
        }.show()
    }

    private fun shareRecipe() {
        val text = if (ytUrl.isNotBlank()) "$mealStr\n$ytUrl" else mealStr
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.share_recipe)))
    }

    override fun finish() {
        super.finish()
        Motion.closeToRight(this)
    }
}
