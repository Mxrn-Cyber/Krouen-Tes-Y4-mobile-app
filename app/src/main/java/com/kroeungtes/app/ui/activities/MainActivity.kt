package com.kroeungtes.app.ui.activities

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import android.view.animation.PathInterpolator
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.kroeungtes.app.R
import com.kroeungtes.app.databinding.ActivityMainBinding
import com.kroeungtes.app.ui.common.EdgeToEdge
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.common.pop

/**
 * Main screen with the curved bottom bar.
 * When you change tab, the dip in the bar and the green + slide along to sit above that tab,
 * the + spins, and the tab's name appears under it.
 */
class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private data class Tab(val destination: Int, val icon: Int, val label: Int)

    private val tabs = listOf(
        Tab(R.id.homeFragment, R.drawable.ic_nav_home, R.string.nav_home),
        Tab(R.id.categoryFragment, R.drawable.ic_nav_category, R.string.nav_category),
        Tab(R.id.favoriteMeals, R.drawable.ic_nav_bookmark, R.string.nav_wishlist),
        Tab(R.id.notificationsFragment, R.drawable.ic_nav_bell, R.string.nav_notifications),
        Tab(R.id.profileFragment, R.drawable.ic_nav_profile, R.string.nav_profile)
    )
    private val tabViews = mutableListOf<View>()
    private var selected = -1
    private var slide: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.light(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        // FragmentContainerView: get the NavController from the host fragment
        val navHost = supportFragmentManager.findFragmentById(R.id.frag_host) as NavHostFragment
        navController = navHost.navController

        buildTabs()
        // Keep the bar in sync with Back and with links like "See all" → Wishlist
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val index = tabs.indexOfFirst { it.destination == destination.id }
            if (index >= 0) moveTo(index, animate = selected >= 0)
        }

        // Green "+" opens Create Recipe (slides up)
        binding.fabCreate.setOnClickListener {
            it.pop()
            Motion.openFromBottom(this, Intent(this, CreateRecipeActivity::class.java))
        }

        // Place the bar pieces once the width is known
        binding.bottomBar.doOnLayout {
            val bar = binding.curvedBar
            bar.barTop = resources.getDimension(R.dimen.bar_top_space)
            bar.notchRadius = resources.getDimension(R.dimen.notch_radius)
            val fabRadius = binding.fabCreate.height / 2f
            val gap = 5f * resources.displayMetrics.density
            binding.fabCreate.translationY = bar.barTop + bar.notchDepth - gap - 2 * fabRadius
            val start = selected.coerceAtLeast(0)
            selected = -1
            moveTo(start, animate = false)
        }
        // If the bar gets wider or narrower later (e.g. the 3-button bar moves to the side),
        // put the dip and the + back above the selected tab.
        binding.tabRow.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft && selected >= 0) snapToSelected()
        }
    }

    /**
     * Edge-to-edge: the pages start below the status bar, and the white bar
     * reaches down behind the gesture bar so there's no gap at the bottom.
     */
    private fun applyInsets() {
        val barHeight = resources.getDimensionPixelSize(R.dimen.bottom_bar_height)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            binding.fragHost.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.fragHost.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = maxOf(barHeight + bars.bottom, keyboard)
            }
            binding.bottomBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = bars.left
                rightMargin = bars.right
            }
            binding.curvedBar.bottomInset = bars.bottom
            WindowInsetsCompat.CONSUMED
        }
    }

    /** Puts the dip and the + straight above the selected tab, without animation. */
    private fun snapToSelected() {
        val tabWidth = binding.tabRow.width / tabs.size.toFloat()
        val x = tabWidth * selected + tabWidth / 2f
        slide?.cancel()
        val fabWidth = binding.fabCreate.width.takeIf { it > 0 }
            ?: resources.getDimensionPixelSize(R.dimen.fab_size)
        binding.curvedBar.notchCenterX = x
        binding.fabCreate.translationX = x - fabWidth / 2f
    }

    /** Used by other screens, e.g. Home "See all" → Wishlist. */
    fun selectTab(destination: Int) {
        val index = tabs.indexOfFirst { it.destination == destination }
        if (index >= 0) navigateTo(index)
    }

    private fun buildTabs() {
        val inflater = LayoutInflater.from(this)
        tabs.forEachIndexed { index, tab ->
            val view = inflater.inflate(R.layout.item_nav_tab, binding.tabRow, false)
            view.findViewById<ImageView>(R.id.tab_icon).setImageResource(tab.icon)
            view.findViewById<TextView>(R.id.tab_label).setText(tab.label)
            view.contentDescription = getString(tab.label)
            view.setOnClickListener { navigateTo(index) }
            binding.tabRow.addView(view)
            tabViews.add(view)
        }
    }

    private fun navigateTo(index: Int) {
        val destination = tabs[index].destination
        if (navController.currentDestination?.id == destination) {
            binding.fabCreate.pop()
            return
        }
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(navController.graph.startDestinationId, false)
            .setEnterAnim(R.anim.tab_enter)
            .setExitAnim(R.anim.tab_exit)
            .setPopEnterAnim(R.anim.tab_enter)
            .setPopExitAnim(R.anim.tab_exit)
            .build()
        navController.navigate(destination, null, options)
    }

    /** Slides the dip + green button to tab [index] and swaps which icon/label shows. */
    private fun moveTo(index: Int, animate: Boolean) {
        if (index == selected || binding.tabRow.width == 0) {
            if (binding.tabRow.width == 0) selected = index
            return
        }
        val previous = selected
        selected = index

        val tabWidth = binding.tabRow.width / tabs.size.toFloat()
        val targetX = tabWidth * index + tabWidth / 2f
        val bar = binding.curvedBar
        val fab = binding.fabCreate
        val startX = if (bar.notchCenterX < 0f) targetX else bar.notchCenterX

        slide?.cancel()
        if (!animate || previous < 0) {
            bar.notchCenterX = targetX
            fab.translationX = targetX - fab.width / 2f
        } else {
            slide = ValueAnimator.ofFloat(startX, targetX).apply {
                duration = 420
                interpolator = OvershootInterpolator(0.9f)
                addUpdateListener {
                    val x = it.animatedValue as Float
                    bar.notchCenterX = x
                    fab.translationX = x - fab.width / 2f
                }
                start()
            }
            // The + spins while it travels, with a little squash
            fab.animate().cancel()
            fab.rotation = 0f
            fab.animate().rotationBy(if (index > previous) 180f else -180f)
                .setDuration(420).setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f)).start()
            fab.scaleX = 0.85f
            fab.scaleY = 0.85f
            fab.animate().scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(OvershootInterpolator(2.5f)).start()
        }

        tabViews.forEachIndexed { i, view ->
            val icon = view.findViewById<View>(R.id.tab_icon)
            val label = view.findViewById<View>(R.id.tab_label)
            val dp = resources.displayMetrics.density
            if (i == index) {
                // Icon dips under the button, the name appears
                icon.animate().alpha(0f).translationY(10 * dp).setStartDelay(0)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .setDuration(if (animate) 180 else 0).start()
                label.translationY = 6 * dp
                label.animate().alpha(1f).translationY(0f).setStartDelay(if (animate) 160 else 0)
                    .setDuration(if (animate) 220 else 0).start()
            } else {
                label.animate().alpha(0f).setStartDelay(0).setDuration(if (animate) 120 else 0).start()
                if (i == previous && animate) {
                    // The icon we left pops back up
                    icon.translationY = 10 * dp
                    icon.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(320)
                        .setInterpolator(OvershootInterpolator(2.2f)).start()
                } else {
                    icon.alpha = 1f
                    icon.translationY = 0f
                }
            }
        }
    }
}
