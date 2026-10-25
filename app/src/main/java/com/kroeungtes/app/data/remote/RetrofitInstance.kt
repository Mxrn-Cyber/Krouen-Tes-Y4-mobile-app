package com.kroeungtes.app.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {

    private const val MEAL_DB = "https://www.themealdb.com/api/json/v1/1/"
    private const val COCKTAIL_DB = "https://www.thecocktaildb.com/api/json/v1/1/"

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val foodApi: FoodApi by lazy { retrofit(MEAL_DB).create(FoodApi::class.java) }

    val mealBrowseApi: MealBrowseApi by lazy { retrofit(MEAL_DB).create(MealBrowseApi::class.java) }

    val cocktailApi: CocktailApi by lazy { retrofit(COCKTAIL_DB).create(CocktailApi::class.java) }
}
