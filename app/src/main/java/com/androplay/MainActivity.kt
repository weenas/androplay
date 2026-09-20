package com.androplay

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        super.onCreate(savedInstanceState)
        setContent {
            AndroPlayTheme {
                var currentScreen by remember { mutableStateOf("mirror") }
                val viewModel: AirPlayViewModel = viewModel()

                LaunchedEffect(viewModel.navigateToSettings.value) {
                    if (viewModel.navigateToSettings.value) {
                        currentScreen = "settings"
                        viewModel.onNavigateToSettingsConsumed()
                    }
                }

                LaunchedEffect(viewModel.navigateBack.value) {
                    if (viewModel.navigateBack.value) {
                        currentScreen = "mirror"
                        viewModel.onNavigateBackConsumed()
                    }
                }

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
