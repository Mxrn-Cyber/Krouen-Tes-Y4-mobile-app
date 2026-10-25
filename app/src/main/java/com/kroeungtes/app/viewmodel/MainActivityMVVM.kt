package com.kroeungtes.app.viewmodel

import androidx.lifecycle.ViewModel
import com.kroeungtes.app.data.browse.FoodType
import com.kroeungtes.app.data.cloud.CloudContent

/** Home screen state that should survive rotation: the category chip the person picked. */
class MainFragMVVM : ViewModel() {

    /** Picked by tapping a chip; null = the one admins chose (dashboard → Home page). */
    var pickedCategory: String? = null

    /** The chip to show selected, from the categories on Home. */
    fun selectedCategory(categories: List<FoodType>): FoodType? {
        val wanted = pickedCategory ?: CloudContent.homeNow().firstCategory
        return categories.firstOrNull { it.id == wanted } ?: categories.firstOrNull()
    }
}
