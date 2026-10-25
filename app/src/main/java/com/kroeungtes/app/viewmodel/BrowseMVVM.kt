package com.kroeungtes.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.kroeungtes.app.R
import com.kroeungtes.app.data.browse.BrowseItem
import com.kroeungtes.app.data.browse.FoodType
import com.kroeungtes.app.data.browse.Region
import com.kroeungtes.app.data.browse.SortBy
import com.kroeungtes.app.data.cloud.CloudContent
import com.kroeungtes.app.data.local.AgeGate
import com.kroeungtes.app.data.local.KhmerRecipes
import com.kroeungtes.app.data.model.RecipeCardItem
import java.text.Collator
import java.util.Locale

/**
 * Categories screen: the dashboard's recipes for the chosen category and region, sorted.
 * Everything is on the phone already (Firestore's copy), so there is no waiting.
 */
class BrowseMVVM(application: Application) : AndroidViewModel(application) {

    data class State(
        val type: FoodType = FoodType.ALL,
        /** null = every region */
        val region: Region? = null,
        val sort: SortBy = SortBy.NAME,
        val items: List<BrowseItem> = emptyList(),
        /** The recipes haven't arrived yet (first start). */
        val loading: Boolean = true
    )

    private val state = MutableLiveData(State())
    private val current: State get() = state.value ?: State()
    private val collator: Collator = Collator.getInstance(Locale.getDefault())

    /** Admins published, edited or removed recipes, or they arrived for the first time. */
    private val refresh = Observer<Any?> { load() }

    /** Admins changed the categories: follow the chosen one, or go back to "All" if it is gone. */
    private val categoriesObserver = Observer<List<FoodType>> { list ->
        val chosen = current.type
        if (chosen == FoodType.ALL) return@Observer
        val fresh = list.firstOrNull { it.id == chosen.id }
            ?.takeIf { !it.adultOnly || AgeGate.isAdult(getApplication()) }
        setType(fresh ?: FoodType.ALL)
    }

    init {
        KhmerRecipes.changes.observeForever(refresh)
        CloudContent.loaded.observeForever(refresh)
        CloudContent.categories.observeForever(categoriesObserver)
    }

    override fun onCleared() {
        KhmerRecipes.changes.removeObserver(refresh)
        CloudContent.loaded.removeObserver(refresh)
        CloudContent.categories.removeObserver(categoriesObserver)
        super.onCleared()
    }

    fun observeState(): LiveData<State> = state

    fun setType(type: FoodType) {
        if (type == current.type) return
        state.value = current.copy(type = type)
        load()
    }

    fun setRegion(region: Region?) {
        if (region == current.region) return
        state.value = current.copy(region = region)
        load()
    }

    fun setSort(sort: SortBy) {
        if (sort == current.sort) return
        state.value = current.copy(sort = sort)
        load()
    }

    private fun load() {
        val s = current
        val app = getApplication<Application>()
        val items = KhmerRecipes.all(app)
            .filter { s.type == FoodType.ALL || it.categoryKey.equals(s.type.id, ignoreCase = true) }
            .filter { s.region == null || Region.of(it.region) == s.region }
            .map { recipe ->
                val subtitle = listOf(recipe.category, app.getString(R.string.cook_time_value, recipe.cookMinutes))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                BrowseItem(
                    card = RecipeCardItem.from(recipe, subtitle),
                    cookMinutes = recipe.cookMinutes,
                    ingredientCount = recipe.ingredients.size,
                    serves = recipe.serves
                )
            }
        state.value = s.copy(items = sorted(s.sort, items), loading = CloudContent.loaded.value != true)
    }

    private fun sorted(sort: SortBy, list: List<BrowseItem>): List<BrowseItem> {
        val byName: Comparator<BrowseItem> = compareBy(collator) { item: BrowseItem -> item.card.name }
        val first: Comparator<BrowseItem>? = when (sort) {
            SortBy.NAME -> null
            SortBy.QUICKEST -> compareBy<BrowseItem> { it.cookMinutes ?: Int.MAX_VALUE }
            SortBy.FEWEST_INGREDIENTS -> compareBy<BrowseItem> { it.ingredientCount ?: Int.MAX_VALUE }
            SortBy.SERVES_MOST -> compareByDescending<BrowseItem> { it.serves ?: 0 }
        }
        return list.sortedWith(first?.then(byName) ?: byName)
    }
}
