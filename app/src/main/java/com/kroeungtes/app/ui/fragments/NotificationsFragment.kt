package com.kroeungtes.app.ui.fragments

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.CycleInterpolator
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.NotificationAdapter
import com.kroeungtes.app.data.cloud.CloudContent
import com.kroeungtes.app.data.local.KhmerRecipes
import com.kroeungtes.app.data.local.NotificationsSeen
import com.kroeungtes.app.ui.activities.MainActivity
import com.kroeungtes.app.ui.activities.MealDetailesActivity
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.common.pop
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_ID
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_STR
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_THUMB

/**
 * Bell tab: news the Krouen Tes team sends from the admin dashboard, newest first.
 * A friendly empty state when there is none.
 */
class NotificationsFragment : Fragment(R.layout.fragment_notifications) {

    /** Notifications newer than this get a "new" dot (taken when the tab opens). */
    private var seenBefore = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        seenBefore = NotificationsSeen.seenAt(requireContext())

        val list = view.findViewById<RecyclerView>(R.id.notifications_list)
        val empty = view.findViewById<View>(R.id.notifications_empty)
        val adapter = NotificationAdapter(
            khmer = KhmerRecipes.isKhmerLanguage(),
            canOpen = { note -> note.recipeId?.let { KhmerRecipes.find(requireContext(), it) } != null },
            onOpen = { note -> note.recipeId?.let { openRecipe(it) } }
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        val circle = view.findViewById<View>(R.id.bell_circle)
        val bell = view.findViewById<View>(R.id.img_bell)
        view.findViewById<View>(R.id.btn_explore).setOnClickListener {
            (activity as? MainActivity)?.selectTab(R.id.homeFragment)
        }
        circle.setOnClickListener { ring(bell) }

        var shownEmpty = false
        CloudContent.announcements.observe(viewLifecycleOwner) { notes ->
            adapter.submit(notes, seenBefore)
            list.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
            empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            if (notes.isEmpty() && !shownEmpty) {
                shownEmpty = true
                circle.pop()
                // Little bell wiggle; tap the bell to ring it again
                ring(bell)
            }
            // Open on screen, so they count as seen
            if (isResumed) NotificationsSeen.markAllSeen(requireContext())
        }
        // Recipes may arrive after the notifications: refresh the "Open recipe" links
        KhmerRecipes.changes.observe(viewLifecycleOwner) { adapter.notifyDataSetChanged() }
    }

    override fun onResume() {
        super.onResume()
        NotificationsSeen.markAllSeen(requireContext())
    }

    private fun openRecipe(id: Int) {
        val recipe = KhmerRecipes.find(requireContext(), id) ?: return
        Motion.openRecipe(requireActivity(), Intent(requireContext(), MealDetailesActivity::class.java).apply {
            putExtra(MEAL_ID, recipe.id.toString())
            putExtra(MEAL_STR, recipe.name)
            putExtra(MEAL_THUMB, recipe.photoUrl.orEmpty())
        })
    }

    private fun ring(bell: View) {
        ObjectAnimator.ofFloat(bell, View.ROTATION, 0f, 14f).apply {
            duration = 700
            interpolator = CycleInterpolator(3f)
            start()
        }
    }
}
