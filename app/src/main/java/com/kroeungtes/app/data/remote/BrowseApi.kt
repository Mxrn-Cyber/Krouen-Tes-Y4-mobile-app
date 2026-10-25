package com.kroeungtes.app.data.remote

import com.kroeungtes.app.data.model.DrinksResponse
import com.kroeungtes.app.data.model.MealDetail
import retrofit2.http.GET
import retrofit2.http.Query

/** TheMealDB calls used by the Categories screen (suspend = called from coroutines). */
interface MealBrowseApi {
    /** Dishes in a TheMealDB category, e.g. "Seafood" (id, name and photo only). */
    @GET("filter.php")
    suspend fun byCategory(@Query("c") category: String): MealListResponse

    /** Dishes from one country, e.g. "Thai" (id, name and photo only). */
    @GET("filter.php")
    suspend fun byArea(@Query("a") area: String): MealListResponse

    /** Dishes whose name contains [query], with full details. */
    @GET("search.php")
    suspend fun search(@Query("s") query: String): MealSearchResponse
}

/** TheCocktailDB (test key "1" — a paid key is needed before the app is published). */
interface CocktailApi {
    /** "Non_Alcoholic" or "Alcoholic". */
    @GET("filter.php")
    suspend fun byAlcoholic(@Query("a") kind: String): DrinksResponse

    /** e.g. "Cocktail", "Coffee / Tea". */
    @GET("filter.php")
    suspend fun byCategory(@Query("c") category: String): DrinksResponse

    @GET("lookup.php")
    suspend fun lookup(@Query("i") id: String): DrinksResponse
}

/** "meals" is null when nothing matches, so the list is nullable here. */
data class MealListResponse(val meals: List<BrowseMeal>?)

/** One row of a filter list. Every field is nullable so one bad row can't break the whole list. */
data class BrowseMeal(val idMeal: String?, val strMeal: String?, val strMealThumb: String?)
data class MealSearchResponse(val meals: List<MealDetail>?)
