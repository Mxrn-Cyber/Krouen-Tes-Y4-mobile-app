package com.kroeungtes.app.ui.auth

import android.animation.Animator
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.kroeungtes.app.R
import com.kroeungtes.app.ui.common.pressScale
import com.kroeungtes.app.ui.common.riseIn
import com.kroeungtes.app.ui.common.spinForever
import com.kroeungtes.app.ui.common.EdgeToEdge
import com.kroeungtes.app.ui.common.marginForSystemBars
import com.kroeungtes.app.ui.activities.MainActivity
import com.kroeungtes.app.databinding.ActivitySignupBinding
import com.google.firebase.auth.FirebaseAuth

class SignupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private lateinit var firebaseAuth: FirebaseAuth
    /** Slowly turning corner blobs, stopped in onDestroy. */
    private val spins = mutableListOf<Animator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.light(this)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.authScroll.marginForSystemBars(ime = true)
        firebaseAuth = FirebaseAuth.getInstance()
        // Logo, title and button glide over from Log in; the other parts rise in
        binding.signupSubtitle.riseIn(delay = 150, distanceDp = 20f)
        listOf(
            binding.signupEmailBox, binding.signupPasswordBox, binding.signupConfirmBox,
            binding.signupOrRow, binding.signupSocialRow, binding.loginRedirectText
        ).forEachIndexed { i, view -> view.riseIn(delay = 220L + 60L * i, distanceDp = 28f, duration = 480) }
        binding.signupButton.pressScale()
        spins += binding.signupBlobTop.spinForever(40_000)
        spins += binding.signupBlobBottom.spinForever(32_000, clockwise = false)
        // Social sign-in is shown in the design but not connected yet
        val comingSoon = android.view.View.OnClickListener {
            Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show()
        }
        binding.signupGoogle.setOnClickListener(comingSoon)
        binding.signupFacebook.setOnClickListener(comingSoon)
        binding.signupButton.setOnClickListener{
            val email = binding.signupEmail.text.toString()
            val password = binding.signupPassword.text.toString()
            val confirmPassword = binding.signupConfirm.text.toString()
            if (email.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty()){
                if (password == confirmPassword){
                    firebaseAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener{
                        if (it.isSuccessful){
                            // New account is signed in already — go straight to Home
                            val intent = Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this, it.exception.toString(), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Password does not matched", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        binding.loginRedirectText.setOnClickListener {
            // Back to Log in (it is already under this screen); the shared parts glide back
            supportFinishAfterTransition()
        }
    }

    override fun onDestroy() {
        spins.forEach { it.cancel() }
        spins.clear()
        super.onDestroy()
    }
}