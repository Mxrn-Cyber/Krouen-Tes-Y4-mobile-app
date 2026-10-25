package com.kroeungtes.app.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.RecipeCardAdapter
import com.kroeungtes.app.data.browse.FoodType
import com.kroeungtes.app.data.browse.Region
import com.kroeungtes.app.data.browse.SortBy
import com.kroeungtes.app.data.cloud.CloudContent
import com.kroeungtes.app.data.local.AgeGate
import com.kroeungtes.app.data.local.KhmerRecipes
import com.kroeungtes.app.data.model.RecipeCardItem
import com.kroeungtes.app.databinding.FragmentBrowseBinding
import com.kroeungtes.app.ui.activities.MealDetailesActivity
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.common.aboveBar
import com.kroeungtes.app.ui.common.riseIn
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_ID
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_STR
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_THUMB
import com.kroeungtes.app.viewmodel.BrowseMVVM
import com.kroeungtes.app.viewmodel.DetailsMVVM

/**
 * Categories tab: the dashboard's recipes filtered by category (chips) and region, and sorted
 * (name, quickest, fewest ingredients, serves most). 18+ categories ask "18 or older?" first.
 */
class BrowseFragment : Fragment() {

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!

    private lateinit var browseMvvm: BrowseMVVM
    private lateinit var detailsMvvm: DetailsMVVM
    private lateinit var adapter: RecipeCardAdapter

    /** Chip view id → the category it stands for. */
    private val chipTypes = mutableMapOf<Int, FoodType>()
    /** The categories the chips were last built from (they come from the admin dashboard). */
    private var chipsFor: List<FoodType>? = null
    private val khmer: Boolean get() = KhmerRecipes.isKhmerLanguage()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Kept by the activity, so the chosen filters survive switching tabs
        browseMvvm = ViewModelProvider(requireActivity())[BrowseMVVM::class.java]
        detailsMvvm = ViewModelProvider(this)[DetailsMVVM::class.java]

