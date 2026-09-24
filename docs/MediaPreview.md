# Media Preview

WizeFiles provides a focused built-in preview for images and videos. It is intentionally a file
inspection surface, not a gallery, image/video editor, music library, or background media player.
Audio uses its own service-backed player documented in `docs/AudioPlayer.md`. Both players keep
Media3 primary and use the shared LibVLC backend only after a parser/decoder capability failure.

## Routing boundary

- Image and video files recognized by MIME type or filename extension use `InternalOpenPolicy`
  and `MediaPreviewActivity`, including specialist LibVLC video containers.
- **Open with another app** remains available from the preview and from the existing selected-file
  menu.
- Audio opens the dedicated WizeFiles audio player. Documents, unsupported MIME families, APKs, and
  other file types keep the existing Android app handoff, except PDF files which use the dedicated
  reader documented in `docs/PdfViewer.md`.
- Supported image and video entries inside archives use the existing guarded extraction/cache
  pipeline, then open internally as a single preview item. Audio uses the same extraction boundary
  before opening as a single-item audio queue; unsupported entries keep the external handoff.
- The current path and MIME type are stored in the Activity Intent for process-death recovery.
  Directory sibling lists remain in a bounded in-process session store and are never put in extras.
- Normal browser sessions contain supported images and videos together in the browser's current sort
  order. Audio, documents, folders, archives, and unsupported files are omitted from the pager.

## Image preview

- Static images use tiled decoding for zoomable large-image memory safety and honor EXIF orientation.
- GIF, SVG, and WebP use the existing Coil/PhotoView pipeline for animation and zoom/pan.
- A real `ViewPager2` page moves between adjacent images and videos while the image is at base zoom.
- Zoomed images keep horizontal gestures for panning; returning to base zoom re-enables page swiping.
- Rotate, Share, Properties, and **Open with another app** are available from the toolbar.

## Video preview

- Media3 provides play/pause, seeking, buffering, fullscreen, rotation, and playback speed. A
  shared `FallbackMediaPlayer` preserves the same controls when LibVLC supplies decoding.
- Horizontal swiping moves directly between videos and images. Player controls and seek-bar drags
  keep their normal behavior instead of triggering page navigation.
- The Activity owns one `FallbackMediaPlayer`. It attaches only to the selected video, releases when the selected
  page becomes an image or the Activity stops, and never prepares adjacent videos.
- Playback position, play intent, speed, selected page, and image rotation survive recreation.
  Per-page playback and rotation state also survive swiping away and back during the viewer session.
- The image/video surface uses `media3-common`, `media3-exoplayer`, and `media3-ui`. The app's
  `media3-session` dependency, notification, and media-playback foreground service belong only to
  the separate audio player. Transformer and effects remain excluded.
- Playback uses WizeFiles' existing non-exported `FileProvider`. Local, SAF, FTP, SFTP, SMB, WebDAV,
  and rclone paths can therefore use the same seekable proxy descriptor already used for external
  apps when the provider supports range access.
- Unsupported codecs, disconnected providers, deleted files, locked Vault content, and failed seeks
  end in a recoverable error surface with **Open with another app**.

## Physical-device validation

1. In one sorted directory, open a sequence such as JPEG → MP4 → PNG → WebM. Swipe in both
   directions and verify the page, title, counter, menu actions, Share, Properties, and external
   handoff always target the visible item.
2. Verify JPEG, PNG, large panorama, EXIF-rotated JPEG, GIF, SVG, and WebP zoom/pan behavior. At base
   zoom, horizontal movement changes pages; while zoomed, it pans without accidental navigation.
3. Open MP4/H.264, WebM/VP9, and one unsupported-codec sample. Verify play/pause, seek, buffering,
   speed, fullscreen swiping, rotation, resume after recreation, and external fallback. Confirm only
   the visible video plays and audio stops when the selected page changes.
4. Open representative image and video entries inside ZIP, 7z, and RAR archives. Verify extraction
   progress, password handling, internal preview, Share, Properties, and **Open with another app**.
   Confirm archive preview opens only the selected entry instead of eagerly extracting siblings.
5. Repeat representative image and video cases from local storage, a SAF document tree, FTP, SFTP,
   SMB, WebDAV, and rclone. Confirm playback begins without a complete pre-download where range
   access is available.
6. Disconnect each remote provider during load/playback, then retry after reconnecting. Delete or
   move the current file and lock Vault content while preview is open. Confirm a controlled error,
   no crash, and no stuck keep-screen-on flag.
7. Verify Light, Dark, Black, dynamic-color, and manual themes in portrait, landscape, tablet,
   foldable dual-pane, mouse/keyboard, and Android TV configurations.
8. Open directories with more than 2,000 mixed image/video files and verify the current item remains
   in the bounded sibling window and Activity extras stay small.

The media preview remains Activity-bound. Audio background playback is isolated in its separate
service and queue, so it does not add adjacent audio pages or persistent video playback here.

