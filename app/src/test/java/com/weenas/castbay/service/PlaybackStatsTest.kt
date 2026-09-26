package com.weenas.castbay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStatsTest {
    @Test
    fun rateMeterMeasuresFramesAndBits() {
        var clock = 0L
        val meter = RateMeter(windowMs = 2000) { clock }
        // 60 fps, 25 000 bytes per frame = 12 Mbps, over one second.
        repeat(61) { i ->
            clock = i * 1000L / 60
            meter.record(25_000)
        }
        assertEquals(60.0, meter.perSecond()!!, 0.5)
        assertEquals(12_000_000.0, meter.bitsPerSecond()!!.toDouble(), 150_000.0)
    }

    @Test
    fun rateMeterNeedsEnoughDataAndForgetsOldSamples() {
        var clock = 0L
        val meter = RateMeter(windowMs = 2000) { clock }
        assertNull(meter.perSecond())
        meter.record(100)
        clock = 100
        meter.record(100)
        assertNull("100 ms is too short to report", meter.perSecond())
        clock = 10_000
        assertNull("samples older than the window are dropped", meter.bitsPerSecond())
    }

    @Test
    fun formatting() {
        assertEquals("12.4 Mbps", StatsFormat.bitrate(12_400_000))
        assertEquals("256 kbps", StatsFormat.bitrate(256_000))
        assertEquals("—", StatsFormat.bitrate(null))
        assertEquals("60 fps", StatsFormat.fps(59.8))
        assertEquals("1728×796", StatsFormat.resolution(1728, 796))
        assertEquals(
            "44.1 kHz · 16-bit · stereo",
            StatsFormat.audioFormat(AudioStats("ALAC", sampleRate = 44100, channels = 2, bitsPerSample = 16))
        )
    }

    @Test
    fun codecNames() {
        assertEquals("H.265", StatsFormat.codecName("video/hevc"))
        assertEquals("H.264", StatsFormat.codecName("avc1.64002A"))
        assertEquals("VP9", StatsFormat.codecName("vp09.00.41.08"))
        assertEquals("HE-AAC", StatsFormat.codecName("mp4a.40.5"))
        assertEquals("AAC-LC", StatsFormat.codecName("mp4a.40.2"))
        assertEquals("—", StatsFormat.codecName(null))
    }
}
