package com.kroeungtes.app.ui.fragments

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.RecentRecipeAdapter
import com.kroeungtes.app.adapters.RecipeCardAdapter
import com.kroeungtes.app.data.browse.FoodType
import com.kroeungtes.app.data.cloud.CloudContent
import com.kroeungtes.app.data.local.KhmerRecipes
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
import com.kroeungtes.app.viewmodel.DetailsMVVM
import com.kroeungtes.app.viewmodel.MainFragMVVM

/**
 * Home (User Flow step 3). Everything on it comes from the admin dashboard:
 * Featured dishes, a row per category chip, the Wishlist and the newest recipes.
 */
class HomeFragment : Fragment() {

    companion object {
        const val MEAL_ID = "com.kroeungtes.app.ui.fragments.idMeal"
        const val MEAL_NAME = "com.kroeungtes.app.ui.fragments.nameMeal"
        const val MEAL_THUMB = "com.kroeungtes.app.ui.fragments.thumbMeal"
        const val CATEGORY_NAME = " com.kroeungtes.app.ui.fragments.categoryName"
        const val MEAL_STR = " com.kroeungtes.app.ui.fragments.strMeal"
        const val MEAL_AREA = " com.kroeungtes.app.ui.fragments.strArea"

        /** How many of the newest recipes the "Recent recipe" row shows. */
        private const val RECENT_COUNT = 12
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeMvvm: MainFragMVVM
    private lateinit var detailMvvm: DetailsMVVM

    private lateinit var khmerAdapter: RecipeCardAdapter
    private lateinit var trendingAdapter: RecipeCardAdapter
    private lateinit var wishlistAdapter: RecipeCardAdapter
    private val recentAdapter = RecentRecipeAdapter(onClick = { openRecipe(it) })
    private var skeletonPulse: ValueAnimator? = null

    /** Chip view id → category; and what the chips / category row last showed. */
    private val chipTypes = mutableMapOf<Int, FoodType>()
    private var chipsFor: List<FoodType>? = null
    private var shownCategory: String? = null
    private val khmer: Boolean get() = KhmerRecipes.isKhmerLanguage()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeMvvm = ViewModelProvider(this)[MainFragMVVM::class.java]
        detailMvvm = ViewModelProvider(this)[DetailsMVVM::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chipsFor = null
        shownCategory = null
        setUpLists()
        setUpClicks()
        observeData()
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
        fun cards() = RecipeCardAdapter(
            onClick = { openRecipe(it) },
            onSaveToggle = { item, saved -> toggleSave(item, saved) },
            fixedWidthPx = cardWidth
        )
        khmerAdapter = cards()
        trendingAdapter = cards()
        wishlistAdapter = cards()
        binding.recViewKhmer.adapter = khmerAdapter
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
        binding.btnKhmerAll.setOnClickListener { openList(KhmerRecipes.LIST_KEY) }
        binding.btnTrendingAll.setOnClickListener { shownCategory?.let { openList(it) } }
        binding.btnRecentAll.setOnClickListener { openList(KhmerRecipes.NEWEST_KEY) }
        binding.btnWishlistAll.setOnClickListener {
            (activity as? MainActivity)?.selectTab(R.id.favoriteMeals)
        }
        // Content is live already; pulling just redraws it
        binding.swipeRefresh.setColorSchemeResources(R.color.brand_green)
        binding.swipeRefresh.setOnRefreshListener {
            showAll()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun observeData() {
        // Anything admins change in the dashboard shows up without a restart
        KhmerRecipes.changes.observe(viewLifecycleOwner) { showAll() }
        CloudContent.home.observe(viewLifecycleOwner) { showAll() }
        CloudContent.loaded.observe(viewLifecycleOwner) { showAll() }
        CloudContent.categories.observe(viewLifecycleOwner) { showAll() }

        detailMvvm.observeSaveMeal().observe(viewLifecycleOwner) { saved ->
            val ids = saved.orEmpty().map { it.mealId.toString() }.toSet()
            khmerAdapter.setSavedIds(ids)
            trendingAdapter.setSavedIds(ids)
            wishlistAdapter.setSavedIds(ids)
        }
        detailMvvm.savedRecipes.observe(viewLifecycleOwner) { list ->
            wishlistAdapter.submit(list.orEmpty().map { RecipeCardItem.from(it, getString(R.string.your_wishlist)) })
            binding.wishlistSection.fadeVisible(!list.isNullOrEmpty())
        }
    }

    private fun showAll() {
        val ctx = context ?: return
        val b = _binding ?: return
        val loaded = CloudContent.loaded.value == true
        showSkeleton(!loaded)

        val featured = CloudContent.homeKhmerDishes(ctx)
        khmerAdapter.submit(featured.map { RecipeCardItem.from(it) })
        b.tvHomeEmpty.visibility = if (loaded && featured.isEmpty()) View.VISIBLE else View.GONE
        b.tvHomeEmpty.text = getString(R.string.home_empty)
        b.btnKhmerAll.visibility = if (featured.isEmpty()) View.GONE else View.VISIBLE

        // Category chips + the row for the chosen one (18+ categories stay on the Categories tab)
        val categories = CloudContent.currentCategories().filter { !it.adultOnly }
        if (categories != chipsFor) {
            chipsFor = categories
            buildChips(categories)
        }
        val selected = homeMvvm.selectedCategory(categories)
        checkChip(selected)
        b.chipScroll.visibility = if (categories.isEmpty()) View.GONE else View.VISIBLE
        b.trendingHeader.visibility = if (selected == null) View.GONE else View.VISIBLE
        b.recViewMealsPopular.visibility = if (selected == null || !loaded) View.GONE else View.VISIBLE
        if (selected != null) {
            b.tvTrending.text = selected.chipLabel(khmer)
            val dishes = KhmerRecipes.inCategory(ctx, selected.id)
            trendingAdapter.submit(dishes.map { RecipeCardItem.from(it, selected.label(khmer)) })
            if (shownCategory != selected.id) {
                shownCategory = selected.id
                b.recViewMealsPopular.scheduleLayoutAnimation()
                b.recViewMealsPopular.scrollToPosition(0)
            }
        } else {
            shownCategory = null
        }

        val newest = KhmerRecipes.newest(ctx).take(RECENT_COUNT)
        recentAdapter.submit(newest.map { RecipeCardItem.from(it) })
    }

    /** One chip per dashboard category; the selected one fills the row below. */
    private fun buildChips(categories: List<FoodType>) {
        val group = binding.chipGroup
        group.removeAllViews()
        chipTypes.clear()
        categories.forEach { type ->
            val chip = layoutInflater.inflate(R.layout.item_category_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = type.chipLabel(khmer)
            chipTypes[chip.id] = type
            chip.setOnClickListener {
                if (shownCategory != type.id) {
                    homeMvvm.pickedCategory = type.id
                    showAll()
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

    private fun checkChip(selected: FoodType?) {
        val group = _binding?.chipGroup ?: return
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            chip.isChecked = chipTypes[chip.id]?.id == selected?.id
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

    /** Pulsing grey cards until the recipes arrive on the very first start. */
    private fun showSkeleton(show: Boolean) {
        val b = _binding ?: return
        if (show == (b.skeleton.visibility == View.VISIBLE) && (skeletonPulse != null) == show) return
        skeletonPulse?.cancel()
        skeletonPulse = null
        b.skeleton.alpha = 1f
        if (show) {
            b.skeleton.visibility = View.VISIBLE
            skeletonPulse = b.skeleton.startSkeletonPulse()
        } else {
            b.skeleton.visibility = View.GONE
        }
    }

    private fun openRecipe(item: RecipeCardItem) {
        Motion.openRecipe(requireActivity(), Intent(requireContext(), MealDetailesActivity::class.java).apply {
            putExtra(MEAL_ID, item.id)
            putExtra(MEAL_STR, item.name)
            putExtra(MEAL_THUMB, item.image.orEmpty())
        })
    }

    private fun openList(key: String) {
        Motion.open(requireActivity(), Intent(requireContext(), MealActivity::class.java).putExtra(CATEGORY_NAME, key))
    }

    override fun onDestroyView() {
        skeletonPulse?.cancel()
        skeletonPulse = null
        super.onDestroyView()
        _binding = null
    }
}
