package com.kroeungtes.app.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.RecipeCardAdapter
import com.kroeungtes.app.data.local.MyRecipeStore
import com.kroeungtes.app.data.local.ProfileStore
import com.kroeungtes.app.data.model.MealDB
import com.kroeungtes.app.data.model.MyRecipe
import com.kroeungtes.app.data.model.RecipeCardItem
import com.kroeungtes.app.databinding.DialogEditProfileBinding
import com.kroeungtes.app.databinding.FragmentProfileBinding
import com.kroeungtes.app.ui.activities.MealDetailesActivity
import com.kroeungtes.app.ui.auth.LoginActivity
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.common.fadeVisible
import com.kroeungtes.app.ui.common.pop
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_ID
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_STR
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_THUMB
import com.kroeungtes.app.viewmodel.DetailsMVVM

/** My profile (slide screen): photo, Edit profile, stats, Recipe / Saved tabs, Log out. */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var detailsMvvm: DetailsMVVM
    private lateinit var adapter: RecipeCardAdapter

    private var myRecipes: List<MyRecipe> = emptyList()
    private var saved: List<MealDB> = emptyList()
    private var showingSaved = false

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            MyRecipeStore.copyPhoto(requireContext(), uri, "avatar_${System.currentTimeMillis()}")?.let { path ->
                ProfileStore.saveAvatar(requireContext(), path)
                bindProfile()
                binding.imgAvatar.pop()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detailsMvvm = ViewModelProviders.of(this)[DetailsMVVM::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = RecipeCardAdapter(onClick = { open(it) })
        binding.rvProfile.layoutManager = GridLayoutManager(requireContext(), resources.getInteger(R.integer.wishlist_columns))
        binding.rvProfile.adapter = adapter

        binding.profileTabs.addTab(binding.profileTabs.newTab().setText(R.string.tab_recipe))
        binding.profileTabs.addTab(binding.profileTabs.newTab().setText(R.string.tab_saved))
        binding.profileTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showingSaved = tab.position == 1
                showList(animate = true)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.imgAvatar.setOnClickListener { pickAvatar.launch("image/*") }
        binding.btnEditProfile.setOnClickListener { editProfile() }
        binding.btnProfileMore.setOnClickListener { showMenu(it) }

        bindProfile()
        MyRecipeStore.observe(requireContext()).observe(viewLifecycleOwner) { list ->
            myRecipes = list.orEmpty()
            updateStats()
            showList(animate = false)
        }
        detailsMvvm.observeSaveMeal().observe(viewLifecycleOwner) { list ->
            saved = list.orEmpty().reversed()
            updateStats()
            showList(animate = false)
        }
    }

    private fun defaultName(): String =
        FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@") ?: getString(R.string.default_author)

    private fun bindProfile() {
        val profile = ProfileStore.load(requireContext(), defaultName())
        binding.tvProfileName.text = profile.name
        binding.tvProfileEmail.text = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        binding.tvProfileBio.text = profile.bio.ifBlank { getString(R.string.bio_empty) }
        Glide.with(this).load(profile.avatarPath ?: R.drawable.logo_krouen_tes)
            .placeholder(R.drawable.logo_krouen_tes).into(binding.imgAvatar)
    }

    private fun updateStats() {
        binding.tvStatRecipes.text = myRecipes.size.toString()
        binding.tvStatSaved.text = saved.size.toString()
        binding.tvStatVideos.text = myRecipes.count { !it.videoUrl.isNullOrBlank() }.toString()
    }

    private fun showList(animate: Boolean) {
        val items = if (showingSaved) saved.map { RecipeCardItem.from(it, getString(R.string.your_wishlist)) }
        else {
            val me = ProfileStore.load(requireContext(), defaultName()).name
            myRecipes.map {
                RecipeCardItem.from(it, getString(R.string.serves_value, it.serves) + " · " +
                    getString(R.string.cook_time_value, it.cookMinutes), me)
            }
        }
        adapter.submit(items)
        if (animate) binding.rvProfile.scheduleLayoutAnimation()
        binding.tvProfileEmpty.text = getString(if (showingSaved) R.string.wishlist_empty else R.string.no_my_recipes)
        binding.tvProfileEmpty.fadeVisible(items.isEmpty())
    }

    private fun editProfile() {
        val current = ProfileStore.load(requireContext(), defaultName())
        val dialog = DialogEditProfileBinding.inflate(layoutInflater)
        dialog.edProfileName.setText(current.name)
        dialog.edProfileBio.setText(current.bio)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_profile)
            .setView(dialog.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialog.edProfileName.text?.toString()?.trim().orEmpty().ifBlank { defaultName() }
                ProfileStore.save(requireContext(), name, dialog.edProfileBio.text?.toString()?.trim().orEmpty())
                bindProfile()
                showList(animate = false)
                binding.profileCard.pop()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 1, 0, R.string.change_photo)
            menu.add(0, 2, 1, R.string.log_out)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> pickAvatar.launch("image/*")
                    2 -> logOut()
                }
                true
            }
        }.show()
    }

    private fun logOut() {
        FirebaseAuth.getInstance().signOut()
        startActivity(Intent(requireContext(), LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        requireActivity().finish()
    }

    private fun open(item: RecipeCardItem) {
        Motion.open(requireActivity(), Intent(requireContext(), MealDetailesActivity::class.java).apply {
            putExtra(MEAL_ID, item.id)
            putExtra(MEAL_STR, item.name)
            putExtra(MEAL_THUMB, item.image.orEmpty())
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
