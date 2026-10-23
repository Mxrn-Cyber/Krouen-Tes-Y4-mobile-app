package com.kroeungtes.app.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.kroeungtes.app.databinding.ActivitySplashBinding
import com.kroeungtes.app.ui.activities.MainActivity
import com.kroeungtes.app.ui.auth.LoginActivity

/**
 * Loading screen (User Flow step 1).
 * Shows the Krouen Tes logo, then opens Home if the user is already signed in,
 * otherwise the Login screen.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    private val openNext = Runnable {
        val signedIn = FirebaseAuth.getInstance().currentUser != null
        val next = if (signedIn) MainActivity::class.java else LoginActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handler.postDelayed(openNext, SPLASH_DELAY_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openNext)
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_DELAY_MS = 1500L
    }
}
