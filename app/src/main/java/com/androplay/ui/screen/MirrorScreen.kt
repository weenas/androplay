package com.androplay.ui.screen

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.androplay.service.StatsFormat
import com.androplay.service.Lyrics
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.androplay.R
import com.androplay.ui.mirroringLabel
import com.androplay.service.PictureLayout
import com.androplay.service.ReceiverSettings
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontFamily
import com.androplay.service.NetworkStatus
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val stream = state.streamInfo
    if (state.connectionState == AirPlayConnectionState.Streaming &&
        (stream.isVideoPlayback || stream.isAudioOnly || stream.isMirroring)
    ) {
        val settings by viewModel.settings.collectAsState()
        val kind = when {
            stream.isVideoPlayback -> StreamKind.VIDEO
            stream.isAudioOnly -> StreamKind.AUDIO
            else -> StreamKind.MIRRORING
        }
        var menuOpen by remember(kind) { mutableStateOf(false) }
        // The playing content takes D-pad focus, and gets it back when the quick menu closes.
        val contentFocus = remember(kind) { FocusRequester() }
        LaunchedEffect(kind, menuOpen) {
            if (!menuOpen) contentFocus.requestFocus()
        }
        BackHandler(enabled = menuOpen) { menuOpen = false }
        // Back leaves casting only when pressed twice, so a stray press doesn't cut it off.
        var backArmed by remember(kind) { mutableStateOf(false) }
        BackHandler(enabled = !menuOpen) {
            if (backArmed) viewModel.endCasting() else backArmed = true
        }
        LaunchedEffect(backArmed) {
            if (backArmed) {
                delay(BACK_AGAIN_WINDOW_MS)
                backArmed = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // Down and Menu are free during playback (left/right/OK control it).
                    if (menuOpen || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (event.key != Key.DirectionDown && event.key != Key.Menu) return@onPreviewKeyEvent false
                    menuOpen = true
                    true
                }
        ) {
            val contentModifier = Modifier.focusRequester(contentFocus)
            when (kind) {
                StreamKind.VIDEO -> VideoPlayback(viewModel = viewModel, pictureMode = settings.pictureMode, modifier = contentModifier)
                StreamKind.AUDIO -> {
                    val song = stream.nowPlaying
                    // Waits a moment before looking up: the length usually arrives after the title.
                    val lyrics by produceState<Lyrics?>(null, settings.showLyrics, song.title, song.artist, song.durationSec.toInt()) {
                        value = null
                        val title = song.title
                        if (!settings.showLyrics || title.isNullOrBlank()) return@produceState
                        delay(LYRICS_LOOKUP_DELAY_MS)
                        value = withContext(Dispatchers.IO) {
                            viewModel.findLyrics(title, song.artist, song.album, song.durationSec)
                        }
                    }
                    AudioPlayback(nowPlaying = song, onCommand = viewModel::remoteControl, lyrics = lyrics, modifier = contentModifier)
                }
                StreamKind.MIRRORING -> MirroringVideo(viewModel = viewModel, streamInfo = stream, pictureMode = settings.pictureMode, modifier = contentModifier)
            }
            if (settings.showStats) {
                StatsOverlay(viewModel, Modifier.align(Alignment.TopStart).padding(24.dp))
            }
            if (backArmed) {
                Text(
                    stringResource(R.string.press_back_again),
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                )
            }
            if (menuOpen) {
                QuickMenu(
                    viewModel = viewModel,
                    hasPicture = kind != StreamKind.AUDIO,
                    player = if (kind == StreamKind.VIDEO) viewModel.videoPlayer else null,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(48.dp)
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
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
                    viewModel = viewModel,
                    onStart = { viewModel.startServer() }
                )
                AirPlayConnectionState.Discovering -> DiscoveringScreen(
                    viewModel = viewModel,
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
                    error = state.errorMessage ?: stringResource(R.string.unknown_error),
                    onRetry = { viewModel.startServer() }
                )
            }
        }
    }
}

@Composable
fun IdleScreen(viewModel: AirPlayViewModel, onStart: () -> Unit) {
    HomeLayout(info = { ReceiverInfo(viewModel = viewModel) }) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_tagline),
            fontSize = 18.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.width(200.dp).initialFocus()
        ) {
            Text(stringResource(R.string.action_start), fontSize = 20.sp)
        }
    }
}

@Composable
fun DiscoveringScreen(viewModel: AirPlayViewModel, lastError: String? = null, onStop: () -> Unit) {
    HomeLayout(info = { ReceiverInfo(viewModel = viewModel) }) {
        val settings by viewModel.settings.collectAsState()
        Text(stringResource(R.string.waiting_title), color = Color.White, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(if (settings.dlnaEnabled) R.string.waiting_how_dlna else R.string.waiting_how, settings.deviceName),
            color = Color.White,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.waiting_same_network), color = Color.Gray, fontSize = 16.sp)
        if (lastError != null) {
            // Why the last AirPlay video stopped, e.g. the TV couldn't reach the video site.
            Spacer(modifier = Modifier.height(16.dp))
            Text(lastError, color = Color(0xFFFFB4AB), fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStop, modifier = Modifier.initialFocus()) { Text(stringResource(R.string.action_stop)) }
    }
}

