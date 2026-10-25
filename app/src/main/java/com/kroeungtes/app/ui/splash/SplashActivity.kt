package com.kroeungtes.app.ui.splash

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.kroeungtes.app.databinding.ActivitySplashBinding
import com.kroeungtes.app.security.LocalData
import com.kroeungtes.app.ui.activities.MainActivity
import com.kroeungtes.app.ui.auth.LoginActivity
import com.kroeungtes.app.ui.common.EdgeToEdge
import com.kroeungtes.app.ui.common.padForSystemBars
import com.kroeungtes.app.ui.common.riseIn
import androidx.core.util.Pair as UtilPair
import kotlinx.coroutines.launch

/**
 * Loading screen (User Flow step 1), with motion:
 * the white blob pops in with a twist, the wok drops from the top and bounces,
 * the name slides up from the bottom, steam rises from the wok and the loading dots bounce.
 * Then it opens Home (signed in) or Log in — the wok flies into the Log-in screen.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    /** Looping animations, stopped when the screen goes away. */
    private val loops = mutableListOf<Animator>()
    /** True once we've started the next screen with a shared-element transition. */
    private var leaving = false

    private val openNext = Runnable {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        // Accounts now sign in with a phone number + SMS code; older email sessions log in again
        val signedIn = user != null && !user.phoneNumber.isNullOrBlank()
        if (user != null && !signedIn) auth.signOut()
        if (signedIn && user != null) {
            lifecycleScope.launch {
                // Data left by a different account on this phone is removed first
                LocalData.claimFor(this@SplashActivity, user.uid)
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        } else {
            // The wok flies from here to the top of the Log-in screen
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this, UtilPair<View, String>(binding.splashWok, binding.splashWok.transitionName)
            )
            leaving = true
            startActivity(Intent(this, LoginActivity::class.java), options.toBundle())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.light(this)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForSystemBars()

        animateIn()
        handler.postDelayed(openNext, SPLASH_DELAY_MS)
    }

    private fun animateIn() {
        val dp = resources.displayMetrics.density

        // 1. Soft corner circles grow in
        listOf(binding.splashCircleTop, binding.splashCircleBottom).forEachIndexed { i, circle ->
            circle.scaleX = 0f
            circle.scaleY = 0f
            circle.animate().scaleX(1f).scaleY(1f).setStartDelay(80L * i).setDuration(700)
                .setInterpolator(DecelerateInterpolator()).start()
        }

        // 2. White blob pops in with a twist; the see-through one fades in and keeps turning
        binding.splashBlob.apply {
            alpha = 0f
            scaleX = 0.2f
            scaleY = 0.2f
            rotation = -25f
            animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(150).setDuration(750)
                .setInterpolator(OvershootInterpolator(1.4f)).start()
        }
        binding.splashBlobBack.alpha = 0f
        binding.splashBlobBack.animate().alpha(1f).setStartDelay(300).setDuration(600).start()
        loop(ObjectAnimator.ofFloat(binding.splashBlobBack, View.ROTATION, 0f, 360f).apply {
            duration = 14_000
            interpolator = LinearInterpolator()
        })

        // 3. The wok drops from the top and bounces into the blob
        binding.splashWok.apply {
            alpha = 0f
            translationY = -420 * dp
            animate().alpha(1f).translationY(0f).setStartDelay(350).setDuration(1000)
                .setInterpolator(BounceInterpolator()).start()
        }

        // 4. Little dots pop in, then float up and down
        listOf(binding.splashDot1, binding.splashDot2, binding.splashDot3).forEachIndexed { i, dot ->
            dot.scaleX = 0f
            dot.scaleY = 0f
            dot.animate().scaleX(1f).scaleY(1f).setStartDelay(700L + 120L * i).setDuration(400)
                .setInterpolator(OvershootInterpolator(3f)).start()
            loop(ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, -8 * dp).apply {
                duration = 1600L + 300L * i
                startDelay = 1100L + 120L * i
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            })
        }

        // 5. Name, Khmer name and tagline slide up from the bottom
        binding.splashTitle.riseIn(delay = 900, distanceDp = 70f, duration = 600)
        binding.splashTitleOther.riseIn(delay = 1050, distanceDp = 60f, duration = 600)
        binding.splashTagline.riseIn(delay = 1200, distanceDp = 50f, duration = 600)

        // 6. Steam puffs rise from the wok, again and again
        listOf(binding.splashSteam1, binding.splashSteam2, binding.splashSteam3).forEachIndexed { i, puff ->
            loop(ObjectAnimator.ofPropertyValuesHolder(
                puff,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -46 * dp),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 0.9f, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.5f, 1.4f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.5f, 1.4f)
            ).apply {
                duration = 1500
                startDelay = 1350L + 420L * i
                interpolator = DecelerateInterpolator()
            })
        }

        // 7. Loading dots bounce one after another
        binding.splashLoader.alpha = 0f
        binding.splashLoader.animate().alpha(1f).setStartDelay(1300).setDuration(300).start()
        listOf(binding.splashLoad1, binding.splashLoad2, binding.splashLoad3).forEachIndexed { i, dot ->
            loop(ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, -9 * dp).apply {
                duration = 360
                startDelay = 1300L + 120L * i
                repeatMode = ValueAnimator.REVERSE
                interpolator = DecelerateInterpolator()
            })
        }
    }

    /** Starts [animator] repeating forever and remembers it so it can be stopped. */
    private fun loop(animator: ValueAnimator) {
        animator.repeatCount = ValueAnimator.INFINITE
        animator.start()
        loops.add(animator)
    }

    override fun onStop() {
        super.onStop()
        if (leaving) {
            // Finish only after the Log-in screen is showing, so the wok can fly across first
            finish()
        } else {
            // User left during the intro: don't open the next screen in the background
            handler.removeCallbacks(openNext)
        }
    }

    override fun onRestart() {
        super.onRestart()
        // Back from the background before we moved on: continue shortly
        if (!leaving) handler.postDelayed(openNext, RESUME_DELAY_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openNext)
        loops.forEach { it.cancel() }
        loops.clear()
        super.onDestroy()
    }

    companion object {
        /** Long enough for every piece of the intro to play once. */
        private const val SPLASH_DELAY_MS = 2600L
        private const val RESUME_DELAY_MS = 600L
    }
}
