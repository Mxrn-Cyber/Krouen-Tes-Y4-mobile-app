package com.kroeungtes.app.ui.activities

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.IngredientAdapter
import com.kroeungtes.app.adapters.StepAdapter
import com.kroeungtes.app.data.browse.Region
import com.kroeungtes.app.data.cloud.CloudContent
import com.kroeungtes.app.data.local.KhmerRecipes
import com.kroeungtes.app.data.local.MyRecipeStore
import com.kroeungtes.app.data.local.ProfileStore
import com.kroeungtes.app.data.model.KhmerRecipe
import com.kroeungtes.app.data.model.MyRecipe
import com.kroeungtes.app.databinding.ActivityMealDetailesBinding
import com.kroeungtes.app.ui.common.EdgeToEdge
import com.kroeungtes.app.ui.common.Images
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.common.padForSystemBars
import com.kroeungtes.app.ui.common.pop
import com.kroeungtes.app.ui.common.riseIn
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_ID
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_STR
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_THUMB
import com.kroeungtes.app.viewmodel.DetailsMVVM

/**
 * Recipe page. Works for the recipes published in the admin dashboard (negative ids, from
 * KhmerRecipes) and recipes the user made on this phone (ids starting with "local-").
 */
class MealDetailesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMealDetailesBinding
    private lateinit var detailsMVVM: DetailsMVVM
    private var mealId = ""
    private var mealStr = ""
    private var mealThumb = ""
    private var ytUrl = ""
    private var portion = 1

    /** True when the photo flew in from a card (shared-element transition). */
    private var usedShared = false
    private var enterStarted = false

    private val stepAdapter = StepAdapter()
    private val ingredientAdapter = IngredientAdapter { checked, total ->
        binding.tvIngredientProgress.text =
            if (checked == 0) getString(R.string.tap_to_tick) else getString(R.string.ingredients_ready, checked, total)
    }

    private val isLocal get() = mealId.startsWith(MyRecipeStore.LOCAL_PREFIX)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.light(this)
        detailsMVVM = ViewModelProvider(this)[DetailsMVVM::class.java]
        binding = ActivityMealDetailesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.detailScroll.padForSystemBars(top = false)
        setUpStatusBarIcons()

        // Photo "flies" in from the card: wait until it has loaded, then start the transition
        intent.getStringExtra(Motion.EXTRA_SHARED_NAME)?.let { name ->
            usedShared = true
            binding.imgMealDetail.transitionName = name
            supportPostponeEnterTransition()
            binding.imgMealDetail.postDelayed({ startEnter() }, 600) // never wait longer than this
        }
        // Text and lists rise in just after the photo
        binding.detailColumn.riseIn(delay = if (usedShared) 250 else 80, distanceDp = 36f)

        mealId = intent.getStringExtra(MEAL_ID).orEmpty()
        mealStr = intent.getStringExtra(MEAL_STR).orEmpty()
        mealThumb = intent.getStringExtra(MEAL_THUMB).orEmpty()

        binding.rvIngredients.adapter = ingredientAdapter
        binding.rvSteps.adapter = stepAdapter
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnMore.setOnClickListener { showMoreMenu(it) }
        binding.btnPortionMinus.setOnClickListener { changePortion(-1) }
        binding.btnPortionPlus.setOnClickListener { changePortion(+1) }
        binding.imgYoutube.setOnClickListener {
            if (ytUrl.isNotBlank()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)))
        }
        updatePortionLabel()
        showHeader(mealStr, mealThumb)

        when {
            isLocal -> showLocalRecipe()
            KhmerRecipes.isKhmerId(mealId) -> waitForRecipe()
            else -> finish() // e.g. an old link to a dish that isn't in the app any more
        }
    }

    private fun setUpSaveButton() {
        binding.btnSave.setOnClickListener {
            val nowSaved = !isMealSavedInDatabase()
            if (nowSaved) saveMeal() else detailsMVVM.deleteMealById(mealId)
            showSaved(nowSaved)
            binding.btnSave.pop()
            Snackbar.make(binding.root,
                if (nowSaved) R.string.saved_to_wishlist else R.string.removed_from_wishlist,
                Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun saveMeal() {
        detailsMVVM.quickSave(mealId, mealStr, mealThumb)
    }

    private fun isMealSavedInDatabase(): Boolean = detailsMVVM.isMealSavedInDatabase(mealId)

    /** Green "Save" button (the slide shows "Follow" here). */
    private fun showSaved(saved: Boolean) {
        binding.btnSave.apply {
            text = getString(if (saved) R.string.saved else R.string.save)
            setIconResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
            setBackgroundColor(getColor(if (saved) R.color.brand_green else R.color.brand_splash))
        }
    }

    // ---------- Recipe from the dashboard ----------

    /**
     * Recipes arrive from Firestore after the app starts (e.g. when Android reopens this page
     * after closing the app), so wait for them; close only if it really isn't published any more.
     */
    private fun waitForRecipe() {
        var shown = false
        val check = {
            if (!shown && !isFinishing) {
                val recipe = KhmerRecipes.find(this, mealId.toInt())
                when {
                    recipe != null -> { shown = true; showKhmerRecipe(recipe) }
                    CloudContent.loaded.value == true -> finish()
                }
            }
        }
        CloudContent.loaded.observe(this) { check() }
        KhmerRecipes.changes.observe(this) { check() }
    }

    private fun showKhmerRecipe(recipe: KhmerRecipe) {
        mealStr = recipe.name
        showHeader(recipe.name, recipe.photoUrl.orEmpty())
        showInfo(recipe.category, getString(Region.of(recipe.region).label))
        if (recipe.otherName.isNotBlank()) {
            binding.tvTitleOther.text = recipe.otherName
            binding.tvTitleOther.visibility = View.VISIBLE
        }
        if (recipe.description.isNotBlank()) {
            binding.tvDescription.text = recipe.description
            binding.tvDescription.visibility = View.VISIBLE
        }
        if (!recipe.photoAuthor.isNullOrBlank()) {
            binding.tvPhotoCredit.text = getString(R.string.photo_credit, recipe.photoAuthor, recipe.photoLicense.orEmpty())
            binding.tvPhotoCredit.visibility = View.VISIBLE
            recipe.photoPage?.let { page ->
                binding.tvPhotoCredit.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page))) }
            }
        }
        binding.tvAuthorName.text = getString(R.string.krouen_pick)
        ingredientAdapter.submit(recipe.ingredients)
        stepAdapter.submit(recipe.steps)
        binding.rvIngredients.scheduleLayoutAnimation()
        binding.imgYoutube.visibility = View.GONE
        showSaved(isMealSavedInDatabase())
        binding.btnSave.visibility = View.VISIBLE
        setUpSaveButton()
    }

    // ---------- Recipe made by the user ----------

    private fun showLocalRecipe() {
        val recipe: MyRecipe = MyRecipeStore.find(this, mealId) ?: run { finish(); return }
        mealStr = recipe.title
        ytUrl = recipe.videoUrl.orEmpty()
        showHeader(recipe.title, recipe.photoPath.orEmpty())
        showInfo(getString(R.string.serves_value, recipe.serves), getString(R.string.cook_time_value, recipe.cookMinutes))
        binding.tvAreaInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        val profile = ProfileStore.load(this, getString(R.string.default_author))
        binding.tvAuthorName.text = profile.name
        binding.tvAuthorPlace.text = getString(R.string.my_recipe)
        profile.avatarPath?.let { Glide.with(this).load(it).into(binding.imgAuthor) }
        ingredientAdapter.submit(recipe.ingredients)
        stepAdapter.submit(recipe.steps)
        binding.imgYoutube.visibility = if (ytUrl.isBlank()) View.GONE else View.VISIBLE
        binding.btnSave.visibility = View.GONE
    }

    // ---------- Shared UI ----------

    private fun showHeader(name: String, image: String) {
        binding.tvTitle.text = getString(R.string.how_to_make, name)
        binding.collapsingToolbar.title = name
        Glide.with(this).load(Images.model(image)).placeholder(R.drawable.placeholder_image).error(R.drawable.placeholder_image)
            .dontAnimate()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                    startEnter()
                    return false
                }

                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?,
                                             dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    startEnter()
                    return false
                }
            })
            .into(binding.imgMealDetail)
    }

    private fun showInfo(first: String, second: String) {
        binding.tvCategoryInfo.text = first
        binding.tvAreaInfo.text = second
        binding.tvAuthorPlace.text = second
        binding.tvCategoryInfo.visibility = if (first.isBlank()) View.GONE else View.VISIBLE
        binding.tvAreaInfo.visibility = if (second.isBlank()) View.GONE else View.VISIBLE
    }

    private fun changePortion(delta: Int) {
        val next = (portion + delta).coerceIn(1, 10)
        if (next == portion) return
        portion = next
        updatePortionLabel()
        binding.tvPortion.pop()
        ingredientAdapter.setFactor(portion)
    }

    private fun updatePortionLabel() {
        binding.tvPortion.text = getString(R.string.portion_value, portion)
        binding.btnPortionMinus.isEnabled = portion > 1
        binding.btnPortionMinus.alpha = if (portion > 1) 1f else 0.35f
    }

    /** "…" menu: share, and delete for the user's own recipes. */
    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.share_recipe)
            if (isLocal) menu.add(0, 2, 1, R.string.delete_recipe)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> shareRecipe()
                    2 -> {
                        MyRecipeStore.delete(this@MealDetailesActivity, mealId)
                        Snackbar.make(binding.root, R.string.recipe_deleted, Snackbar.LENGTH_SHORT).show()
                        finish()
                    }
                }
                true
            }
        }.show()
    }

    private fun shareRecipe() {
        val text = if (ytUrl.isNotBlank()) "$mealStr\n$ytUrl" else mealStr
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.share_recipe)))
    }

    private fun startEnter() {
        if (!usedShared || enterStarted) return
        enterStarted = true
        supportStartPostponedEnterTransition()
    }

    override fun finish() {
        super.finish()
        // With the flying photo, Android animates the way back itself
        if (!usedShared) Motion.closeToRight(this)
    }

    /** White status-bar icons over the photo; dark ones once the header has collapsed to white. */
    private fun setUpStatusBarIcons() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        var darkIcons = false
        binding.appBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, offset ->
            val bar = binding.collapsingToolbar
            val collapsed = bar.height + offset < bar.scrimVisibleHeightTrigger
            if (collapsed != darkIcons) {
                darkIcons = collapsed
                controller.isAppearanceLightStatusBars = collapsed
            }
        })
    }
}