/**
 * Status and actions beside the receiver info on wide screens (TVs are only ~540 dp tall at
 * 1080p), stacked and scrollable on narrow ones.
 */
@Composable
private fun HomeLayout(info: @Composable () -> Unit, primary: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = primary
                )
                Spacer(modifier = Modifier.width(48.dp))
                Box(modifier = Modifier.weight(1.2f)) { info() }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                primary()
                Spacer(modifier = Modifier.height(32.dp))
                info()
            }
        }
    }
}

/**
 * What a user needs to cast to this TV at a glance: its AirPlay name, network and address,
 * and how the receiver is set up.
 */
@Composable
fun ReceiverInfo(viewModel: AirPlayViewModel) {
    val settings by viewModel.settings.collectAsState()
    val network by viewModel.network.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshNetwork()
    }

    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x1FFFFFFF))
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        InfoRow(stringResource(R.string.info_name), settings.deviceName)
        InfoRow(stringResource(R.string.info_network), networkLabel(network))
        if (network.type == NetworkStatus.Type.WIFI && network.ssid == null) {
            if (!viewModel.canReadWifiName()) {
                // Android only reveals the Wi-Fi name to apps with the location permission.
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text(stringResource(R.string.show_wifi_name))
                }
            } else {
                Text(stringResource(R.string.wifi_name_needs_location), color = Color.Gray, fontSize = 14.sp)
            }
        }
        InfoRow(stringResource(R.string.info_ip), network.ipv4.joinToString(", ").ifEmpty { "—" })
        val mirroring = remember(settings) { viewModel.mirroringProfile(settings) }
        InfoRow(stringResource(R.string.info_mirroring), mirroringLabel(mirroring))
        InfoRow(stringResource(R.string.info_dlna), stringResource(if (settings.dlnaEnabled) R.string.info_dlna_on else R.string.off))
        InfoRow(stringResource(R.string.info_password), stringResource(if (settings.requirePassword) R.string.setting_password_on else R.string.setting_password_off))
        InfoRow(stringResource(R.string.info_second_device), stringResource(if (settings.allowTakeover) R.string.info_takes_over else R.string.info_refused))
        InfoRow(stringResource(R.string.info_version), viewModel.appVersion)
        if (network.type == NetworkStatus.Type.NONE) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.no_network), color = Color(0xFFFFB4AB), fontSize = 16.sp)
        }
    }
}

@Composable
private fun networkLabel(network: NetworkStatus): String = when (network.type) {
    NetworkStatus.Type.WIFI -> network.ssid?.let { stringResource(R.string.network_wifi_named, it) } ?: stringResource(R.string.network_wifi)
    NetworkStatus.Type.ETHERNET -> stringResource(R.string.network_ethernet)
    NetworkStatus.Type.OTHER -> stringResource(R.string.network_other)
    NetworkStatus.Type.NONE -> stringResource(R.string.network_none)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = Color.Gray, fontSize = 18.sp, modifier = Modifier.weight(0.45f))
        Text(value, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(0.55f))
    }
}

@Composable
fun RegisteringScreen(onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.publishing), color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onStop, modifier = Modifier.initialFocus()) { Text(stringResource(R.string.action_stop)) }
    }
}

@Composable
fun AdvertisingOnlyScreen(onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.advertising_only_title), color = Color.White, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.advertising_only_detail), color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onStop, modifier = Modifier.initialFocus()) { Text(stringResource(R.string.action_stop)) }
    }
}

@Composable
fun ConnectingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.connecting), color = Color.White)
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
        Text(stringResource(R.string.connected), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.connected_from, streamInfo.sourceName), color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Text("${streamInfo.videoWidth}x${streamInfo.videoHeight}", color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.stopServer() }, modifier = Modifier.initialFocus()) {
            Text(stringResource(R.string.action_stop), fontSize = 20.sp)
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
        Text(stringResource(R.string.streaming), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.streaming_source, streamInfo.sourceName), color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.stopServer() }, modifier = Modifier.initialFocus()) {
            Text(stringResource(R.string.action_stop), fontSize = 20.sp)
        }
    }
}

/**
 * Audio streaming (e.g. a music app): cover art, track details and progress. The remote's
 * OK and left/right keys control the sender; media keys reach it through the media session.
 */
