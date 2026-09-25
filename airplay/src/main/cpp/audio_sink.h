#pragma once

#include <jni.h>
#include <cstdint>

namespace androplay {
void setAudioSink(JNIEnv *env, jobject sink);
/** Compressed AAC-ELD frame (screen mirroring). */
void dispatchAudio(const uint8_t *data, int length, int64_t ptsUs);
/** Decoded interleaved S16 stereo PCM at 44.1 kHz (ALAC audio streaming). */
void dispatchPcm(const int16_t *samples, int count, int64_t ptsUs);
/** The sender flushed (pause, seek, next track): drop audio not yet played. */
void dispatchAudioFlush();
/** The sender's volume slider in AirPlay dB (-30 to 0, -144 = mute). */
void dispatchVolume(float db);
/** Now-playing info for audio streaming: a DMAP "mlit" listing item. */
void dispatchMetadata(const void *dmap, int length);
/** Cover art image bytes (usually JPEG); empty clears it. */
void dispatchCoverArt(const void *image, int length);
void dispatchProgress(double positionSec, double durationSec);
}

