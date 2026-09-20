# AndroPlay - AirPlay Receiver for Android TV

A free, open-source AirPlay 2 receiver that turns Android TV into an AirPlay-compatible display and speaker.

## What is AndroPlay?

AndroPlay is an Android TV app that receives screen mirroring, audio, and video streams from iOS/iPadOS, macOS, and other AirPlay-enabled devices. Built on the [UxPlay](https://github.com/FDH2/UxPlay) library (GPL-3.0).

## Features

- **Screen Mirroring** - Mirror iPhone/iPad/Mac screens to Android TV (H.264/H.265)
- **Audio Streaming** - Stream music with AAC-ELD/AAC-LC/ALAC decoding
- **Video Casting** - Cast in-app videos via HLS
- **Android TV Optimized** - D-pad navigation, Picture-in-Picture, media session integration
- **Open Source** - GPL-3.0 licensed, fully transparent

## Requirements

- Android 8.0+ (API 26)
- Android TV or Google TV device
- Same local network as AirPlay sender

## How It Works

1. Install AndroPlay on your Android TV
2. Open the app and tap **Start**
3. On your iPhone/iPad/Mac, open Control Center → Screen Mirroring
4. Select "AndroPlay" from the device list
5. Your screen is now mirrored to Android TV!

## Technical Implementation

- **AirPlay Protocol**: UxPlay C library handles RAOP/RTSP/RTP protocol stack
- **JNI Bridge**: Native C code interfaces with Android Java/Kotlin layer
- **Video Decoding**: Android MediaCodec (hardware accelerated)
- **Audio Output**: AudioTrack for low-latency audio
- **HLS Streaming**: Media3/ExoPlayer for video casting
- **mDNS Discovery**: Bonjour/Avahi for device discovery

## Building from Source

```bash
# Clone and initialize submodules
git clone https://github.com/your-org/AndroPlay.git
cd AndroPlay
git submodule update --init --recursive

# Build (requires Android SDK 35, NDK r25+, CMake 3.22+)
./gradlew assembleDebug
```

## Project Structure

```
AndroPlay/
├── app/                    # Android application
│   ├── src/main/
│   │   ├── java/com/androplay/
│   │   │   ├── MainActivity.kt
│   │   │   ├── service/       # AirPlay & Discovery services
│   │   │   ├── native/        # Native bridge (JNI)
│   │   │   ├── ui/            # Compose UI screens
│   │   │   ├── viewmodel/     # MVVM ViewModels
│   │   │   └── receiver/      # BroadcastReceivers
│   │   ├── cpp/              # C++ native code (CMake)
│   │   └── res/              # Android resources
│   └── build.gradle.kts
├── native/
│   └── uxplay/               # UxPlay submodule
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
