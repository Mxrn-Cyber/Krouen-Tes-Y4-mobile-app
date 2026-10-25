package com.kroeungtes.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.kroeungtes.app.security.AppLock
import com.kroeungtes.app.security.LockActivity
import com.kroeungtes.app.ui.auth.LoginActivity
import com.kroeungtes.app.ui.splash.SplashActivity

/**
 * Watches every screen so the app lock can cover the app when it starts
 * or comes back from the background.
 */
class KrouenTesApp : Application() {

    private var startedScreens = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                if (startedScreens == 0) AppLock.onAppShown()
                startedScreens++
            }

            override fun onActivityResumed(activity: Activity) {
                if (!isProtected(activity)) return
                // Keep the Recent apps preview blank while the lock is on (follows the switch)
                AppLock.applyPrivacy(activity)
                if (AppLock.needsUnlock(activity)) {
                    activity.startActivity(
                        Intent(activity, LockActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    )
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedScreens = (startedScreens - 1).coerceAtLeast(0)
                if (startedScreens == 0 && !activity.isChangingConfigurations) AppLock.onAppHidden()
            }

            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** Screens shown before log-in, and the lock screen itself, are never covered. */
    private fun isProtected(activity: Activity): Boolean =
        activity !is LockActivity && activity !is SplashActivity && activity !is LoginActivity
}
