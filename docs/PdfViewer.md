# PDF Viewer

WizeFiles provides a focused, read-only PDF reader for documents opened from the file browser. It
uses AndroidX PDF behind WizeFiles-owned Activity, fragment, routing, provider, and link-policy
boundaries. It is a document inspection surface, not a PDF editor.

The app compiles against SDK 36 with extension 19, as required by AndroidX PDF alpha19. Runtime
reading/rendering remains backported by the library to WizeFiles' API 30 minimum.

## Routing and sources

- `InternalOpenPolicy` routes `application/pdf` to `PdfViewerActivity`; images, videos, audio, and
  unsupported documents retain their existing destinations.
- Local, root, SAF, FTP, SFTP, SMB, WebDAV, rclone/cloud, and accessible Vault paths reuse the
  existing seekable WizeFiles `FileProvider` URI. The viewer does not request storage permissions or
  copy a remote PDF eagerly merely to open it.
- PDF entries inside archives remain behind the existing password, extraction-size, failure, and
  cache lifecycle. Only the selected entry is extracted; PDF siblings are never extracted eagerly.

## Reader behavior
- Pages render progressively in a continuous vertical reader with pinch/double-tap zoom, fast page
  navigation, text selection, copying, document search, password prompts, internal bookmarks,
  accessibility, and keyboard/mouse navigation supplied by AndroidX PDF.
- Phones and narrow windows use one page per row. Windows at least 840 dp wide use two pages per row
  and preserve the first visible page when the layout changes.
- Search closes before Back exits fullscreen or the Activity. Rotation and process recreation reuse
  the Intent path and AndroidX fragment state rather than storing PDF bytes or passwords.
- Share, Properties, **Open with another app**, fullscreen, Retry, and Close/Back remain available.
- Dark themes style WizeFiles chrome and the surrounding surface; PDF page colors are not inverted.

## Security and failure behavior
- `PdfViewerActivity` is non-exported and no system-wide PDF intent filter is registered.
- The AndroidX annotation toolbox is disabled. WizeFiles does not issue `ACTION_ANNOTATE`, write PDF
  changes, embed a WebView, or execute PDF JavaScript.
- Internal page links stay in the reader. Only `https`, `http`, and `mailto` external links are
  handed to Android; `file`, `content`, `intent`, `javascript`, `data`, and custom schemes are
  blocked.
- Passwords remain inside AndroidX's transient password flow and are never stored, logged, or placed
  in Activity extras.
- Missing, corrupt, unsupported, disconnected, or unreadable PDFs show a controlled error with
  Retry and **Open with another app** instead of a blank screen.

## Product boundary

This release does not add annotation, form saving, signatures, text editing, page manipulation,
merge/split, redaction, PDF creation, OCR, printing, persistent bookmarks, reading history, or
system-wide PDF registration.

## Physical-device validation

1. Open text, image-heavy, malformed, zero-byte, password-protected, linked, and 250 MB-class PDFs.
2. Verify search, selection, Copy/Select all, internal bookmarks, allowed web/mail links, and blocked
   local/custom schemes.
3. Rotate, resize multi-window, fold/unfold, and enter/exit fullscreen; confirm the reading position
   and one/two-page layout remain stable.
4. Test local, root, SAF, FTP, SFTP, SMB, WebDAV, rclone, and Vault PDFs, including disconnection,
   deletion, rename, and Vault relock while viewing.
5. Open password-protected ZIP, 7z, and RAR PDF entries; confirm single-entry guarded extraction and
   cache cleanup.
6. Verify API 30, 35, and 36 devices, accessibility services, D-pad, mouse, keyboard, Light, Dark,
   Black, dynamic-color, portrait, landscape, tablet, and foldable configurations.
7. Record APK size before and after the AndroidX PDF dependency before merging.
