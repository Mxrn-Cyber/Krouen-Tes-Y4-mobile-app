package com.kroeungtes.app.viewmodel

import android.util.Log
import androidx.lifecycle.*
import com.kroeungtes.app.data.model.*
import com.kroeungtes.app.data.remote.RetrofitInstance
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

const val TAG = "MainMVVM"

// TheMealDB categories for the Home rows (Khmer recipes come from Firestore in phase 2)
const val TRENDING_CATEGORY = "Beef"
const val RECENT_CATEGORY = "Chicken"

class MainFragMVVM: ViewModel() {
    private val mutableCategory = MutableLiveData<CategoryResponse>()
    private val mutableRandomMeal = MutableLiveData<RandomMealResponse>()
    private val mutableMealsByCategory = MutableLiveData<MealsResponse>()
    private val mutableRecentMeals = MutableLiveData<MealsResponse>()

    /** Selected chip on Home. Declared before init{} so it is set when refresh() runs. */
    var trendingCategory = TRENDING_CATEGORY
        private set

    init {
        refresh()
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
        loadTrending(trendingCategory)
        getRecentMeals(RECENT_CATEGORY)
    }

    fun loadTrending(category: String) {
        trendingCategory = category
        getMealsByCategory(category)
    }

    private fun getMealsByCategory(category:String) {

        RetrofitInstance.foodApi.getMealsByCategory(category).enqueue(object : Callback<MealsResponse> {
            override fun onResponse(call: Call<MealsResponse>, response: Response<MealsResponse>) {
                mutableMealsByCategory.value = response.body() ?: MealsResponse(emptyList())
            }

            override fun onFailure(call: Call<MealsResponse>, t: Throwable) {
                Log.e(TAG, t.message.toString())
                mutableMealsByCategory.value = MealsResponse(emptyList())
            }

        })
    }

    private fun getRecentMeals(category: String) {
        RetrofitInstance.foodApi.getMealsByCategory(category).enqueue(object : Callback<MealsResponse> {
            override fun onResponse(call: Call<MealsResponse>, response: Response<MealsResponse>) {
                mutableRecentMeals.value = response.body() ?: MealsResponse(emptyList())
            }

            override fun onFailure(call: Call<MealsResponse>, t: Throwable) {
                Log.e(TAG, t.message.toString())
                mutableRecentMeals.value = MealsResponse(emptyList())
            }
        })
    }

    fun observeRecentMeals(): LiveData<MealsResponse> = mutableRecentMeals

    fun observeMealByCategory(): LiveData<MealsResponse> {
        return mutableMealsByCategory
    }

    fun observeRandomMeal(): LiveData<RandomMealResponse> {
        return mutableRandomMeal
    }

    fun observeCategories(): LiveData<CategoryResponse> {
        return mutableCategory
    }

}