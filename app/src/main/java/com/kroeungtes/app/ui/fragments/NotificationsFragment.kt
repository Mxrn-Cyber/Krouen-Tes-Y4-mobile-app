package com.kroeungtes.app.ui.fragments

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.CycleInterpolator
import androidx.fragment.app.Fragment
import com.kroeungtes.app.R
import com.kroeungtes.app.ui.activities.MainActivity
import com.kroeungtes.app.ui.common.pop

/** Bell tab from the mockup. Friendly empty state until push notifications are added (phase 2). */
class NotificationsFragment : Fragment(R.layout.fragment_notifications) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val circle = view.findViewById<View>(R.id.bell_circle)
        val bell = view.findViewById<View>(R.id.img_bell)
        circle.pop()
        // Little bell wiggle; tap the bell to ring it again
        ring(bell)
        circle.setOnClickListener { ring(bell) }

        view.findViewById<View>(R.id.btn_explore).setOnClickListener {
            (activity as? MainActivity)?.binding?.bottomNavigation?.selectedItemId = R.id.homeFragment
        }
    }

    private fun ring(bell: View) {
        ObjectAnimator.ofFloat(bell, View.ROTATION, 0f, 14f).apply {
            duration = 700
            interpolator = CycleInterpolator(3f)
            start()
        }
    }
}
