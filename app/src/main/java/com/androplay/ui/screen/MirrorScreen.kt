package com.androplay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androplay.service.AirPlayConnectionState
import com.androplay.viewmodel.AirPlayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorScreen(viewModel: AirPlayViewModel) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AndroPlay") },
                actions = {
                    IconButton(onClick = { viewModel.navigateToSettings() }) {
                        Text("⚙")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (state.connectionState) {
                AirPlayConnectionState.Idle -> IdleScreen(
                    onStart = { viewModel.startServer() }
                )
                AirPlayConnectionState.Discovering -> DiscoveringScreen(
                    onStop = { viewModel.stopServer() }
                )
                AirPlayConnectionState.Connecting -> ConnectingScreen()
                AirPlayConnectionState.Connected -> ConnectedScreen(
                    viewModel = viewModel,
                    streamInfo = state.streamInfo
                )
                AirPlayConnectionState.Streaming -> StreamingScreen(
                    viewModel = viewModel,
                    streamInfo = state.streamInfo
                )
                AirPlayConnectionState.Disconnected -> DisconnectedScreen(
                    onStart = { viewModel.startServer() }
                )
                AirPlayConnectionState.Error -> ErrorScreen(
                    error = state.errorMessage ?: "Unknown error",
                    onRetry = { viewModel.startServer() }
                )
            }
        }
    }
}

@Composable
fun IdleScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AndroPlay",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "AirPlay Receiver for Android TV",
            fontSize = 18.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.width(200.dp)
        ) {
            Text("Start", fontSize = 20.sp)
        }
    }
}

@Composable
fun DiscoveringScreen(onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Scanning for AirPlay devices...", color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onStop) { Text("Stop") }
    }
}

@Composable
fun ConnectingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Connecting...", color = Color.White)
    }
}

@Composable
fun ConnectedScreen(viewModel: AirPlayViewModel, streamInfo: com.androplay.service.StreamInfo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Connected", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text("From: ${streamInfo.sourceName}", color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Text("${streamInfo.videoWidth}x${streamInfo.videoHeight}", color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.stopServer() }) {
            Text("Stop", fontSize = 20.sp)
        }
    }
}

@Composable
fun StreamingScreen(viewModel: AirPlayViewModel, streamInfo: com.androplay.service.StreamInfo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Streaming", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Source: ${streamInfo.sourceName}", color = Color.Gray)
        Text("Resolution: ${streamInfo.videoWidth}x${streamInfo.videoHeight}", color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.stopServer() }) {
            Text("Stop", fontSize = 20.sp)
        }
    }
}

@Composable
fun DisconnectedScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Disconnected", fontSize = 32.sp, color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStart) {
            Text("Start", fontSize = 20.sp)
        }
    }
}

@Composable
fun ErrorScreen(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Error", fontSize = 32.sp, color = Color.Red)
        Spacer(modifier = Modifier.height(16.dp))
        Text(error, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRetry) {
            Text("Retry", fontSize = 20.sp)
        }
    }
}
