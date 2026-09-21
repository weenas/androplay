package com.androplay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    val settings by viewModel.settings.collectAsState()
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
                Text("Receiver", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                DeviceNameSetting(value = settings.deviceName) { name ->
                    viewModel.updateSettings { it.copy(deviceName = name) }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                Text("Display", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { ChoiceSetting("Resolution", settings.resolution, listOf("Auto", "1080p", "1440p", "4K")) { viewModel.updateSettings { current -> current.copy(resolution = it) } } }
            item { ChoiceSetting("Frame Rate", settings.frameRate, listOf("Auto", "30 FPS", "60 FPS")) { viewModel.updateSettings { current -> current.copy(frameRate = it) } } }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                Text("Audio", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { ChoiceSetting("Audio Latency", settings.audioLatency, listOf("100 ms", "250 ms", "500 ms")) { viewModel.updateSettings { current -> current.copy(audioLatency = it) } } }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                Text("Security", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { DeviceNameSetting(if (settings.pin.isBlank()) "PIN (None)" else "PIN", settings.pin, isPassword = true) { pin -> viewModel.updateSettings { it.copy(pin = pin) } } }
        }
    }
}

@Composable
fun ChoiceSetting(label: String, value: String, choices: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsRow(label, value, Modifier.fillMaxWidth().padding(vertical = 12.dp).clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { onSelected(choice); expanded = false }) }
        }
    }
}

@Composable
fun DeviceNameSetting(label: String = "Device Name", value: String, isPassword: Boolean = false, onSaved: (String) -> Unit) {
    var editing by remember(value) { mutableStateOf(value) }
    OutlinedTextField(value = editing, onValueChange = { editing = it }, label = { Text(label) }, singleLine = true,
        visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (isPassword) androidx.compose.ui.text.input.KeyboardType.NumberPassword else androidx.compose.ui.text.input.KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { TextButton(onClick = { onSaved(editing) }) { Text("Save") } })
}

@Composable
fun SettingsRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Text(value, color = Color.Gray, fontSize = 16.sp)
    }
}
