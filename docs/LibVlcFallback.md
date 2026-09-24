# LibVLC fallback playback

WizeFiles keeps Media3 as the primary audio and video engine. LibVLC 3.7.5 is a compatibility
fallback for uncommon containers and codecs; it is not initialized for ordinary playback.

## Activation boundary

`FallbackMediaPlayer` is the only Player exposed to `PlayerView` and `MediaSession`. It forwards
normal playback to ExoPlayer and activates LibVLC only after Media3 reports one of the bounded
container-parser or decoder capability error codes listed in `LibVlcFallbackPolicy`. File,
provider, network, DRM, and unrelated runtime errors do not trigger a second decoder.

Filename routing activates the registered specialist audio and video extensions even when a
provider reports `application/octet-stream`. Archive entries still remain external until the
existing guarded extraction job completes.

## Preserved behavior

- Audio keeps one service, MediaSession, controller, queue, notification, lock-screen surface,
  previous/next controls, playback speed, and bounded session store.
- Video keeps one PlayerView, pager owner, fullscreen control, playback state, and screen-on policy.
- LibVLC playback requests platform audio focus, handles transient/permanent focus loss, and pauses
  for `ACTION_AUDIO_BECOMING_NOISY`.
- A failed LibVLC retry becomes a normal Player error. The existing **Open with another app** action
  remains available.
- Sequential audio retries Media3 on the next item, so supported neighbors return to the primary
  backend instead of keeping LibVLC active for the whole queue.

## Native provenance

The AAR is pinned as `org.videolan.android:libvlc-all:3.7.5` with SHA-256
`2c25507adb1260aa4d81aad8c2ce98765d98026b9381f49ea454d0b8092f21cb`. The
93,158,778-byte AAR contains `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`; every
`arm64-v8a` and `x86_64` `libc++_shared.so`, `libvlc.so`, and `libvlcjni.so` PT_LOAD segment has
16 KiB alignment. Release validation must measure the resulting per-ABI and universal APK sizes
before merge.

## Physical-device validation

1. Verify common MP3, FLAC, M4A, MP4, MKV, and WebM files remain on Media3.
2. Verify AC3, APE, DSF/DFF, DTS, MOD/XM/S3M, WMA/WV and specialist video containers such as
   FLV, M2TS, MXF, RMVB, TS, VOB, and WMV retry through LibVLC when Media3 rejects them.
3. Exercise play/pause, seek, speed, previous/next, notification, lock screen, wired headset,
   Bluetooth, audio focus, and unplug-to-pause while LibVLC is active.
4. Test local, SAF, network, cloud, rclone, Vault, and guarded extracted archive sources. A source
   that LibVLC cannot open must show the controlled error without looping or crashing.
5. Compare debug and minified release APK sizes and rerun the 16 KiB native-alignment gate.
