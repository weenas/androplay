package com.androplay.service

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.util.ArrayDeque

/**
 * Receives complete Annex B access units from the protocol layer and renders them on a TV Surface.
 *
 * The decoder runs in asynchronous mode on its own thread. Incoming frames are queued and fed as
 * soon as MediaCodec releases an input buffer, so a momentarily busy decoder no longer causes
 * frames to be dropped (which used to corrupt every following P-frame until the next IDR).
 *
 * RPiPlay delivers SPS/PPS as a separate buffer only once per session, so that codec config is
 * cached and replayed whenever the decoder is (re)created, e.g. when the Surface appears late.
 */
class VideoRenderer {
    private val lock = Any()
    private val handler = Handler(HandlerThread("AndroPlay-video").apply { start() }.looper)

    private var surface: Surface? = null
    private var codec: MediaCodec? = null
    private var width = 0
    private var height = 0
    private var mimeType = MediaFormat.MIMETYPE_VIDEO_AVC

    /** Latest parameter-set buffer (SPS/PPS, or VPS/SPS/PPS for HEVC). */
    private var codecConfig: ByteArray? = null
    private var awaitingKeyFrame = true
    private val pendingFrames = ArrayDeque<Frame>()
    private val freeInputs = ArrayDeque<Int>()
    private var droppedFrames = 0L

    private class Frame(val data: ByteArray, val presentationTimeUs: Long, val flags: Int) {
        val isConfig: Boolean get() = flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
    }

    fun setSurface(value: Surface?) {
        synchronized(lock) {
            if (surface === value) return
            surface = value
            val decoder = codec
            if (decoder != null && value != null && value.isValid) {
                // Swap outputs without tearing down the decoder, so no new IDR frame is needed.
                try {
                    decoder.setOutputSurface(value)
                    return
                } catch (error: Exception) {
                    Log.w(TAG, "setOutputSurface failed, recreating decoder", error)
                }
            }
            releaseCodecLocked()
            startCodecIfReadyLocked()
        }
    }

    fun configure(width: Int, height: Int, isH265: Boolean = false) {
        require(width > 0 && height > 0) { "Video dimensions must be positive" }
        val requestedMime = if (isH265) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
        synchronized(lock) {
            if (this.width == width && this.height == height && mimeType == requestedMime) return
            releaseCodecLocked()
            if (mimeType != requestedMime) codecConfig = null
            this.width = width
            this.height = height
            mimeType = requestedMime
            startCodecIfReadyLocked()
        }
    }

    /**
     * Queues one access unit for decoding. Returns false when the frame was discarded
     * (no decoder yet, waiting for a key frame, or the backlog was flushed).
     */
    fun render(accessUnit: ByteArray, presentationTimeUs: Long): Boolean {
        if (accessUnit.isEmpty()) return false
        synchronized(lock) {
            val nal = scanNals(accessUnit)

            if (nal.hasParameterSets && !nal.hasSlice) {
                codecConfig = accessUnit
                if (codec != null) enqueueLocked(Frame(accessUnit, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG))
                return true
            }

            if (codec == null) return false
            if (awaitingKeyFrame && !nal.hasRandomAccess) {
                droppedFrames++
                return false
            }

            if (pendingFrames.size >= MAX_PENDING_FRAMES) {
                // The decoder cannot keep up. Dropping arbitrary P-frames would corrupt the
                // picture, so flush the backlog and resume cleanly from the next key frame.
                Log.w(TAG, "Decoder backlog of ${pendingFrames.size} frames, resyncing on next key frame")
                droppedFrames += pendingFrames.count { !it.isConfig }
                pendingFrames.removeAll { !it.isConfig }
                awaitingKeyFrame = true
                if (!nal.hasRandomAccess) {
                    droppedFrames++
                    return false
                }
            }

            awaitingKeyFrame = false
            val flags = if (nal.hasRandomAccess) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            enqueueLocked(Frame(accessUnit, presentationTimeUs, flags))
            return true
        }
    }

    fun stop() {
        synchronized(lock) {
            releaseCodecLocked()
            width = 0
            height = 0
            codecConfig = null
            if (droppedFrames > 0) Log.i(TAG, "Session ended, $droppedFrames video frames dropped")
            droppedFrames = 0
        }
    }

    private fun enqueueLocked(frame: Frame) {
        pendingFrames.addLast(frame)
        feedLocked()
    }

