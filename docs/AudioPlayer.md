# Audio Player

WizeFiles provides a lightweight built-in audio player for files the user opens from the browser.
It is deliberately a file-manager playback surface, not a music library.

## Routing and queue

- `InternalOpenPolicy` routes audio to `AudioPlayerActivity`; images and videos remain in the mixed
  media preview, and unsupported MIME families retain the external Android handoff.
- The queue contains MIME- or extension-recognized audio files from the current folder, including
  specialist LibVLC formats, in the browser's active sort order.
  It is bounded to 2,000 items, always retains the selected file, and stays in an in-process session
  store rather than Activity or controller extras.
- Audio entries opened inside an archive continue through the guarded extraction, password, size,
  and error pipeline. The extracted selection opens internally as a one-item queue; WizeFiles never
  extracts archive siblings eagerly.

## Playback architecture

- `AudioPlaybackService` owns one `FallbackMediaPlayer` and MediaSession. The wrapper keeps Media3
  primary, and initializes LibVLC only after Media3 reports an unsupported parser or decoder. The
  Activity still connects through one MediaController, so fallback never moves playback state or
  the queue between owners.
- Queue identifiers are delivered with Media3 custom commands accepted only from WizeFiles or
  trusted system controllers. External apps cannot replace or stop the queue through exported
  start-service actions.
- Media3 supplies the notification, lock-screen, headset, Bluetooth, previous/next, and seek state.
  The service requests media audio focus and pauses when audio would otherwise switch to speakers.
- Sequential playback advances automatically and stops after the last item. Shuffle and repeat are
  disabled.
- Dismissing the task keeps active playback running. If the queue is paused when the task is
  dismissed, the service releases its player, notification, descriptors, and session state.

## Player surface

- Swiping the artwork or pressing Previous/Next selects the same Media3 queue item.
- Title, artist, album, artwork, queue position, elapsed time, duration, seek state, buffering state,
  play/pause state, and notification controls follow the active item.
- Metadata falls back to the filename and an audio placeholder when stream tags or embedded artwork
  are absent. Artwork bytes are held in a small LRU cache instead of being retained for the entire
  possible 2,000-item queue.
- Playback speed, Share, Properties, **Open with another app**, and **Stop playback and close** are
  available from the player.
- Opening another app pauses WizeFiles first. WizeFiles video and audio players both request audio
  focus, preventing simultaneous playback.

## Provider and failure behavior

- Local, SAF, FTP, SFTP, SMB, WebDAV, rclone, and accessible Vault paths reuse WizeFiles' existing
  seekable non-exported FileProvider. Remote media streams rather than requiring an eager full-file
  copy when its provider supports range access.
- Missing files, disconnected providers, invalid sessions, failed seeks, and missing
  metadata/artwork produce controlled errors. Unsupported Media3 parsers/codecs retry once through
  LibVLC; a LibVLC failure remains controlled and **Open with another app** stays available.
- No storage scanning permission, codec pack, FFmpeg, media database, playlists, favorites, artist or
  album browser, equalizer, lyrics, crossfade, recommendations, editing, history, or boot resume is
  added.

## Physical-device validation

1. Open a sorted MP3 → FLAC → M4A → OGG sequence. Swipe artwork and use Previous/Next; confirm the
   filename, metadata, artwork, queue counter, seek state, and notification always target one item.
2. Seek while playing and buffering. Confirm seeking never changes tracks, and automatic advance
   stops after the final item without repeating.
3. Press Back, turn off the screen, and use notification, lock-screen, wired-headset, and Bluetooth
   controls. Confirm all surfaces remain synchronized. Remove headphones and confirm playback pauses.
4. Pause playback and dismiss WizeFiles; confirm the service and notification stop. Repeat while
   playing and confirm playback continues until Stop is selected.
5. Open a WizeFiles video while background audio plays, then open another audio app. Confirm there is
   no overlapping sound and focus transitions are predictable.
6. Test missing tags, no artwork, large embedded artwork, corrupt media, and an unsupported codec.
   Confirm filename/placeholder fallback, bounded memory, controlled errors, and external handoff.
7. Test local storage, SAF, FTP, SFTP, SMB, WebDAV, rclone, and Vault. Disconnect remote providers,
   delete the active file, and lock Vault content during playback; confirm no crash or stuck service.
8. Open audio entries in password-protected ZIP, 7z, and RAR archives. Confirm guarded extraction and
   one-item playback without eager sibling extraction.
9. Verify Light, Dark, Black, dynamic-color, and manual themes in portrait, landscape, tablet,
   foldable, mouse/keyboard, and Android TV configurations.
10. Open a folder with more than 2,000 audio files and verify the selected item remains in the
    bounded queue and Activity/controller extras remain small.

