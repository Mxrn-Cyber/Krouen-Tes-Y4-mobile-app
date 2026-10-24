package com.kroeungtes.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.kroeungtes.app.R
import com.kroeungtes.app.data.local.KhmerRecipes
import com.kroeungtes.app.data.local.MealsDatabase
import com.kroeungtes.app.data.local.Repository
import com.kroeungtes.app.data.model.MealDB
import com.kroeungtes.app.data.model.MealDetail
import com.kroeungtes.app.data.model.RandomMealResponse
import com.kroeungtes.app.data.remote.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DetailsMVVM(application: Application) : AndroidViewModel(application) {
    private val mutableMealDetail = MutableLiveData<List<MealDetail>>()
    private val mutableMealBottomSheet = MutableLiveData<List<MealDetail>>()
    private  var allMeals: LiveData<List<MealDB>>
    private  var repository: Repository

    init {
        val mealDao = MealsDatabase.getInstance(application).dao()
        repository = Repository(mealDao)
        allMeals = repository.mealList
    }

    fun getAllSavedMeals() {
        viewModelScope.launch(Dispatchers.Main) {
        }
    }

    fun insertMeal(meal: MealDB) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertFavoriteMeal(meal)
            withContext(Dispatchers.Main) {
            }
        }
    }

    /**
     * Save straight from a recipe card: store what we have right away,
     * then replace it with the full details (category, area, video...) when they arrive.
     */
    fun quickSave(id: String, name: String, thumb: String?) {
        val mealId = id.toIntOrNull() ?: return
        if (mealId < 0) {
            // Built-in Khmer recipe: everything is already on the phone
            val k = KhmerRecipes.find(getApplication<Application>(), mealId)
            insertMeal(MealDB(mealId, k?.name ?: name, getApplication<Application>().getString(R.string.khmer_region),
                k?.category.orEmpty(), k?.steps?.joinToString("\n").orEmpty(), k?.photoUrl ?: thumb.orEmpty(), ""))
            return
        }
        insertMeal(MealDB(mealId, name, "", "", "", thumb.orEmpty(), ""))
        RetrofitInstance.foodApi.getMealById(id).enqueue(object : Callback<RandomMealResponse> {
            override fun onResponse(call: Call<RandomMealResponse>, response: Response<RandomMealResponse>) {
                val d = response.body()?.meals?.firstOrNull() ?: return
                // Only fill in details if the user didn't unsave it in the meantime
                viewModelScope.launch(Dispatchers.IO) {
                    if (repository.getMealById(id) != null) {
                        repository.insertFavoriteMeal(MealDB(mealId, d.strMeal, d.strArea.orEmpty(), d.strCategory.orEmpty(),
                            d.strInstructions.orEmpty(), d.strMealThumb.orEmpty(), d.strYoutube.orEmpty()))
                    }
                }
            }

            override fun onFailure(call: Call<RandomMealResponse>, t: Throwable) {
                Log.e(TAG, t.message.toString())
            }
        })
    }

    fun deleteMeal(meal:MealDB) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteMeal(meal)
    }

    fun getMealById(id: String) {
        RetrofitInstance.foodApi.getMealById(id).enqueue(object : Callback<RandomMealResponse> {
            override fun onResponse(call: Call<RandomMealResponse>, response: Response<RandomMealResponse>) {
                mutableMealDetail.value = response.body()?.meals ?: return
            }

            override fun onFailure(call: Call<RandomMealResponse>, t: Throwable) {
                Log.e(TAG, t.message.toString())
            }

        })
    }

    fun isMealSavedInDatabase(mealId: String): Boolean {
        var meal: MealDB? = null
        runBlocking(Dispatchers.IO) {
            meal = repository.getMealById(mealId)
        }
        if (meal == null)
            return false
        return true

    }

    fun deleteMealById(mealId:String){
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMealById(mealId)
        }
    }

    fun getMealByIdBottomSheet(id: String) {
        RetrofitInstance.foodApi.getMealById(id).enqueue(object : Callback<RandomMealResponse> {
            override fun onResponse(call: Call<RandomMealResponse>, response: Response<RandomMealResponse>) {
                mutableMealBottomSheet.value = response.body()?.meals ?: return
            }

            override fun onFailure(call: Call<RandomMealResponse>, t: Throwable) {
                Log.e(TAG, t.message.toString())
            }

        })
    }

    fun observeMealDetail(): LiveData<List<MealDetail>> {
        return mutableMealDetail
    }

    /** Clear after showing so the sheet doesn't reopen when the screen is rebuilt. */
    fun consumeBottomSheet() {
        mutableMealBottomSheet.value = null
    }

    fun observeMealBottomSheet(): LiveData<List<MealDetail>> {
        return mutableMealBottomSheet
    }

    fun observeSaveMeal(): LiveData<List<MealDB>> {
        return allMeals
    }
}