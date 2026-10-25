package com.kroeungtes.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.kroeungtes.app.R
import com.kroeungtes.app.data.browse.BrowseItem
import com.kroeungtes.app.data.browse.Countries
import com.kroeungtes.app.data.browse.Country
import com.kroeungtes.app.data.browse.DRINK_PREFIX
import com.kroeungtes.app.data.browse.DrinkKind
import com.kroeungtes.app.data.browse.FoodType
import com.kroeungtes.app.data.browse.SortBy
import com.kroeungtes.app.data.browse.isSuitableName
import com.kroeungtes.app.data.cloud.CloudContent
import com.kroeungtes.app.data.local.AgeGate
import com.kroeungtes.app.data.local.KhmerRecipes
import com.kroeungtes.app.data.model.Drink
import com.kroeungtes.app.data.model.Ingredient
import com.kroeungtes.app.data.model.MealDetail
import com.kroeungtes.app.data.model.RecipeCardItem
import com.kroeungtes.app.data.remote.BrowseMeal
import com.kroeungtes.app.data.remote.RetrofitInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/**
 * Categories screen: finds dishes for the chosen type and country, then sorts them.
 * Built-in Khmer dishes always show (they work offline); online dishes come from
 * TheMealDB (food) and TheCocktailDB (drinks).
 */
class BrowseMVVM(application: Application) : AndroidViewModel(application) {

    data class State(
        val type: FoodType = FoodType.ALL,
        /** null = all countries */
        val country: Country? = null,
        val sort: SortBy = SortBy.NAME,
        val items: List<BrowseItem> = emptyList(),
        val loading: Boolean = false,
        /** Online dishes couldn't be loaded (no internet or server error). */
        val offline: Boolean = false,
        /** Some dishes have no value for the chosen sort, so they are listed last. */
        val someUnsorted: Boolean = false
    )

    private val state = MutableLiveData(State())
    private val current: State get() = state.value ?: State()
    private var raw: List<BrowseItem> = emptyList()
    /** The online part of [raw], kept so Khmer dishes can be refreshed without downloading again. */
    private var online: List<BrowseItem> = emptyList()
    private var job: Job? = null
    private val collator: Collator = Collator.getInstance(Locale.getDefault())

    /** Admins published, edited or removed Khmer dishes in the dashboard. */
    private var seenChange = KhmerRecipes.changes.value
    private val recipesObserver = Observer<Int> { change ->
        if (change == seenChange) return@Observer
        seenChange = change
        refreshKhmer()
    }

    /** Admins changed the categories: follow the chosen one, or go back to "All" if it is gone. */
    private val categoriesObserver = Observer<List<FoodType>> { list ->
        val chosen = current.type
        if (chosen == FoodType.ALL) return@Observer
        val fresh = list.firstOrNull { it.id == chosen.id }
            ?.takeIf { !it.adultOnly || AgeGate.isAdult(getApplication()) }
        setType(fresh ?: FoodType.ALL)
    }

    /** Admins hid or showed a world dish: take it out of (or back into) the list. */
    private var seenHidden = CloudContent.hiddenNow()
    private val hiddenObserver = Observer<Set<String>> { hidden ->
        if (hidden == seenHidden) return@Observer
        seenHidden = hidden
        if (!current.loading) state.value = withItems(current, raw)
    }

    init {
        load()
        KhmerRecipes.changes.observeForever(recipesObserver)
        CloudContent.categories.observeForever(categoriesObserver)
        CloudContent.hiddenDishes.observeForever(hiddenObserver)
    }

    override fun onCleared() {
        KhmerRecipes.changes.removeObserver(recipesObserver)
        CloudContent.categories.removeObserver(categoriesObserver)
        CloudContent.hiddenDishes.removeObserver(hiddenObserver)
        super.onCleared()
    }

    fun observeState(): LiveData<State> = state

    fun setType(type: FoodType) {
        if (type == current.type) return
        state.value = current.copy(type = type)
        load()
    }

    fun setCountry(country: Country?) {
        if (country == current.country) return
        state.value = current.copy(country = country)
        load()
    }

    fun setSort(sort: SortBy) {
        if (sort == current.sort) return
        // While a new list is loading, just remember the choice: load() sorts with it when done
        if (current.loading) {
            state.value = current.copy(sort = sort)
            return
        }
        state.value = withItems(current.copy(sort = sort), raw)
    }

    fun retry() = load()

