package com.kroeungtes.app.ui.auth

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair as UtilPair
import com.kroeungtes.app.R
import com.kroeungtes.app.ui.common.pressScale
import com.kroeungtes.app.ui.common.riseIn
import com.kroeungtes.app.ui.common.spinForever
import com.kroeungtes.app.ui.common.EdgeToEdge
import com.kroeungtes.app.ui.common.marginForSystemBars
import com.kroeungtes.app.ui.activities.MainActivity
import com.kroeungtes.app.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth
    /** Slowly turning corner blobs, stopped in onDestroy. */
    private val spins = mutableListOf<Animator>()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.light(this)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.authScroll.marginForSystemBars(ime = true)
        firebaseAuth = FirebaseAuth.getInstance()
        // The wok arrives from the splash screen; the rest rises in one after another
        binding.loginTitle.riseIn(delay = 120, distanceDp = 20f)
        binding.loginSubtitle.riseIn(delay = 200, distanceDp = 20f)
        listOf(
            binding.loginEmailBox, binding.loginPasswordBox, binding.forgotPassword, binding.loginButton,
            binding.loginOrRow, binding.loginSocialRow, binding.signupRedirectText
        ).forEachIndexed { i, view -> view.riseIn(delay = 280L + 60L * i, distanceDp = 28f, duration = 480) }
        binding.loginButton.pressScale()
        spins += binding.loginBlobTop.spinForever(40_000)
        spins += binding.loginBlobBottom.spinForever(32_000, clockwise = false)
        // Social sign-in is shown in the design but not connected yet
        val comingSoon = android.view.View.OnClickListener {
            Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show()
        }
        binding.loginGoogle.setOnClickListener(comingSoon)
        binding.loginFacebook.setOnClickListener(comingSoon)
        binding.loginButton.setOnClickListener {
            val email = binding.loginEmail.text.toString()
            val password = binding.loginPassword.text.toString()
            if (email.isNotEmpty() && password.isNotEmpty()){
                firebaseAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener{
                    if (it.isSuccessful){
                        // Go to Home and remove Login/Sign-up from the back stack
                        val intent = Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, it.exception.toString(), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        binding.forgotPassword.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            val view = layoutInflater.inflate(R.layout.dialog_forgot, null)
            val userEmail = view.findViewById<EditText>(R.id.editBox)
            builder.setView(view)
            val dialog = builder.create()
            view.findViewById<Button>(R.id.btnReset).setOnClickListener {
                compareEmail(userEmail)
                dialog.dismiss()
            }
            view.findViewById<Button>(R.id.btnCancel).setOnClickListener {
                dialog.dismiss()
            }
            if (dialog.window != null){
                dialog.window!!.setBackgroundDrawable(ColorDrawable(0))
            }
            dialog.show()
        }
        binding.signupRedirectText.setOnClickListener {
            // Logo, title and green button glide over to the Sign-up screen
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this,
                UtilPair<View, String>(binding.loginLogo, binding.loginLogo.transitionName),
                UtilPair<View, String>(binding.loginTitle, binding.loginTitle.transitionName),
                UtilPair<View, String>(binding.loginButton, binding.loginButton.transitionName)
            )
            startActivity(Intent(this, SignupActivity::class.java), options.toBundle())
        }
    }

    override fun onDestroy() {
        spins.forEach { it.cancel() }
        spins.clear()
        super.onDestroy()
    }
    //Outside onCreate
    private fun compareEmail(email: EditText){
        if (email.text.toString().isEmpty()){
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email.text.toString()).matches()){
            return
        }
        firebaseAuth.sendPasswordResetEmail(email.text.toString())
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Check your email", Toast.LENGTH_SHORT).show()
                }
            }
    }
}

