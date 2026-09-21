package com.androplay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androplay.viewmodel.AirPlayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AirPlayViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            item {
                Text("Display", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { SettingsRow("Resolution", "Auto") }
            item { SettingsRow("Frame Rate", "Auto") }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                Text("Audio", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { SettingsRow("Audio Latency", "250 ms") }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                Text("Security", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { SettingsRow("PIN", "None") }
        }
    }
}

@Composable
fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Text(value, color = Color.Gray, fontSize = 16.sp)
    }
}
