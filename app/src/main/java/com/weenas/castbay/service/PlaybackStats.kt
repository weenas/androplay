package com.weenas.castbay.service

import android.os.SystemClock

/** A "stats for nerds" snapshot of what is playing, for the optional on-screen overlay. */
data class PlaybackStats(
    /** e.g. "Screen mirroring", "AirPlay video", "AirPlay audio". */
    val source: String,
    val video: VideoStats? = null,
    val audio: AudioStats? = null,
    /** Extra lines, e.g. network speed and buffer for AirPlay video. */
    val extra: List<Pair<String, String>> = emptyList()
)

data class VideoStats(
    /** e.g. "H.265", "VP9". */
    val codec: String,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Double? = null,
    val bitrateBps: Long? = null,
    val decoder: String? = null,
    val droppedFrames: Long = 0
)

data class AudioStats(
    /** e.g. "AAC-ELD", "ALAC". */
    val codec: String,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitsPerSample: Int = 0,
    val bitrateBps: Long? = null,
    val decoder: String? = null
)

/**
 * Measures frames and bytes per second over a sliding window, for stream types that carry no
 * nominal rate (mirroring, AirPlay audio). Thread-safe; [now] is injectable for tests.
 */
class RateMeter(
    private val windowMs: Long = 2000,
    private val now: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val times = ArrayDeque<Long>()
    private val sizes = ArrayDeque<Int>()
    private var windowBytes = 0L

    @Synchronized
    fun record(bytes: Int) {
        val time = now()
        times.addLast(time)
        sizes.addLast(bytes)
        windowBytes += bytes
        trim(time)
    }

    /** Frames per second over the window, or null before there is enough data. */
    @Synchronized
    fun perSecond(): Double? {
        val span = span() ?: return null
        return (times.size - 1) * 1000.0 / span
    }

    /** Bits per second over the window, or null before there is enough data. */
    @Synchronized
    fun bitsPerSecond(): Long? {
        val span = span() ?: return null
        // The first sample marks the window's start; its bytes arrived before the span.
        return ((windowBytes - sizes.first()) * 8 * 1000 / span)
    }

    @Synchronized
    fun reset() {
        times.clear()
        sizes.clear()
        windowBytes = 0
    }

    /** Milliseconds between the first and last sample, if long enough to be meaningful. */
    private fun span(): Long? {
        trim(now())
        if (times.size < 2) return null
        return (times.last() - times.first()).takeIf { it >= MIN_SPAN_MS }
    }

    private fun trim(time: Long) {
        while (times.isNotEmpty() && time - times.first() > windowMs) {
            times.removeFirst()
            windowBytes -= sizes.removeFirst()
        }
    }

    private companion object {
        const val MIN_SPAN_MS = 500L
    }
}

/** Formatting shared by the overlay and tests. */
object StatsFormat {
    fun bitrate(bps: Long?): String = when {
        bps == null || bps <= 0 -> "—"
        bps >= 1_000_000 -> "%.1f Mbps".format(java.util.Locale.US, bps / 1_000_000.0)
        else -> "%d kbps".format(java.util.Locale.US, bps / 1000)
    }

    fun fps(fps: Double?): String = if (fps == null || fps <= 0) "—" else "%.0f fps".format(java.util.Locale.US, fps)

    fun resolution(width: Int, height: Int): String = if (width > 0 && height > 0) "${width}×$height" else "—"

    fun audioFormat(stats: AudioStats): String = listOfNotNull(
        stats.sampleRate.takeIf { it > 0 }?.let { "%.1f kHz".format(java.util.Locale.US, it / 1000.0) },
        stats.bitsPerSample.takeIf { it > 0 }?.let { "$it-bit" },
        when (stats.channels) {
            0 -> null
            1 -> "mono"
            2 -> "stereo"
            else -> "${stats.channels} ch"
        }
    ).joinToString(" · ").ifEmpty { "—" }

    /** Human names for MIME types and RFC 6381 codec strings (e.g. "vp09.00.41.08"). */
    fun codecName(mimeOrCodecs: String?): String {
        val value = mimeOrCodecs?.lowercase().orEmpty()
        return when {
            value.startsWith("video/hevc") || value.startsWith("hvc1") || value.startsWith("hev1") -> "H.265"
            value.startsWith("video/avc") || value.startsWith("avc1") || value.startsWith("avc3") -> "H.264"
            value.contains("vp9") || value.startsWith("vp09") -> "VP9"
            value.contains("av01") || value.contains("video/av01") -> "AV1"
            value.startsWith("mp4a.40.2") -> "AAC-LC"
            value.startsWith("mp4a.40.5") || value.startsWith("mp4a.40.29") -> "HE-AAC"
            value.startsWith("audio/mp4a") || value.startsWith("mp4a") -> "AAC"
            value.contains("opus") -> "Opus"
            value.contains("ac-3") || value.contains("ac3") -> "Dolby Digital"
            value.contains("ec-3") || value.contains("eac3") -> "Dolby Digital Plus"
            value.isEmpty() -> "—"
            else -> mimeOrCodecs.orEmpty()
        }
    }
}
