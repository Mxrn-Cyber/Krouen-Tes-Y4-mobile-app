package com.kroeungtes.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class Meal(
    val idMeal: String,
    val strMeal: String,
    val strMealThumb: String
)