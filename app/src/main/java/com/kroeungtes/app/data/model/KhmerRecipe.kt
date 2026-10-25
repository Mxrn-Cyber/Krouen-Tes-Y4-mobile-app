package com.kroeungtes.app.data.model

/**
 * A Khmer recipe built into the app (assets/recipes/khmer_recipes.json),
 * already in the phone's language (Khmer or English).
 * Ids are negative (-101, -102 …) so they never clash with TheMealDB ids in the Wishlist.
 */
data class KhmerRecipe(
    val id: Int,
    val name: String,
    /** The name in the other language, e.g. "អាម៉ុកត្រី" when the app is in English. */
    val otherName: String,
    val category: String,
    val description: String,
    val serves: Int,
    val cookMinutes: Int,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val photoUrl: String?,
    val photoAuthor: String?,
    val photoLicense: String?,
    val photoPage: String?,
    /** English category ("Soup", "Fish" …) whatever the phone language — used by the Categories filters. */
    val categoryKey: String = "",
    /** everywhere, coast, north, northwest or uplands (set in the dashboard). */
    val region: String = "everywhere"
)
