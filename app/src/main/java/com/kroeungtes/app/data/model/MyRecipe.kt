package com.kroeungtes.app.data.model

/** A recipe the user made on the Create Recipe screen (saved on the phone). */
data class MyRecipe(
    val id: String,
    val title: String,
    val photoPath: String?,
    val serves: Int,
    val cookMinutes: Int,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val videoUrl: String?,
    val createdAt: Long
)
