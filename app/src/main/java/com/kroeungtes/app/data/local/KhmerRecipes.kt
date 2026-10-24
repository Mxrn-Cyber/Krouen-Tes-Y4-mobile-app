package com.kroeungtes.app.data.local

import android.content.Context
import com.google.gson.Gson
import com.kroeungtes.app.data.model.Ingredient
import com.kroeungtes.app.data.model.KhmerRecipe
import java.util.Locale

/**
 * Loads the built-in Khmer recipes from assets/recipes/khmer_recipes.json.
 * Text comes back in Khmer when the phone language is Khmer, otherwise English.
 */
object KhmerRecipes {

    /** Passed to MealActivity to show the full Khmer list ("See all"). */
    const val LIST_KEY = "__khmer_dishes__"

    private const val FILE = "recipes/khmer_recipes.json"

    // ---- JSON shape (see the asset file) ----
    private data class Text(val en: String?, val km: String?) {
        fun pick(khmer: Boolean): String = (if (khmer) km else en) ?: en ?: km.orEmpty()
        fun other(khmer: Boolean): String = (if (khmer) en else km).orEmpty()
    }
    private data class IngredientJson(val en: String?, val km: String?, val amount: Text?, val image: String?)
    private data class StepsJson(val en: List<String>?, val km: List<String>?)
    private data class PhotoJson(val url: String?, val page: String?, val author: String?, val license: String?)
    private data class RecipeJson(
        val id: Int, val name: Text, val category: Text?, val description: Text?,
        val serves: Int, val cookMinutes: Int, val ingredients: List<IngredientJson>?,
        val steps: StepsJson?, val photo: PhotoJson?
    )
    private data class FileJson(val recipes: List<RecipeJson>?)

    private var raw: List<RecipeJson>? = null
    private var cacheLang: String? = null
    private var cache: List<KhmerRecipe> = emptyList()

    fun isKhmerLanguage(): Boolean = Locale.getDefault().language == "km"

    /** Built-in Khmer recipes have negative ids. */
    fun isKhmerId(id: String): Boolean = (id.toIntOrNull() ?: 0) < 0

    fun all(context: Context): List<KhmerRecipe> {
        val lang = Locale.getDefault().language
        if (cacheLang != lang) {
            cache = load(context).map { it.localized(isKhmerLanguage()) }
            cacheLang = lang
        }
        return cache
    }

    fun find(context: Context, id: Int): KhmerRecipe? = all(context).firstOrNull { it.id == id }

    /** Matches the dish name in both languages and the category. */
    fun search(context: Context, query: String): List<KhmerRecipe> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return emptyList()
        return all(context).filter {
            it.name.lowercase(Locale.ROOT).contains(q) || it.otherName.lowercase(Locale.ROOT).contains(q) ||
                it.category.lowercase(Locale.ROOT).contains(q)
        }
    }

    private fun load(context: Context): List<RecipeJson> {
        raw?.let { return it }
        val list = try {
            context.assets.open(FILE).bufferedReader(Charsets.UTF_8).use { reader ->
                Gson().fromJson(reader, FileJson::class.java)?.recipes.orEmpty()
            }
        } catch (e: Exception) {
            emptyList()
        }
        raw = list
        return list
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
                name = (if (khmer) it.km else it.en) ?: it.en.orEmpty(),
                amount = it.amount?.pick(khmer).orEmpty(),
                imageName = it.image
            )
        },
        steps = (if (khmer) steps?.km else steps?.en) ?: steps?.en.orEmpty(),
        photoUrl = photo?.url,
        photoAuthor = photo?.author,
        photoLicense = photo?.license,
        photoPage = photo?.page
    )
}
