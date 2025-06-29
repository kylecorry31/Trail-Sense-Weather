package com.kylecorry.trail_sense_weather.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kylecorry.andromeda.core.tryOrNothing
import com.kylecorry.andromeda.fragments.AndromedaActivity
import com.kylecorry.andromeda.fragments.ColorTheme
import com.kylecorry.trail_sense_weather.R
import com.kylecorry.trail_sense_weather.app.NavigationUtils.setupWithNavController

class MainActivity : AndromedaActivity() {

    private val permissions = mutableListOf<String>(
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        ExceptionHandler.initialize(this)
        setColorTheme(ColorTheme.System, true)
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        bottomNavigationView.setupWithNavController(findNavController(), false)

        bindLayoutInsets()

        requestPermissions(permissions) {
            findNavController().navigate(R.id.action_main)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent ?: return
        setIntent(intent)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        bottomNavigationView.selectedItemId = savedInstanceState.getInt(
            "page",
            R.id.action_main
        )
        if (savedInstanceState.containsKey("navigation")) {
            tryOrNothing {
                val bundle = savedInstanceState.getBundle("navigation_arguments")
                findNavController().navigate(savedInstanceState.getInt("navigation"), bundle)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", bottomNavigationView.selectedItemId)
        findNavController().currentBackStackEntry?.arguments?.let {
            outState.putBundle("navigation_arguments", it)
        }
        findNavController().currentDestination?.id?.let {
            outState.putInt("navigation", it)
        }
    }

    private fun bindLayoutInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.coordinator)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
            }
            windowInsets
        }
    }

    private fun findNavController(): NavController {
        return (supportFragmentManager.findFragmentById(R.id.fragment_holder) as NavHostFragment).navController
    }
}
