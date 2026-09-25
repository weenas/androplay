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
 */
data class MirroringProfile(val h265: Boolean, val width: Int, val height: Int) {
    val label: String
        get() = "${if (h265) "H.265" else "H.264"}, up to ${if (height >= 2160) "4K" else "${height}p"}"

    companion object {
        fun of(settings: ReceiverSettings, hevc: HevcSupport, displayWidth: Int, displayHeight: Int): MirroringProfile {
            val h265 = settings.videoCodec == ReceiverSettings.CODEC_AUTO && hevc.hardware
            val (width, height) = settings.displaySize(displayWidth, displayHeight, allowUhd = h265 && hevc.uhd)
            return MirroringProfile(h265, width, height)
        }
    }
}