    private fun load() {
        val type = current.type
        val country = current.country
        job?.cancel()
        state.value = current.copy(loading = true, offline = false)
        job = viewModelScope.launch {
            val khmer = khmerDishes(type, country)
            val online: List<BrowseItem>? = try {
                onlineDishes(type, country)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            // The user may have picked something else while we waited
            if (type != current.type || country != current.country) return@launch
            this@BrowseMVVM.online = online.orEmpty()
            raw = khmer + online.orEmpty()
            state.value = withItems(current.copy(loading = false, offline = online == null), raw)
        }
    }

    private fun refreshKhmer() {
        // A download is running: start again so it uses the new dishes
        if (current.loading) {
            load()
            return
        }
        raw = khmerDishes(current.type, current.country) + online
        state.value = withItems(current, raw)
    }

    // ---------- Khmer dishes (dashboard or built-in) ----------

    private fun khmerDishes(type: FoodType, country: Country?): List<BrowseItem> {
        // Drinks categories can hold Khmer drinks too, if admins add some
        if (country != null && country !== Countries.CAMBODIA) return emptyList()
        val app = getApplication<Application>()
        return KhmerRecipes.all(app)
            .filter { type == FoodType.ALL || it.categoryKey.equals(type.khmerKey, ignoreCase = true) }
            .map { recipe ->
                val subtitle = listOf(recipe.category, app.getString(R.string.cook_time_value, recipe.cookMinutes))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                BrowseItem(
                    card = RecipeCardItem.from(recipe).copy(subtitle = subtitle),
                    cookMinutes = recipe.cookMinutes,
                    ingredientCount = recipe.ingredients.size,
                    serves = recipe.serves
                )
            }
    }

    // ---------- Online dishes ----------

    private suspend fun onlineDishes(type: FoodType, country: Country?): List<BrowseItem> {
        val khmerLang = KhmerRecipes.isKhmerLanguage()
        when (type.drink) {
            DrinkKind.SOFT -> return drinks(RetrofitInstance.cocktailApi.byAlcoholic("Non_Alcoholic").drinks, type, khmerLang)
            DrinkKind.COCKTAIL -> return drinks(RetrofitInstance.cocktailApi.byCategory("Cocktail").drinks, type, khmerLang)
            null -> Unit
        }
        if (country === Countries.CAMBODIA) return emptyList()

        val api = RetrofitInstance.mealBrowseApi
        val typeLabel = if (type == FoodType.ALL) "" else type.label(khmerLang)
        val mealCategory = type.mealCategory
        return when {
            // Every dish in the world is too many to list: the screen asks to pick a type or a country
            type == FoodType.ALL && country == null -> emptyList()
            type == FoodType.ALL && country != null ->
                api.byArea(country.api).meals.orEmpty().mapNotNull { light(it, listOf(country.label(khmerLang))) }
            mealCategory != null -> {
                val meals = api.byCategory(mealCategory).meals.orEmpty()
                if (country == null) {
                    meals.mapNotNull { light(it, listOf(typeLabel)) }
                } else {
                    val inCountry = api.byArea(country.api).meals.orEmpty().map { it.idMeal }.toSet()
                    meals.filter { it.idMeal in inCountry }.mapNotNull { light(it, listOf(typeLabel, country.label(khmerLang))) }
                }
            }
            type.searchWords.isNotEmpty() ->
                type.searchWords
                    .flatMap { word -> api.search(word).meals.orEmpty() }
                    .distinctBy { it.idMeal }
                    .filter { country == null || it.strArea.equals(country.api, ignoreCase = true) }
                    .mapNotNull { full(it, typeLabel, khmerLang) }
            else -> emptyList()
        }
    }

    /** A dish from a filter list: only id, name and photo are known. Null when the row has no id or name. */
    private fun light(meal: BrowseMeal, subtitleParts: List<String>): BrowseItem? {
        val id = meal.idMeal
        val name = meal.strMeal
        if (id.isNullOrBlank() || name.isNullOrBlank()) return null
        return BrowseItem(
            card = RecipeCardItem(id, name, meal.strMealThumb, subtitleParts.filter { it.isNotBlank() }.joinToString(" · "))
        )
    }

    /** A dish from a name search: full details, so we can count its ingredients. Null when it has no id or name. */
    private fun full(meal: MealDetail, typeLabel: String, khmerLang: Boolean): BrowseItem? {
        // Gson can leave these null even though MealDetail says String
        val id: String? = meal.idMeal
        val name: String? = meal.strMeal
        if (id.isNullOrBlank() || name.isNullOrBlank()) return null
        val subtitle = listOf(typeLabel, Countries.labelFor(meal.strArea, khmerLang))
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        return BrowseItem(
            card = RecipeCardItem(id, name, meal.strMealThumb, subtitle),
            ingredientCount = Ingredient.fromMeal(meal).size
        )
    }

    private fun drinks(list: List<Drink>?, type: FoodType, khmerLang: Boolean): List<BrowseItem> =
        list.orEmpty()
            .filter { !it.idDrink.isNullOrBlank() && isSuitableName(it.strDrink) }
            .map { drink ->
                BrowseItem(
                    card = RecipeCardItem(
                        id = DRINK_PREFIX + drink.idDrink,
                        name = drink.strDrink.orEmpty(),
                        image = drink.strDrinkThumb,
                        subtitle = type.label(khmerLang),
                        savable = false
                    )
                )
            }

    // ---------- Sorting ----------

    private fun withItems(s: State, all: List<BrowseItem>): State {
        // World dishes admins hid never show
        val hidden = CloudContent.hiddenNow()
        val list = if (hidden.isEmpty()) all else all.filter { it.card.id !in hidden }
        val key: ((BrowseItem) -> Int?)? = when (s.sort) {
            SortBy.NAME -> null
            SortBy.QUICKEST -> { item -> item.cookMinutes }
            SortBy.FEWEST_INGREDIENTS -> { item -> item.ingredientCount }
            SortBy.SERVES_MOST -> { item -> item.serves?.let { -it } }
        }
        val byName: Comparator<BrowseItem> = compareBy(collator) { item: BrowseItem -> item.card.name }
        val sorted = if (key == null) {
            list.sortedWith(byName)
        } else {
            // Dishes that don't say (null) go last, then name order
            list.sortedWith(compareBy<BrowseItem>({ key(it) == null }, { key(it) }).then(byName))
        }
        return s.copy(items = sorted, someUnsorted = key != null && list.any { key(it) == null })
    }
}
