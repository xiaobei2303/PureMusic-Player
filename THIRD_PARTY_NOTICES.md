# Third-party notices

Pure Music uses AndroidX Media3 / ExoPlayer and the Jellyfin Media3 FFmpeg decoder extension.

- `org.jellyfin.media3:media3-ffmpeg-decoder:1.6.1+1` is distributed under GPL-3.0.
- The FFmpeg libraries included by that decoder have their own notices and licensing terms; consult the dependency's published source and notices for the exact build configuration.
- AndroidX Media3 is distributed under the Apache License 2.0.

Because the selected FFmpeg decoder artifact is GPL-3.0, distributing a combined APK may trigger GPL source-distribution and license obligations for the combined work. Keep this notice, provide the corresponding source and build instructions, and review the upstream license files before public distribution. This project is currently intended as a user-buildable source project, not a commercial closed-source release.
