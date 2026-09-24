# AndroPlay - AirPlay Receiver for Android TV

An open-source Android TV project for receiving AirPlay streams. The Android app and receiver lifecycle are under development; streaming is not available in the current build.

## What is AndroPlay?

AndroPlay aims to receive screen mirroring, audio, and video streams from AirPlay-enabled devices. The planned protocol integration is [UxPlay](https://github.com/FDH2/UxPlay) (GPL-3.0), but it is not included or wired up yet.

## Planned Features

- **Screen Mirroring** - Mirror iPhone/iPad/Mac screens to Android TV (H.264/H.265)
- **Audio Streaming** - Stream music with AAC-ELD/AAC-LC/ALAC decoding
- **Video Casting** - Cast in-app videos via HLS
- **Android TV Optimized** - D-pad navigation, Picture-in-Picture, media session integration
- **Open Source** - GPL-3.0 licensed, fully transparent

## Requirements

- Android 8.0+ (API 26)
- Android TV or Google TV device
- Same local network as AirPlay sender

## Current Status

The Android TV UI, persistent receiver settings, foreground service start/stop flow, and an Android MediaCodec video rendering path build successfully. The renderer accepts complete Annex B H.264/H.265 access units and displays them on a SurfaceView when a protocol backend supplies frames. Pressing **Start** still reports that the receiver engine is unavailable because the native AirPlay protocol implementation is not packaged yet. The app does not advertise itself as an AirPlay target or receive a stream.

## Planned Technical Implementation

- **AirPlay Protocol**: UxPlay C library handles RAOP/RTSP/RTP protocol stack
- **JNI Bridge**: Native C code interfaces with Android Java/Kotlin layer
- **Video Decoding**: Android MediaCodec (hardware accelerated)
- **Audio Output**: AudioTrack for low-latency audio
- **HLS Streaming**: Media3/ExoPlayer for video casting
- **mDNS Discovery**: Bonjour/Avahi for device discovery

## Building from Source

```bash
# Clone
git clone git@github.com:weenas/androplay.git
cd AndroPlay

# Build the Android UI and service shell (requires Android SDK 35)
./gradlew assembleDebug
```

The native CMake target is disabled by default. `-PenableNativeBuild=true` enables the current JNI skeleton and requires a complete Android NDK and CMake installation; it does not yet provide a working AirPlay receiver. The repository does not currently contain a UxPlay submodule. Upstream UxPlay builds a static `airplay` protocol library and uses GStreamer renderers, so Android integration requires porting that library and implementing Android video/audio renderers.

## Project Structure

```
AndroPlay/
├── app/                    # Android application
│   ├── src/main/
│   │   ├── java/com/androplay/
│   │   │   ├── MainActivity.kt
│   │   │   ├── service/       # Service lifecycle and bridge
│   │   │   ├── ui/            # Compose UI screens
│   │   │   ├── viewmodel/     # MVVM ViewModels
│   │   │   └── receiver/      # BroadcastReceivers
│   │   ├── cpp/              # C++ native code (CMake)
│   │   └── res/              # Android resources
│   └── build.gradle.kts
└── README.md

## License

This project is licensed under the GNU General Public License v3.0.
See the LICENSE file for details.

## Credits

- [UxPlay](https://github.com/FDH2/UxPlay) - AirPlay protocol implementation (GPL-3.0)
- Android Open Source Project - Base platform

## Disclaimer

This project is not affiliated with or endorsed by Apple Inc.
AirPlay is a trademark of Apple Inc.
