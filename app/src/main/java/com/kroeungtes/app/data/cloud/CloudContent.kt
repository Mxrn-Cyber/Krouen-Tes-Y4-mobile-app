package com.kroeungtes.app.data.cloud

import android.util.Log
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.kroeungtes.app.data.browse.DrinkKind
import com.kroeungtes.app.data.browse.FoodType
import com.kroeungtes.app.data.local.KhmerRecipes
import com.kroeungtes.app.data.model.KhmerRecipe
import com.kroeungtes.app.viewmodel.RECENT_CATEGORY
import com.kroeungtes.app.viewmodel.TRENDING_CATEGORY
import java.util.Date

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

    /** Home page set up by admins (dashboard → Home page). */
    data class HomeSettings(
        /** Khmer recipe ids for the first row, in order; empty = all published recipes. */
        val featured: List<Int> = emptyList(),
        val trendingCategory: String = TRENDING_CATEGORY,
        val recentCategory: String = RECENT_CATEGORY
    )

    /** News for the Notifications tab (dashboard → Notifications). */
    data class Announcement(
        val id: String,
        private val titleEn: String, private val titleKm: String,
        private val bodyEn: String, private val bodyKm: String,
        val imageUrl: String?,
        /** A Khmer recipe to open when tapped. */
        val recipeId: Int?,
        val publishedAt: Date?
    ) {
        fun title(khmer: Boolean) = if (khmer) titleKm.ifBlank { titleEn } else titleEn.ifBlank { titleKm }
        fun body(khmer: Boolean) = if (khmer) bodyKm.ifBlank { bodyEn } else bodyEn.ifBlank { bodyKm }
    }

    private val hiddenLive = MutableLiveData<Set<String>>(emptySet())
    private val homeLive = MutableLiveData(HomeSettings())
    private val announcementsLive = MutableLiveData<List<Announcement>>(emptyList())

    /** Card ids of world dishes admins hid ("52772", "drink-11007"). */
    val hiddenDishes: LiveData<Set<String>> get() = hiddenLive
    fun hiddenNow(): Set<String> = hiddenLive.value.orEmpty()

    val home: LiveData<HomeSettings> get() = homeLive
    fun homeNow(): HomeSettings = homeLive.value ?: HomeSettings()

    /** Published notifications, newest first. */
    val announcements: LiveData<List<Announcement>> get() = announcementsLive

    /** The Khmer dishes row on Home: the admins' picks in their order, or every recipe. */
    fun homeKhmerDishes(context: Context): List<KhmerRecipe> {
        val all = KhmerRecipes.all(context)
        val picked = homeNow().featured.mapNotNull { id -> all.firstOrNull { it.id == id } }
        return picked.ifEmpty { all }
    }

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

        db.collection("hiddenDishes").addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "Hidden dishes not loaded", error)
                return@addSnapshotListener
            }
            hiddenLive.value = snap?.documents.orEmpty().map { it.id }.toSet()
        }

        db.collection("settings").document("home").addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "Home settings not loaded", error)
                return@addSnapshotListener
            }
            val defaults = HomeSettings()
            homeLive.value = HomeSettings(
                featured = (snap?.get("featured") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty(),
                trendingCategory = snap?.getString("trendingCategory")?.takeIf { it.isNotBlank() } ?: defaults.trendingCategory,
                recentCategory = snap?.getString("recentCategory")?.takeIf { it.isNotBlank() } ?: defaults.recentCategory
            )
        }

        db.collection("announcements").whereEqualTo("status", "published").addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "Notifications not loaded", error)
                return@addSnapshotListener
            }
            announcementsLive.value = snap?.documents.orEmpty().map { doc ->
                Announcement(
                    id = doc.id,
                    titleEn = doc.getString("titleEn").orEmpty(), titleKm = doc.getString("titleKm").orEmpty(),
                    bodyEn = doc.getString("bodyEn").orEmpty(), bodyKm = doc.getString("bodyKm").orEmpty(),
                    imageUrl = doc.getString("imageUrl")?.takeIf { it.startsWith("https://") },
                    recipeId = doc.getLong("recipeNumId")?.toInt(),
                    publishedAt = doc.getTimestamp("publishedAt")?.toDate()
                )
            }.sortedByDescending { it.publishedAt?.time ?: Long.MAX_VALUE }
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

/**
 * This list without the world dishes admins hid; updates when either changes.
 * [filter] gets the current value and the hidden card ids.
 */
fun <T> LiveData<T>.withoutHiddenDishes(filter: (T, Set<String>) -> T): LiveData<T> {
    val source = this
    val out = MediatorLiveData<T>()
    val update = {
        val value = source.value
        if (value != null) out.value = filter(value, CloudContent.hiddenNow())
    }
    out.addSource(source) { update() }
    out.addSource(CloudContent.hiddenDishes) { update() }
    return out
}
