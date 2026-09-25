package com.androplay.service

import android.os.SystemClock

/** What an audio-streaming sender (e.g. a music app) is playing. */
data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** Encoded image (usually JPEG), or null. */
    val coverArt: ByteArray? = null,
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    /** [SystemClock.elapsedRealtime] when [positionSec] was reported. */
    val positionAtMs: Long = 0L,
    /** False while the sender is paused (it flushed and stopped sending audio). */
    val playing: Boolean = true
) {
    /** The position now: it advances from the last report only while [playing]. */
    fun currentPositionSec(nowMs: Long = SystemClock.elapsedRealtime()): Double {
        if (durationSec <= 0.0 || !playing) return positionSec
        return (positionSec + (nowMs - positionAtMs) / 1000.0).coerceIn(0.0, durationSec)
    }

    /** Freezes the position at [nowMs], e.g. when the sender pauses. */
    fun paused(nowMs: Long = SystemClock.elapsedRealtime()) =
        copy(positionSec = currentPositionSec(nowMs), positionAtMs = nowMs, playing = false)

    /** Resumes counting from [nowMs]. */
    fun resumed(nowMs: Long = SystemClock.elapsedRealtime()) =
        copy(positionAtMs = nowMs, playing = true)

    // ByteArray has identity equality; compare the image by content so state updates
    // with the same cover don't count as changes.
    override fun equals(other: Any?): Boolean =
        other is NowPlaying && title == other.title && artist == other.artist &&
            album == other.album && coverArt.contentEquals(other.coverArt) &&
            positionSec == other.positionSec && durationSec == other.durationSec &&
            positionAtMs == other.positionAtMs && playing == other.playing

    override fun hashCode(): Int =
        listOf(title, artist, album, coverArt?.contentHashCode(), positionSec, durationSec, positionAtMs, playing)
            .hashCode()
}

/**
 * Parses the DMAP metadata AirPlay audio senders send: an "mlit" (listing item) container of
 * 4-byte tag + 4-byte big-endian length + value entries.
 */
object DmapMetadata {
    data class Track(val title: String?, val artist: String?, val album: String?)

    /** Returns null if [data] is not a well-formed "mlit" item. */
    fun parse(data: ByteArray): Track? {
        val (tag, length) = header(data, 0) ?: return null
        if (tag != "mlit" || 8 + length > data.size) return null
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var offset = 8
        val end = 8 + length
        while (offset + 8 <= end) {
            val (itemTag, itemLength) = header(data, offset) ?: return null
            val start = offset + 8
            if (start + itemLength > end) return null
            val value by lazy { String(data, start, itemLength, Charsets.UTF_8).takeIf { it.isNotBlank() } }
            when (itemTag) {
                "minm" -> title = value
                "asar" -> artist = value
                "asal" -> album = value
            }
            offset = start + itemLength
        }
        return Track(title, artist, album)
    }

    private fun header(data: ByteArray, offset: Int): Pair<String, Int>? {
        if (offset + 8 > data.size) return null
        val tag = String(data, offset, 4, Charsets.US_ASCII)
        val length = (data[offset + 4].toInt() and 0xff shl 24) or
            (data[offset + 5].toInt() and 0xff shl 16) or
            (data[offset + 6].toInt() and 0xff shl 8) or
            (data[offset + 7].toInt() and 0xff)
        if (length < 0) return null
        return tag to length
    }
}