@Composable
fun AudioPlayback(
    nowPlaying: NowPlaying,
    onCommand: (DacpClient.Command) -> Unit,
    lyrics: Lyrics? = null,
    modifier: Modifier = Modifier
) {
    val cover = remember(nowPlaying.coverArt) {
        nowPlaying.coverArt?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    // Ticks the progress between the sender's (infrequent) reports.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(nowPlaying, lyrics != null) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            // Lyrics lines change faster than the progress bar needs.
            delay(if (lyrics != null) 200 else 500)
        }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Row(
        modifier = modifier
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
                Image(bitmap = cover, contentDescription = stringResource(R.string.cover_art), contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            } else {
                Text("♪", fontSize = 120.sp, color = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.width(64.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(nowPlaying.title ?: stringResource(R.string.airplay_audio), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            nowPlaying.artist?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, fontSize = 26.sp, color = Color(0xFFDDDDDD), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            nowPlaying.album?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, fontSize = 20.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (lyrics != null) {
                Spacer(modifier = Modifier.height(24.dp))
                LyricsView(lyrics, nowPlaying.currentPositionSec(now))
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
                stringResource(if (nowPlaying.playing) R.string.audio_hint_playing else R.string.audio_hint_paused),
                fontSize = 16.sp,
                color = Color(0xFF888888)
            )
        }
    }
}

/** A few lines of [lyrics] around the one being sung at [positionSec], which is highlighted. */
@Composable
private fun LyricsView(lyrics: Lyrics, positionSec: Double) {
    val current = lyrics.indexAt(positionSec)
    val first = (current - LYRICS_CONTEXT_LINES).coerceAtLeast(0)
    Column(modifier = Modifier.height(190.dp)) {
        for (index in first..(current + LYRICS_CONTEXT_LINES).coerceAtMost(lyrics.lines.lastIndex)) {
            val line = lyrics.lines[index].text.ifEmpty { "♪" }
            val active = index == current
            Text(
                line,
                fontSize = if (active) 24.sp else 20.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) Color.White else Color(0xFF8A8A8A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }
    }
}

private const val LYRICS_CONTEXT_LINES = 2
private const val LYRICS_LOOKUP_DELAY_MS = 1500L

private fun formatTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Full-screen AirPlay video. The phone stays the main remote; the TV remote can also pause
 * (OK / play-pause) and skip 10 s (left/right, rewind/fast-forward).
 */
@Composable
fun VideoPlayback(viewModel: AirPlayViewModel, pictureMode: String, modifier: Modifier = Modifier) {
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
        modifier = modifier
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
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    // Spinner while loading or rebuffering, so a slow start isn't a black screen.
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    // Keys are handled by the Compose container above.
                    isFocusable = false
                }
            },
            update = {
                it.player = player
                it.resizeMode = when (pictureMode) {
                    ReceiverSettings.PICTURE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    ReceiverSettings.PICTURE_STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
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
private const val BACK_AGAIN_WINDOW_MS = 3000L

private enum class StreamKind { VIDEO, AUDIO, MIRRORING }

/** "Stats for nerds": what is playing and how, refreshed every second. */
@Composable
fun StatsOverlay(viewModel: AirPlayViewModel, modifier: Modifier = Modifier) {
    var stats by remember { mutableStateOf(viewModel.playbackStats()) }
    LaunchedEffect(Unit) {
        while (true) {
            stats = viewModel.playbackStats()
            delay(1000)
        }
    }
    val current = stats ?: return
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        StatsLine(current.source, "", bold = true)
        current.video?.let { video ->
            StatsLine(
                "Video",
                listOf(
                    video.codec, StatsFormat.resolution(video.width, video.height),
                    StatsFormat.fps(video.fps), StatsFormat.bitrate(video.bitrateBps)
                ).joinToString(" · ")
            )
            StatsLine("", listOfNotNull(video.decoder, "dropped ${video.droppedFrames}").joinToString(" · "))
        }
        current.audio?.let { audio ->
            StatsLine(
                "Audio",
                listOf(audio.codec, StatsFormat.audioFormat(audio), StatsFormat.bitrate(audio.bitrateBps))
                    .joinToString(" · ")
            )
            audio.decoder?.let { StatsLine("", it) }
        }
        current.extra.forEach { (label, value) -> StatsLine(label, value) }
    }
}

@Composable
private fun StatsLine(label: String, value: String, bold: Boolean = false) {
    Row {
        Text(
            label,
            color = if (bold) Color.White else Color(0xFFB0B0B0),
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            modifier = if (bold) Modifier else Modifier.width(80.dp)
        )
        Text(value, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

/**
 * Full-screen mirrored picture, sized by [pictureMode]: letterboxed to the sender's aspect ratio
 * (Fit), cropped to cover the screen (Fill) or stretched.
 */
@Composable
fun MirroringVideo(
    viewModel: AirPlayViewModel,
    streamInfo: com.androplay.service.StreamInfo,
    pictureMode: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
            // Focusable so the remote's keys reach the quick menu shortcut.
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        val (width, height) = PictureLayout.size(
            pictureMode, maxWidth.value, maxHeight.value, streamInfo.frameWidth, streamInfo.frameHeight
        )
        val videoModifier = Modifier.requiredSize(width.dp, height.dp)
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
        Text(stringResource(R.string.disconnected), fontSize = 32.sp, color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStart, modifier = Modifier.initialFocus()) {
            Text(stringResource(R.string.action_start), fontSize = 20.sp)
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
        Text(stringResource(R.string.error_title), fontSize = 32.sp, color = Color.Red)
        Spacer(modifier = Modifier.height(16.dp))
        Text(error, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRetry, modifier = Modifier.initialFocus()) {
            Text(stringResource(R.string.action_retry), fontSize = 20.sp)
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