        adapter = RecipeCardAdapter(
            onClick = { open(it) },
            onSaveToggle = { item, saved -> toggleSave(item, saved) }
        )
        binding.browseList.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.meal_list_columns), GridLayoutManager.VERTICAL, false)
        binding.browseList.adapter = adapter

        chipsFor = null
        CloudContent.categories.observe(viewLifecycleOwner) { list ->
            if (list == chipsFor) return@observe
            chipsFor = list
            buildTypeChips(list, browseMvvm.observeState().value?.type ?: FoodType.ALL)
        }
        binding.btnRegion.setOnClickListener { pickRegion() }
        binding.btnSort.setOnClickListener { pickSort() }

        detailsMvvm.observeSaveMeal().observe(viewLifecycleOwner) { saved ->
            adapter.setSavedIds(saved.orEmpty().map { it.mealId.toString() }.toSet())
        }
        browseMvvm.observeState().observe(viewLifecycleOwner) { render(it) }

        binding.tvBrowseTitle.riseIn(delay = 0, distanceDp = 16f)
        binding.filterRow.riseIn(delay = 80, distanceDp = 16f)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------- Filters ----------

    private fun buildTypeChips(categories: List<FoodType>, selected: FoodType) {
        val group = binding.typeChips
        group.setOnCheckedStateChangeListener(null)
        group.removeAllViews()
        chipTypes.clear()
        (listOf(FoodType.ALL) + categories).forEachIndexed { index, type ->
            val chip = layoutInflater.inflate(R.layout.item_category_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = type.chipLabel(khmer)
            chipTypes[chip.id] = type
            group.addView(chip)
            if (type.id == selected.id) group.check(chip.id)
            // Chips fade in one after another
            chip.alpha = 0f
            chip.animate().alpha(1f).setStartDelay(25L * index).setDuration(220).start()
        }
        group.setOnCheckedStateChangeListener { _, checkedIds ->
            val type = checkedIds.firstOrNull()?.let { chipTypes[it] } ?: return@setOnCheckedStateChangeListener
            onTypePicked(type)
        }
    }

    private fun onTypePicked(type: FoodType) {
        val ctx = context ?: return
        if (type.adultOnly && !AgeGate.isAdult(ctx)) {
            val previous = browseMvvm.observeState().value?.type ?: FoodType.ALL
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.age_check_title)
                .setMessage(R.string.age_check_message)
                .setPositiveButton(R.string.age_check_yes) { _, _ ->
                    AgeGate.confirmAdult(ctx)
                    browseMvvm.setType(type)
                }
                .setNegativeButton(R.string.age_check_no) { _, _ -> selectChip(previous) }
                .setOnCancelListener { selectChip(previous) }
                .show()
            return
        }
        browseMvvm.setType(type)
    }

    /** Puts the selection back on [type] (after "No" on the age check). */
    private fun selectChip(type: FoodType) {
        val id = chipTypes.entries.firstOrNull { it.value.id == type.id }?.key ?: return
        _binding?.typeChips?.check(id)
    }

    private fun pickRegion() {
        val current = browseMvvm.observeState().value?.region
        val regions = Region.entries
        val labels = listOf(getString(R.string.all_regions)) + regions.map { getString(it.label) }
        val checked = if (current == null) 0 else regions.indexOf(current) + 1
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pick_region)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                browseMvvm.setRegion(if (which == 0) null else regions[which - 1])
                dialog.dismiss()
            }
            .show()
    }

    private fun pickSort() {
        val current = browseMvvm.observeState().value?.sort ?: SortBy.NAME
        val options = SortBy.entries
        val labels = options.map { getString(it.label) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_title)
            .setSingleChoiceItems(labels.toTypedArray(), options.indexOf(current)) { dialog, which ->
                browseMvvm.setSort(options[which])
                dialog.dismiss()
            }
            .show()
    }

    // ---------- List ----------

    private fun render(s: BrowseMVVM.State) {
        val b = _binding ?: return
        // Keep the checked chip in step when the list goes back to "All" by itself
        val checked = chipTypes[b.typeChips.checkedChipId]
        if (checked != null && checked.id != s.type.id) selectChip(s.type)

        b.btnRegion.text = s.region?.let { getString(it.label) } ?: getString(R.string.all_regions)
        b.btnSort.text = getString(s.sort.label)
        b.browseLoading.visibility = if (s.loading) View.VISIBLE else View.GONE

        adapter.submit(s.items.map { it.card })
        if (!s.loading) b.browseList.scheduleLayoutAnimation()
        b.tvBrowseCount.text = resources.getQuantityString(R.plurals.recipe_count, s.items.size, s.items.size)
        b.tvBrowseEmpty.visibility = if (!s.loading && s.items.isEmpty()) View.VISIBLE else View.GONE

        // First start without internet: nothing to show until the recipes arrive
        b.tvBrowseHint.text = getString(R.string.content_waiting)
        b.tvBrowseHint.visibility = if (s.loading) View.VISIBLE else View.GONE
    }

    private fun toggleSave(item: RecipeCardItem, saved: Boolean) {
        if (saved) detailsMvvm.quickSave(item.id, item.name, item.image) else detailsMvvm.deleteMealById(item.id)
        Snackbar.make(binding.root,
            if (saved) R.string.saved_to_wishlist else R.string.removed_from_wishlist,
            Snackbar.LENGTH_SHORT
        ).aboveBar().setAction(R.string.undo) {
            if (saved) detailsMvvm.deleteMealById(item.id) else detailsMvvm.quickSave(item.id, item.name, item.image)
        }.show()
    }

    private fun open(item: RecipeCardItem) {
        Motion.openRecipe(requireActivity(), Intent(requireContext(), MealDetailesActivity::class.java).apply {
            putExtra(MEAL_ID, item.id)
            putExtra(MEAL_STR, item.name)
            putExtra(MEAL_THUMB, item.image.orEmpty())
        })
    }
}
