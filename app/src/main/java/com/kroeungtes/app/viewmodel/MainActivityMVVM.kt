package com.kroeungtes.app.viewmodel

import android.util.Log
import androidx.lifecycle.*
import com.kroeungtes.app.data.cloud.CloudContent
import com.kroeungtes.app.data.cloud.withoutHiddenDishes
import com.kroeungtes.app.data.model.*
import com.kroeungtes.app.data.remote.RetrofitInstance
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

const val TAG = "MainMVVM"

// TheMealDB groups for the Home rows until admins choose others (dashboard → Home page)
const val TRENDING_CATEGORY = "Beef"
const val RECENT_CATEGORY = "Chicken"

class MainFragMVVM: ViewModel() {
    private val mutableCategory = MutableLiveData<CategoryResponse>()
    private val mutableRandomMeal = MutableLiveData<RandomMealResponse>()
    private val mutableMealsByCategory = MutableLiveData<MealsResponse>()
    private val mutableRecentMeals = MutableLiveData<MealsResponse>()

    /** Selected chip on Home. Declared before init{} so it is set when refresh() runs. */
    var trendingCategory = CloudContent.homeNow().trendingCategory
        private set

    /** TheMealDB group of the "Recent recipe" row. */
    var recentCategory = CloudContent.homeNow().recentCategory
        private set

    /** The person tapped a chip, so admin changes to the Trending row don't override it. */
    private var userPicked = false

    /** Admins changed the Home page: follow it without a restart. */
    private val homeObserver = Observer<CloudContent.HomeSettings> { home ->
        if (!userPicked && home.trendingCategory != trendingCategory) {
            trendingCategory = home.trendingCategory
            getMealsByCategory(trendingCategory)
        }
        if (home.recentCategory != recentCategory) {
            recentCategory = home.recentCategory
            getRecentMeals(recentCategory)
        }
    }

    // World dishes admins hid never show
    private val visibleMealsByCategory = mutableMealsByCategory.withoutHiddenDishes { response, hidden ->
        response.copy(meals = response.meals.orEmpty().filter { it.idMeal !in hidden })
    }
    private val visibleRecentMeals = mutableRecentMeals.withoutHiddenDishes { response, hidden ->
        response.copy(meals = response.meals.orEmpty().filter { it.idMeal !in hidden })
    }

    init {
        refresh()
        CloudContent.home.observeForever(homeObserver)
    }

    override fun onCleared() {
        CloudContent.home.removeObserver(homeObserver)
        super.onCleared()
    }


    private fun getAllCategories() {
        RetrofitInstance.foodApi.getCategories().enqueue(object : Callback<CategoryResponse> {
            override fun onResponse(call: Call<CategoryResponse>, response: Response<CategoryResponse>) {
                mutableCategory.value = response.body() ?: return
            }

            override fun onFailure(call: Call<CategoryResponse>, t: Throwable) {
                Log.d(TAG, t.message.toString())
            }
        })
    }

    private fun getRandomMeal() {
        RetrofitInstance.foodApi.getRandomMeal().enqueue(object : Callback<RandomMealResponse> {
            override fun onResponse(call: Call<RandomMealResponse>, response: Response<RandomMealResponse>) {
                mutableRandomMeal.value = response.body() ?: return
            }

            override fun onFailure(call: Call<RandomMealResponse>, t: Throwable) {
                Log.e(TAG, t.message.toString())
            }

        })
    }

    /** Pull-to-refresh reloads everything. */
    fun refresh() {
        getAllCategories()
        getMealsByCategory(trendingCategory)
        getRecentMeals(recentCategory)
    }

    /** A chip on Home was tapped. */
    fun loadTrending(category: String) {
        userPicked = true
        trendingCategory = category
        getMealsByCategory(category)
    }

    private fun getMealsByCategory(category:String) {

        RetrofitInstance.foodApi.getMealsByCategory(category).enqueue(object : Callback<MealsResponse> {
            override fun onResponse(call: Call<MealsResponse>, response: Response<MealsResponse>) {
                // A newer choice (chip or admin) is on its way: drop this answer
                if (category != trendingCategory) return
                mutableMealsByCategory.value = response.body() ?: MealsResponse(emptyList())
            }

            override fun onFailure(call: Call<MealsResponse>, t: Throwable) {
                Log.e(TAG, t.message.toString())
                if (category != trendingCategory) return
                mutableMealsByCategory.value = MealsResponse(emptyList())
            }

        })
    }

    private fun getRecentMeals(category: String) {
        RetrofitInstance.foodApi.getMealsByCategory(category).enqueue(object : Callback<MealsResponse> {
            override fun onResponse(call: Call<MealsResponse>, response: Response<MealsResponse>) {
                if (category != recentCategory) return
                mutableRecentMeals.value = response.body() ?: MealsResponse(emptyList())
            }

            override fun onFailure(call: Call<MealsResponse>, t: Throwable) {
                Log.e(TAG, t.message.toString())
                if (category != recentCategory) return
                mutableRecentMeals.value = MealsResponse(emptyList())
            }
        })
    }

    fun observeRecentMeals(): LiveData<MealsResponse> = visibleRecentMeals

    fun observeMealByCategory(): LiveData<MealsResponse> {
        return visibleMealsByCategory
    }

    fun observeRandomMeal(): LiveData<RandomMealResponse> {
        return mutableRandomMeal
    }

    fun observeCategories(): LiveData<CategoryResponse> {
        return mutableCategory
    }

}