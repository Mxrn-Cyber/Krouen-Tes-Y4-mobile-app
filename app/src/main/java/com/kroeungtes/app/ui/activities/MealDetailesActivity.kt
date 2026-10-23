package com.kroeungtes.app.ui.activities

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.Glide
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.IngredientAdapter
import com.kroeungtes.app.data.model.Ingredient
import com.kroeungtes.app.data.model.MealDB
import com.kroeungtes.app.data.model.MealDetail
import com.kroeungtes.app.databinding.ActivityMealDetailesBinding
import com.kroeungtes.app.viewmodel.DetailsMVVM
import com.kroeungtes.app.ui.fragments.HomeFragment
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_ID
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_STR
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_THUMB
import com.google.android.material.snackbar.Snackbar

class MealDetailesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMealDetailesBinding
    private lateinit var detailsMVVM: DetailsMVVM
    private var mealId = ""
    private var mealStr = ""
    private var mealThumb = ""
    private var ytUrl = ""
    private lateinit var dtMeal:MealDetail
    private val ingredientAdapter = IngredientAdapter()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detailsMVVM = ViewModelProviders.of(this)[DetailsMVVM::class.java]
        binding = ActivityMealDetailesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvIngredients.adapter = ingredientAdapter
        binding.btnBack.setOnClickListener { finish() }
        binding.btnMore.setOnClickListener { shareRecipe() }

        showLoading()

        getMealInfoFromIntent()
        setUpViewWithMealInformation()
        setFloatingButtonStatues()

        detailsMVVM.getMealById(mealId)

        detailsMVVM.observeMealDetail().observe(this, object : Observer<List<MealDetail>> {
            override fun onChanged(t: List<MealDetail>?) {
                setTextsInViews(t!![0])
                stopLoading()
            }

        })

        binding.imgYoutube.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)))
        }


        binding.btnSave.setOnClickListener {
            if(isMealSavedInDatabase()){
                deleteMeal()
                showSaved(false)
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Removed from Wishlist",
                Snackbar.LENGTH_SHORT).show()
            }else{
                saveMeal()
                showSaved(true)
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Saved to Wishlist",
                    Snackbar.LENGTH_SHORT).show()
            }
        }

    }

    /** Green "Save" button from the slide (where the mockup shows "Follow"). */
    private fun showSaved(saved: Boolean) {
        binding.btnSave.apply {
            text = getString(if (saved) R.string.saved else R.string.save)
            setIconResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
            setBackgroundColor(getColor(if (saved) R.color.brand_green else R.color.brand_splash))
        }
    }

    /** "..." button: share the recipe name + video link with other apps. */
    private fun shareRecipe() {
        val text = if (ytUrl.isNotBlank()) "$mealStr\n$ytUrl" else mealStr
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, mealStr))
    }



    private fun deleteMeal() {
        detailsMVVM.deleteMealById(mealId)
    }

    private fun setFloatingButtonStatues() {
        showSaved(isMealSavedInDatabase())
    }

    private fun isMealSavedInDatabase(): Boolean {
       return detailsMVVM.isMealSavedInDatabase(mealId)
    }

    private fun saveMeal() {
        val meal = MealDB(dtMeal.idMeal.toInt(),
            dtMeal.strMeal,
            dtMeal.strArea,
            dtMeal.strCategory,
            dtMeal.strInstructions,
            dtMeal.strMealThumb,
            dtMeal.strYoutube)

        detailsMVVM.insertMeal(meal)
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.visibility = View.GONE
        binding.imgYoutube.visibility = View.INVISIBLE
    }


    private fun stopLoading() {
        binding.progressBar.visibility = View.INVISIBLE
        binding.btnSave.visibility = View.VISIBLE

    }

    private fun setTextsInViews(meal: MealDetail) {
        this.dtMeal = meal
        ytUrl = meal.strYoutube.orEmpty()
        binding.apply {
            tvContent.text = meal.strInstructions
            tvCategoryInfo.text = meal.strCategory
            tvAreaInfo.text = meal.strArea
            tvAuthorPlace.text = meal.strArea
            tvCategoryInfo.visibility = View.VISIBLE
            tvAreaInfo.visibility = View.VISIBLE
            imgYoutube.visibility = if (ytUrl.isBlank()) View.GONE else View.VISIBLE
        }
        ingredientAdapter.submit(Ingredient.fromMeal(meal))
    }


    private fun setUpViewWithMealInformation() {
        binding.apply {
            tvTitle.text = getString(R.string.how_to_make, mealStr)
            Glide.with(applicationContext)
                .load(mealThumb)
                .placeholder(R.drawable.mealtest)
                .into(imgMealDetail)
        }

    }

    private fun getMealInfoFromIntent() {
        val tempIntent = intent

        this.mealId = tempIntent.getStringExtra(MEAL_ID)!!
        this.mealStr = tempIntent.getStringExtra(MEAL_STR)!!
        this.mealThumb = tempIntent.getStringExtra(MEAL_THUMB)!!
    }

}