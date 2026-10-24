package com.kroeungtes.app.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.kroeungtes.app.R
import com.kroeungtes.app.ui.common.pop

/** Bell tab from the mockup. Shows an empty state until push notifications are added (phase 2). */
class NotificationsFragment : Fragment(R.layout.fragment_notifications) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.tv_notifications_empty).pop()
    }
}
