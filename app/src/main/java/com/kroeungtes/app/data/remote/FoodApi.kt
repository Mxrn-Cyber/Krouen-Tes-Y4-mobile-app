package com.kroeungtes.app.data.remote

import com.kroeungtes.app.data.model.*
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query


interface FoodApi {
    @GET("categories.php")
    fun getCategories(): Call<CategoryResponse>

    /** Meals in a category, e.g. "Beef", "Seafood" (used by Home chips, Category tab, See all). */
    @GET("filter.php")
    fun getMealsByCategory(@Query("c") category: String): Call<MealsResponse>

    /** Meals that use an ingredient, e.g. "chicken". */
    @GET("filter.php")
    fun getMealsByIngredient(@Query("i") ingredient: String): Call<MealsResponse>

    @GET ("random.php")
    fun getRandomMeal():Call<RandomMealResponse>

    @GET("lookup.php?")
    fun getMealById(@Query("i") id:String):Call<RandomMealResponse>

    @GET("search.php?")
    fun getMealByName(@Query("s") s:String):Call<RandomMealResponse>
}