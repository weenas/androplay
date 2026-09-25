#pragma once

#include <jni.h>
#include <cstdint>

namespace androplay {
void setAudioSink(JNIEnv *env, jobject sink);
void dispatchAudio(const uint8_t *data, int length, int64_t ptsUs);
}

