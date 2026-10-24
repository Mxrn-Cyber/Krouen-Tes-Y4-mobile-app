package com.kroeungtes.app.ui.fragments

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.RecentRecipeAdapter
import com.kroeungtes.app.adapters.RecipeCardAdapter
import com.kroeungtes.app.data.local.KhmerRecipes
import com.kroeungtes.app.data.model.Category
import com.kroeungtes.app.data.model.RecipeCardItem
import com.kroeungtes.app.databinding.FragmentHomeBinding
import com.kroeungtes.app.ui.activities.MainActivity
import com.kroeungtes.app.ui.activities.MealActivity
import com.kroeungtes.app.ui.activities.MealDetailesActivity
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.common.aboveBar
import com.kroeungtes.app.ui.common.fadeVisible
import com.kroeungtes.app.ui.common.pressScale
import com.kroeungtes.app.ui.common.riseIn
import com.kroeungtes.app.ui.common.startSkeletonPulse
import com.kroeungtes.app.ui.dialogs.MealBottomDialog
import com.kroeungtes.app.viewmodel.DetailsMVVM
import com.kroeungtes.app.viewmodel.MainFragMVVM
import com.kroeungtes.app.viewmodel.RECENT_CATEGORY

/**
 * Home (User Flow step 3), laid out like the slide and made interactive:
 * pull to refresh, category chips, skeleton loading, save from cards, staggered fade-in.
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

    private lateinit var khmerAdapter: RecipeCardAdapter
    private lateinit var trendingAdapter: RecipeCardAdapter
    private lateinit var wishlistAdapter: RecipeCardAdapter
    private val recentAdapter = RecentRecipeAdapter(
        onClick = { openRecipe(it.idMeal, it.strMeal, it.strMealThumb) },
        onLongClick = { detailMvvm.getMealByIdBottomSheet(it.idMeal) }
    )
    private var skeletonPulse: ValueAnimator? = null
    private var chipsBuilt = false

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
        chipsBuilt = false
        setUpLists()
        setUpClicks()
        observeData()
        showSkeleton(true)
        if (savedInstanceState == null) {
            binding.tvGreeting.riseIn(delay = 0, distanceDp = 14f)
            binding.searchBar.riseIn(delay = 90, distanceDp = 14f)
        }
    }

    private fun setUpLists() {
        // Card width follows the screen: about 55% of the usable width so the next card peeks in
        // (190 dp on a 366 dp phone like the OPPO PLE110), never wider than the dimen for this size class.
        val usable = resources.displayMetrics.widthPixels - resources.getDimensionPixelSize(R.dimen.screen_padding)
        val cardWidth = minOf(resources.getDimensionPixelSize(R.dimen.card_trending_width), (usable * 0.55f).toInt())
        khmerAdapter = RecipeCardAdapter(
            onClick = { openRecipe(it.id, it.name, it.image.orEmpty()) },
            onSaveToggle = { item, saved -> toggleSave(item, saved) },
            fixedWidthPx = cardWidth
        )
        binding.recViewKhmer.adapter = khmerAdapter
        khmerAdapter.submit(KhmerRecipes.all(requireContext()).map { RecipeCardItem.from(it) })

        trendingAdapter = RecipeCardAdapter(
            onClick = { openRecipe(it.id, it.name, it.image.orEmpty()) },
            onLongClick = { detailMvvm.getMealByIdBottomSheet(it.id) },
            onSaveToggle = { item, saved -> toggleSave(item, saved) },
            fixedWidthPx = cardWidth
        )
        wishlistAdapter = RecipeCardAdapter(
            onClick = { openRecipe(it.id, it.name, it.image.orEmpty()) },
            onLongClick = { detailMvvm.getMealByIdBottomSheet(it.id) },
            onSaveToggle = { item, saved -> toggleSave(item, saved) },
            fixedWidthPx = cardWidth
        )
        binding.recViewMealsPopular.adapter = trendingAdapter
        binding.recViewWishlist.adapter = wishlistAdapter
        binding.recViewRecent.adapter = recentAdapter
    }

    private fun setUpClicks() {
        binding.searchBar.pressScale(0.98f)
        binding.searchBar.setOnClickListener {
            val nav = findNavController()
            if (nav.currentDestination?.id == R.id.homeFragment) nav.navigate(R.id.action_homeFragment_to_searchFragment)
        }
        binding.btnTrendingAll.setOnClickListener { openList(homeMvvm.trendingCategory) }
        binding.btnRecentAll.setOnClickListener { openList(RECENT_CATEGORY) }
        binding.btnKhmerAll.setOnClickListener { openList(KhmerRecipes.LIST_KEY) }
        binding.btnWishlistAll.setOnClickListener {
            (activity as? MainActivity)?.selectTab(R.id.favoriteMeals)
        }
        binding.swipeRefresh.setColorSchemeResources(R.color.brand_green)
        binding.swipeRefresh.setOnRefreshListener { homeMvvm.refresh() }
    }

    private fun observeData() {
        homeMvvm.observeCategories().observe(viewLifecycleOwner) { response ->
            buildChips(response?.categories.orEmpty())
        }
        homeMvvm.observeMealByCategory().observe(viewLifecycleOwner) { response ->
            val subtitle = getString(R.string.recipe_with, homeMvvm.trendingCategory)
            trendingAdapter.submit(response?.meals.orEmpty().map { RecipeCardItem.from(it, subtitle) })
            binding.recViewMealsPopular.scheduleLayoutAnimation()
            binding.recViewMealsPopular.scrollToPosition(0)
            showSkeleton(false)
            binding.swipeRefresh.isRefreshing = false
        }
        homeMvvm.observeRecentMeals().observe(viewLifecycleOwner) { response ->
            recentAdapter.submit(response?.meals.orEmpty().reversed().take(12))
            binding.recViewRecent.scheduleLayoutAnimation()
        }
        detailMvvm.observeSaveMeal().observe(viewLifecycleOwner) { saved ->
            val list = saved.orEmpty()
            val ids = list.map { it.mealId.toString() }.toSet()
            khmerAdapter.setSavedIds(ids)
            trendingAdapter.setSavedIds(ids)
            wishlistAdapter.setSavedIds(ids)
            wishlistAdapter.submit(list.reversed().map { RecipeCardItem.from(it, getString(R.string.your_wishlist)) })
            binding.wishlistSection.fadeVisible(list.isNotEmpty())
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
            detailMvvm.consumeBottomSheet()
        }
    }

    /** One chip per TheMealDB category; the selected one fills the Trending row. */
    private fun buildChips(categories: List<Category>) {
        if (chipsBuilt || categories.isEmpty()) return
        chipsBuilt = true
        val group = binding.chipGroup
        group.removeAllViews()
        categories.forEach { category ->
            val chip = layoutInflater.inflate(R.layout.item_category_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = category.strCategory
            chip.isChecked = category.strCategory == homeMvvm.trendingCategory
            chip.setOnClickListener {
                if (homeMvvm.trendingCategory != category.strCategory) {
                    showSkeleton(true)
                    homeMvvm.loadTrending(category.strCategory)
                }
            }
            group.addView(chip)
            // Chips pop in one after another
            chip.scaleX = 0.7f
            chip.scaleY = 0.7f
            chip.alpha = 0f
            chip.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(40L * group.childCount)
                .setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
        }
    }

    private fun toggleSave(item: RecipeCardItem, saved: Boolean) {
        if (saved) detailMvvm.quickSave(item.id, item.name, item.image)
        else detailMvvm.deleteMealById(item.id)
        Snackbar.make(binding.root,
            if (saved) R.string.saved_to_wishlist else R.string.removed_from_wishlist,
            Snackbar.LENGTH_SHORT
        ).aboveBar().setAction(R.string.undo) {
            if (saved) detailMvvm.deleteMealById(item.id) else detailMvvm.quickSave(item.id, item.name, item.image)
        }.show()
    }

    private fun showSkeleton(show: Boolean) {
        skeletonPulse?.cancel()
        binding.skeleton.alpha = 1f
        if (show) {
            binding.skeleton.visibility = View.VISIBLE
            binding.recViewMealsPopular.visibility = View.INVISIBLE
            skeletonPulse = binding.skeleton.startSkeletonPulse()
        } else {
            binding.skeleton.visibility = View.GONE
            binding.recViewMealsPopular.visibility = View.VISIBLE
        }
    }

    private fun openRecipe(id: String, name: String, thumb: String) {
        Motion.openRecipe(requireActivity(), Intent(requireContext(), MealDetailesActivity::class.java).apply {
            putExtra(MEAL_ID, id)
            putExtra(MEAL_STR, name)
            putExtra(MEAL_THUMB, thumb)
        })
    }

    private fun openList(category: String) {
        Motion.open(requireActivity(), Intent(requireContext(), MealActivity::class.java).putExtra(CATEGORY_NAME, category))
    }

    override fun onDestroyView() {
        skeletonPulse?.cancel()
        super.onDestroyView()
        _binding = null
    }
}
