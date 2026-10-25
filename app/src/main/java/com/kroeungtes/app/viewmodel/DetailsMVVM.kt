package com.kroeungtes.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.kroeungtes.app.R
import com.kroeungtes.app.data.local.KhmerRecipes
import com.kroeungtes.app.data.local.MealsDatabase
import com.kroeungtes.app.data.local.Repository
import com.kroeungtes.app.data.model.KhmerRecipe
import com.kroeungtes.app.data.model.MealDB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** The Wishlist (saved recipes, stored on the phone with Room). */
class DetailsMVVM(application: Application) : AndroidViewModel(application) {
    private val repository: Repository
    private val allMeals: LiveData<List<MealDB>>

    /** Saved recipes that are still published in the dashboard (newest recipes first). */
    val savedRecipes: LiveData<List<KhmerRecipe>>

    init {
        val mealDao = MealsDatabase.getInstance(application).dao()
        repository = Repository(mealDao)
        allMeals = repository.mealList
        // Dishes saved from the old world-food lists can't be opened any more
        viewModelScope.launch(Dispatchers.IO) { repository.deleteWorldDishes() }

        val saved = MediatorLiveData<List<KhmerRecipe>>()
        val update = {
            val app = getApplication<Application>()
            // Rows come sorted by id; dashboard ids go down for newer recipes
            saved.value = allMeals.value.orEmpty().reversed().mapNotNull { KhmerRecipes.find(app, it.mealId) }
        }
        saved.addSource(allMeals) { update() }
        saved.addSource(KhmerRecipes.changes) { update() }
        savedRecipes = saved
    }

    fun insertMeal(meal: MealDB) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertFavoriteMeal(meal)
        }
    }

    /** Save straight from a recipe card. */
    fun quickSave(id: String, name: String, thumb: String?) {
        val mealId = id.toIntOrNull()?.takeIf { it < 0 } ?: return
        val app = getApplication<Application>()
        val k = KhmerRecipes.find(app, mealId)
        insertMeal(MealDB(mealId, k?.name ?: name, app.getString(R.string.khmer_region),
            k?.category.orEmpty(), k?.steps?.joinToString("\n").orEmpty(), k?.photoUrl ?: thumb.orEmpty(), ""))
    }

    fun deleteMeal(meal: MealDB) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteMeal(meal)
    }

    fun isMealSavedInDatabase(mealId: String): Boolean {
        var meal: MealDB? = null
        runBlocking(Dispatchers.IO) {
            meal = repository.getMealById(mealId)
        }
        return meal != null
    }

    fun deleteMealById(mealId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMealById(mealId)
        }
    }

    /** Every saved row (ids only matter: used to fill the bookmark icons). */
    fun observeSaveMeal(): LiveData<List<MealDB>> = allMeals
}
