# CastBay - AirPlay Receiver for Android TV

An open-source Android TV project for receiving AirPlay streams. The receiver is under active development: screen mirroring and in-app video casting (e.g. the YouTube app) work on real devices.

## What is CastBay?

CastBay aims to receive screen mirroring, audio, and video streams from AirPlay-enabled devices. The protocol module embeds the protocol library (`lib/`) from [UxPlay](https://github.com/FDH2/UxPlay), with Android NsdManager advertising, MediaCodec/AudioTrack output for mirroring, and Media3 ExoPlayer for AirPlay video.

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

Tested with an iPhone on a Sony BRAVIA (Android 12):

- **Screen mirroring** with AAC-ELD audio, portrait and landscape.
- **AirPlay video (HLS)** from in-app players such as YouTube: the phone hands over the playlists and the TV plays them with ExoPlayer, resuming at the phone's position.

AirPlay video is fetched by the TV itself (as on an Apple TV), so the TV must be able to reach the video source directly. For YouTube that means `googlevideo.com`; on networks where the phone only reaches it through a proxy or VPN, the TV needs one too, or playback stays black.

Not done yet: audio-only AirPlay (music apps send ALAC, which Android has no built-in decoder for), H.265 mirroring, and on-screen loading/error feedback during AirPlay video.

## Planned Technical Implementation

- **AirPlay Protocol**: UxPlay's `lib/` (RAOP/RTSP/RTP, pairing, FairPlay, AirPlay video with FCUP)
- **JNI Bridge**: Native C code interfaces with Android Java/Kotlin layer
- **Video Decoding**: Android MediaCodec (hardware accelerated)
- **Audio Output**: AudioTrack for low-latency audio
- **HLS Streaming**: Media3/ExoPlayer for video casting
- **mDNS Discovery**: Android NsdManager, publishing the TXT records UxPlay builds

## Building from Source

```bash
# Clone protocol dependencies with the repository
git clone --recurse-submodules git@github.com:weenas/androplay.git
cd CastBay

# Build (requires JDK 17-21, Android SDK 35, NDK 27.0.12077973, and CMake 3.22.1;
# JDK 26 breaks AGP 8.7.3's prefab step)
./gradlew assembleDebug
```

For an existing clone, initialize the pinned dependencies before building:

```bash
git submodule update --init --recursive
```

## Project Structure

```
CastBay/
├── app/                    # Android application
│   ├── src/main/
│   │   ├── java/com/weenas/castbay/
│   │   │   ├── MainActivity.kt
│   │   │   ├── service/       # Service lifecycle and bridge
│   │   │   ├── ui/            # Compose UI screens
│   │   │   ├── viewmodel/     # MVVM ViewModels
│   │   │   └── receiver/      # BroadcastReceivers
│   │   ├── cpp/              # C++ native code (CMake)
│   │   └── res/              # Android resources
│   └── build.gradle.kts
├── airplay/                # Android library, JNI bridge, and native protocol build
├── third_party/            # Pinned UxPlay and libplist submodules
└── README.md
```

## License

This project is licensed under the GNU General Public License v3.0.
See the LICENSE file for details.

## Credits

- [UxPlay](https://github.com/FDH2/UxPlay) - AirPlay protocol library (mirroring, audio, HLS video)
- [RPiPlay](https://github.com/FD-/RPiPlay) - the original core UxPlay's library grew from
- [libplist](https://github.com/libimobiledevice/libplist) - binary plist support
- Android Open Source Project - Base platform

## Disclaimer

This project is not affiliated with or endorsed by Apple Inc.
AirPlay is a trademark of Apple Inc.
