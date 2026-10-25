package com.kroeungtes.app.data.model

/**
 * What a recipe card needs to show, whatever the source
 * (a recipe from the dashboard, or a recipe the user created on this phone).
 */
data class RecipeCardItem(
    val id: String,
    val name: String,
    val image: String?,
    val subtitle: String,
    val isLocal: Boolean = false,
    /** Person who made it (user's own recipes). Null = published by the Krouen Tes team. */
    val author: String? = null,
    /** False for things that can't go in the Wishlist. */
    val savable: Boolean = true
) {
    companion object {
        fun from(khmer: KhmerRecipe) = RecipeCardItem(
            khmer.id.toString(), khmer.name, khmer.photoUrl,
            listOf(khmer.otherName, khmer.category).filter { it.isNotBlank() }.joinToString(" · ")
        )

        fun from(khmer: KhmerRecipe, subtitle: String) =
            RecipeCardItem(khmer.id.toString(), khmer.name, khmer.photoUrl, subtitle)

        fun from(mine: MyRecipe, subtitle: String, author: String) =
            RecipeCardItem(mine.id, mine.title, mine.photoPath, subtitle, isLocal = true, author = author)
    }
}
