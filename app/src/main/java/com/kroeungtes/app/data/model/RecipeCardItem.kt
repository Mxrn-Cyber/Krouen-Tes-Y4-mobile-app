package com.kroeungtes.app.data.model

/**
 * What a recipe card needs to show, whatever the source
 * (TheMealDB list, Wishlist row, search result or a recipe the user created).
 */
data class RecipeCardItem(
    val id: String,
    val name: String,
    val image: String?,
    val subtitle: String,
    val isLocal: Boolean = false,
    /** Person who made it (user's own recipes). Null = picked by Krouen Tes from the recipe library. */
    val author: String? = null
) {
    companion object {
        fun from(meal: Meal, subtitle: String) = RecipeCardItem(meal.idMeal, meal.strMeal, meal.strMealThumb, subtitle)

        fun from(saved: MealDB, fallbackSubtitle: String): RecipeCardItem {
            val parts = listOf(saved.mealCategory.orEmpty(), saved.mealCountry.orEmpty()).filter { it.isNotBlank() }
            return RecipeCardItem(
                saved.mealId.toString(), saved.mealName, saved.mealThumb,
                if (parts.isEmpty()) fallbackSubtitle else parts.joinToString(" · ")
            )
        }

        fun from(detail: MealDetail) = RecipeCardItem(
            detail.idMeal, detail.strMeal, detail.strMealThumb,
            listOf(detail.strCategory.orEmpty(), detail.strArea.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
        )

        fun from(khmer: KhmerRecipe) = RecipeCardItem(
            khmer.id.toString(), khmer.name, khmer.photoUrl,
            listOf(khmer.otherName, khmer.category).filter { it.isNotBlank() }.joinToString(" · ")
        )

        fun from(mine: MyRecipe, subtitle: String, author: String) =
            RecipeCardItem(mine.id, mine.title, mine.photoPath, subtitle, isLocal = true, author = author)
    }
}
