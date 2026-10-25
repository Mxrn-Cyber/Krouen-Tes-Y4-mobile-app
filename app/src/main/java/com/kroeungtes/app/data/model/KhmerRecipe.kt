package com.kroeungtes.app.data.model

/**
 * A recipe published in the admin dashboard, already in the phone's language (Khmer or English).
 * Ids are negative (-101, -102 …); the Wishlist stores them by id.
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
    /** Dashboard category id ("soup", "fish" …) whatever the phone language — used by the filters. */
    val categoryKey: String = "",
    /** everywhere, coast, north, northwest or uplands (set in the dashboard). */
    val region: String = "everywhere"
)
