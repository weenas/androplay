package com.androplay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class DmapMetadataTest {
    private fun item(tag: String, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag.toByteArray(Charsets.US_ASCII))
        val n = value.size
        out.write(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()))
        out.write(value)
        return out.toByteArray()
    }

    private fun item(tag: String, value: String) = item(tag, value.toByteArray(Charsets.UTF_8))

    private fun listing(vararg items: ByteArray) =
        item("mlit", items.fold(ByteArray(0)) { acc, b -> acc + b })

    @Test
    fun readsTitleArtistAlbum() {
        val data = listing(
            item("mper", byteArrayOf(0, 0, 0, 0, 0, 0, 0, 42)),
            item("minm", "晴天"),
            item("asar", "周杰伦"),
            item("asal", "叶惠美"),
            item("caps", byteArrayOf(1))
        )
        assertEquals(DmapMetadata.Track("晴天", "周杰伦", "叶惠美"), DmapMetadata.parse(data))
    }

    @Test
    fun missingOrBlankFieldsAreNull() {
        val data = listing(item("minm", "Song"), item("asar", ""))
        assertEquals(DmapMetadata.Track("Song", null, null), DmapMetadata.parse(data))
    }

    @Test
    fun rejectsOtherContainersAndTruncatedData() {
        assertNull(DmapMetadata.parse(item("abcd", "x")))
        assertNull(DmapMetadata.parse(byteArrayOf(1, 2, 3)))
        val data = listing(item("minm", "Song"))
        assertNull(DmapMetadata.parse(data.copyOf(data.size - 2)))
    }

    @Test
    fun positionAdvancesButStaysWithinDuration() {
        val playing = NowPlaying(positionSec = 10.0, durationSec = 12.0, positionAtMs = 1_000)
        assertEquals(11.5, playing.currentPositionSec(nowMs = 2_500), 1e-9)
        assertEquals(12.0, playing.currentPositionSec(nowMs = 60_000), 1e-9)
    }

    @Test
    fun aGapInAudioMeansPaused() {
        val playing = NowPlaying(positionSec = 10.0, durationSec = 100.0, positionAtMs = 1_000)
        assertEquals(false, playing.stalled(lastAudioAtMs = 5_000, nowMs = 5_500))
        assertEquals(true, playing.stalled(lastAudioAtMs = 5_000, nowMs = 6_200))
        assertEquals(false, playing.paused(nowMs = 5_000).stalled(lastAudioAtMs = 5_000, nowMs = 9_000))
        assertEquals("no audio yet", false, playing.stalled(lastAudioAtMs = 0, nowMs = 9_000))
    }

    @Test
    fun pauseFreezesAndResumeContinuesThePosition() {
        val paused = NowPlaying(positionSec = 10.0, durationSec = 100.0, positionAtMs = 1_000).paused(nowMs = 3_000)
        assertEquals(12.0, paused.currentPositionSec(nowMs = 30_000), 1e-9)
        val resumed = paused.resumed(nowMs = 30_000)
        assertEquals(13.0, resumed.currentPositionSec(nowMs = 31_000), 1e-9)
    }
}
