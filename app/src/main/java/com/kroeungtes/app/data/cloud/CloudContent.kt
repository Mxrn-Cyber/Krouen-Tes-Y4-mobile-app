package com.kroeungtes.app.data.cloud

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.kroeungtes.app.data.browse.DrinkKind
import com.kroeungtes.app.data.browse.FoodType
import com.kroeungtes.app.data.local.KhmerRecipes

/**
 * Categories and recipes that admins manage in the web dashboard (Firestore).
 * Listens for changes, so edits reach the app within seconds. Firestore keeps an offline copy;
 * before the first download (or if the database is empty) the built-in content is used.
 */
object CloudContent {
    private const val TAG = "CloudContent"

    private val categoriesLive = MutableLiveData(FoodType.DEFAULTS)

    /** Visible categories in the admins' order (without "All"). */
    val categories: LiveData<List<FoodType>> get() = categoriesLive

    fun currentCategories(): List<FoodType> = categoriesLive.value ?: FoodType.DEFAULTS

    private var started = false
    /** Admins have added categories, so the dashboard content is in use. */
    private var managed = false
    private var publishedDocs: List<Map<String, Any?>> = emptyList()
    private var recipesLoaded = false

    private fun pushRecipes() {
        if (recipesLoaded) KhmerRecipes.setCloud(publishedDocs, managed)
    }

    /** Call once when the app starts (categories and published recipes are public). */
    fun start() {
        if (started) return
        started = true
        val db = FirebaseFirestore.getInstance()

        db.collection("categories").addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "Categories not loaded", error)
                return@addSnapshotListener
            }
            val docs = snap?.documents.orEmpty()
            managed = docs.isNotEmpty()
            pushRecipes()
            // Nothing in the database yet: use the built-in list
            if (docs.isEmpty()) {
                categoriesLive.value = FoodType.DEFAULTS
                return@addSnapshotListener
            }
            categoriesLive.value = docs.mapNotNull { toFoodType(it) }
                .sortedBy { it.first }
                .map { it.second }
        }

        db.collection("recipes").whereEqualTo("status", "published").addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "Recipes not loaded", error)
                return@addSnapshotListener
            }
            publishedDocs = snap?.documents.orEmpty().mapNotNull { it.data }
            recipesLoaded = true
            pushRecipes()
        }
    }

    /** Firestore category → chip. Hidden ones are skipped. Returns (order, type). */
    private fun toFoodType(doc: DocumentSnapshot): Pair<Long, FoodType>? {
        // "all" is the app's own "All" chip
        if (doc.getBoolean("hidden") == true || doc.id == FoodType.ALL.id) return null
        val en = doc.getString("nameEn")?.takeIf { it.isNotBlank() } ?: return null
        val drink = when (doc.getString("drink")) {
            "soft" -> DrinkKind.SOFT
            "cocktail" -> DrinkKind.COCKTAIL
            else -> null
        }
        val search = (doc.get("mealDbSearch") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val type = FoodType(
            id = doc.id,
            en = en,
            km = doc.getString("nameKm").orEmpty(),
            khmerKey = doc.id,
            mealCategory = doc.getString("mealDbCategory")?.takeIf { it.isNotBlank() },
            searchWords = search,
            drink = drink,
            adultOnly = doc.getBoolean("adultOnly") == true || drink == DrinkKind.COCKTAIL,
            icon = doc.getString("icon").orEmpty()
        )
        return (doc.getLong("order") ?: 999L) to type
    }
}
