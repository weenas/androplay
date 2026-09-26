# The native protocol core calls into these by name (FindClass / GetStaticMethodID /
# GetMethodID in jni_bridge.cpp, video_sink.cpp, audio_sink.cpp): R8 must not rename or
# remove them, or the receiver crashes at run time.
-keep class com.weenas.castbay.protocol.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
