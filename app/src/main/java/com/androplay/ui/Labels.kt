package com.androplay.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.androplay.R
import com.androplay.service.MirroringProfile
import com.androplay.service.ReceiverSettings

/**
 * The on-screen name of a stored setting value. Settings keep their English values (so saved
 * settings survive a language change); only what is shown is translated.
 */
@Composable
fun settingValueLabel(value: String): String = when (value) {
    ReceiverSettings.RESOLUTION_AUTO, ReceiverSettings.FRAME_RATE_AUTO, ReceiverSettings.CODEC_AUTO -> stringResource(R.string.auto)
    ReceiverSettings.CODEC_H264_ONLY -> stringResource(R.string.setting_codec_h264_only)
    ReceiverSettings.PICTURE_FIT -> stringResource(R.string.picture_fit)
    ReceiverSettings.PICTURE_FILL -> stringResource(R.string.picture_fill)
    ReceiverSettings.PICTURE_STRETCH -> stringResource(R.string.picture_stretch)
    else -> value.removeSuffix(" FPS").toIntOrNull()?.let { stringResource(R.string.frame_rate_fps, it) } ?: value
}

/** E.g. "H.265 · up to 4K" or "H.264 · 1080p (1080p screen)", in the TV's language. */
@Composable
fun mirroringLabel(profile: MirroringProfile): String {
    val size = if (profile.upTo4k) stringResource(R.string.mirroring_up_to_4k) else "${profile.height}p"
    val note = when (profile.cap) {
        MirroringProfile.Cap.SCREEN -> stringResource(R.string.mirroring_note_screen, profile.panelLines)
        MirroringProfile.Cap.DECODER -> stringResource(R.string.mirroring_note_decoder)
        MirroringProfile.Cap.NEEDS_H265 -> stringResource(R.string.mirroring_note_needs_h265)
        MirroringProfile.Cap.NO_HW_DECODER -> stringResource(R.string.mirroring_note_no_hw_decoder)
        null -> null
    }
    return "${profile.codec} · $size" + (note?.let { " ($it)" } ?: "")
}
