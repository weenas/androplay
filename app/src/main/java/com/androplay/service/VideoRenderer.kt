package com.androplay.service

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

/** Receives complete Annex B access units from the protocol layer and renders them on a TV Surface. */
class VideoRenderer {
    private var surface: Surface? = null
    private var codec: MediaCodec? = null
    private var width = 0
    private var height = 0
    private var mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
    private var awaitingKeyFrame = true

    @Synchronized
    fun setSurface(value: Surface?) {
        if (surface === value) return
        releaseCodec()
        surface = value
        startCodecIfReady()
    }

    @Synchronized
    fun configure(width: Int, height: Int, isH265: Boolean = false) {
        require(width > 0 && height > 0) { "Video dimensions must be positive" }
        val requestedMime = if (isH265) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
        if (this.width == width && this.height == height && mimeType == requestedMime) return
        releaseCodec()
        this.width = width
        this.height = height
        mimeType = requestedMime
        startCodecIfReady()
    }

    /** Returns false when no decoder/surface is ready or when the decoder cannot accept this frame. */
    @Synchronized
    fun render(accessUnit: ByteArray, presentationTimeUs: Long): Boolean {
        if (accessUnit.isEmpty()) return false
        val decoder = codec ?: return false
        if (awaitingKeyFrame && !containsRandomAccessNal(accessUnit)) return false
        return try {
            val inputIndex = decoder.dequeueInputBuffer(0)
            if (inputIndex < 0) return false
            val input = decoder.getInputBuffer(inputIndex)
            if (input == null) {
                decoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0)
                return false
            }
            if (accessUnit.size > input.capacity()) {
                Log.w(TAG, "Dropping oversized video access unit: ${accessUnit.size} bytes")
                decoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0)
                return false
            }
            input.clear()
            input.put(accessUnit)
            decoder.queueInputBuffer(inputIndex, 0, accessUnit.size, presentationTimeUs, 0)
            awaitingKeyFrame = false
            drainOutput(decoder)
            true
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Video decoder failed", error)
            releaseCodec()
            false
        }
    }

    @Synchronized
    fun stop() {
        releaseCodec()
        width = 0
        height = 0
    }

    private fun startCodecIfReady() {
        val target = surface ?: return
        if (!target.isValid || width <= 0 || height <= 0) return
        try {
            val decoder = MediaCodec.createDecoderByType(mimeType)
            try {
                decoder.configure(MediaFormat.createVideoFormat(mimeType, width, height), target, null, 0)
                decoder.start()
                codec = decoder
            } catch (error: Exception) {
                decoder.release()
                throw error
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start $mimeType decoder", error)
        }
    }

    private fun drainOutput(decoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = decoder.dequeueOutputBuffer(info, 0)
            if (outputIndex < 0) break
            decoder.releaseOutputBuffer(outputIndex, info.size > 0)
        }
    }

    private fun releaseCodec() {
        awaitingKeyFrame = true
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

    private fun containsRandomAccessNal(data: ByteArray): Boolean {
        val h265 = mimeType == MediaFormat.MIMETYPE_VIDEO_HEVC
        var index = 0
        while (index < data.size - 4) {
            if (data[index] == 0.toByte() && data[index + 1] == 0.toByte() &&
                data[index + 2] == 0.toByte() && data[index + 3] == 1.toByte()) {
                val type = if (h265) (data[index + 4].toInt() ushr 1) and 0x3f
                else data[index + 4].toInt() and 0x1f
                if (if (h265) type in 19..21 || type in 32..34 else type == 5 || type == 7) {
                    return true
                }
                index += 4
            } else {
                index++
            }
        }
        return false
    }

    private companion object {
        const val TAG = "AndroPlayVideo"
    }
}
