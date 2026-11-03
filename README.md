# Pure Music

Pure Music is a local-first Android music player project. It is intentionally limited to listening to music: no streaming catalog, social feed, video feed, advertising SDK or account system.

## Current project scope

- Scan on-device audio through Android MediaStore.
- Search the local library by title, artist and album.
- Create local playlists and add/remove tracks.
- Play in a Media3 background media session.
- Import local LRC/TXT lyric files and optionally search LRCLIB when a track is selected.
- Three playback modes: equal-probability random, repeat-all and repeat-one.
- Choose a background image from the device and persist it; adjust blur, image strength and brightness.
- Simplified-Chinese interface with a dedicated Now Playing screen, local album-art lookup, seek control and automatically scrolling synchronized lyrics.
- On any audio-focus loss, pause. Audio-focus gain deliberately does not restart playback; the user must press Play.
- FFmpeg extension is configured as a preferred decoder fallback for formats supported by the bundled decoder.

## Random playback definition

Shuffle mode performs a fresh independent uniform draw over all entries in the active queue at each automatic track boundary. The currently playing item is not excluded, so selecting the same track twice in a row is a valid outcome. This is intentionally different from a conventional shuffled permutation that plays every item once before repeating.

The manual Next button uses the same independent random selection in shuffle mode. Repeat-all and repeat-one are handled by Media3 repeat modes.

## Audio format support

Media3 platform decoders and the Jellyfin FFmpeg extension cover many common formats, including MP3, AAC/M4A, FLAC, WAV, Ogg Vorbis and Opus, with additional support depending on the decoder build and file container. No app can promise every possible codec/container combination: damaged files, DRM-protected files and uncommon formats may still fail. Verify each target format on real devices before advertising it as supported.

The FFmpeg decoder artifact is GPL-3.0. Review `THIRD_PARTY_NOTICES.md` and the upstream license/source obligations before redistributing APKs. This project is not presented as a closed-source commercial build.

## Build

Requirements:

- Android Studio with Android SDK Platform 35, or JDK 17+ and Android SDK Platform 35.
- Gradle 8.11.1 or compatible Gradle installation.

Open this folder in Android Studio and let it sync, then run `:app:assembleDebug` and `:app:testDebugUnitTest`.

A GitHub Actions workflow is included at `.github/workflows/android.yml`. Push the source to a GitHub repository to compile and run unit tests in CI; the debug APK is uploaded as a workflow artifact.

## Current validation status

This source snapshot has not been compiled or tested against an Android SDK in the current environment. The CI workflow is provided to perform that build on a configured runner. Device testing is still required for background playback, call interruptions, Bluetooth/headset behavior, storage permissions, lyric lookup and each advertised audio format.


## v4 playback behavior

The MediaSession now exposes a forwarding player that intercepts both `seekToNext()` and `seekToNextMediaItem()`. When the saved mode is true random, each external Next action independently samples every queue item with equal probability; the current track is intentionally not excluded. This covers the app's MediaSession transport path used by lock-screen/notification controls and compatible Bluetooth controllers. It still requires Android build and real-device verification.

The launcher app label is simplified Chinese: “纯净音乐”. Transient audio-focus loss pauses playback while retaining the focus request; a later focus gain does not resume playback automatically.
