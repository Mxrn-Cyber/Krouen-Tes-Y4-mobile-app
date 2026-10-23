package com.kroeungtes.app.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.kroeungtes.app.R
import com.kroeungtes.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FragmentContainerView: get the NavController from the host fragment
        val navHost = supportFragmentManager.findFragmentById(R.id.frag_host) as NavHostFragment
        NavigationUI.setupWithNavController(binding.bottomNavigation, navHost.navController)

        // Green "+" in the middle of the bar — Create Recipe screen comes in the next phase
        binding.fabCreate.setOnClickListener {
            Toast.makeText(this, R.string.create_recipe_soon, Toast.LENGTH_SHORT).show()
        }
    }
}
