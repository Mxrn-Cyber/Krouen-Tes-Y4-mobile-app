package com.kroeungtes.app.data.model

/**
 * A drink from TheCocktailDB. Filter lists only fill the id, name and photo;
 * lookup.php fills the rest.
 */
data class Drink(
    val idDrink: String?,
    val strDrink: String?,
    val strDrinkThumb: String?,
    val strCategory: String? = null,
    val strAlcoholic: String? = null,
    val strGlass: String? = null,
    val strInstructions: String? = null,
    val strIngredient1: String? = null,
    val strIngredient2: String? = null,
    val strIngredient3: String? = null,
    val strIngredient4: String? = null,
    val strIngredient5: String? = null,
    val strIngredient6: String? = null,
    val strIngredient7: String? = null,
    val strIngredient8: String? = null,
    val strIngredient9: String? = null,
    val strIngredient10: String? = null,
    val strIngredient11: String? = null,
    val strIngredient12: String? = null,
    val strIngredient13: String? = null,
    val strIngredient14: String? = null,
    val strIngredient15: String? = null,
    val strMeasure1: String? = null,
    val strMeasure2: String? = null,
    val strMeasure3: String? = null,
    val strMeasure4: String? = null,
    val strMeasure5: String? = null,
    val strMeasure6: String? = null,
    val strMeasure7: String? = null,
    val strMeasure8: String? = null,
    val strMeasure9: String? = null,
    val strMeasure10: String? = null,
    val strMeasure11: String? = null,
    val strMeasure12: String? = null,
    val strMeasure13: String? = null,
    val strMeasure14: String? = null,
    val strMeasure15: String? = null
) {
    /** The ingredient / measure pairs (up to 15), skipping empty ones. */
    fun ingredients(): List<Ingredient> {
        val names: List<String?> = listOf(strIngredient1, strIngredient2, strIngredient3, strIngredient4, strIngredient5, strIngredient6, strIngredient7, strIngredient8, strIngredient9, strIngredient10, strIngredient11, strIngredient12, strIngredient13, strIngredient14, strIngredient15)
        val amounts: List<String?> = listOf(strMeasure1, strMeasure2, strMeasure3, strMeasure4, strMeasure5, strMeasure6, strMeasure7, strMeasure8, strMeasure9, strMeasure10, strMeasure11, strMeasure12, strMeasure13, strMeasure14, strMeasure15)
        return names.indices.mapNotNull { i ->
            val name = names[i]?.trim().orEmpty()
            if (name.isEmpty()) null else Ingredient(name, amounts[i]?.trim().orEmpty())
        }
    }
}

data class DrinksResponse(val drinks: List<Drink>?)
