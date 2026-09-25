package com.androplay.service

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/** The TV's HEVC (H.265) decoding ability, as far as mirroring needs it. */
data class HevcSupport(
    /** A hardware HEVC decoder exists; software ones can't keep up with live mirroring. */
    val hardware: Boolean,
    /** That decoder handles 3840x2160 at 30 fps. */
    val uhd: Boolean
) {
    companion object {
        val NONE = HevcSupport(hardware = false, uhd = false)

        /** Queries the platform decoders once; cheap enough to call at receiver start. */
        fun detect(): HevcSupport {
            val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) } &&
                    isHardware(info)
            }
            if (decoders.isEmpty()) return NONE
            val uhd = decoders.any { info ->
                runCatching {
                    info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                        .videoCapabilities.areSizeAndRateSupported(3840, 2160, 30.0)
                }.getOrDefault(false)
            }
            return HevcSupport(hardware = true, uhd = uhd)
        }

        private fun isHardware(info: MediaCodecInfo): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                // Before API 29 there is no flag; Android's own software codecs have these prefixes.
                !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                    !info.name.startsWith("c2.android.", ignoreCase = true)
            }
    }
}

/**
 * What the receiver offers senders for screen mirroring: the codec and the display size they
 * encode to. Senders pick H.265 when it is offered with a 4K display, as UxPlay pairs them.
 * [note] says why the size is capped, when it is.
 */
data class MirroringProfile(val h265: Boolean, val width: Int, val height: Int, val note: String? = null) {
    /** For the home and settings screens, e.g. "H.265 · up to 4K" or "H.265 · 1080p (1080p screen)". */
    val label: String
        get() {
            val codec = if (h265) "H.265" else "H.264"
            val size = if (height >= UHD_HEIGHT) "up to 4K" else "${height}p"
            return "$codec · $size" + (note?.let { " ($it)" } ?: "")
        }

    companion object {
        private const val UHD_HEIGHT = 2160

        /** [panelWidth]/[panelHeight]: the panel's largest mode, not the (often 1080p) UI mode. */
        fun of(settings: ReceiverSettings, hevc: HevcSupport, panelWidth: Int, panelHeight: Int): MirroringProfile {
            val h265 = settings.videoCodec == ReceiverSettings.CODEC_AUTO && hevc.hardware
            val (width, height) = settings.displaySize(panelWidth, panelHeight, allowUhd = h265 && hevc.uhd)
            val panel = minOf(panelWidth, panelHeight)
            val note = when {
                settings.resolution != ReceiverSettings.RESOLUTION_AUTO || height >= UHD_HEIGHT -> null
                panel <= 0 -> null
                panel < UHD_HEIGHT -> "${panel}p screen"
                h265 -> "decoder can't do 4K"
                settings.videoCodec == ReceiverSettings.CODEC_H264_ONLY -> "4K needs H.265"
                else -> "no hardware H.265 decoder for 4K"
            }
            return MirroringProfile(h265, width, height, note)
        }
    }
}
