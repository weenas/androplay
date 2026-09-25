# AndroPlay - AirPlay Receiver for Android TV

An open-source Android TV project for receiving AirPlay streams. The receiver is under active development and now includes an initial AirPlay mirroring protocol path.

## What is AndroPlay?

AndroPlay aims to receive screen mirroring, audio, and video streams from AirPlay-enabled devices. The current protocol module embeds the legacy-mirroring core from [RPiPlay](https://github.com/FD-/RPiPlay), with Android mDNS advertising and MediaCodec output.

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

The Android TV UI, persistent receiver settings, foreground service lifecycle, mDNS discovery, native AirPlay listener, JNI bridge, and Android MediaCodec video path build successfully. Pressing **Start** now starts the protocol listener and advertises its real port. The initial core supports legacy H.264 mirroring; audio output, H.265, in-app video casting, and broad sender compatibility are not complete. Device-level streaming validation is still in progress.

## Planned Technical Implementation

- **AirPlay Protocol**: RPiPlay currently handles the legacy RAOP/RTSP/RTP protocol stack
- **JNI Bridge**: Native C code interfaces with Android Java/Kotlin layer
- **Video Decoding**: Android MediaCodec (hardware accelerated)
- **Audio Output**: AudioTrack for low-latency audio
- **HLS Streaming**: Media3/ExoPlayer for video casting
- **mDNS Discovery**: Bonjour/Avahi for device discovery

## Building from Source

```bash
# Clone protocol dependencies with the repository
git clone --recurse-submodules git@github.com:weenas/androplay.git
cd AndroPlay

# Build (requires JDK 17, Android SDK 35, NDK 27.0.12077973, and CMake 3.22.1)
./gradlew assembleDebug
```

For an existing clone, initialize the pinned dependencies before building:

```bash
git submodule update --init --recursive
```

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
├── airplay/                # Android library, JNI bridge, and native protocol build
├── third_party/            # Pinned RPiPlay and libplist submodules
└── README.md
```

## License

This project is licensed under the GNU General Public License v3.0.
See the LICENSE file for details.

## Credits

- [RPiPlay](https://github.com/FD-/RPiPlay) - legacy AirPlay mirroring protocol core
- [libplist](https://github.com/libimobiledevice/libplist) - binary plist support
- [UxPlay](https://github.com/FDH2/UxPlay) - reference for newer AirPlay receiver behavior
- Android Open Source Project - Base platform

## Disclaimer

This project is not affiliated with or endorsed by Apple Inc.
AirPlay is a trademark of Apple Inc.
