package com.androplay.ui.screen

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

    // Mirroring and AirPlay video own the whole screen: no app bar, no padding, just the picture.
    if (state.connectionState == AirPlayConnectionState.Streaming) {
        if (state.streamInfo.isVideoPlayback) {
            VideoPlayback(viewModel = viewModel)
            return
        }
        if (state.streamInfo.isMirroring) {
            MirroringVideo(viewModel = viewModel, streamInfo = state.streamInfo)
            return
        }
    }

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
                    lastError = state.errorMessage,
                    onStop = { viewModel.stopServer() }
                )
                AirPlayConnectionState.Registering -> RegisteringScreen(
                    onStop = { viewModel.stopServer() }
                )
                AirPlayConnectionState.AdvertisingOnly -> AdvertisingOnlyScreen(
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
            modifier = Modifier.width(200.dp).initialFocus()
        ) {
            Text("Start", fontSize = 20.sp)
        }
    }
}

@Composable
fun DiscoveringScreen(lastError: String? = null, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Waiting for an AirPlay connection...", color = Color.White)
        if (lastError != null) {
            // Why the last AirPlay video stopped, e.g. the TV couldn't reach the video site.
            Spacer(modifier = Modifier.height(16.dp))
            Text(lastError, color = Color(0xFFFFB4AB), fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onStop, modifier = Modifier.initialFocus()) { Text("Stop") }
    }
}

@Composable
fun RegisteringScreen(onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Publishing AndroPlay on the local network...", color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onStop, modifier = Modifier.initialFocus()) { Text("Stop") }
    }
}

@Composable
fun AdvertisingOnlyScreen(onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("AndroPlay is visible on the local network", color = Color.White, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Discovery preview only — AirPlay streaming is not available yet.", color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onStop, modifier = Modifier.initialFocus()) { Text("Stop") }
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
        Button(onClick = { viewModel.stopServer() }, modifier = Modifier.initialFocus()) {
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Streaming", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Source: ${streamInfo.sourceName}", color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.stopServer() }, modifier = Modifier.initialFocus()) {
            Text("Stop", fontSize = 20.sp)
        }
    }
}

/** Full-screen AirPlay video. The sender is the remote control, so no on-screen controls. */
@Composable
fun VideoPlayback(viewModel: AirPlayViewModel) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                // Spinner while loading or rebuffering, so a slow start isn't a black screen.
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                keepScreenOn = true
            }
        },
        update = { it.player = viewModel.videoPlayer },
        onRelease = { it.player = null },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}

/** Full-screen mirrored picture, letterboxed to the sender's aspect ratio. */
@Composable
fun MirroringVideo(viewModel: AirPlayViewModel, streamInfo: com.androplay.service.StreamInfo) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val videoModifier = if (streamInfo.frameWidth > 0 && streamInfo.frameHeight > 0) {
            Modifier.aspectRatio(streamInfo.frameWidth.toFloat() / streamInfo.frameHeight)
        } else {
            Modifier.fillMaxSize()
        }
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            viewModel.setVideoSurface(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            viewModel.setVideoSurface(null)
                        }
                    })
                }
            },
            modifier = videoModifier
        )
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
        Button(onClick = onStart, modifier = Modifier.initialFocus()) {
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
        Button(onClick = onRetry, modifier = Modifier.initialFocus()) {
            Text("Retry", fontSize = 20.sp)
        }
    }
}

/** Moves D-pad focus to this element when it first appears, so the primary action is one OK press away. */
@Composable
private fun Modifier.initialFocus(): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) { requester.requestFocus() }
    return focusRequester(requester)
}
