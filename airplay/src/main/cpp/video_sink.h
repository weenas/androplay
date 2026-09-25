#pragma once

#include <jni.h>
#include <cstdint>

namespace androplay {
void initJvm(JavaVM *vm);
JavaVM *jvm();
void setVideoSink(JNIEnv *env, jobject sink);
/** One Annex B access unit; [isH265] tells H.264 and H.265 (HEVC) mirroring apart. */
void dispatchVideo(const uint8_t *data, int length, int64_t ptsUs, bool isH265);
void dispatchSessionEnd();
}

