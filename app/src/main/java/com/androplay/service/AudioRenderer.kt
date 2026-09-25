package com.androplay.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer
import java.util.ArrayDeque

/**
 * Decodes the AAC-ELD audio RPiPlay delivers (raw frames, 44.1 kHz stereo, 480 samples each)
 * and plays it through an [AudioTrack].
 *
 * Mirroring is live, so frames are played as they arrive rather than scheduled by timestamp.
 * The decoder is created lazily on the first frame and torn down when the session ends.
 */
class AudioRenderer {
    private val lock = Any()
    private val handler = Handler(HandlerThread("AndroPlay-audio").apply { start() }.looper)

    private var codec: MediaCodec? = null
    private var track: AudioTrack? = null
    private val pendingFrames = ArrayDeque<ByteArray>()
    private val freeInputs = ArrayDeque<Int>()
    private var droppedFrames = 0L

    fun render(frame: ByteArray) {
        if (frame.isEmpty()) return
        synchronized(lock) {
            if (codec == null) startCodecLocked()
            if (codec == null) return
            if (pendingFrames.size >= MAX_PENDING_FRAMES) {
                // Audio frames decode independently, so dropping the oldest only costs a
                // short gap and keeps latency bounded.
                pendingFrames.removeFirst()
                droppedFrames++
            }
            pendingFrames.addLast(frame)
            feedLocked()
        }
    }

    fun stop() {
        synchronized(lock) {
            releaseLocked()
            if (droppedFrames > 0) Log.i(TAG, "Session ended, $droppedFrames audio frames dropped")
            droppedFrames = 0
        }
    }

    private fun startCodecLocked() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD)
            setInteger(MediaFormat.KEY_IS_ADTS, 0)
            setByteBuffer("csd-0", ByteBuffer.wrap(ELD_AUDIO_SPECIFIC_CONFIG))
        }
        val decoder = try {
            MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to create AAC decoder", error)
            return
        }
        try {
            decoder.setCallback(DecoderCallback(decoder), handler)
            decoder.configure(format, null, null, 0)
            decoder.start()
            codec = decoder
            Log.i(TAG, "AAC-ELD decoder started: ${decoder.name}")
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start AAC-ELD decoder", error)
            decoder.release()
        }
    }

    private fun feedLocked() {
        val decoder = codec ?: return
        while (freeInputs.isNotEmpty() && pendingFrames.isNotEmpty()) {
            val index = freeInputs.removeFirst()
            val frame = pendingFrames.removeFirst()
            try {
                val input = decoder.getInputBuffer(index) ?: continue
                if (frame.size > input.capacity()) {
                    decoder.queueInputBuffer(index, 0, 0, 0, 0)
                    droppedFrames++
                    continue
                }
                input.clear()
                input.put(frame)
                decoder.queueInputBuffer(index, 0, frame.size, 0, 0)
            } catch (error: IllegalStateException) {
                Log.e(TAG, "Audio decoder rejected input", error)
                releaseLocked()
                return
            }
        }
    }

    private fun releaseLocked() {
        pendingFrames.clear()
        freeInputs.clear()
        codec?.let { decoder ->
            codec = null
            try {
                decoder.stop()
            } catch (error: IllegalStateException) {
                Log.w(TAG, "Audio decoder was already stopped", error)
            } finally {
                decoder.release()
            }
        }
        track?.let {
            track = null
            try {
                it.stop()
            } catch (error: IllegalStateException) {
                Log.w(TAG, "AudioTrack was already stopped", error)
            }
            it.release()
        }
    }

    private fun createTrack(sampleRate: Int, channels: Int): AudioTrack {
        val channelMask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer * 2)
            .build()
            .also { it.play() }
    }

    /** Callbacks run on the audio thread; [owner] guards against events from a released codec. */
    private inner class DecoderCallback(private val owner: MediaCodec) : MediaCodec.Callback() {
        override fun onInputBufferAvailable(mc: MediaCodec, index: Int) {
            synchronized(lock) {
                if (codec !== owner) return
                freeInputs.addLast(index)
                feedLocked()
            }
        }

        override fun onOutputBufferAvailable(mc: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            val output: AudioTrack
            val pcm: ByteArray
            synchronized(lock) {
                if (codec !== owner) return
                try {
                    val buffer = mc.getOutputBuffer(index)
                    pcm = if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        ByteArray(info.size).also { buffer.get(it) }
                    } else {
                        ByteArray(0)
                    }
                    mc.releaseOutputBuffer(index, false)
                } catch (error: IllegalStateException) {
                    Log.w(TAG, "Could not read audio output buffer", error)
                    return
                }
                output = track ?: createTrack(SAMPLE_RATE, CHANNELS).also { track = it }
            }
            // Blocking write paces this thread to playback without holding the lock, so the
            // protocol thread can keep queueing input meanwhile.
            if (pcm.isNotEmpty()) output.write(pcm, 0, pcm.size)
        }

        override fun onOutputFormatChanged(mc: MediaCodec, format: MediaFormat) {
            Log.i(TAG, "Audio decoder output format: $format")
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            synchronized(lock) {
                if (codec !== owner) return
                track?.release()
                track = createTrack(sampleRate, channels)
            }
        }

        override fun onError(mc: MediaCodec, error: MediaCodec.CodecException) {
            synchronized(lock) {
                if (codec !== owner) return
                Log.e(TAG, "Audio decoder error (transient=${error.isTransient})", error)
                if (!error.isTransient) releaseLocked()
            }
        }
    }

    private companion object {
        const val TAG = "AndroPlayAudio"
        const val SAMPLE_RATE = 44100
        const val CHANNELS = 2
        /** AudioSpecificConfig for AAC-ELD, 44.1 kHz, stereo, 480-sample frames (as in RPiPlay). */
        val ELD_AUDIO_SPECIFIC_CONFIG = byteArrayOf(0xF8.toByte(), 0xE8.toByte(), 0x50, 0x00)
        /** About half a second of 480-sample frames. */
        const val MAX_PENDING_FRAMES = 48
    }
}
