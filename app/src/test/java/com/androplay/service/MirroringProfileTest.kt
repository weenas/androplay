package com.androplay.service

import org.junit.Assert.assertEquals
import org.junit.Test

class MirroringProfileTest {
    private val none = HevcSupport.NONE
    private val hevc1080 = HevcSupport(hardware = true, uhd = false)
    private val hevc4k = HevcSupport(hardware = true, uhd = true)

    @Test
    fun withoutHardwareHevcItIsH264AtUpTo1080p() {
        assertEquals(MirroringProfile(false, 1920, 1080), MirroringProfile.of(ReceiverSettings(), none, 3840, 2160))
        assertEquals(MirroringProfile(false, 1920, 1080), MirroringProfile.of(ReceiverSettings(), none, 1920, 1080))
    }

    @Test
    fun hevcOn4kTvOffers4k() {
        assertEquals(MirroringProfile(true, 3840, 2160), MirroringProfile.of(ReceiverSettings(), hevc4k, 3840, 2160))
        assertEquals("H.265, up to 4K", MirroringProfile.of(ReceiverSettings(), hevc4k, 3840, 2160).label)
    }

    @Test
    fun hevcWithout4kStaysAt1080p() {
        // H.265 still helps quality at 1080p; 4K is only offered when the decoder can do it.
        assertEquals(MirroringProfile(true, 1920, 1080), MirroringProfile.of(ReceiverSettings(), hevc1080, 3840, 2160))
        assertEquals(MirroringProfile(true, 1920, 1080), MirroringProfile.of(ReceiverSettings(), hevc4k, 1920, 1080))
        assertEquals("H.265, up to 1080p", MirroringProfile.of(ReceiverSettings(), hevc4k, 1920, 1080).label)
    }

    @Test
    fun h264OnlyAndExplicitResolutionsWin() {
        val h264Only = ReceiverSettings(videoCodec = ReceiverSettings.CODEC_H264_ONLY)
        assertEquals(MirroringProfile(false, 1920, 1080), MirroringProfile.of(h264Only, hevc4k, 3840, 2160))
        val fixed1080 = ReceiverSettings(resolution = "1080p")
        assertEquals(MirroringProfile(true, 1920, 1080), MirroringProfile.of(fixed1080, hevc4k, 3840, 2160))
        val fixed720 = ReceiverSettings(resolution = "720p")
        assertEquals(MirroringProfile(true, 1280, 720), MirroringProfile.of(fixed720, hevc4k, 3840, 2160))
    }
}
