package com.kroeungtes.app.data.browse

import androidx.annotation.StringRes
import com.kroeungtes.app.R
import com.kroeungtes.app.data.model.RecipeCardItem

/**
 * A category chip (Categories tab and Home), in English and Khmer.
 * Admins create these in the web dashboard (Firestore "categories"); recipes point to one by [id].
 * [adultOnly] asks "Are you 18 or older?" first.
 */
data class FoodType(
    val id: String,
    private val en: String,
    private val km: String,
    val adultOnly: Boolean = false,
    val icon: String = ""
) {
    fun label(khmer: Boolean): String = if (khmer) km.ifBlank { en } else en

    /** Text on the chip, with the icon in front. */
    fun chipLabel(khmer: Boolean): String = if (icon.isBlank()) label(khmer) else "$icon ${label(khmer)}"

    companion object {
        val ALL = FoodType("all", "All", "ទាំងអស់")
    }
}

/** Where in Cambodia a dish comes from (set on each recipe in the dashboard). */
enum class Region(val key: String, @StringRes val label: Int) {
    EVERYWHERE("everywhere", R.string.region_everywhere),
    COAST("coast", R.string.region_coast),
    NORTH("north", R.string.region_north),
    NORTHWEST("northwest", R.string.region_northwest),
    UPLANDS("uplands", R.string.region_uplands);

    companion object {
        fun of(key: String?): Region = entries.firstOrNull { it.key == key } ?: EVERYWHERE
    }
}

enum class SortBy(@StringRes val label: Int) {
    NAME(R.string.sort_name),
    QUICKEST(R.string.sort_quickest),
    FEWEST_INGREDIENTS(R.string.sort_fewest),
    SERVES_MOST(R.string.sort_serves)
}

/** A card plus the facts we can sort by. */
data class BrowseItem(
    val card: RecipeCardItem,
    val cookMinutes: Int? = null,
    val ingredientCount: Int? = null,
    val serves: Int? = null
)
