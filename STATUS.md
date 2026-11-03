# Pure Music development status

## Included in this source snapshot

- [x] Kotlin / Jetpack Compose Android project skeleton
- [x] MediaStore local audio scanning and library search
- [x] Media3 ExoPlayer background service and MediaSession
- [x] Explicit audio-focus handling: pause on permanent/transient/duck focus loss; never auto-resume on focus gain
- [x] LRCLIB automatic lyric lookup
- [x] LRC timestamp parser and active-line highlighting
- [x] Manual lyric document import
- [x] Room playlist creation, deletion, details, playback, adding/removing tracks
- [x] Playback mode setting persisted across launches
- [x] Repeat-all and repeat-one via Media3 repeat modes
- [x] Independent equal-probability random draw, including possible immediate repeats, at automatic track boundaries
- [x] Random selection for the in-app Next button
- [x] MediaSession forwarding player routes lock-screen / notification / Bluetooth Next commands through the independent random selector
- [x] Custom background image selection with persisted URI
- [x] Background blur, strength and brightness controls persisted locally
- [x] Jellyfin Media3 FFmpeg decoder dependency and preferred extension renderer mode
- [x] Unit tests for random-selection bounds, approximate distribution and repeat-allowed behavior
- [x] GitHub Actions workflow to compile, test and upload a debug APK

## Not yet verified / remaining work

- [ ] Compile with Android SDK / Gradle and fix any build errors found by CI
- [ ] Verify MediaSession / Bluetooth next-button behavior in random mode on real devices (source implementation added; device behavior not yet tested)
- [ ] Verify random mode and repeat modes on real devices
- [ ] Verify custom background persistence and large-image memory use
- [ ] Test incoming calls, notification sounds, navigation prompts and other audio-focus interruptions
- [ ] Test headset disconnect behavior
- [ ] Test each codec/container combination on multiple Android devices
- [ ] Verify FFmpeg native libraries for target ABIs and 16 KB page-size compatibility
- [x] Full-screen-style Now Playing page and synchronized lyric auto-scroll added in v3
- [ ] Verify signed release APK and install/update behavior

**Important:** This is an updated source snapshot, not a compiled or device-tested APK. The current environment does not contain Android SDK / Gradle, so no APK build is claimed as successful.

## v4 playback-control update

- [x] Added a Media3 `ForwardingPlayer` around the ExoPlayer session player. In random mode, `seekToNext()` and `seekToNextMediaItem()` each independently choose any queue item with equal probability; the current item remains eligible.
- [x] Advertised next commands when a queue exists so controllers connected before/after mode changes can still expose Next.
- [x] Changed the Android launcher label to simplified Chinese (“纯净音乐”).
- [x] Refined audio-focus loss handling: transient loss pauses without abandoning the request; focus gain only marks focus available and never resumes playback. Permanent loss abandons focus.

Still pending: compile/API verification for the forwarding wrapper and on-device lock-screen/Bluetooth testing.

## v3 interface update

- [x] Added a dedicated full-screen-style “正在播放” page with local album artwork (when MediaStore exposes it), track details, seek slider, previous/play-pause/next and playback-mode controls.
- [x] Added a fourth bottom navigation destination named “播放”; tapping the mini-player track information also opens it.
- [x] Added automatic scrolling to the active synchronized lyric line.
- [x] Kept untimed/plain lyrics from incorrectly jumping to the final line.
- [x] Changed the visible app brand label to simplified Chinese (“纯净音乐”); visible navigation and controls use simplified Chinese.

Still not verified: Android Gradle compilation, Bluetooth/media-notification Next behavior in random mode, real-device playback and codec testing. Album artwork is a best-effort local MediaStore lookup; a generic music icon is shown when artwork is unavailable.
