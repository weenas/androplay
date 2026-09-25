package com.androplay.ui.screen

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import com.androplay.service.DacpClient
import com.androplay.service.NowPlaying
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import androidx.media3.common.Player
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
        if (state.streamInfo.isAudioOnly) {
            AudioPlayback(nowPlaying = state.streamInfo.nowPlaying, onCommand = viewModel::remoteControl)
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

/**
 * Audio streaming (e.g. a music app): cover art, track details and progress. The remote's
 * OK and left/right keys control the sender; media keys reach it through the media session.
 */
@Composable
fun AudioPlayback(nowPlaying: NowPlaying, onCommand: (DacpClient.Command) -> Unit) {
    val cover = remember(nowPlaying.coverArt) {
        nowPlaying.coverArt?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    // Ticks the progress between the sender's (infrequent) reports.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(nowPlaying) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val command = when (event.key) {
                    Key.DirectionCenter, Key.Enter -> DacpClient.Command.PLAY_PAUSE
                    Key.DirectionLeft -> DacpClient.Command.PREVIOUS
                    Key.DirectionRight -> DacpClient.Command.NEXT
                    else -> null
                } ?: return@onKeyEvent false
                onCommand(command)
                true
            }
            .initialFocus()
            .focusable()
            .padding(horizontal = 96.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                Image(bitmap = cover, contentDescription = "Cover art", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            } else {
                Text("♪", fontSize = 120.sp, color = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.width(64.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(nowPlaying.title ?: "AirPlay audio", fontSize = 40.sp, fontWeight = FontWeight.Bold,
                color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            nowPlaying.artist?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, fontSize = 26.sp, color = Color(0xFFDDDDDD), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            nowPlaying.album?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, fontSize = 20.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (nowPlaying.durationSec > 0) {
                val position = nowPlaying.currentPositionSec(now)
                Spacer(modifier = Modifier.height(40.dp))
                LinearProgressIndicator(
                    progress = { (position / nowPlaying.durationSec).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Color.White,
                    trackColor = Color(0xFF444444)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), fontSize = 18.sp, color = Color.Gray)
                    Text(formatTime(nowPlaying.durationSec), fontSize = 18.sp, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                (if (nowPlaying.playing) "OK: pause" else "OK: play") + "    ◀ ▶: previous / next",
                fontSize = 16.sp,
                color = Color(0xFF888888)
            )
        }
    }
}

private fun formatTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Full-screen AirPlay video. The phone stays the main remote; the TV remote can also pause
 * (OK / play-pause) and skip 10 s (left/right, rewind/fast-forward).
 */
@Composable
fun VideoPlayback(viewModel: AirPlayViewModel) {
    val player = viewModel.videoPlayer
    var paused by remember(player) { mutableStateOf(player?.playWhenReady == false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                paused = !playWhenReady
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause ->
                        viewModel.toggleVideoPause()
                    Key.DirectionLeft, Key.MediaRewind -> viewModel.seekVideoBy(-SEEK_STEP_SEC)
                    Key.DirectionRight, Key.MediaFastForward -> viewModel.seekVideoBy(SEEK_STEP_SEC)
                    else -> return@onKeyEvent false
                }
                true
            }
            .initialFocus()
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    // Spinner while loading or rebuffering, so a slow start isn't a black screen.
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    // Keys are handled by the Compose container above.
                    isFocusable = false
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )
        if (paused) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Text("❚❚", fontSize = 44.sp, color = Color.White)
            }
        }
    }
}

private const val SEEK_STEP_SEC = 10

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
