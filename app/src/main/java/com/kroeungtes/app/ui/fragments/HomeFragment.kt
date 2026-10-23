package com.kroeungtes.app.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.FavoriteMealsRecyclerAdapter
import com.kroeungtes.app.adapters.MostPopularRecyclerAdapter
import com.kroeungtes.app.adapters.OnItemClick
import com.kroeungtes.app.adapters.OnLongItemClick
import com.kroeungtes.app.adapters.RecentRecipeAdapter
import com.kroeungtes.app.data.model.Meal
import com.kroeungtes.app.data.model.MealDB
import com.kroeungtes.app.databinding.FragmentHomeBinding
import com.kroeungtes.app.ui.activities.MainActivity
import com.kroeungtes.app.ui.activities.MealActivity
import com.kroeungtes.app.ui.activities.MealDetailesActivity
import com.kroeungtes.app.ui.dialogs.MealBottomDialog
import com.kroeungtes.app.viewmodel.DetailsMVVM
import com.kroeungtes.app.viewmodel.MainFragMVVM
import com.kroeungtes.app.viewmodel.RECENT_INGREDIENT
import com.kroeungtes.app.viewmodel.TRENDING_INGREDIENT

/**
 * Home (User Flow step 3), laid out like the slide:
 * greeting → search bar → Trending → Your Wishlist → Recent recipe.
 */
class HomeFragment : Fragment() {

    companion object {
        const val MEAL_ID = "com.kroeungtes.app.ui.fragments.idMeal"
        const val MEAL_NAME = "com.kroeungtes.app.ui.fragments.nameMeal"
        const val MEAL_THUMB = "com.kroeungtes.app.ui.fragments.thumbMeal"
        const val CATEGORY_NAME = " com.kroeungtes.app.ui.fragments.categoryName"
        const val MEAL_STR = " com.kroeungtes.app.ui.fragments.strMeal"
        const val MEAL_AREA = " com.kroeungtes.app.ui.fragments.strArea"
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeMvvm: MainFragMVVM
    private lateinit var detailMvvm: DetailsMVVM

    private val trendingAdapter = MostPopularRecyclerAdapter()
    private val wishlistAdapter = FavoriteMealsRecyclerAdapter()
    private val recentAdapter = RecentRecipeAdapter(
        onClick = { openRecipe(it.idMeal, it.strMeal, it.strMealThumb) },
        onLongClick = { detailMvvm.getMealByIdBottomSheet(it.idMeal) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeMvvm = ViewModelProviders.of(this)[MainFragMVVM::class.java]
        detailMvvm = ViewModelProviders.of(this)[DetailsMVVM::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setLoading(true)
        setUpLists()
        setUpClicks()
        observeData()
    }

    private fun setUpLists() {
        trendingAdapter.subtitle = getString(R.string.recipe_with, TRENDING_INGREDIENT.replaceFirstChar { it.uppercase() })
        trendingAdapter.setOnClickListener(object : OnItemClick {
            override fun onItemClick(meal: Meal) = openRecipe(meal.idMeal, meal.strMeal, meal.strMealThumb)
        })
        trendingAdapter.setOnLongCLickListener(object : OnLongItemClick {
            override fun onItemLongClick(meal: Meal) = detailMvvm.getMealByIdBottomSheet(meal.idMeal)
        })
        binding.recViewMealsPopular.adapter = trendingAdapter

        wishlistAdapter.fixedItemWidthPx = resources.getDimensionPixelSize(R.dimen.card_trending_width)
        wishlistAdapter.setOnFavoriteMealClickListener(object : FavoriteMealsRecyclerAdapter.OnFavoriteClickListener {
            override fun onFavoriteClick(meal: MealDB) =
                openRecipe(meal.mealId.toString(), meal.mealName, meal.mealThumb)
        })
        wishlistAdapter.setOnFavoriteLongClickListener(object : FavoriteMealsRecyclerAdapter.OnFavoriteLongClickListener {
            override fun onFavoriteLongCLick(meal: MealDB) = detailMvvm.getMealByIdBottomSheet(meal.mealId.toString())
        })
        binding.recViewWishlist.adapter = wishlistAdapter

        binding.recViewRecent.adapter = recentAdapter
    }

    private fun setUpClicks() {
        binding.searchBar.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_searchFragment)
        }
        binding.btnTrendingAll.setOnClickListener { openList(TRENDING_INGREDIENT) }
        binding.btnRecentAll.setOnClickListener { openList(RECENT_INGREDIENT) }
        binding.btnWishlistAll.setOnClickListener {
            (activity as? MainActivity)?.binding?.bottomNavigation?.selectedItemId = R.id.favoriteMeals
        }
    }

    private fun observeData() {
        homeMvvm.observeMealByCategory().observe(viewLifecycleOwner) { response ->
            trendingAdapter.setMealList(response?.meals.orEmpty())
            setLoading(false)
        }
        homeMvvm.observeRecentMeals().observe(viewLifecycleOwner) { response ->
            // Show the newest-looking part of the list first
            recentAdapter.submit(response?.meals.orEmpty().reversed().take(12))
        }
        detailMvvm.observeSaveMeal().observe(viewLifecycleOwner) { saved ->
            val list = saved.orEmpty()
            wishlistAdapter.setFavoriteMealsList(list)
            binding.wishlistSection.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }
        detailMvvm.observeMealBottomSheet().observe(viewLifecycleOwner) { details ->
            val meal = details?.firstOrNull() ?: return@observe
            MealBottomDialog().apply {
                arguments = Bundle().apply {
                    putString(CATEGORY_NAME, meal.strCategory)
                    putString(MEAL_AREA, meal.strArea)
                    putString(MEAL_NAME, meal.strMeal)
                    putString(MEAL_THUMB, meal.strMealThumb)
                    putString(MEAL_ID, meal.idMeal)
                }
            }.show(childFragmentManager, "BottomSheetDialog")
        }
    }

    private fun openRecipe(id: String, name: String, thumb: String) {
        startActivity(Intent(requireContext(), MealDetailesActivity::class.java).apply {
            putExtra(MEAL_ID, id)
            putExtra(MEAL_STR, name)
            putExtra(MEAL_THUMB, thumb)
        })
    }

    private fun openList(filter: String) {
        startActivity(Intent(requireContext(), MealActivity::class.java).putExtra(CATEGORY_NAME, filter))
    }

    private fun setLoading(loading: Boolean) {
        binding.loading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.homeContent.alpha = if (loading) 0f else 1f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
