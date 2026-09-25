package com.androplay.service

import org.junit.Assert.assertEquals
import org.junit.Test

class MirroringProfileTest {
    private val none = HevcSupport.NONE
    private val hevc1080 = HevcSupport(hardware = true, uhd = false)
    private val hevc4k = HevcSupport(hardware = true, uhd = true)

    private fun profile(settings: ReceiverSettings, hevc: HevcSupport, w: Int, h: Int) =
        MirroringProfile.of(settings, hevc, w, h).let { Triple(it.h265, it.width, it.height) to it.label }

    @Test
    fun hevcOn4kPanelWith4kDecoderOffers4k() {
        assertEquals(Triple(true, 3840, 2160) to "H.265 · up to 4K", profile(ReceiverSettings(), hevc4k, 3840, 2160))
    }

    @Test
    fun a1080pScreenStaysAt1080pAndSaysWhy() {
        // H.265 still improves quality at 1080p; 4K would only be scaled down again.
        assertEquals(Triple(true, 1920, 1080) to "H.265 · 1080p (1080p screen)", profile(ReceiverSettings(), hevc4k, 1920, 1080))
        assertEquals(Triple(false, 1920, 1080) to "H.264 · 1080p (1080p screen)", profile(ReceiverSettings(), none, 1920, 1080))
    }

    @Test
    fun a4kPanelWithoutA4kDecoderSaysWhy() {
        assertEquals(Triple(true, 1920, 1080) to "H.265 · 1080p (decoder can't do 4K)", profile(ReceiverSettings(), hevc1080, 3840, 2160))
        assertEquals(Triple(false, 1920, 1080) to "H.264 · 1080p (no hardware H.265 decoder for 4K)", profile(ReceiverSettings(), none, 3840, 2160))
        val h264Only = ReceiverSettings(videoCodec = ReceiverSettings.CODEC_H264_ONLY)
        assertEquals(Triple(false, 1920, 1080) to "H.264 · 1080p (4K needs H.265)", profile(h264Only, hevc4k, 3840, 2160))
    }

    @Test
    fun explicitResolutionsWinWithoutANote() {
        assertEquals(Triple(true, 1920, 1080) to "H.265 · 1080p", profile(ReceiverSettings(resolution = "1080p"), hevc4k, 3840, 2160))
        assertEquals(Triple(true, 1280, 720) to "H.265 · 720p", profile(ReceiverSettings(resolution = "720p"), hevc4k, 3840, 2160))
    }
}