    /** Copies queued frames into the input buffers the decoder has handed us. */
    private fun feedLocked() {
        val decoder = codec ?: return
        while (freeInputs.isNotEmpty() && pendingFrames.isNotEmpty()) {
            val index = freeInputs.removeFirst()
            val frame = pendingFrames.removeFirst()
            try {
                val input = decoder.getInputBuffer(index)
                if (input == null || frame.data.size > input.capacity()) {
                    Log.w(TAG, "Dropping video access unit of ${frame.data.size} bytes")
                    decoder.queueInputBuffer(index, 0, 0, frame.presentationTimeUs, 0)
                    if (!frame.isConfig) {
                        droppedFrames++
                        awaitingKeyFrame = true
                    }
                    continue
                }
                input.clear()
                input.put(frame.data)
                decoder.queueInputBuffer(index, 0, frame.data.size, frame.presentationTimeUs, frame.flags)
            } catch (error: IllegalStateException) {
                Log.e(TAG, "Video decoder rejected input", error)
                restartCodecLocked()
                return
            }
        }
    }

    private fun startCodecIfReadyLocked() {
        val target = surface ?: return
        if (!target.isValid || width <= 0 || height <= 0 || codec != null) return
        val decoder = try {
            MediaCodec.createDecoderByType(mimeType)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to create $mimeType decoder", error)
            return
        }
        try {
            decoder.setCallback(DecoderCallback(decoder), handler)
            decoder.configure(MediaFormat.createVideoFormat(mimeType, width, height), target, null, 0)
            codec = decoder
            awaitingKeyFrame = true
            codecConfig?.let { pendingFrames.addFirst(Frame(it, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)) }
            decoder.start()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start $mimeType decoder", error)
            codec = null
            pendingFrames.clear()
            decoder.release()
        }
    }

    private fun releaseCodecLocked() {
        awaitingKeyFrame = true
        pendingFrames.clear()
        freeInputs.clear()
        val decoder = codec ?: return
        codec = null
        try {
            decoder.stop()
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Decoder was already stopped", error)
        } finally {
            decoder.release()
        }
    }

    private fun restartCodecLocked() {
        releaseCodecLocked()
        startCodecIfReadyLocked()
    }

    /** Callbacks run on the decoder thread; [owner] guards against events from a released codec. */
    private inner class DecoderCallback(private val owner: MediaCodec) : MediaCodec.Callback() {
        override fun onInputBufferAvailable(mc: MediaCodec, index: Int) {
            synchronized(lock) {
                if (codec !== owner) return
                freeInputs.addLast(index)
                feedLocked()
            }
        }

        override fun onOutputBufferAvailable(mc: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            synchronized(lock) {
                if (codec !== owner) return
                try {
                    // Mirroring is live: show every decoded frame immediately.
                    mc.releaseOutputBuffer(index, info.size > 0)
                } catch (error: IllegalStateException) {
                    Log.w(TAG, "Could not release output buffer", error)
                }
            }
        }

        override fun onOutputFormatChanged(mc: MediaCodec, format: MediaFormat) {
            Log.i(TAG, "Decoder output format: $format")
        }

        override fun onError(mc: MediaCodec, error: MediaCodec.CodecException) {
            synchronized(lock) {
                if (codec !== owner) return
                Log.e(TAG, "Video decoder error (transient=${error.isTransient})", error)
                if (!error.isTransient) restartCodecLocked()
            }
        }
    }

    private class NalSummary(
        val hasParameterSets: Boolean,
        val hasSlice: Boolean,
        val hasRandomAccess: Boolean
    )

    /** Walks Annex B NAL units (3- or 4-byte start codes) and classifies their types. */
    private fun scanNals(data: ByteArray): NalSummary {
        val h265 = mimeType == MediaFormat.MIMETYPE_VIDEO_HEVC
        var parameterSets = false
        var slice = false
        var randomAccess = false
        var index = 0
        while (index + 3 < data.size) {
            if (data[index] == 0.toByte() && data[index + 1] == 0.toByte() && data[index + 2] == 1.toByte()) {
                val header = data[index + 3].toInt() and 0xFF
                if (h265) {
                    val type = (header ushr 1) and 0x3F
                    if (type in 32..34) parameterSets = true
                    if (type in 0..31) slice = true
                    if (type in 16..21) randomAccess = true
                } else {
                    val type = header and 0x1F
                    if (type == 7 || type == 8) parameterSets = true
                    if (type in 1..5) slice = true
                    if (type == 5) randomAccess = true
                }
                index += 3
            } else {
                index++
            }
        }
        return NalSummary(parameterSets, slice, randomAccess)
    }

    private companion object {
        const val TAG = "AndroPlayVideo"
        /** About two seconds at 60 fps before the backlog is flushed. */
        const val MAX_PENDING_FRAMES = 120
    }
}
