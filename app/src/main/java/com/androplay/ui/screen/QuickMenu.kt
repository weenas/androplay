package com.androplay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.androplay.service.MediaTracks
import com.androplay.service.ReceiverSettings
import com.androplay.viewmodel.AirPlayViewModel

/**
 * Settings that apply while something plays, opened with the remote's Down or Menu key and
 * closed with Back. OK on a row switches it to its next value.
 *
 * [hasPicture] adds the picture mode (mirroring and AirPlay video); [player] adds the AirPlay
 * video's audio tracks and subtitles when it has a choice of them.
 */
@Composable
fun QuickMenu(viewModel: AirPlayViewModel, hasPicture: Boolean, player: Player?, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsState()
    // Track lists change as a stream loads, and after a selection.
    var tracks by remember(player) { mutableStateOf(player?.currentTracks ?: Tracks.EMPTY) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    val audioChoices = remember(tracks) { player?.let { MediaTracks.choices(it, C.TRACK_TYPE_AUDIO) }.orEmpty() }
    val subtitleChoices = remember(tracks) { player?.let { MediaTracks.choices(it, C.TRACK_TYPE_TEXT) }.orEmpty() }

    val firstRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstRow.requestFocus() }

    Column(
        modifier = modifier
            .width(420.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE6202020))
            .padding(vertical = 16.dp)
    ) {
        Text(
            "Playback",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        MenuRow("Playback stats", if (settings.showStats) "On" else "Off", Modifier.focusRequester(firstRow)) {
            viewModel.updateSettings { it.copy(showStats = !it.showStats) }
        }
        if (hasPicture) {
            MenuRow("Picture", settings.pictureMode) {
                val modes = ReceiverSettings.PICTURE_MODES
                viewModel.updateSettings {
                    it.copy(pictureMode = modes[(modes.indexOf(it.pictureMode) + 1) % modes.size])
                }
            }
        }
        if (player != null && audioChoices.size > 1) {
            MenuRow("Audio", audioChoices.firstOrNull { it.selected }?.label ?: "Auto") {
                MediaTracks.next(audioChoices)?.let { MediaTracks.select(player, C.TRACK_TYPE_AUDIO, it) }
            }
        }
        if (player != null && subtitleChoices.isNotEmpty()) {
            MenuRow("Subtitles", subtitleChoices.firstOrNull { it.selected }?.label ?: "Off") {
                MediaTracks.next(subtitleChoices)?.let { MediaTracks.select(player, C.TRACK_TYPE_TEXT, it) }
            }
        }
        MenuRow("Stop casting", "") { viewModel.endCasting() }
        Text(
            "OK: change    Back: close",
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun MenuRow(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color.Transparent)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val text = if (focused) Color.Black else Color.White
        Text(label, color = text, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (focused) Color.DarkGray else Color(0xFFBDBDBD), fontSize = 18.sp)
    }
}
