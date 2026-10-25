package com.kroeungtes.app.data.local

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.kroeungtes.app.data.model.Ingredient
import com.kroeungtes.app.data.model.KhmerRecipe
import java.util.Locale

/**
 * The recipes admins publish in the web dashboard (Firestore, delivered by CloudContent).
 * Firestore keeps an offline copy, so they still show without internet after the first load.
 * Text comes back in Khmer when the phone language is Khmer, otherwise English.
 */
object KhmerRecipes {

    /** Passed to MealActivity to show the Featured list ("See all"). */
    const val LIST_KEY = "__khmer_dishes__"
    /** Passed to MealActivity to show the newest recipes. */
    const val NEWEST_KEY = "__newest__"

    // ---- Shape of a recipe document (see admin-dashboard/public/js/view-recipes.js) ----
    private data class Text(val en: String?, val km: String?) {
        fun pick(khmer: Boolean): String = (if (khmer) km else en)?.takeIf { it.isNotBlank() } ?: en ?: km.orEmpty()
        fun other(khmer: Boolean): String = (if (khmer) en else km).orEmpty()
    }
    private data class IngredientJson(val en: String?, val km: String?, val amount: Text?, val image: String?)
    private data class StepsJson(val en: List<String>?, val km: List<String>?)
    private data class PhotoJson(val url: String?, val page: String?, val author: String?, val license: String?)
    private data class RecipeJson(
        val id: Int, val name: Text, val category: Text?, val description: Text?,
        val serves: Int, val cookMinutes: Int, val ingredients: List<IngredientJson>?,
        val steps: StepsJson?, val photo: PhotoJson?,
        /** Dashboard category id. */
        val categoryId: String? = null,
        val region: String? = null
    )

    private var recipes: List<RecipeJson> = emptyList()
    private val changesLive = MutableLiveData(0)

    /** Goes up by one whenever the recipe list changes, so screens can refresh. */
    val changes: LiveData<Int> get() = changesLive

    /** Called by CloudContent with the published Firestore recipes (raw document data). */
    fun setCloud(docs: List<Map<String, Any?>>) {
        val gson = Gson()
        val list = docs.mapNotNull { data ->
            try {
                val map = data.toMutableMap()
                map.remove("createdAt")
                map.remove("updatedAt")
                map["id"] = (map["numId"] as? Number)?.toInt() ?: return@mapNotNull null
                gson.fromJson(gson.toJsonTree(map), RecipeJson::class.java)
                    ?.takeIf { it.name.en != null || it.name.km != null }
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.id } // oldest (-101) first; new ones get lower numbers
        if (list == recipes) return
        recipes = list
        cacheLang = null
        changesLive.value = (changesLive.value ?: 0) + 1
    }

    private var cacheLang: String? = null
    private var cache: List<KhmerRecipe> = emptyList()

    fun isKhmerLanguage(): Boolean = Locale.getDefault().language == "km"

    /** Dashboard recipes have negative ids (-101, -102 …). */
    fun isKhmerId(id: String): Boolean = (id.toIntOrNull() ?: 0) < 0

    /** Every published recipe, oldest first. */
    @Suppress("UNUSED_PARAMETER")
    fun all(context: Context): List<KhmerRecipe> {
        val lang = Locale.getDefault().language
        if (cacheLang != lang) {
            cache = recipes.map { it.localized(isKhmerLanguage()) }
            cacheLang = lang
        }
        return cache
    }

    /** Newest first (the dashboard gives each new recipe a lower number). */
    fun newest(context: Context): List<KhmerRecipe> = all(context).sortedBy { it.id }

    fun inCategory(context: Context, categoryId: String): List<KhmerRecipe> =
        all(context).filter { it.categoryKey.equals(categoryId, ignoreCase = true) }

    fun find(context: Context, id: Int): KhmerRecipe? = all(context).firstOrNull { it.id == id }

    /** Matches the name in both languages, the category and the ingredients. */
    fun search(context: Context, query: String): List<KhmerRecipe> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return emptyList()
        fun String.has() = lowercase(Locale.ROOT).contains(q)
        val byName = all(context).filter { it.name.has() || it.otherName.has() }
        val more = all(context).filter { r ->
            r !in byName && (r.category.has() || r.ingredients.any { it.name.has() })
        }
        return byName + more
    }

    private fun RecipeJson.localized(khmer: Boolean) = KhmerRecipe(
        id = id,
        name = name.pick(khmer),
        otherName = name.other(khmer),
        category = category?.pick(khmer).orEmpty(),
        description = description?.pick(khmer).orEmpty(),
        serves = serves,
        cookMinutes = cookMinutes,
        ingredients = ingredients.orEmpty().map {
            Ingredient(
                name = (if (khmer) it.km?.takeIf { km -> km.isNotBlank() } else it.en) ?: it.en.orEmpty(),
                amount = it.amount?.pick(khmer).orEmpty(),
                imageName = it.image
            )
        },
        steps = (if (khmer) steps?.km?.takeIf { it.isNotEmpty() } else steps?.en) ?: steps?.en.orEmpty(),
        photoUrl = photo?.url,
        photoAuthor = photo?.author,
        photoLicense = photo?.license,
        photoPage = photo?.page,
        categoryKey = categoryId?.takeIf { it.isNotBlank() } ?: category?.en.orEmpty(),
        region = region ?: "everywhere"
    )
}
