package com.kroeungtes.app.data.model

/** One row of the Ingredient list on the Recipe page. */
data class Ingredient(
    val name: String,
    val amount: String,
    /** A picture link for this ingredient (https), if the recipe has one. */
    val imageName: String? = null
) {
    /** Null shows the plain ingredient icon. */
    val imageUrl: String?
        get() = imageName?.takeIf { it.startsWith("https://") }
}
