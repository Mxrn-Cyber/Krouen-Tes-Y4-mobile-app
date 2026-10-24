package com.kroeungtes.app.ui.activities

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.kroeungtes.app.R
import com.kroeungtes.app.databinding.ActivityMainBinding
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.common.pop

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FragmentContainerView: get the NavController from the host fragment
        val navHost = supportFragmentManager.findFragmentById(R.id.frag_host) as NavHostFragment
        NavigationUI.setupWithNavController(binding.bottomNavigation, navHost.navController)

        // Green "+" in the middle of the bar opens Create Recipe (slides up)
        binding.fabCreate.setOnClickListener {
            it.pop()
            Motion.openFromBottom(this, Intent(this, CreateRecipeActivity::class.java))
        }
    }
}
