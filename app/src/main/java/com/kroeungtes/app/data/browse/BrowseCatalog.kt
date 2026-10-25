package com.kroeungtes.app.data.browse

import androidx.annotation.StringRes
import com.kroeungtes.app.R
import com.kroeungtes.app.data.model.RecipeCardItem

/** Drinks come from TheCocktailDB; their ids start with this so the Recipe page knows where to load them. */
const val DRINK_PREFIX = "drink-"

enum class DrinkKind { SOFT, COCKTAIL }

/**
 * Food types on the Categories screen, in English and Khmer, and where each one finds dishes:
 * - [khmerKey]: category of the built-in Khmer recipes (assets/recipes/khmer_recipes.json)
 * - [mealCategory]: TheMealDB category (filter.php?c=)
 * - [searchWords]: TheMealDB name search (search.php?s=) when it has no such category
 * - [drink]: TheCocktailDB instead of food
 */
enum class FoodType(
    private val en: String,
    private val km: String,
    val khmerKey: String? = null,
    val mealCategory: String? = null,
    val searchWords: List<String> = emptyList(),
    val drink: DrinkKind? = null
) {
    ALL("All", "ទាំងអស់"),
    BEEF("Beef", "សាច់គោ", khmerKey = "Beef", mealCategory = "Beef"),
    CHICKEN("Chicken", "សាច់មាន់", khmerKey = "Chicken", mealCategory = "Chicken"),
    PORK("Pork", "សាច់ជ្រូក", khmerKey = "Pork", mealCategory = "Pork"),
    FISH("Fish", "ត្រី", khmerKey = "Fish", searchWords = listOf("fish")),
    SEAFOOD("Seafood", "គ្រឿងសមុទ្រ", khmerKey = "Seafood", mealCategory = "Seafood"),
    SOUP("Soup", "សម្ល", khmerKey = "Soup", searchWords = listOf("soup")),
    NOODLES("Noodles", "មី និងគុយទាវ", khmerKey = "Noodles", searchWords = listOf("noodle")),
    SALAD("Salad", "ញាំ និងសាឡាដ", khmerKey = "Salad", searchWords = listOf("salad")),
    DESSERT("Dessert", "បង្អែម", khmerKey = "Dessert", mealCategory = "Dessert"),
    COOKIES("Cookies", "នំខូគី", searchWords = listOf("cookie", "biscuit")),
    VEGETARIAN("Vegetarian", "អាហារបួស", mealCategory = "Vegetarian"),
    VEGAN("Vegan", "អាហារបួសសុទ្ធ", mealCategory = "Vegan"),
    BREAKFAST("Breakfast", "អាហារពេលព្រឹក", mealCategory = "Breakfast"),
    PASTA("Pasta", "ប៉ាស្តា", mealCategory = "Pasta"),
    DRINKS("Drinks", "ភេសជ្ជៈ", drink = DrinkKind.SOFT),
    COCKTAILS("Cocktails 18+", "ស្រាក្រឡុក 18+", drink = DrinkKind.COCKTAIL);

    fun label(khmer: Boolean): String = if (khmer) km else en
}

/** A country to filter by. [api] is TheMealDB's "area" value. */
class Country(val api: String, private val en: String, private val km: String) {
    fun label(khmer: Boolean): String = if (khmer) km else en
}

object Countries {
    /** Only the built-in Khmer dishes (TheMealDB has none from Cambodia). */
    val CAMBODIA = Country("Cambodia", "Cambodia", "កម្ពុជា")

    /** TheMealDB area values that returned dishes when checked on 25 Oct 2026, with the country name to show. */
    val ALL: List<Country> = listOf(
        CAMBODIA,
        Country("Algerian", "Algeria", "អាល់ហ្សេរី"),
        Country("Argentina", "Argentina", "អាហ្សង់ទីន"),
        Country("Australian", "Australia", "អូស្ត្រាលី"),
        Country("Canadian", "Canada", "កាណាដា"),
        Country("Chinese", "China", "ចិន"),
        Country("Croatian", "Croatia", "ក្រូអាស៊ី"),
        Country("Egyptian", "Egypt", "អេហ្ស៊ីប"),
        Country("France", "France", "បារាំង"),
        Country("Greek", "Greece", "ក្រិក"),
        Country("India", "India", "ឥណ្ឌា"),
        Country("Irish", "Ireland", "អៀរឡង់"),
        Country("Italian", "Italy", "អ៊ីតាលី"),
        Country("Jamaican", "Jamaica", "ហ្សាម៉ាអ៊ីក"),
        Country("Japanese", "Japan", "ជប៉ុន"),
        Country("Kenyan", "Kenya", "កេនយ៉ា"),
        Country("Malaysian", "Malaysia", "ម៉ាឡេស៊ី"),
        Country("Mexican", "Mexico", "ម៉ិកស៊ិក"),
        Country("Moroccan", "Morocco", "ម៉ារ៉ុក"),
        Country("Netherlands", "Netherlands", "ហូឡង់"),
        Country("Norway", "Norway", "ន័រវែស"),
        Country("Filipino", "Philippines", "ហ្វីលីពីន"),
        Country("Polish", "Poland", "ប៉ូឡូញ"),
        Country("Portuguese", "Portugal", "ព័រទុយហ្គាល់"),
        Country("Russian", "Russia", "រុស្ស៊ី"),
        Country("Saudi Arabian", "Saudi Arabia", "អារ៉ាប៊ីសាអូឌីត"),
        Country("Slovakia", "Slovakia", "ស្លូវ៉ាគី"),
        Country("Spanish", "Spain", "អេស្ប៉ាញ"),
        Country("Syrian", "Syria", "ស៊ីរី"),
        Country("Thai", "Thailand", "ថៃ"),
        Country("Tunisian", "Tunisia", "ទុយនីស៊ី"),
        Country("Turkish", "Turkey", "តួកគី"),
        Country("Ukrainian", "Ukraine", "អ៊ុយក្រែន"),
        Country("British", "United Kingdom", "ចក្រភពអង់គ្លេស"),
        Country("United States", "United States", "សហរដ្ឋអាមេរិក"),
        Country("Uruguayan", "Uruguay", "អ៊ុយរូហ្គាយ"),
        Country("Venezuela", "Venezuela", "វ៉េណេស៊ុយអេឡា"),
        Country("Vietnamese", "Vietnam", "វៀតណាម")
    )

    /** Country name for a TheMealDB area value ("Thai" → "Thailand"); empty when unknown. */
    fun labelFor(area: String?, khmer: Boolean): String =
        ALL.firstOrNull { it.api.equals(area, ignoreCase = true) }?.label(khmer) ?: area.orEmpty()
}

enum class SortBy(@StringRes val label: Int) {
    NAME(R.string.sort_name),
    QUICKEST(R.string.sort_quickest),
    FEWEST_INGREDIENTS(R.string.sort_fewest),
    SERVES_MOST(R.string.sort_serves)
}

/** A card plus the facts we can sort by (null = the source doesn't say). */
data class BrowseItem(
    val card: RecipeCardItem,
    val cookMinutes: Int? = null,
    val ingredientCount: Int? = null,
    val serves: Int? = null
)

/** Hides drink names that aren't suitable for a family recipe app. */
private val rudeWords = Regex(
    """\b(ass|asshole|screwed|orgasm\w*|sex\w*|bitch\w*|fuck\w*|slut\w*|nipple\w*|dick\w*|pussy|penis|boob\w*|cum)\b""",
    RegexOption.IGNORE_CASE
)

fun isSuitableName(name: String?): Boolean = !name.isNullOrBlank() && !rudeWords.containsMatchIn(name)
