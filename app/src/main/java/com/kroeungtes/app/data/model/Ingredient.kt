package com.kroeungtes.app.data.model

/** One row of the Ingredient list on the Recipe page. */
data class Ingredient(val name: String, val amount: String) {
    /** TheMealDB has a small picture for most ingredients. */
    val imageUrl: String
        get() = "https://www.themealdb.com/images/ingredients/${name.trim().replace(" ", "%20")}-Small.png"

    companion object {
        /** Collects the 20 strIngredientN / strMeasureN pairs, skipping empty ones. */
        fun fromMeal(m: MealDetail): List<Ingredient> {
            val names: List<String?> = listOf(
                m.strIngredient1, m.strIngredient2, m.strIngredient3, m.strIngredient4, m.strIngredient5,
                m.strIngredient6, m.strIngredient7, m.strIngredient8, m.strIngredient9, m.strIngredient10,
                m.strIngredient11, m.strIngredient12, m.strIngredient13, m.strIngredient14, m.strIngredient15,
                m.strIngredient16, m.strIngredient17, m.strIngredient18, m.strIngredient19, m.strIngredient20
            )
            val amounts: List<String?> = listOf(
                m.strMeasure1, m.strMeasure2, m.strMeasure3, m.strMeasure4, m.strMeasure5,
                m.strMeasure6, m.strMeasure7, m.strMeasure8, m.strMeasure9, m.strMeasure10,
                m.strMeasure11, m.strMeasure12, m.strMeasure13, m.strMeasure14, m.strMeasure15,
                m.strMeasure16, m.strMeasure17, m.strMeasure18, m.strMeasure19, m.strMeasure20
            )
            return names.indices.mapNotNull { i ->
                val name = names[i]?.trim().orEmpty()
                if (name.isEmpty()) null else Ingredient(name, amounts[i]?.trim().orEmpty())
            }
        }
    }
}
