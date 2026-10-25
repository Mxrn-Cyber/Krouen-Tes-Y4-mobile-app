package com.kroeungtes.app.data.local

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kroeungtes.app.data.model.MyRecipe
import java.io.File
import java.util.UUID

/**
 * Recipes made on the Create Recipe screen, kept on the phone (JSON in SharedPreferences).
 * Phase 2 moves these to Firebase so other users can see them.
 */
object MyRecipeStore {
    private const val PREFS = "my_recipes"
    private const val KEY = "recipes_json"
    const val LOCAL_PREFIX = "local-"

    private val gson = Gson()
    private val live = MutableLiveData<List<MyRecipe>>()

    fun observe(context: Context): LiveData<List<MyRecipe>> {
        if (live.value == null) live.value = load(context)
        return live
    }

    fun all(context: Context): List<MyRecipe> = live.value ?: load(context).also { live.value = it }

    fun find(context: Context, id: String): MyRecipe? = all(context).firstOrNull { it.id == id }

    fun add(context: Context, recipe: MyRecipe) = save(context, listOf(recipe) + all(context))

    fun delete(context: Context, id: String) = save(context, all(context).filterNot { it.id == id })

    fun newId(): String = LOCAL_PREFIX + UUID.randomUUID().toString()

    /** Copies a picked photo into app storage so it is still there after a restart. */
    fun copyPhoto(context: Context, uri: Uri, name: String): String? = try {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "$name.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file.absolutePath
    } catch (e: Exception) {
        null
    }

    /** Removes every saved recipe and photo from the phone (log out with "remove data", or delete account). */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "photos").deleteRecursively()
        live.postValue(emptyList())
    }

    private fun load(context: Context): List<MyRecipe> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return try {
            gson.fromJson<List<MyRecipe>>(json, object : TypeToken<List<MyRecipe>>() {}.type).orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, list: List<MyRecipe>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, gson.toJson(list)).apply()
        live.value = list
    }
}
