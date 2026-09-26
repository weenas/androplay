package com.androplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androplay.ui.screen.MirrorScreen
import com.androplay.ui.screen.SettingsScreen
import com.androplay.ui.screen.AboutScreen
import com.androplay.ui.theme.AndroPlayTheme
import com.androplay.viewmodel.AirPlayViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest theme only paints the launch screen; the app itself has a plain background.
        setTheme(R.style.Theme_AndroPlay)
        super.onCreate(savedInstanceState)
        setContent {
            AndroPlayTheme {
                var currentScreen by remember { mutableStateOf("mirror") }
                val viewModel: AirPlayViewModel = viewModel()

                val navigateToSettings by viewModel.navigateToSettings.collectAsState()
                val navigateBack by viewModel.navigateBack.collectAsState()

                LaunchedEffect(navigateToSettings) {
                    if (navigateToSettings) {
                        currentScreen = "settings"
                        viewModel.onNavigateToSettingsConsumed()
                    }
                }

                LaunchedEffect(navigateBack) {
                    if (navigateBack) {
                        currentScreen = "mirror"
                        viewModel.onNavigateBackConsumed()
                    }
                }

                // The remote's Back key leaves sub-screens instead of closing the app.
                BackHandler(enabled = currentScreen != "mirror") { currentScreen = "mirror" }

                when (currentScreen) {
                    "mirror" -> MirrorScreen(viewModel = viewModel)
                    "settings" -> SettingsScreen(
                        viewModel = viewModel,
                        onBack = { currentScreen = "mirror" }
                    )
                    "about" -> AboutScreen(onBack = { currentScreen = "mirror" })
                }
            }
        }
    }
}
