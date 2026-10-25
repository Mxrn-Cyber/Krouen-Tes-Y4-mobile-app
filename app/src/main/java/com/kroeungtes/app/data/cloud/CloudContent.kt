package com.kroeungtes.app.data.cloud

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.kroeungtes.app.data.browse.FoodType
import com.kroeungtes.app.data.local.KhmerRecipes
import com.kroeungtes.app.data.model.KhmerRecipe
import java.util.Date

/**
 * Everything the app shows comes from the admin web dashboard (Firestore): categories, recipes,
 * the Home page setup and notifications. Listens for changes, so edits reach the app within seconds.
 * Firestore keeps an offline copy for when there is no internet.
 */
object CloudContent {
    private const val TAG = "CloudContent"

    /** Home page set up by admins (dashboard → Home page). */
    data class HomeSettings(
        /** Recipe ids for the Featured row, in order; empty = every recipe. */
        val featured: List<Int> = emptyList(),
        /** Category chip selected first on Home; null = the first category. */
        val firstCategory: String? = null
    )

    /** News for the Notifications tab (dashboard → Notifications). */
    data class Announcement(
        val id: String,
        private val titleEn: String, private val titleKm: String,
        private val bodyEn: String, private val bodyKm: String,
        val imageUrl: String?,
        /** A recipe to open when tapped. */
        val recipeId: Int?,
        val publishedAt: Date?
    ) {
        fun title(khmer: Boolean) = if (khmer) titleKm.ifBlank { titleEn } else titleEn.ifBlank { titleKm }
        fun body(khmer: Boolean) = if (khmer) bodyKm.ifBlank { bodyEn } else bodyEn.ifBlank { bodyKm }
    }

    private val categoriesLive = MutableLiveData<List<FoodType>>(emptyList())
    private val loadedLive = MutableLiveData(false)
    private val homeLive = MutableLiveData(HomeSettings())
    private val announcementsLive = MutableLiveData<List<Announcement>>(emptyList())

    /** Visible categories in the admins' order (without "All"). */
    val categories: LiveData<List<FoodType>> get() = categoriesLive
    fun currentCategories(): List<FoodType> = categoriesLive.value.orEmpty()

    /** False until the recipes have arrived once (from the internet or the offline copy). */
    val loaded: LiveData<Boolean> get() = loadedLive

    val home: LiveData<HomeSettings> get() = homeLive
    fun homeNow(): HomeSettings = homeLive.value ?: HomeSettings()

    /** Published notifications, newest first. */
    val announcements: LiveData<List<Announcement>> get() = announcementsLive

    /** The Featured row on Home: the admins' picks in their order, or every recipe. */
    fun homeKhmerDishes(context: Context): List<KhmerRecipe> {
        val all = KhmerRecipes.all(context)
        val picked = homeNow().featured.mapNotNull { id -> all.firstOrNull { it.id == id } }
        return picked.ifEmpty { all }
    }

    private var started = false

    /** Call once when the app starts (all of this is public, read-only content). */
    fun start() {
        if (started) return
        started = true
        val db = FirebaseFirestore.getInstance()

        db.collection("categories").addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "Categories not loaded", error)
                return@addSnapshotListener
            }
            categoriesLive.value = snap?.documents.orEmpty()
                .mapNotNull { toFoodType(it) }
                .sortedBy { it.first }
                .map { it.second }
        }

        // Metadata changes too: an empty offline copy followed by an empty answer from the
        // internet only changes "from cache", and that is what tells us loading has finished
        db.collection("recipes").whereEqualTo("status", "published")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (error != null) {
                Log.w(TAG, "Recipes not loaded", error)
                // Stop waiting: screens show "no recipes" instead of loading forever
                markLoaded()
                return@addSnapshotListener
            }
            val docs = snap?.documents.orEmpty()
            KhmerRecipes.setCloud(docs.mapNotNull { it.data })
            // An empty offline copy on the very first start isn't an answer yet: wait for the internet
            if (docs.isNotEmpty() || snap?.metadata?.isFromCache == false) markLoaded()
        }

        db.collection("settings").document("home").addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "Home settings not loaded", error)
                return@addSnapshotListener
            }
            homeLive.value = HomeSettings(
                featured = (snap?.get("featured") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty(),
                firstCategory = snap?.getString("firstCategory")?.takeIf { it.isNotBlank() }
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

    private fun markLoaded() {
        if (loadedLive.value != true) loadedLive.value = true
    }

    /** Firestore category → chip. Hidden ones are skipped. Returns (order, type). */
    private fun toFoodType(doc: DocumentSnapshot): Pair<Long, FoodType>? {
        // "all" is the app's own "All" chip
        if (doc.getBoolean("hidden") == true || doc.id == FoodType.ALL.id) return null
        val en = doc.getString("nameEn")?.takeIf { it.isNotBlank() } ?: return null
        val type = FoodType(
            id = doc.id,
            en = en,
            km = doc.getString("nameKm").orEmpty(),
            adultOnly = doc.getBoolean("adultOnly") == true || doc.getString("drink") == "cocktail",
            icon = doc.getString("icon").orEmpty()
        )
        return (doc.getLong("order") ?: 999L) to type
    }
}
