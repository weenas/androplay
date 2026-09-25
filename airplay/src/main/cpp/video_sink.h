#pragma once

#include <jni.h>
#include <cstdint>

namespace androplay {
void initJvm(JavaVM *vm);
JavaVM *jvm();
void setVideoSink(JNIEnv *env, jobject sink);
void dispatchVideo(const uint8_t *data, int length, int64_t ptsUs);
void dispatchSessionEnd();
}

